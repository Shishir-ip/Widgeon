package com.widgeon.app.ai

import com.widgeon.app.data.Prefs
import com.widgeon.app.widgeon
import android.content.Context

/** Builds the configured provider from user settings. */
object AiClients {

    fun fromPrefs(context: Context): AiProvider {
        val prefs = context.widgeon.prefs
        return when (prefs.provider) {
            Prefs.PROVIDER_GEMINI -> GeminiProvider(
                apiKey = prefs.apiKey(Prefs.PROVIDER_GEMINI),
                model = prefs.geminiModel.ifBlank { Prefs.MODEL_GEMINI_DEFAULT },
            )
            Prefs.PROVIDER_GROQ -> OpenAiCompatProvider(
                id = Prefs.PROVIDER_GROQ,
                displayName = "Groq",
                baseUrl = "https://api.groq.com/openai/v1",
                apiKey = prefs.apiKey(Prefs.PROVIDER_GROQ),
                model = prefs.groqModel.ifBlank { Prefs.MODEL_GROQ_DEFAULT },
            )
            Prefs.PROVIDER_OPENROUTER -> OpenAiCompatProvider(
                id = Prefs.PROVIDER_OPENROUTER,
                displayName = "OpenRouter",
                baseUrl = "https://openrouter.ai/api/v1",
                apiKey = prefs.apiKey(Prefs.PROVIDER_OPENROUTER),
                model = prefs.openRouterModel.ifBlank { Prefs.MODEL_OPENROUTER_DEFAULT },
                extraHeaders = mapOf(
                    "HTTP-Referer" to "https://github.com/widgeon/app",
                    "X-Title" to "Widgeon",
                ),
            )
            else -> OpenAiCompatProvider(
                id = Prefs.PROVIDER_CUSTOM,
                displayName = "Custom endpoint",
                baseUrl = prefs.customEndpoint.trim().let {
                    if (it.isBlank()) throw AiException.Auth("Add your OpenAI-compatible endpoint URL first in Settings.")
                    it
                },
                apiKey = prefs.apiKey(Prefs.PROVIDER_CUSTOM),
                model = prefs.customModel.ifBlank { throw AiException.Auth("Enter a model name for your custom endpoint in Settings.") },
            )
        }
    }

    /** Quick key/config test used by the "Test connection" button in Settings. */
    suspend fun test(context: Context): Result<String> = try {
        val p = fromPrefs(context)
        val reply = p.complete(listOf(AiMessage.user("Reply with exactly: OK")), temperature = 0.0)
        Result.success(reply.take(40))
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
