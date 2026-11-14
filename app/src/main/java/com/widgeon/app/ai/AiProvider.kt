package com.widgeon.app.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Message passed to chat-style providers. */
data class AiMessage(val role: String, val content: String) {
    companion object {
        fun system(content: String) = AiMessage("system", content)
        fun user(content: String) = AiMessage("user", content)
        fun assistant(content: String) = AiMessage("assistant", content)
    }
}

/** Error categories surfaced to the UI with tailored copy + actions. */
sealed class AiException(message: String) : Exception(message) {
    /** API key missing, invalid or unauthorized (401/403). */
    class Auth(message: String) : AiException(message)

    /** Rate limit / credits exhausted (429, RESOURCE_EXHAUSTED, 402). */
    class Quota(message: String) : AiException(message)

    /** No usable network. */
    class Network(message: String) : AiException(message)

    /** Provider responded but with a server error. */
    class Server(message: String) : AiException(message)

    /** Response arrived but could not be turned into a design. */
    class BadResponse(message: String) : AiException(message)
}

/**
 * A single chat-completion backend. Implementations translate [complete] into
 * their wire format and throw typed [AiException]s on failure.
 */
interface AiProvider {
    /** One of Prefs.PROVIDER_* */
    val id: String

    /** Human label for dialogs. */
    val displayName: String

    suspend fun complete(messages: List<AiMessage>, temperature: Double = 0.7): String
}

/** Minimal HTTP layer shared by all providers. */
object Http {

    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    data class Response(val code: Int, val body: String)

    fun postJson(url: String, body: String, headers: Map<String, String> = emptyMap()): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 20000
            conn.readTimeout = 90000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Response(code, text)
        } catch (e: IOException) {
            throw AiException.Network("Could not reach the provider: ${e.message ?: "network error"}")
        } finally {
            conn.disconnect()
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): Response {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
            Response(code, text)
        } catch (e: IOException) {
            throw AiException.Network("Could not reach the provider: ${e.message ?: "network error"}")
        } finally {
            conn.disconnect()
        }
    }

    /** Best-effort extraction of an error message out of a JSON error body. */
    fun errorMessage(body: String): String? = try {
        val obj = json.parseToJsonElement(body).jsonObject
        val err = obj["error"]
        when {
            err is JsonObject -> (err["message"]?.toString() ?: err.toString())
                .trim('"').take(220)
            obj.containsKey("message") -> obj["message"].toString().trim('"').take(220)
            else -> null
        }
    } catch (t: Throwable) {
        body.take(160).ifBlank { null }
    }

    /** Map an HTTP status (+body) to the right typed exception. */
    fun raiseForStatus(code: Int, body: String) {
        val msg = errorMessage(body)
        when (code) {
            401, 403 -> throw AiException.Auth(msg ?: "Your API key was rejected (HTTP $code). Double-check it in Settings.")
            402 -> throw AiException.Quota(msg ?: "This account has run out of credits (HTTP 402).")
            429 -> throw AiException.Quota(msg ?: "Rate limit or free-tier quota exceeded (HTTP 429). Wait a moment or top up credits.")
            in 500..599 -> throw AiException.Server(msg ?: "The provider had a server problem (HTTP $code). Try again shortly.")
            else -> throw AiException.Server(msg ?: "Request failed (HTTP $code).")
        }
    }
}
