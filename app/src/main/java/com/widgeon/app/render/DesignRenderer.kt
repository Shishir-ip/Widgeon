package com.widgeon.app.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Everything the renderer needs about "now". */
data class LiveSnapshot(
    val timeMillis: Long = System.currentTimeMillis(),
    val battery: Int = 100,
    val weatherTemp: Double? = null,
    val weatherCode: Int? = null,
    /** Global slideshow counter; rotating images use (rotation + index) % images.size. */
    val rotation: Int = 0,
)

/**
 * A clock/date element the LAUNCHER renders live via a TextClock overlay
 * (Android 12+). Positions are in dp, relative to the widget root.
 */
data class LiveOverlay(
    val leftDp: Float,
    val topDp: Float,
    val widthDp: Float,
    val fontSizeDp: Float,
    val color: Int,
    val pattern: String,
    val style: Int,
)

data class RenderResult(val bitmap: Bitmap, val overlays: List<LiveOverlay>)

/**
 * Canvas layout engine: measures + draws the element tree of a [WidgetDesign]
 * into a bitmap. The same code path renders the in-app preview and the real
 * home-screen widget, guaranteeing WYSIWYG. Content is auto-fitted: if the
 * design overflows its box, type/gaps scale down until it fits.
 */
object DesignRenderer {

    fun render(
        design: WidgetDesign,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        data: LiveSnapshot,
        imageLoader: ((String) -> Bitmap?)? = null,
    ): Bitmap = renderDetailed(design, widthPx, heightPx, density, data, includeLive = true, imageLoader = imageLoader).bitmap

    fun renderDetailed(
        design: WidgetDesign,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        data: LiveSnapshot,
        includeLive: Boolean,
        imageLoader: ((String) -> Bitmap?)? = null,
    ): RenderResult {
        val w = widthPx.coerceAtLeast(8)
        val h = heightPx.coerceAtLeast(8)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val ink = ColorUtil.inkFor(design.background.color, design.background.colorEnd)

        val pad = design.padding * density
        val innerW = (w - pad * 2).roundToInt().coerceAtLeast(4)
        val innerH = (h - pad * 2).roundToInt().coerceAtLeast(4)
        val root = Element(type = ElementTypes.COLUMN, gap = 6, elements = design.elements)

        // Adaptive scale: render target vs the size the design was composed for.
        // A 2×2 design dropped into a 4×4 slot grows; shrinking back shrinks it.
        val targetWDp = w / density
        val targetHDp = h / density
        val nominal = com.widgeon.app.ai.PromptBuilder.approxDp(design.cols, design.rows)
        val adaptive = min(targetWDp / nominal.first, targetHDp / nominal.second).coerceIn(0.45f, 2.2f)

        // Pass 1 — measure how much the tree overflows its box at the adaptive scale.
        val probe = Ctx(density, data, ink, adaptive, 1f, includeLive, imageLoader, design.images)
        val rootFit = fitOf(root, innerW.toFloat(), innerH.toFloat(), probe, 0)

        // Pass 2 — render with the overflow correction applied on top.
        val fit = if (rootFit.factor > 1.02f) (1f / rootFit.factor).coerceIn(0.4f, 1f) else 1f
        val ctx = Ctx(density, data, ink, adaptive, fit, includeLive, imageLoader, design.images)

        drawBackground(canvas, design.background, w.toFloat(), h.toFloat(), ctx)
        drawElement(canvas, root, pad, pad, innerW.toFloat(), innerH.toFloat(), ctx)
        return RenderResult(bmp, ctx.overlays)
    }

    // ------------------------------------------------------------------ ctx

    private class Ctx(
        val d: Float,
        val data: LiveSnapshot,
        val ink: Int,
        /** Actual widget size vs the design's nominal size — scales ALL dp values. */
        val adaptive: Float,
        /** Overflow correction (≤ 1) from the fit pass. */
        val fit: Float,
        val includeLive: Boolean,
        val imageLoader: ((String) -> Bitmap?)?,
        val images: List<String>,
    ) {
        val total: Float = adaptive * fit
        val overlays = ArrayList<LiveOverlay>(4)
    }

    private fun Float.dp(ctx: Ctx) = this * ctx.d * ctx.adaptive
    private fun Int.dp(ctx: Ctx) = this * ctx.d * ctx.adaptive
    private fun Float.ts(ctx: Ctx) = this * ctx.d * ctx.total  // text-scaled dp
    private fun Int.ts(ctx: Ctx) = this * ctx.d * ctx.total

    private fun Element.isLiveText() = type == ElementTypes.CLOCK || type == ElementTypes.DATE

    // -------------------------------------------------------------- fitting

    private class Fit(val factor: Float, val required: Float)

