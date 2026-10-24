package com.onyx.keyboard.keyboard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Giphy REST client for the optional GIF panel.
 *
 * Uses Giphy's public beta key; swap [API_KEY] for your own production key.
 * All network activity is strictly opt-in (Quick settings → Network GIF panel)
 * and is never used for typing data — that stays on the device, always.
 */
object GiphyClient {

    private const val API_KEY = "dc6zaTOxFJmzC" // Giphy public beta key
    private const val ENDPOINT = "https://api.giphy.com/v1/gifs/search"

    suspend fun search(query: String, limit: Int = 24): List<GifItem> =
        withContext(Dispatchers.IO) {
            val url = "$ENDPOINT?api_key=$API_KEY&q=" +
                URLEncoder.encode(query, "UTF-8") +
                "&limit=$limit&rating=pg"
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 6000
            try {
                require(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(body: String): List<GifItem> {
        val root = JSONObject(body)
        val data = root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<GifItem>(data.length())
        for (i in 0 until data.length()) {
            val obj = data.optJSONObject(i) ?: continue
            val id = obj.optString("id", "$i")
            val images = obj.optJSONObject("images") ?: continue
            val preview = images.optJSONObject("fixed_width_small")
                ?.optString("url").orEmpty()
            val full = images.optJSONObject("downsized")
                ?.optString("url").orEmpty()
            if (preview.isNotEmpty()) {
                out += GifItem(id, preview, full.ifBlank { preview })
            }
        }
        return out
    }
}
