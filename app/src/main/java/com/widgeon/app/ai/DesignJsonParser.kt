package com.widgeon.app.ai

import com.widgeon.app.data.Background
import com.widgeon.app.data.DesignRepository
import com.widgeon.app.data.Element
import com.widgeon.app.data.ElementTypes
import com.widgeon.app.data.WidgetAction
import com.widgeon.app.data.WidgetDesign
import com.widgeon.app.render.ColorUtil
import kotlinx.serialization.json.Json

/**
 * Turns a raw model response into a sanitized [WidgetDesign].
 * Handles markdown fences, prose around the JSON, wrapper objects and
 * out-of-range values so a quirky model output still renders safely.
 */
object DesignJsonParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(raw: String, prompt: String): WidgetDesign {
        val extracted = extractJsonObject(raw)
            ?: throw AiException.BadResponse("The model did not return a widget design (no JSON found). Try again.")

        val design = try {
            json.decodeFromString(WidgetDesign.serializer(), extracted)
        } catch (first: Throwable) {
            // Some models wrap the payload: {"widget":{...}} / {"design":{...}} / {"data":{...}}
            var unwrapped: WidgetDesign? = null
            for (key in listOf("widget", "design", "data", "result")) {
                try {
                    val root = json.parseToJsonElement(extracted).let { it }
                    val obj = root.toString()
                    val reg = Regex("\"$key\"\\s*:")
                    if (reg.containsMatchIn(obj)) {
                        val inner = extractInnerObject(extracted, key)
                        if (inner != null) {
                            unwrapped = json.decodeFromString(WidgetDesign.serializer(), inner)
                            break
                        }
                    }
                } catch (_: Throwable) { }
            }
            unwrapped ?: throw AiException.BadResponse(
                "The model's JSON didn't match the widget schema (${first.message?.take(120) ?: "invalid"}). Try regenerating."
            )
        }

