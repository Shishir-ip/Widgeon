package com.widgeon.app.ai

import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Any OpenAI-compatible /chat/completions endpoint.
 * Covers Groq, OpenRouter and fully custom self-hosted endpoints.
 */
class OpenAiCompatProvider(
    override val id: String,
    override val displayName: String,
    private val baseUrl: String,
    private val apiKey: String,
    private val model: String,
    private val extraHeaders: Map<String, String> = emptyMap(),
) : AiProvider {

    override suspend fun complete(messages: List<AiMessage>, temperature: Double): String {
        if (apiKey.isBlank()) {
            throw AiException.Auth("Add your $displayName API key first in Settings.")
        }
        val base = baseUrl.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"

        val body = buildJsonObject {
            put("model", model)
            put("messages", buildJsonArray {
                messages.forEach { m ->
                    add(buildJsonObject {
                        put("role", m.role)
                        put("content", m.content)
                    })
                }
            })
            put("temperature", temperature)
            put("max_tokens", 8192)
        }

        val headers = extraHeaders + mapOf("Authorization" to "Bearer $apiKey")
        val resp = Http.postJson(url, body.toString(), headers)
        if (resp.code !in 200..299) Http.raiseForStatus(resp.code, resp.body)

        return try {
            val root = Http.json.parseToJsonElement(resp.body).jsonObject
            val content = root["choices"]?.jsonArray
                ?.firstOrNull()?.jsonObject
                ?.get("message")?.jsonObject
                ?.get("content")?.jsonPrimitive?.content
            if (content.isNullOrBlank()) {
                throw AiException.BadResponse("The model returned an empty answer. Try again or pick another model.")
            }
            content
        } catch (e: AiException) {
            throw e
        } catch (t: Throwable) {
            throw AiException.BadResponse("Could not read the model's response.")
        }
    }
}
