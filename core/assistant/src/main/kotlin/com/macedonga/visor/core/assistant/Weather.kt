package com.macedonga.visor.core.assistant

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Where the phone is: a place name and its coordinates. */
data class Spot(val name: String, val lat: Double, val lon: Double)

/** The forecast from Open-Meteo: free, no key, no account. Only coordinates or a place name leave the phone. */
internal object Weather {

    /** [name] as a place, from Open-Meteo's geocoder; null when nothing matches. */
    suspend fun find(name: String): Spot? {
        val r = get("https://geocoding-api.open-meteo.com/v1/search?count=1&name=" + URLEncoder.encode(name, "UTF-8"))
        val hit = r.optJSONArray("results")?.optJSONObject(0) ?: return null
        val label = listOf(hit.optString("name"), hit.optString("country")).filter { it.isNotEmpty() }.joinToString(", ")
        return Spot(label, hit.getDouble("latitude"), hit.getDouble("longitude"))
    }

    /** Now, today and tomorrow at [spot], in a few plain lines for the model. */
    suspend fun forecast(spot: Spot): String {
        val r = get(
            "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f".format(Locale.ROOT, spot.lat, spot.lon) +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=2",
        )
        val now = r.getJSONObject("current")
        val daily = r.getJSONObject("daily")
        fun day(i: Int, label: String) = "$label: ${sky(daily.getJSONArray("weather_code").optInt(i))}, " +
            "${daily.getJSONArray("temperature_2m_min").optDouble(i).c()} to ${daily.getJSONArray("temperature_2m_max").optDouble(i).c()}, " +
            "${daily.getJSONArray("precipitation_probability_max").optInt(i)}% chance of rain."
        return "Weather in ${spot.name}. Now: ${sky(now.optInt("weather_code"))}, ${now.optDouble("temperature_2m").c()} " +
            "(feels like ${now.optDouble("apparent_temperature").c()}), wind ${now.optDouble("wind_speed_10m").toInt()} km/h. " +
            day(0, "Today") + " " + day(1, "Tomorrow")
    }

    private fun Double.c() = "${Math.round(this)}°C"

    /** WMO weather codes, as Open-Meteo documents them. */
    private fun sky(code: Int) = when (code) {
        0 -> "clear"
        1, 2 -> "partly cloudy"
        3 -> "overcast"
        45, 48 -> "fog"
        in 51..57 -> "drizzle"
        in 61..67, in 80..82 -> "rain"
        in 71..77, 85, 86 -> "snow"
        in 95..99 -> "thunderstorms"
        else -> "unknown sky"
    }

    private suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val http = URL(url).openConnection() as HttpURLConnection
        try {
            http.connectTimeout = 5_000
            http.readTimeout = 8_000
            check(http.responseCode in 200..299) { "the weather service answered HTTP ${http.responseCode}" }
            JSONObject(http.inputStream.use { it.readBytes().decodeToString() })
        } finally {
            http.disconnect()
        }
    }
}
