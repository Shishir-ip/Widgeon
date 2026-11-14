package com.widgeon.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.widgeon.app.data.Background
import com.widgeon.app.data.Element
import com.widgeon.app.data.ElementTypes
import com.widgeon.app.data.WidgetDesign
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the renderer needs about "now". */
data class LiveSnapshot(
    val timeMillis: Long = System.currentTimeMillis(),
    val battery: Int = 100,
    val weatherTemp: Double? = null,
    val weatherCode: Int? = null,
)

/**
 * Canvas layout engine: measures + draws the element tree of a [WidgetDesign]
 * into a bitmap. The exact same code path renders the in-app preview and the
 * real home-screen widget, guaranteeing WYSIWYG.
 */
object DesignRenderer {

    fun render(
        design: WidgetDesign,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        data: LiveSnapshot,
    ): Bitmap {
        val w = widthPx.coerceAtLeast(8)
        val h = heightPx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ink = ColorUtil.inkFor(design.background.color, design.background.colorEnd)
        val ctx = Ctx(density, data, ink)

        drawBackground(canvas, design.background, w.toFloat(), h.toFloat(), ctx)

        val pad = design.padding * density
        val innerW = (w - pad * 2).roundToInt().coerceAtLeast(4)
        val innerH = (h - pad * 2).roundToInt().coerceAtLeast(4)
        val root = Element(type = ElementTypes.COLUMN, gap = 6, elements = design.elements)
        drawElement(canvas, root, pad, pad, innerW.toFloat(), innerH.toFloat(), ctx)
        return bmp
    }

    // ------------------------------------------------------------------ ctx

    private class Ctx(val d: Float, val data: LiveSnapshot, val ink: Int)

    private fun Float.dp(ctx: Ctx) = this * ctx.d
    private fun Int.dp(ctx: Ctx) = this * ctx.d

    private fun Element.isBatteryBar() = type == ElementTypes.BATTERY && style == "bar"

    // ----------------------------------------------------------- background

