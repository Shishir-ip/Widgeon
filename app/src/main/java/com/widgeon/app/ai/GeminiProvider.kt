package com.widgeon.app.ai

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Google AI Studio (Gemini) — generateContent endpoint.
 * Free tier keys: https://aistudio.google.com/apikey
 */
class GeminiProvider(
    private val apiKey: String,
    private val model: String,
) : AiProvider {

    override val id = "gemini"
    override val displayName = "Google Gemini"

    override suspend fun complete(messages: List<AiMessage>, temperature: Double): String {
        if (apiKey.isBlank()) throw AiException.Auth("Add your Gemini API key first (free at aistudio.google.com).")
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val systemText = messages.filter { it.role == "system" }.joinToString("\n\n") { it.content }
        val contents = buildJsonArray {
            messages.filter { it.role != "system" }.forEach { m ->
                add(buildJsonObject {
                    put("role", if (m.role == "assistant") "model" else "user")
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", m.content) }) })
                })
            }
        }

        val body = buildJsonObject {
            if (systemText.isNotBlank()) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(buildJsonObject { put("text", systemText) }) })
                })
            }
            put("contents", contents)
            put("generationConfig", buildJsonObject {
                put("temperature", temperature)
                put("maxOutputTokens", 8192)
            })
        }

        val resp = Http.postJson(url, body.toString())
        if (resp.code !in 200..299) {
            // Gemini quota errors often arrive as 400/429 with RESOURCE_EXHAUSTED
            if (resp.body.contains("RESOURCE_EXHAUSTED") || resp.body.contains("quota", ignoreCase = true)) {
                throw AiException.Quota(Http.errorMessage(resp.body) ?: "Gemini quota exceeded for today. Check usage at aistudio.google.com or wait for the reset.")
            }
            Http.raiseForStatus(resp.code, resp.body)
        }

        return try {
            val root = Http.json.parseToJsonElement(resp.body).jsonObject
            val candidates = root["candidates"]?.jsonArray
                ?: throw AiException.BadResponse("Gemini returned no candidates.")
            val first = candidates.first().jsonObject
            val parts: kotlinx.serialization.json.JsonArray =
                first["content"]?.jsonObject?.get("parts")?.jsonArray
                    ?: kotlinx.serialization.json.JsonArray(emptyList())
            val text = parts.joinToString("") { p ->
                (p as? JsonObject)?.get("text")?.jsonPrimitive?.content ?: ""
            }
            if (text.isBlank()) throw AiException.BadResponse("Gemini returned an empty answer (possibly safety-blocked). Try rephrasing the prompt.")
            text
        } catch (e: AiException) {
            throw e
        } catch (t: Throwable) {
            throw AiException.BadResponse("Could not read Gemini's response.")
        }
    }
}