    /**
     * Recursively estimates how much a container's content overflows its box.
     * [factor] > 1 means content needs (factor ×) the available space on the
     * container's main axis; [required] is the estimated main-axis size.
     */
    private fun fitOf(el: Element, availW: Float, availH: Float, ctx: Ctx, depth: Int): Fit {
        if (depth > 6) return Fit(1f, 0f)
        val pad2 = el.padding.dp(ctx) * 2f
        val gap = el.gap.ts(ctx)
        val innerW = ((el.width?.dp(ctx) ?: availW) - pad2).coerceAtLeast(4f)
        val innerH = ((el.height?.dp(ctx) ?: availH) - pad2).coerceAtLeast(4f)
        val kids = el.elements
        if (kids.isEmpty()) return Fit(1f, 0f)

        val isColumn = el.type == ElementTypes.COLUMN
        var factor = 1f
        var mainTotal = gap * (kids.size - 1).coerceAtLeast(0)
        var crossMax = 0f

        for (c in kids) {
            if (c.weight > 0f) continue // flexible: absorbs leftover space
            when {
                c.type == ElementTypes.SPACER ->
                    mainTotal += ((if (isColumn) c.height else c.width)?.dp(ctx) ?: 0f)

                c.type == ElementTypes.ROW || c.type == ElementTypes.COLUMN -> {
                    val f = fitOf(c, innerW, innerH, ctx, depth + 1)
                    factor = max(factor, f.factor)
                    if (isColumn) mainTotal += f.required else crossMax = max(crossMax, f.required)
                }

                else -> {
                    val s = measuredSize(c, innerW, innerH, ctx, inRow = !isColumn)
                    if (isColumn) {
                        mainTotal += s[1]
                    } else {
                        mainTotal += naturalWidthOf(c, ctx)
                        crossMax = max(crossMax, s[1])
                    }
                }
            }
        }

        return if (isColumn) {
            val f = mainTotal / innerH
            Fit(max(factor, f), mainTotal + pad2)
        } else {
            val f = mainTotal / innerW
            Fit(max(factor, f), crossMax + pad2)
        }
    }

