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
You are Widgeon's design engine. You convert a user's description into a JSON spec for an Android home-screen widget. The widget is rendered from your JSON — nothing else. Respond with EXACTLY ONE JSON object and nothing else: no markdown fences, no explanations, no comments.

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
- {"type":"divider","thickness":1,"color":"#33FFFFFF"}     // horizontal line
- {"type":"spacer","height":8}                             // invisible gap; can use weight to absorb space
- {"type":"progress","value":72,"color":"#FFFFFFFF","trackColor":"#26FFFFFF","height":8,"cornerRadius":4}
- {"type":"button","text":"Open","background":"#FFFFFF","color":"#FF000000","fontSize":13,"height":34,"cornerRadius":17,"action":{"type":"openUrl","url":"https://..."}}
- {"type":"row","gap":10,"align":"center","elements":[...]}    // horizontal stack; children lay left→right
- {"type":"column","gap":6,"elements":[...]}                   // vertical stack

## Hard rules
1. LAYOUT: use "column" (top-level recommended) and "row" to compose. Add "weight":1 to exactly one child of a row/column to make it absorb the leftover space (great with spacer for vertical centering).
2. COLOR: text colors must contrast with the background. Use "color":"auto" to inherit an automatically-contrasting ink color. Secondary text: same hue at ~60% alpha (e.g. "#99FFFFFF").
3. SIZES are dp. Keep every element inside the widget's content area — never rely on clipping.
4. LIVE DATA: clock/date/battery/weather ONLY when appropriate for the request. The rest is static text/icons.
5. HIERARCHY: one clear hero element (big time or number), max two font families, 2-3 tints of the same color. It must look designed, not generated.
6. NO images, NO network content, NO unknown element types, NO trailing commas, NO text outside the JSON.
7. The JSON must be immediately parseable: valid UTF-8, escaped quotes inside strings, real numbers unquoted.
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
        sb.append("USER REQUEST: ").append(req.prompt.trim()).append('\n')
        sb.append("\nGRID SIZE: ").append(req.cols).append('x').append(req.rows)
            .append(" (visible area approx ").append(approx.first).append('x').append(approx.second)
            .append(" dp — plan font sizes and layout so everything fits comfortably).\n")
        sb.append("\nSTYLE: ").append(req.style).append(" — ").append(style).append('\n')
        if (req.accent.isNotBlank()) sb.append("\nACCENT COLOR: use ").append(req.accent).append(" as the only accent (background/text stay monochrome).\n")
        sb.append("\nCORNER RADIUS: ").append(req.cornerRadius).append("dp.\n")
        if (wants.isNotEmpty()) sb.append("\nMUST INCLUDE live data: ").append(wants.joinToString("; ")).append(".\n")
        else sb.append("\nNO live-data elements needed — static content only.\n")
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
        val user = "CURRENT WIDGET JSON:\n$designJson\n\nCHANGE REQUEST: ${instruction.trim()}\n\n" +
            "Return the COMPLETE updated widget JSON (the whole object, with your changes applied). Same schema, same rules. Nothing but the JSON."
        return listOf(AiMessage.system(systemPrompt())) + keepHistory + AiMessage.user(user)
    }

    /** Rough visible dp area for a grid footprint (matches launcher cell formula 70n-30). */
    fun approxDp(cols: Int, rows: Int): Pair<Int, Int> =
        (cols * 70 - 30) to (rows * 70 - 30)
}
