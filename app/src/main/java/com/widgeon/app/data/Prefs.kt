package com.widgeon.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * App settings + secrets. API keys live in EncryptedSharedPreferences and are
 * excluded from backups (see res/xml/backup_rules.xml).
 */
class Prefs(context: Context) {

    private val secure: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "widgeon_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    private val plain: SharedPreferences =
        context.getSharedPreferences("widgeon_prefs", Context.MODE_PRIVATE)

    // ---------- Provider / keys ----------

    var provider: String
        get() = secure.getString(KEY_PROVIDER, PROVIDER_GEMINI) ?: PROVIDER_GEMINI
        set(value) = secure.edit().putString(KEY_PROVIDER, value).apply()

    fun apiKey(providerId: String): String =
        secure.getString("key_$providerId", "").orEmpty()

    fun setApiKey(providerId: String, key: String) =
        secure.edit().putString("key_$providerId", key.trim()).apply()

    var geminiModel: String
        get() = secure.getString("gemini_model", MODEL_GEMINI_DEFAULT) ?: MODEL_GEMINI_DEFAULT
        set(v) = secure.edit().putString("gemini_model", v.trim()).apply()

    var groqModel: String
        get() = secure.getString("groq_model", MODEL_GROQ_DEFAULT) ?: MODEL_GROQ_DEFAULT
        set(v) = secure.edit().putString("groq_model", v.trim()).apply()

    var openRouterModel: String
        get() = secure.getString("or_model", MODEL_OPENROUTER_DEFAULT) ?: MODEL_OPENROUTER_DEFAULT
        set(v) = secure.edit().putString("or_model", v.trim()).apply()

    var customModel: String
        get() = secure.getString("custom_model", "") ?: ""
        set(v) = secure.edit().putString("custom_model", v.trim()).apply()

    var customEndpoint: String
        get() = secure.getString("custom_endpoint", "") ?: ""
        set(v) = secure.edit().putString("custom_endpoint", v.trim()).apply()

    // ---------- Appearance / feedback ----------

    var themeMode: String
        get() = plain.getString("theme_mode", THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) = plain.edit().putString("theme_mode", v).apply()

    var hapticsEnabled: Boolean
        get() = plain.getBoolean("haptics", true)
        set(v) = plain.edit().putBoolean("haptics", v).apply()

    var notifEnabled: Boolean
        get() = plain.getBoolean("notifications", true)
        set(v) = plain.edit().putBoolean("notifications", v).apply()

    var onboardingDone: Boolean
        get() = plain.getBoolean("onboarding_done", false)
        set(v) = plain.edit().putBoolean("onboarding_done", v).apply()

    // ---------- Weather ----------

    var weatherCity: String
        get() = plain.getString("weather_city", "") ?: ""
        set(v) = plain.edit().putString("weather_city", v.trim()).apply()

    var weatherCache: String?
        get() = plain.getString("weather_cache", null)
        set(v) = plain.edit().putString("weather_cache", v).apply()

    var weatherCacheAt: Long
        get() = plain.getLong("weather_cache_at", 0L)
        set(v) = plain.edit().putLong("weather_cache_at", v).apply()

    // ---------- Widget bindings (appWidgetId -> designId) ----------

    fun bindWidget(appWidgetId: Int, designId: String) =
        plain.edit().putString("bind_$appWidgetId", designId).apply()

    fun unbindWidget(appWidgetId: Int) =
        plain.edit().remove("bind_$appWidgetId").apply()

    fun boundDesignId(appWidgetId: Int): String? =
        plain.getString("bind_$appWidgetId", null)

    fun allBindings(): Map<Int, String> =
        plain.all.entries
            .filter { it.key.startsWith("bind_") }
            .mapNotNull { (k, v) -> (k.removePrefix("bind_").toIntOrNull())?.let { it to (v as? String ?: "") } }
            .filter { it.second.isNotBlank() }
            .toMap()

    fun clearBindingsFor(designId: String) {
        val editor = plain.edit()
        allBindings().forEach { (id, d) -> if (d == designId) editor.remove("bind_$id") }
        editor.apply()
    }

    companion object {
        const val KEY_PROVIDER = "provider"
        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_OPENROUTER = "openrouter"
        const val PROVIDER_CUSTOM = "custom"

        const val MODEL_GEMINI_DEFAULT = "gemini-3.5-flash-lite"
        const val MODEL_GROQ_DEFAULT = "openai/gpt-oss-120b"
        const val MODEL_OPENROUTER_DEFAULT = "openai/gpt-oss-120b"

        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"

        val PROVIDER_ORDER = listOf(PROVIDER_GEMINI, PROVIDER_GROQ, PROVIDER_OPENROUTER, PROVIDER_CUSTOM)
    }
}
