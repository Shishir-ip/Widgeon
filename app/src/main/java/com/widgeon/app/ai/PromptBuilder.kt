package com.widgeon.app.ai

import com.widgeon.app.data.WidgetDesign
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Advanced options chosen in the UI before generating. */
data class CreateRequest(
    val prompt: String,
    val cols: Int = 2,
    val rows: Int = 2,
    val style: String = "Minimal",
    val accent: String = "",          // hex like #FFFFFF, empty = none
    val cornerRadius: Int = 24,
    val wantClock: Boolean = false,
    val wantDate: Boolean = false,
    val wantBattery: Boolean = false,
    val wantWeather: Boolean = false,
    val notes: String = "",
)

/** Builds the system + user prompts that drive design generation and editing. */
object PromptBuilder {

    private val styleGuide = mapOf(
        "Minimal" to "Ultra-clean minimalism. Lots of breathing space, generous padding, one or two weights, restrained color. Prefer whitespace over decoration.",
        "Dark Glass" to "Frosted-glass dark aesthetic: deep translucent backgrounds (#CC0B0B0B..#E6141414), soft hairline borders (#26FFFFFF), crisp white text with dim secondary text.",
        "Light Clean" to "Bright, airy light card: near-white background (#F2FFFFFF), soft dark text (#E6141414), subtle gray secondary text, pill-shaped accents.",
        "Outline" to "Line-art style: transparent or very dark background, thin 1-2dp borders, monospaced or condensed type, technical blueprint feel.",
        "Bold" to "Poster-like bold design: huge display numbers, ultra-heavy weights (sans-serif-black), tight leading, dramatic contrast.",
        "Editorial" to "Magazine typography: serif display text paired with small sans-serif labels, uppercase micro-labels with wide letter spacing, refined grid.",
    )

    val styleNames = styleGuide.keys.toList()

    val ideaChips = listOf(
        "Big clock with date" to "A minimal clock widget showing the time large and the weekday below",
        "Battery + storage vibe" to "A battery status widget with a progress bar and a percentage number, monochrome",
        "Focus mantra" to "A widget showing a rotating motivational mantra in bold type: 'Deep work only'",
        "Weather now" to "A weather widget with current temperature, condition emoji and city name",
        "Pomodoro label" to "A widget that says FOCUS in huge letters with a thin divider and '25 min sprint' below",
        "Quote card" to "An elegant quote widget: 'Simplicity is the ultimate sophistication.'",
    )

    fun systemPrompt(): String = """
You are Widgeon's design engine, a world-class home-screen widget designer. You convert a user's description into a JSON spec for an Android widget that Widgeon renders EXACTLY as specified. Respond with EXACTLY ONE JSON object and nothing else: no markdown fences, no explanations, no comments.

## Top-level schema
{
  "name": string,                // short widget name, 2-4 words
  "cols": int,                   // grid width  1..5 (user usually fixes this)
  "rows": int,                   // grid height 1..5
  "background": {
    "type": "solid" | "gradient",
    "color": string,             // "#RRGGBB" or "#AARRGGBB"
    "colorEnd": string|null,     // only for gradient
    "orientation": "TL_BR"|"BL_TR"|"T_B"|"L_R"|"R_L",
    "cornerRadius": int,         // dp, 0..40
    "borderColor": string|null,
    "borderWidth": int           // dp, usually 0
  },
  "padding": int,                // inner padding dp, 8..24
  "action": {"type":"none"|"openUrl"|"openApp", "url":string|null, "appPackage":string|null},
  "elements": [ Element, ... ]
}

## Element schema (common optional fields: fontSize dp-float, color, bold, italic, font, align "start"|"center"|"end", weight, width, height, background, cornerRadius, padding)
- {"type":"text","text":"...", "fontSize":14, "maxLines":3, "spacing":0.5, "font":"sans-serif"}
    fonts: sans-serif | sans-serif-light | sans-serif-medium | sans-serif-black | sans-serif-condensed | serif | monospace
- {"type":"clock","format":"HH:mm","fontSize":44}         // live time; formats like "HH:mm", "h:mm", "h:mm a"
- {"type":"date","format":"EEE, MMM d","fontSize":13}     // live date; java patterns like "EEEE", "d MMMM"
- {"type":"battery","style":"percent"|"bar","fontSize":14} // live battery %; bar draws a progress bar
- {"type":"weather","style":"temp"|"condition","fontSize":16} // live temperature ("21°") or condition emoji; only if user asked for weather
- {"type":"icon","emoji":"⚡","fontSize":24}               // icons are EMOJI only
- {"type":"image","index":0,"rotate":false,"width":90,"height":90,"cornerRadius":16}
    // a photo slot the user attaches after generating. index = which attached photo (0,1,2…).
    // rotate:true cycles through ALL attached photos on every refresh. Always give images explicit width+height.
- {"type":"divider","thickness":1,"color":"#33FFFFFF"}     // horizontal line
- {"type":"spacer","height":8}                             // invisible gap; can use weight to absorb space
- {"type":"progress","value":72,"color":"#FFFFFFFF","trackColor":"#26FFFFFF","height":8,"cornerRadius":4}
- {"type":"button","text":"Open","background":"#FFFFFF","color":"#FF000000","fontSize":13,"height":34,"cornerRadius":17,"action":{"type":"openUrl","url":"https://..."}}
- {"type":"row","gap":10,"align":"center","elements":[...]}    // horizontal stack; children lay left→right
- {"type":"column","gap":6,"elements":[...]}                   // vertical stack

## Hard rules
1. OBEY THE REQUEST. Build exactly what the user describes — a photo widget gets "image" elements, a quote widget gets text, a timer label gets text. NEVER fall back to a clock unless the user asked for one. NEVER add clock/date/battery/weather elements unless they are requested or clearly essential to the concept.
2. IMAGES: when the user asks for photos, place "image" elements matching the requested count (e.g. "2 or 3 pictures" → 3 slots, index 0..2). If they want them to change/rotate over time set "rotate":true on the slots. The user attaches the actual photos in the editor afterwards; until then a tasteful placeholder shows.
3. LAYOUT: use "column" (recommended root) and "row" to compose. Add "weight":1 to exactly one child of a row/column to absorb leftover space (great for vertical centering with a spacer).
4. COLOR: text colors must contrast with the background. Use "color":"auto" to inherit an automatically-contrasting ink. Secondary text: same hue at ~60% alpha (e.g. "#99FFFFFF").
5. SIZES are dp and MUST fit the widget's content area (visible area minus 2×padding). Sizes are strictly enforced by the renderer — oversized text gets scaled down, so aim to fit naturally.
6. HIERARCHY: one clear hero element, max two font families, 2-3 tints of the same color. It must look designed, not generated.
7. NO unknown element types, NO trailing commas, NO text outside the JSON.

## Composition by size (visible content area, approx)
- 1×1 (~70×70dp): ONE hero element only — a huge time, number, or emoji. padding 8-10. Nothing else.
- 2×2 (~110×110dp): hero + one small label/divider. Max 4 elements. padding 10-14.
- 4×2 (~250×110dp): a horizontal row of 2-3 stats with a small header. Use row + weight. padding 12-16.
- 2×4 (~110×220dp): vertical stack — hero on top, 2-3 compact rows below. padding 12-16.
- 4×4 (~250×220dp): dashboard — up to 8 elements, mini rows/columns, one accent. padding 14-18.

## BUDGET MATH (critical — follow it)
The renderer scales content down if it overflows, but a design that fits naturally looks far better. Compute before writing:
contentHeight = visibleHeight − 2×padding − gaps. Example for 2×2 with padding 12 and three elements with gap 6: 110 − 24 − 12 = 74dp → a 40dp clock + 12dp label + 8dp divider = 60dp ✓ fits. If your hero fontSize is 56, nothing else fits in a 2×2 — reduce it or drop elements.
""".trim()

