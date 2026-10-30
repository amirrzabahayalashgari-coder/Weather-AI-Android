package com.weatherai.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var cityInput: EditText
    private lateinit var status: TextView
    private lateinit var content: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(15, 38, 76)
        window.navigationBarColor = Color.rgb(15, 38, 76)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 22, 18, 18); setBackgroundColor(Color.rgb(242, 247, 255)) }
        val title = TextView(this).apply { text = "هواشناسی هوشمند"; textSize = 26f; setTextColor(Color.rgb(18, 48, 92)); gravity = Gravity.CENTER; setPadding(0, 8, 0, 18) }
        page.addView(title)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        cityInput = EditText(this).apply { setSingleLine(true); hint = "نام شهر"; textSize = 16f; setText("تهران") }
        row.addView(cityInput, LinearLayout.LayoutParams(0, 54, 1f))
        row.addView(Button(this).apply { text = "جستجو"; setOnClickListener { searchCity(cityInput.text.toString().trim()) } })
        page.addView(row)
        status = TextView(this).apply { text = "در حال آماده‌سازی..."; textSize = 14f; setTextColor(Color.DKGRAY); setPadding(4, 14, 4, 10) }
        page.addView(status)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(page)
        searchCity("تهران")
    }

    private fun searchCity(city: String) {
        if (city.isBlank()) { status.text = "لطفاً نام شهر را وارد کنید."; return }
        status.text = "در حال دریافت اطلاعات هواشناسی..."
        content.removeAllViews()
        executor.execute {
            try {
                val encoded = URLEncoder.encode(city, "UTF-8")
                val geo = getJson("https://geocoding-api.open-meteo.com/v1/search?name=" + encoded + "&count=1&language=fa&format=json")
                val results = geo.optJSONArray("results")
                if (results == null || results.length() == 0) { runOnUiThread { status.text = "شهری با این نام پیدا نشد." }; return@execute }
                val place = results.getJSONObject(0)
                val lat = place.getDouble("latitude")
                val lon = place.getDouble("longitude")
                val cityName = place.optString("name", city)
                val country = place.optString("country", "")
                val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,surface_pressure,wind_speed_10m&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max&timezone=auto&forecast_days=7&wind_speed_unit=kmh"
                val weather = getJson(endpoint)
                runOnUiThread { status.text = cityName + "، " + country; showWeather(weather, cityName, country) }
            } catch (e: Exception) { runOnUiThread { status.text = "خطا در دریافت اطلاعات؛ اتصال اینترنت را بررسی کنید." } }
        }
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"; connection.connectTimeout = 15000; connection.readTimeout = 15000
        return try { JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) } finally { connection.disconnect() }
    }

    private fun showWeather(data: JSONObject, city: String, country: String) {
        content.removeAllViews()
        val current = data.getJSONObject("current")
        val temp = current.optDouble("temperature_2m", 0.0).toInt()
        val feels = current.optDouble("apparent_temperature", 0.0).toInt()
        addCard(city + "، " + country, weatherText(current.optInt("weather_code")), temp.toString() + "°", "دمای احساسی: " + feels + "°")
        val details = "رطوبت: " + current.optInt("relative_humidity_2m") + "%\nباد: " + current.optDouble("wind_speed_10m").toInt() + " کیلومتر بر ساعت\nفشار: " + current.optDouble("surface_pressure").toInt() + " هکتوپاسکال\nبارش: " + current.optDouble("precipitation") + " میلی‌متر"
        addCard("جزئیات وضعیت فعلی", details, "", "")
        val daily = data.optJSONObject("daily") ?: return
        val dates = daily.optJSONArray("time") ?: return
        val highs = daily.optJSONArray("temperature_2m_max") ?: return
        val lows = daily.optJSONArray("temperature_2m_min") ?: return
        val codes = daily.optJSONArray("weather_code")
        val uv = daily.optJSONArray("uv_index_max")
        for (i in 0 until dates.length()) {
            val line = weatherText(codes?.optInt(i) ?: 0) + "\nبیشینه: " + highs.optDouble(i).toInt() + "°  |  کمینه: " + lows.optDouble(i).toInt() + "°\nشاخص UV: " + (uv?.optDouble(i)?.toInt() ?: 0)
            addCard(dates.optString(i), line, "", "")
        }
    }

    private fun addCard(heading: String, detail: String, large: String, sub: String) {
        val card = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 16, 18, 16); setBackgroundColor(Color.WHITE) }
        card.addView(TextView(this).apply { text = heading; textSize = 18f; setTextColor(Color.rgb(25, 61, 105)) })
        if (large.isNotEmpty()) card.addView(TextView(this).apply { text = large; textSize = 48f; setTextColor(Color.rgb(22, 104, 180)); gravity = Gravity.CENTER; setPadding(0, 8, 0, 4) })
        if (sub.isNotEmpty()) card.addView(TextView(this).apply { text = sub; textSize = 15f; gravity = Gravity.CENTER; setTextColor(Color.DKGRAY) })
        card.addView(TextView(this).apply { text = detail; textSize = 15f; setTextColor(Color.DKGRAY); setPadding(0, 8, 0, 0) })
        val params = LinearLayout.LayoutParams(-1, -2); params.setMargins(0, 8, 0, 8); content.addView(card, params)
    }

    private fun weatherText(code: Int): String = when (code) {
        0 -> "آسمان صاف"; 1, 2 -> "کمی ابری"; 3 -> "ابری"; 45, 48 -> "مه‌آلود"
        51, 53, 55 -> "نم‌نم باران"; 56, 57, 66, 67 -> "باران یخ‌زده"
        61, 63, 65, 80, 81, 82 -> "بارانی"; 71, 73, 75, 77, 85, 86 -> "برفی"
        95, 96, 99 -> "رعد و برق"; else -> "وضعیت نامشخص"
    }

    override fun onDestroy() { executor.shutdown(); super.onDestroy() }
}