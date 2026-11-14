package com.widgeon.app.render

import android.graphics.Color

/** Hex color parsing with graceful fallbacks ("auto" -> null -> caller picks ink). */
object ColorUtil {

    private val named = mapOf(
        "white" to 0xFFFFFFFF.toInt(),
        "black" to 0xFF000000.toInt(),
        "transparent" to 0,
    )

    /** Returns ARGB int or null if unparseable / "auto". */
    fun parse(raw: String?): Int? {
        if (raw.isNullOrBlank()) return null
        val s = raw.trim().lowercase()
        named[s]?.let { return it }
        if (s == "auto") return null
        val hex = if (s.startsWith("#")) s.substring(1) else s
        return when (hex.length) {
            6 -> try {
                Color.parseColor("#$hex")
            } catch (_: IllegalArgumentException) {
                null
            }
            8 -> try {
                Color.parseColor("#$hex")
            } catch (_: IllegalArgumentException) {
                null
            }
            3 -> try {
                val r = hex[0]; val g = hex[1]; val b = hex[2]
                Color.parseColor("#$r$r$g$g$b$b")
            } catch (_: IllegalArgumentException) {
                null
            }
            else -> null
        }
    }

    /** Parses or falls back to [fallback]. */
    fun parseOr(raw: String?, fallback: Int): Int = parse(raw) ?: fallback

    /** Perceived luminance 0..1 */
    fun luminance(color: Int): Float {
        val r = Color.red(color) / 255f
        val g = Color.green(color) / 255f
        val b = Color.blue(color) / 255f
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** Auto ink: white on dark backgrounds, near-black on light ones. */
    fun inkFor(colorRaw: String?, colorEndRaw: String?): Int {
        val a = parse(colorRaw) ?: 0xFF121212.toInt()
        val b = parse(colorEndRaw) ?: a
        val l = (luminance(a) + luminance(b)) / 2f
        val effective = if (Color.alpha(a) < 40) 0.25f else l  // translucent bg = unknown wallpaper, go light
        return if (effective > 0.6f) 0xF0141414.toInt() else 0xFFF7F7F7.toInt()
    }

    fun isValid(raw: String?): Boolean = raw != null && (parse(raw) != null || raw.trim().lowercase() == "auto")
}