        return sanitize(design, prompt)
    }

    private fun extractInnerObject(text: String, key: String): String? {
        val kIdx = text.indexOf("\"$key\"")
        if (kIdx < 0) return null
        val brace = text.indexOf('{', kIdx)
        if (brace < 0) return null
        return balancedFrom(text, brace)
    }

    /** Finds the first balanced {...} block, respecting strings and escapes. */
    private fun extractJsonObject(text: String): String? {
        val start = text.indexOf('{')
        if (start < 0) return null
        return balancedFrom(text, start)
    }

    private fun balancedFrom(text: String, start: Int): String? {
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (escaped) { escaped = false; continue }
            when {
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
        }
        // Unterminated JSON: try to close it (common with truncated responses)
        if (depth > 0) {
            var patched = text.substring(start)
            if (inString) patched += '"'
            repeat(depth) { patched += '}' }
            return patched
        }
        return null
    }

    // ---------- Normalization ----------

    private fun sanitize(d: WidgetDesign, prompt: String): WidgetDesign {
        val cols = d.cols.coerceIn(1, 5)
        val rows = d.rows.coerceIn(1, 5)
        val bg = sanitizeBg(d.background)
        val elements = sanitizeElements(d.elements, depth = 0).take(60)

        return d.copy(
            id = d.id.ifBlank { DesignRepository.newId() },
            name = d.name.trim().take(40).ifBlank { "Widget" },
            prompt = prompt,
            cols = cols,
            rows = rows,
            background = bg,
            padding = d.padding.coerceIn(0, 32),
            elements = elements,
            images = sanitizeImages(d.images),
            action = sanitizeAction(d.action),
        )
    }

    private fun sanitizeImages(images: List<String>): List<String> =
        images.asSequence()
            .map { it.substringAfterLast('/').substringAfterLast('\\').trim() }
            .filter { it.isNotEmpty() && Regex("^[A-Za-z0-9._-]+$").matches(it) }
            .distinct()
            .take(12)
            .toList()

    private fun sanitizeBg(b: Background): Background {
        val type = if (b.type.equals("gradient", true)) "gradient" else "solid"
        return b.copy(
            type = type,
            color = ColorUtil.parse(b.color)?.let { hex(it) } ?: "#E6111111",
            colorEnd = if (type == "gradient") {
                ColorUtil.parse(b.colorEnd ?: b.color)?.let { hex(it) }
            } else null,
            orientation = when (b.orientation.uppercase()) {
                "BL_TR", "T_B", "L_R", "R_L" -> b.orientation.uppercase()
                else -> "TL_BR"
            },
            cornerRadius = b.cornerRadius.coerceIn(0, 44),
            borderColor = ColorUtil.parse(b.borderColor)?.let { hex(it) },
            borderWidth = b.borderWidth.coerceIn(0, 6),
        )
    }

    private fun sanitizeElements(list: List<Element>, depth: Int): List<Element> {
        if (depth > 6) return emptyList()
        val out = ArrayList<Element>(list.size)
        for (e in list) {
            val type = e.type.trim().lowercase()
            if (type !in ElementTypes.all) continue
            when (type) {
                ElementTypes.ROW, ElementTypes.COLUMN -> out.add(
                    e.copy(
                        type = type,
                        gap = e.gap.coerceIn(0, 48),
                        elements = sanitizeElements(e.elements, depth + 1).take(24),
                    )
                )
                ElementTypes.TEXT -> if (!e.text.isNullOrBlank()) out.add(sanitizeLeaf(e, type))
                ElementTypes.BUTTON -> out.add(sanitizeLeaf(e, type).copy(action = sanitizeAction(e.action)))
                ElementTypes.CLOCK -> out.add(sanitizeLeaf(e.copy(format = e.format ?: "HH:mm"), type))
                ElementTypes.DATE -> out.add(sanitizeLeaf(e.copy(format = e.format ?: "EEE, MMM d"), type))
                ElementTypes.BATTERY -> out.add(
                    sanitizeLeaf(e, type).copy(style = if (e.style == "bar") "bar" else "percent")
                )
                ElementTypes.WEATHER -> out.add(
                    sanitizeLeaf(e, type).copy(style = if (e.style == "condition") "condition" else "temp")
                )
                ElementTypes.IMAGE -> out.add(
                    sanitizeLeaf(e, type).copy(index = e.index.coerceIn(0, 9), rotate = e.rotate)
                )
                else -> out.add(sanitizeLeaf(e, type))
            }
        }
        return out
    }

    private fun sanitizeLeaf(e: Element, type: String): Element = e.copy(
        type = type,
        text = e.text?.take(600),
        emoji = e.emoji?.take(8),
        fontSize = e.fontSize.coerceIn(6f, 120f),
        color = if (ColorUtil.isValid(e.color)) e.color?.trim() else null,
        font = when (e.font) {
            "sans-serif-light", "sans-serif-medium", "sans-serif-black", "sans-serif-condensed", "serif", "monospace" -> e.font
            else -> "sans-serif"
        },
        align = when (e.align) { "center", "end" -> e.align; else -> "start" },
        maxLines = e.maxLines.coerceIn(1, 10),
        gap = e.gap.coerceIn(0, 48),
        width = e.width?.coerceIn(4, 500),
        height = e.height?.coerceIn(4, 500),
        thickness = e.thickness.coerceIn(1, 12),
        value = e.value.coerceIn(0, 100),
        background = ColorUtil.parse(e.background)?.let { hex(it) },
        cornerRadius = e.cornerRadius.coerceIn(0, 44),
        padding = e.padding.coerceIn(0, 32),
    )

    private fun sanitizeAction(a: WidgetAction?): WidgetAction? {
        if (a == null) return null
        return when (a.type.lowercase()) {
            "openurl" -> if (!a.url.isNullOrBlank()) WidgetAction("openUrl", a.url.take(500)) else null
            "openapp" -> if (!a.appPackage.isNullOrBlank()) WidgetAction("openApp", appPackage = a.appPackage.take(200)) else null
            else -> null
        }
    }

    private fun hex(color: Int): String = String.format("#%08X", color)
}