    /** Single-line natural width of a leaf element, for row overflow checks. */
    private fun naturalWidthOf(el: Element, ctx: Ctx): Float = when {
        el.width != null -> el.width.dp(ctx)
        el.type == ElementTypes.SPACER || el.type == ElementTypes.DIVIDER -> 0f
        el.type == ElementTypes.ICON -> (el.height?.toFloat() ?: el.fontSize * 1.7f).ts(ctx)
        el.type == ElementTypes.IMAGE -> ((el.height ?: el.width ?: 80)).dp(ctx)
        el.type == ElementTypes.BUTTON ->
            textPaint(el, ctx).measureText(el.text ?: "Button") + 28f.dp(ctx)
        else -> textPaint(el, ctx).measureText(resolveText(el, ctx))
    }

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
                strokeWidth = max(1f, bg.borderWidth.dp(ctx))
                color = border
            }
            val inset = bp.strokeWidth / 2f
            canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), radius, radius, bp)
        }
    }

    // ------------------------------------------------------------- measuring

    /** Returns [w, h] in px for the element given the available box. */
    private fun measuredSize(el: Element, availW: Float, availH: Float, ctx: Ctx, inRow: Boolean): FloatArray {
        val fixedW = el.width?.dp(ctx)
        val fixedH = el.height?.dp(ctx)
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

            ElementTypes.BATTERY -> if (el.style == "bar") {
                floatArrayOf(fixedW ?: availW, fixedH ?: 8f.dp(ctx))
            } else {
                val paint = textPaint(el, ctx)
                val w = fixedW ?: availW
                val layout = staticLayout(resolveText(el, ctx), paint, w.toInt().coerceAtLeast(4), el.maxLines)
                floatArrayOf(w, min(layout.height.toFloat(), availH.coerceAtLeast(4f)) + pad2)
            }

            ElementTypes.ICON -> {
                val s = fixedH ?: (el.fontSize * 1.7f).ts(ctx)
                floatArrayOf(s, s)
            }

            ElementTypes.IMAGE -> {
                val w = fixedW ?: availW
                val h = fixedH ?: (if (el.height == null && el.width != null) el.width.dp(ctx) else availH)
                floatArrayOf(w, h)
            }

            ElementTypes.DIVIDER -> floatArrayOf(fixedW ?: availW, el.thickness.coerceAtLeast(1).dp(ctx))
            ElementTypes.SPACER -> floatArrayOf(fixedW ?: 0f, fixedH ?: 0f)

            ElementTypes.PROGRESS ->
                floatArrayOf(fixedW ?: availW, fixedH ?: 8f.dp(ctx))

            ElementTypes.BUTTON -> {
                val paint = textPaint(el, ctx)
                val textW = paint.measureText(el.text ?: "Button")
                val w = fixedW ?: (if (inRow) textW + 28f.dp(ctx) else availW)
                val h = fixedH ?: (el.fontSize * 2.3f).ts(ctx).coerceAtLeast(28f.dp(ctx))
                floatArrayOf(w, h)
            }

            else -> floatArrayOf(fixedW ?: availW, fixedH ?: (el.fontSize * 1.4f).ts(ctx))
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
            ElementTypes.IMAGE -> drawImage(canvas, el, ix, iy, iw, ih, ctx)
            ElementTypes.DIVIDER -> {
                val p = Paint().apply {
                    color = ColorUtil.parseOr(el.color, ctx.ink)
                    strokeWidth = el.thickness.coerceAtLeast(1).dp(ctx)
                }
                val cy = iy + ih / 2f
                canvas.drawLine(ix, cy, ix + iw, cy, p)
            }
            ElementTypes.PROGRESS -> drawBar(canvas, ix, iy, iw, ih, el.value / 100f, el, ctx)
            ElementTypes.BATTERY -> if (el.style == "bar") {
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
        // Hand clock/date elements to the launcher's TextClock (live, no alarms)
        if (el.isLiveText() && !ctx.includeLive && ctx.overlays.size < 4) {
            ctx.overlays.add(
                LiveOverlay(
                    leftDp = x / ctx.d,
                    topDp = y / ctx.d,
                    widthDp = w / ctx.d,
                    fontSizeDp = el.fontSize * ctx.total,
                    color = ColorUtil.parseOr(el.color, ctx.ink),
                    pattern = el.format ?: if (el.type == ElementTypes.CLOCK) "HH:mm" else "EEE, MMM d",
                    style = when {
                        el.bold && el.italic -> Typeface.BOLD_ITALIC
                        el.bold -> Typeface.BOLD
                        el.italic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    },
                )
            )
            return
        }
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

    private fun drawImage(canvas: Canvas, el: Element, x: Float, y: Float, w: Float, h: Float, ctx: Ctx) {
        val bmp = resolveImage(el, ctx)
        val radius = el.cornerRadius.dp(ctx)
        val rect = RectF(x, y, x + w, y + h)
        if (bmp == null) {
            // Neutral monochrome placeholder that still looks intentional
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ctx.ink and 0x00FFFFFF or 0x14FFFFFF }
            canvas.drawRoundRect(rect, radius, radius, p)
            val bp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 1f.dp(ctx)
                color = ctx.ink and 0x00FFFFFF or 0x33FFFFFF
            }
            canvas.drawRoundRect(rect, radius, radius, bp)
            val ip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                textSize = min(w, h) * 0.3f
                color = ctx.ink and 0x00FFFFFF or 0x66FFFFFF
            }
            val cy = y + h / 2f - (ip.descent() + ip.ascent()) / 2f
            canvas.drawText("🖼", x + w / 2f, cy, ip)
            return
        }
        val save = canvas.save()
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        canvas.clipPath(clip)
        // Center-crop: scale the source region into the destination box
        val bw = bmp.width.toFloat()
        val bh = bmp.height.toFloat()
        val scale = max(w / bw, h / bh)
        val sw = w / scale
        val sh = h / scale
        val sx = (bw - sw) / 2f
        val sy = (bh - sh) / 2f
        val p = Paint(Paint.FILTER_BITMAP_FLAG)
        canvas.translate(x, y)
        canvas.scale(w / sw, h / sh)
        canvas.drawBitmap(bmp, -sx, -sy, p)
        canvas.restoreToCount(save)
    }

    private fun resolveImage(el: Element, ctx: Ctx): Bitmap? {
        if (ctx.images.isEmpty()) return null
        val idx = if (el.rotate) (ctx.data.rotation + el.index).mod(ctx.images.size) else el.index % ctx.images.size
        return ctx.imageLoader?.invoke(ctx.images[idx])
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
            textSize = el.fontSize.ts(ctx)
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
        val gap = el.gap.ts(ctx)
        val sizes = children.map { measuredSize(it, w, h, ctx, inRow = false).copyOf() }

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
        val gap = el.gap.ts(ctx)
        val sizes = children.map { measuredSize(it, w, h, ctx, inRow = true).copyOf() }

        val usedByFixed = sizes.withIndex().sumOf { (i, s) ->
            val c = children[i]
            if (c.weight > 0f || c.width != null || c.type == ElementTypes.BUTTON) s[0].toDouble() else 0.0
        }.toFloat() + gap * (children.size - 1)
        val flexibleIdx = children.indices.filter {
            children[it].weight <= 0f && children[it].width == null && children[it].type != ElementTypes.BUTTON
        }
        val share = if (flexibleIdx.isNotEmpty()) ((w - usedByFixed).coerceAtLeast(0f) / flexibleIdx.size) else 0f
        flexibleIdx.forEach { sizes[it][0] = share.coerceAtLeast(8f) }

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
        textSize = el.fontSize.ts(ctx)
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
            .setLineSpacing(0f, 1.04f)
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
