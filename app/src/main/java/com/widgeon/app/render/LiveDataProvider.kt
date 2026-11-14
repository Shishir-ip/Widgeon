package com.widgeon.app.render

import android.content.Context
import android.os.BatteryManager
import com.widgeon.app.ai.Http
import com.widgeon.app.widgeon

/** Builds a [LiveSnapshot] from the system + weather cache. */
object LiveDataProvider {

    fun snapshot(context: Context): LiveSnapshot {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val battery = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 1..100 } ?: 87
        val weather = WeatherRepository.current(context)
        return LiveSnapshot(
            timeMillis = System.currentTimeMillis(),
            battery = battery,
            weatherTemp = weather?.temp,
            weatherCode = weather?.code,
            rotation = context.widgeon.prefs.rotationCounter,
        )
    }
}

data class WeatherNow(val temp: Double?, val code: Int?, val city: String?)

/**
 * Tiny Open-Meteo client (no API key required) with a cache in prefs.
 * Docs: https://open-meteo.com/en/docs
 */
object WeatherRepository {

    private const val MAX_AGE_MS = 30L * 60 * 1000

    fun current(context: Context): WeatherNow? {
        val prefs = context.widgeon.prefs
        val raw = prefs.weatherCache ?: return null
        return runCatching {
            val obj = com.widgeon.app.ai.Http.json.parseToJsonElement(raw).let { it }
            val temp = obj.toString().let { Regex("\"temp\":(-?[0-9.]+)").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            val code = obj.toString().let { Regex("\"code\":([0-9]+)").find(it)?.groupValues?.get(1)?.toIntOrNull() }
            WeatherNow(temp, code, prefs.weatherCity.ifBlank { null })
        }.getOrNull()
    }

    fun isStale(context: Context): Boolean =
        System.currentTimeMillis() - context.widgeon.prefs.weatherCacheAt > MAX_AGE_MS

    suspend fun refresh(context: Context): WeatherNow? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val city = context.widgeon.prefs.weatherCity.trim()
        if (city.isBlank()) return@withContext current(context)
        try {
            val geo = Http.get(
                "https://geocoding-api.open-meteo.com/v1/search?name=" +
                    java.net.URLEncoder.encode(city, "UTF-8") + "&count=1&language=en&format=json"
            )
            if (geo.code !in 200..299) return@withContext current(context)
            val lat = Regex("\"latitude\":(-?[0-9.]+)").find(geo.body)?.groupValues?.get(1)
            val lon = Regex("\"longitude\":(-?[0-9.]+)").find(geo.body)?.groupValues?.get(1)
            if (lat == null || lon == null) return@withContext current(context)

            val wx = Http.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code"
            )
            if (wx.code !in 200..299) return@withContext current(context)
            val temp = Regex("\"temperature_2m\":(-?[0-9.]+)").find(wx.body)?.groupValues?.get(1)?.toDoubleOrNull()
            val code = Regex("\"weather_code\":([0-9]+)").find(wx.body)?.groupValues?.get(1)?.toIntOrNull()

            val now = WeatherNow(temp, code, city)
            context.widgeon.prefs.weatherCache = "{\"temp\":${temp ?: 0},\"code\":${code ?: 0}}"
            context.widgeon.prefs.weatherCacheAt = System.currentTimeMillis()
            now
        } catch (t: Throwable) {
            current(context)
        }
    }
}
