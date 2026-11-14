package com.widgeon.app.data

import kotlinx.serialization.Serializable

/**
 * The AI-facing widget design contract. The model returns a single JSON object
 * shaped exactly like [WidgetDesign] (without id/version/timestamps, which the
 * app fills in). Everything has safe defaults so partial output still renders.
 */

@Serializable
data class WidgetDesign(
    val id: String = "",
    val name: String = "Widget",
    val prompt: String = "",
    val cols: Int = 2,
    val rows: Int = 2,
    val background: Background = Background(),
    val padding: Int = 14,
    val elements: List<Element> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val version: Int = 1,
    /** Root-level tap action applied when nothing more specific exists. */
    val action: WidgetAction? = null,
) {
    val sizeLabel: String get() = "${cols}×$rows"
}

@Serializable
data class Background(
    /** solid | gradient */
    val type: String = "solid",
    val color: String = "#E6111111",
    val colorEnd: String? = null,
    /** TL_BR | BL_TR | T_B | L_R | R_L */
    val orientation: String = "TL_BR",
    val cornerRadius: Int = 24,
    val borderColor: String? = null,
    val borderWidth: Int = 0,
)

@Serializable
data class WidgetAction(
    /** none | openUrl | openApp */
    val type: String = "none",
    val url: String? = null,
    val appPackage: String? = null,
)

/**
 * One renderable node. `type` chooses behavior:
 * text | clock | date | battery | weather | icon | divider | spacer | progress | button | row | column
 * row/column nodes nest children via [elements].
 */
@Serializable
data class Element(
    val type: String,
    val text: String? = null,
    val emoji: String? = null,
    val fontSize: Float = 14f,
    val color: String? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    /** sans-serif | sans-serif-light | sans-serif-medium | sans-serif-black | sans-serif-condensed | serif | monospace */
    val font: String = "sans-serif",
    /** start | center | end */
    val align: String = "start",
    val format: String? = null,
    val spacing: Float = 0f,
    val maxLines: Int = 6,
    /** row/column gap between children, dp */
    val gap: Int = 8,
    /** weight: fill remaining space in the parent's main axis */
    val weight: Float = 0f,
    val width: Int? = null,
    val height: Int? = null,
    val thickness: Int = 1,
    val value: Int = 60,
    val trackColor: String? = null,
    val background: String? = null,
    val cornerRadius: Int = 12,
    val padding: Int = 0,
    /** battery: percent | bar ; weather: temp | condition ; clock: digital */
    val style: String = "percent",
    val action: WidgetAction? = null,
    val elements: List<Element> = emptyList(),
)

/** Supported element type names, also used to validate AI output. */
object ElementTypes {
    const val TEXT = "text"
    const val CLOCK = "clock"
    const val DATE = "date"
    const val BATTERY = "battery"
    const val WEATHER = "weather"
    const val ICON = "icon"
    const val DIVIDER = "divider"
    const val SPACER = "spacer"
    const val PROGRESS = "progress"
    const val BUTTON = "button"
    const val ROW = "row"
    const val COLUMN = "column"

    val all = setOf(TEXT, CLOCK, DATE, BATTERY, WEATHER, ICON, DIVIDER, SPACER, PROGRESS, BUTTON, ROW, COLUMN)
}
