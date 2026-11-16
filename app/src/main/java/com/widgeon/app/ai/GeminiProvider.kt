package com.widgeon.app.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.booleanOrNull

/**
 * Google AI Studio (Gemini) — generateContent endpoint.
 * Free tier keys: https://aistudio.google.com/apikey
 *
 * Notes from live testing (v1.4):
 *  - The key is sent via the x-goog-api-key header (not the URL).
 *  - Flash-family models think by default (gemini-3.6-flash burned ~1k thought
 *    tokens per call); thinkingConfig.thinkingBudget=0 disables that for speed,
 *    with an automatic retry without it for models that require thinking.
 *  - Thought-summary parts are filtered defensively, finishReason is surfaced.
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
) : AiProvider {

    override val id = "gemini"
    override val displayName = "Google Gemini"

    override suspend fun complete(messages: List<AiMessage>, temperature: Double): String {
        if (apiKey.isBlank()) throw AiException.Auth("Add your Gemini API key first (free at aistudio.google.com).")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"
        val headers = mapOf("x-goog-api-key" to apiKey)

        val systemText = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val contents = buildJsonArray {
            messages.filter { it.role != "system" }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", m.content) }) })
                })
            }
        }

        fun body(disableThinking: Boolean) = buildJsonObject {
            if (systemText.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", systemText) }) })
                })
            }
            put("contents", contents)
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", 16384)
                if (disableThinking) {
                    put("thinkingConfig", buildJsonObject { put("thinkingBudget", 0) })
                }
            })
        }

        // Flash-family models think by default (e.g. gemini-3.6-flash burned ~1k
        // thought tokens per call) — disable for speed. NOTE from live testing:
        // flash-LITE models REJECT thinkingConfig entirely (HTTP 400), so only
        // non-lite flash models get it, and any 400 with it attached falls back.
        val wantsThinkingOff = model.contains("flash", ignoreCase = true) &&
            !model.contains("lite", ignoreCase = true) &&
            !model.contains("pro", ignoreCase = true)

        var resp = Http.postJson(url, body(wantsThinkingOff).toString(), headers)
        if (resp.code == 400 && wantsThinkingOff) {
            resp = Http.postJson(url, body(false).toString(), headers)
        }
        if (resp.code !in 200..299) {
            if (resp.body.contains("RESOURCE_EXHAUSTED") || resp.body.contains("quota", ignoreCase = true)) {
                throw AiException.Quota(
                    Http.errorMessage(resp.body)
                        ?: "Gemini quota exceeded. Check usage at aistudio.google.com or wait for the reset."
                )
            }
            if (resp.code == 404 && resp.body.contains("not found", ignoreCase = true)) {
                throw AiException.Server(
                    "Model \"$model\" isn't available for this key. Open Settings → Google Gemini and pick a valid model " +
                        "(e.g. gemini-3.5-flash-lite, gemini-3.6-flash, gemini-2.5-flash)."
                )
            }
            Http.raiseForStatus(resp.code, resp.body)
        }

        return try {
            val root = Http.json.parseToJsonElement(resp.body).jsonObject
            val candidates = root["candidates"]?.jsonArray
                ?: throw AiException.BadResponse("Gemini returned no candidates.")
            val first = candidates.first().jsonObject
            val allParts: List<JsonObject> = run {
                val arr: kotlinx.serialization.json.JsonArray =
                    first["content"]?.jsonObject?.get("parts")?.jsonArray
                        ?: kotlinx.serialization.json.JsonArray(emptyList())
                arr.filterIsInstance<JsonObject>()
                    .filter { it["thought"]?.jsonPrimitive?.booleanOrNull != true } // drop thought summaries
            }
            val text = allParts.joinToString("") { p ->
                p["text"]?.jsonPrimitive?.content ?: ""
            }
            if (text.isBlank()) {
                val reason = first["finishReason"]?.jsonPrimitive?.content ?: "UNKNOWN"
                throw AiException.BadResponse(
                    when (reason) {
                        "SAFETY", "RECITATION", "BLOCKLIST", "PROHIBITED_CONTENT", "SPII" ->
                            "Gemini blocked this request ($reason). Try rephrasing the prompt."
                        "MAX_TOKENS" ->
                            "The answer ran out of output space before the JSON finished. Try a shorter request."
                        else ->
                            "Gemini returned an empty answer ($reason). Try again."
                    }
                )
            }
            text
        } catch (e: AiException) {
            throw e
        } catch (t: Throwable) {
            throw AiException.BadResponse("Could not read Gemini's response.")
        }
    }
}