    private fun drawBackground(canvas: Canvas, bg: Background, w: Float, h: Float, ctx: Ctx) {
        val radius = bg.cornerRadius.dp(ctx)
        val rect = RectF(0f, 0f, w, h)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val start = ColorUtil.parse(bg.color) ?: 0xE6111111.toInt()
        if (bg.type == "gradient" && bg.colorEnd != null) {
            val end = ColorUtil.parse(bg.colorEnd) ?: start
            val x0: Float; val y0: Float; val x1: Float; val y1: Float
            when (bg.orientation.uppercase()) {
                "BL_TR" -> { x0 = 0f; y0 = h; x1 = w; y1 = 0f }
                "T_B" -> { x0 = 0f; y0 = 0f; x1 = 0f; y1 = h }
                "L_R" -> { x0 = 0f; y0 = 0f; x1 = w; y1 = 0f }
                "R_L" -> { x0 = w; y0 = 0f; x1 = 0f; y1 = 0f }
                else -> { x0 = 0f; y0 = 0f; x1 = w; y1 = h }
            }
            paint.shader = LinearGradient(x0, y0, x1, y1, start, end, Shader.TileMode.CLAMP)
        } else {
            paint.color = start
        }
        canvas.drawRoundRect(rect, radius, radius, paint)

        val border = ColorUtil.parse(bg.borderColor)
        if (border != null && bg.borderWidth > 0) {
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = bg.borderWidth.dp(ctx)
                color = border
            }
            val inset = bp.strokeWidth / 2f
            canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, bp)
        }
    }

    // ------------------------------------------------------------- measuring

    /** Returns [w, h] in px for the element given the available box. */
    private fun measuredSize(el: Element, availW: Float, availH: Float, ctx: Ctx, inRow: Boolean): FloatArray {
        val d = ctx.d
        val fixedW = el.width?.times(d)
        val fixedH = el.height?.times(d)
        val pad2 = el.padding.dp(ctx) * 2f

        return when (el.type) {
            ElementTypes.ROW, ElementTypes.COLUMN ->
                floatArrayOf(fixedW ?: availW, fixedH ?: availH)

            ElementTypes.TEXT, ElementTypes.CLOCK, ElementTypes.DATE,
            ElementTypes.WEATHER -> {
                val paint = textPaint(el, ctx)
                val w = fixedW ?: availW
                val layout = staticLayout(resolveText(el, ctx), paint, w.toInt().coerceAtLeast(4), el.maxLines)
                floatArrayOf(w, min(layout.height.toFloat(), availH.coerceAtLeast(4f)) + pad2)
            }

            ElementTypes.BATTERY -> if (el.isBatteryBar()) {
                floatArrayOf(fixedW ?: availW, fixedH ?: 8f.dp(ctx))
            } else {
                val paint = textPaint(el, ctx)
                val w = fixedW ?: availW
                val layout = staticLayout(resolveText(el, ctx), paint, w.toInt().coerceAtLeast(4), el.maxLines)
                floatArrayOf(w, min(layout.height.toFloat(), availH.coerceAtLeast(4f)) + pad2)
            }

            ElementTypes.ICON -> {
                val s = fixedH ?: (el.fontSize * 1.7f).dp(ctx)
                floatArrayOf(s, s)
            }

            ElementTypes.DIVIDER -> floatArrayOf(fixedW ?: availW, el.thickness.coerceAtLeast(1).dp(ctx))
            ElementTypes.SPACER -> floatArrayOf(fixedW ?: 0f, fixedH ?: 0f)

            ElementTypes.PROGRESS ->
                floatArrayOf(fixedW ?: availW, fixedH ?: 8f.dp(ctx))

            ElementTypes.BUTTON -> {
                val paint = textPaint(el, ctx)
                val textW = paint.measureText(el.text ?: "Button")
                val w = fixedW ?: (if (inRow) textW + 28f.dp(ctx) else availW)
                val h = fixedH ?: (el.fontSize * 2.3f).dp(ctx).coerceAtLeast(28f.dp(ctx))
                floatArrayOf(w, h)
            }

            else -> floatArrayOf(fixedW ?: availW, fixedH ?: (el.fontSize * 1.4f).dp(ctx))
        }
    }

    // -------------------------------------------------------------- drawing

    private fun drawElement(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        el.background?.let { raw ->
            ColorUtil.parse(raw)?.let { color ->
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
                canvas.drawRoundRect(RectF(x, y, x + w, y + h), el.cornerRadius.dp(ctx), el.cornerRadius.dp(ctx), p)
            }
        }

        val pad = el.padding.dp(ctx)
        val ix = x + pad
        val iy = y + pad
        val iw = (w - pad * 2).coerceAtLeast(2f)
        val ih = (h - pad * 2).coerceAtLeast(2f)

        when (el.type) {
            ElementTypes.ROW -> drawRow(canvas, el, ix, iy, iw, ih, ctx)
            ElementTypes.COLUMN -> drawColumn(canvas, el, ix, iy, iw, ih, ctx)
            ElementTypes.ICON -> drawIcon(canvas, el, ix, iy, iw, ih, ctx)
            ElementTypes.DIVIDER -> {
                val p = Paint().apply {
                    color = ColorUtil.parseOr(el.color, ctx.ink)
                    strokeWidth = el.thickness.coerceAtLeast(1).dp(ctx)
                }
                val cy = iy + ih / 2f
                canvas.drawLine(ix, cy, ix + iw, cy, p)
            }
            ElementTypes.PROGRESS -> drawBar(canvas, ix, iy, iw, ih, el.value / 100f, el, ctx)
            ElementTypes.BATTERY -> if (el.isBatteryBar()) {
                drawBar(canvas, ix, iy, iw, ih, ctx.data.battery / 100f, el, ctx)
            } else {
                drawTextLike(canvas, el, ix, iy, iw, ih, ctx)
            }
            ElementTypes.BUTTON -> drawButton(canvas, el, ix, iy, iw, ih, ctx)
            ElementTypes.SPACER -> Unit
            else -> drawTextLike(canvas, el, ix, iy, iw, ih, ctx)
        }
    }

    private fun drawTextLike(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val paint = textPaint(el, ctx)
        val layout = staticLayout(resolveText(el, ctx), paint, w.toInt().coerceAtLeast(4), el.maxLines)
        var dx = x
        if (el.align == "center" && layout.width < w) dx = x + (w - layout.width) / 2f
        if (el.align == "end" && layout.width < w) dx = x + (w - layout.width)
        val dy = if (layout.height > h) y - (layout.height - h) / 2f else y
        canvas.save()
        canvas.translate(dx, dy)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawIcon(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val emoji = el.emoji ?: el.text ?: return
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = min(w, h) * 0.86f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
        }
        val cy = y + h / 2f - (p.descent() + p.ascent()) / 2f
        canvas.drawText(emoji, x + w / 2f, cy, p)
    }

    private fun drawBar(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, frac: Float, el: Element, ctx: Ctx) {
        val r = h / 2f
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.color = el.trackColor?.let { ColorUtil.parse(it) } ?: (ctx.ink and 0x00FFFFFF or 0x26000000)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, p)
        p.color = ColorUtil.parseOr(el.color, ctx.ink)
        val fw = (w * frac.coerceIn(0f, 1f)).coerceAtLeast(h.coerceAtMost(w))
        if (fw > 0f) canvas.drawRoundRect(RectF(x, y, x + fw, y + h), r, r, p)
    }

    private fun drawButton(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val bg = ColorUtil.parse(el.background) ?: ctx.ink
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        val r = if (el.cornerRadius > 0) el.cornerRadius.dp(ctx) else h / 2f
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), r, r, p)

        val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = el.fontSize.dp(ctx)
            typeface = typefaceFor(el)
            color = ColorUtil.parseOr(el.color, contrastFor(bg))
            textAlign = Paint.Align.CENTER
        }
        val cy = y + h / 2f - (tp.descent() + tp.ascent()) / 2f
        canvas.drawText(el.text ?: "Button", x + w / 2f, cy, tp)
    }

    // ---------------------------------------------------------- containers

    private fun drawColumn(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val children = el.elements
        if (children.isEmpty()) return
        val gap = el.gap.dp(ctx)
        val sizes = children.map { measuredSize(it, w, h, ctx, inRow = false).copyOf() }

        // Weight distribution on the main (vertical) axis
        val fixedTotal = sizes.sumOf { it[1].toDouble() }.toFloat() + gap * (children.size - 1)
        val leftover = (h - fixedTotal).coerceAtLeast(0f)
        val weightSum = children.sumOf { it.weight.toDouble() }.toFloat()
        if (weightSum > 0.01f) {
            children.forEachIndexed { i, c ->
                if (c.weight > 0) sizes[i][1] += leftover * (c.weight / weightSum)
            }
        }

        var cy = y
        children.forEachIndexed { i, c ->
            val (cw, ch) = sizes[i]
            val cx = when (el.align) {
                "center" -> x + (w - cw) / 2f
                "end" -> x + (w - cw)
                else -> x
            }
            drawElement(canvas, c, cx, cy, cw, ch.coerceAtMost(y + h - cy), ctx)
            cy += sizes[i][1] + gap
        }
    }

    private fun drawRow(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val children = el.elements
        if (children.isEmpty()) return
        val gap = el.gap.dp(ctx)
        val sizes = children.map { measuredSize(it, w, h, ctx, inRow = true).copyOf() }

        // Unweighted children without a fixed width share the leftover width equally
        val usedByFixed = sizes.withIndex().sumOf { (i, s) ->
            val c = children[i]
            if (c.weight > 0f || c.width != null || c.type == ElementTypes.BUTTON) s[0].toDouble() else 0.0
        }.toFloat() + gap * (children.size - 1)
        val flexibleIdx = children.indices.filter {
            children[it].weight <= 0f && children[it].width == null && children[it].type != ElementTypes.BUTTON
        }
        val share = if (flexibleIdx.isNotEmpty()) ((w - usedByFixed).coerceAtLeast(0f) / flexibleIdx.size) else 0f
        flexibleIdx.forEach { sizes[it][0] = share.coerceAtLeast(8f) }

        // Weight distribution on the main (horizontal) axis
        val fixedTotal = sizes.sumOf { it[0].toDouble() }.toFloat() + gap * (children.size - 1)
        val leftover = (w - fixedTotal).coerceAtLeast(0f)
        val weightSum = children.sumOf { it.weight.toDouble() }.toFloat()
        if (weightSum > 0.01f) {
            children.forEachIndexed { i, c ->
                if (c.weight > 0) sizes[i][0] += leftover * (c.weight / weightSum)
            }
        }

        var cx = x
        children.forEachIndexed { i, c ->
            val (cw, ch) = sizes[i]
            val cy = when (el.align) {
                "center" -> y + (h - ch) / 2f
                "end" -> y + (h - ch)
                else -> y
            }
            drawElement(canvas, c, cx, cy, cw.coerceAtMost(x + w - cx), ch, ctx)
            cx += sizes[i][0] + gap
        }
    }

    // ------------------------------------------------------------- helpers

    private fun textPaint(el: Element, ctx: Ctx): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = el.fontSize.dp(ctx)
        color = ColorUtil.parseOr(el.color, ctx.ink)
        typeface = typefaceFor(el)
        letterSpacing = el.spacing
    }

    private fun typefaceFor(el: Element): Typeface {
        val style = when {
            el.bold && el.italic -> Typeface.BOLD_ITALIC
            el.bold -> Typeface.BOLD
            el.italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(el.font, style)
    }

    private fun staticLayout(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout {
        val clamped = text.take(400)
        return StaticLayout.Builder
            .obtain(clamped, 0, clamped.length, paint, width.coerceAtLeast(4))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.06f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
    }

    private fun resolveText(el: Element, ctx: Ctx): String = when (el.type) {
        ElementTypes.CLOCK -> formatDate(el.format ?: "HH:mm", ctx.data.timeMillis)
        ElementTypes.DATE -> formatDate(el.format ?: "EEE, MMM d", ctx.data.timeMillis)
        ElementTypes.BATTERY -> "${ctx.data.battery}%"
        ElementTypes.WEATHER -> when (el.style) {
            "condition" -> weatherEmoji(ctx.data.weatherCode)
            else -> ctx.data.weatherTemp?.let { "${it.roundToInt()}°" } ?: "--°"
        }
        ElementTypes.ICON -> el.emoji ?: el.text ?: ""
        else -> el.text ?: ""
    }

    private fun formatDate(pattern: String, millis: Long): String = try {
        SimpleDateFormat(sanitizePattern(pattern), Locale.getDefault()).format(Date(millis))
    } catch (_: Throwable) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    /** Keeps only SimpleDateFormat pattern letters + safe punctuation. */
    private fun sanitizePattern(p: String): String {
        val allowed = "yYMLdDEaHhKkmsSzZX'/:,-. ".toSet()
        return p.filter { it in allowed }
    }

    fun weatherEmoji(code: Int?): String = when (code) {
        0 -> "☀️"
        1, 2 -> "🌤️"
        3 -> "☁️"
        45, 48 -> "🌫️"
        in 51..57 -> "🌦️"
        in 61..67 -> "🌧️"
        in 71..77 -> "🌨️"
        in 80..82 -> "🌧️"
        in 85..86 -> "🌨️"
        in 95..99 -> "⛈️"
        else -> "🌡️"
    }

    private fun contrastFor(color: Int): Int =
        if (ColorUtil.luminance(color) > 0.6f) 0xF0141414.toInt() else 0xFFF7F7F7.toInt()
}