    fun createMessages(req: CreateRequest): List<AiMessage> {
        val approx = approxDp(req.cols, req.rows)
        val style = styleGuide[req.style] ?: styleGuide.getValue("Minimal")
        val wants = buildList {
            if (req.wantClock) add("a live clock element (essential)")
            if (req.wantDate) add("a live date element")
            if (req.wantBattery) add("a live battery element")
            if (req.wantWeather) add("a live weather element (temperature and/or condition)")
        }

        val sb = StringBuilder()
        sb.append("Design an Android home-screen widget.\n\n")
        sb.append("USER REQUEST (obey it literally — this defines the widget's content):\n“").append(req.prompt.trim()).append("”\n")
        sb.append("\nGRID SIZE: ").append(req.cols).append('x').append(req.rows)
            .append(" (set \"cols\": ").append(req.cols).append(" and \"rows\": ").append(req.rows)
            .append(" EXACTLY — this is the user's chosen size, and the visible area is approx ")
            .append(approx.first).append('x').append(approx.second)
            .append(" dp — follow the composition guide for it).\n")
        sb.append("\nSTYLE: ").append(req.style).append(" — ").append(style).append('\n')
        if (req.accent.isNotBlank()) sb.append("\nACCENT COLOR: use ").append(req.accent).append(" as the only accent (background/text stay monochrome).\n")
        sb.append("\nCORNER RADIUS: ").append(req.cornerRadius).append("dp.\n")
        if (wants.isNotEmpty()) sb.append("\nMUST INCLUDE live data: ").append(wants.joinToString("; ")).append(".\n")
        else sb.append("\nNO live-data elements unless the request clearly needs them — do not add a clock if the user didn't ask for one.\n")
        if (req.notes.isNotBlank()) sb.append("\nEXTRA USER NOTES: ").append(req.notes.trim()).append('\n')
        sb.append("\nReturn the single JSON object now.")

        return listOf(AiMessage.system(systemPrompt()), AiMessage.user(sb.toString()))
    }

    fun refineMessages(current: WidgetDesign, instruction: String, keepHistory: List<AiMessage>): List<AiMessage> {
        val json = Json {
            encodeDefaults = false
            explicitNulls = false
        }
        val designJson = json.encodeToString(current.copy(prompt = "", id = "", createdAt = 0, updatedAt = 0, version = 0))
        val user = "CURRENT WIDGET JSON:\n$designJson\n" +
            (if (current.images.isNotEmpty()) "\nATTACHED PHOTOS: ${current.images.size} (image index 0..${current.images.size - 1} are available).\n" else "") +
            "\nCHANGE REQUEST: ${instruction.trim()}\n\n" +
            "Return the COMPLETE updated widget JSON (the whole object, with your changes applied). Same schema, same rules, obey the size guide. Nothing but the JSON."
        return listOf(AiMessage.system(systemPrompt())) + keepHistory + AiMessage.user(user)
    }

    /** Rough visible dp area for a grid footprint (matches launcher cell formula 70n-30). */
    fun approxDp(cols: Int, rows: Int): Pair<Int, Int> =
        (cols * 70 - 30) to (rows * 70 - 30)
}
