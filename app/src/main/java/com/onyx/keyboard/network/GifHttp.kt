package com.onyx.keyboard.network

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Hardened downloader for the optional GIF panel.
 *
 * The naive `URL.readBytes()` has NO timeout: a stalled CDN froze the IME
 * coroutine forever and left the keyboard unable to insert anything until
 * the service was restarted. This wrapper adds:
 *  - connect/read timeouts so a dead network can never hang the IME,
 *  - a hard size cap (a hostile/huge payload can't exhaust memory),
 *  - a content-type sanity check (HTML error pages are rejected),
 *  - clean disconnect even on failure.
 */
object GifHttp {

    /** 15 MB is far beyond any real GIF preview/full asset. */
    const val MAX_BYTES: Long = 15L * 1024 * 1024
    const val CONNECT_TIMEOUT_MS = 10_000
    const val READ_TIMEOUT_MS = 20_000

    /**
     * Downloads [url]; returns null on ANY failure (bad status, timeout,
     * oversize, wrong content type) — the caller falls back to inserting
     * the plain URL text.
     */
    fun fetch(url: String): ByteArray? = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "OnyxKeyboard/1.0 (IME GIF panel)")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) return@runCatching null
            val bytes = readCapped(conn)
            if (!isAcceptable(conn.contentType, bytes)) null else bytes
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Streams the body while enforcing [MAX_BYTES] (also guards chunked responses). */
    private fun readCapped(conn: HttpURLConnection): ByteArray? {
        val declared = conn.contentLengthLong
        if (declared > MAX_BYTES) return null
        val input = conn.inputStream ?: return null
        val initial = if (declared in 1..Int.MAX_VALUE.toLong()) declared.toInt() else 32 * 1024
        val out = ByteArrayOutputStream(initial)
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    /**
     * Type/size gate — pure so tests can run it without sockets.
     * Accepts image/gif and unknown/missing types (some CDNs use
     * application/octet-stream), rejects HTML/text (error pages) and
     * anything empty or over the cap.
     */
    fun isAcceptable(contentType: String?, bytes: ByteArray?, maxBytes: Long = MAX_BYTES): Boolean {
        if (bytes == null || bytes.isEmpty() || bytes.size > maxBytes) return false
        val type = contentType?.substringBefore(';')?.trim()?.lowercase()
        if (type != null && (type.startsWith("text/") || type == "application/json")) return false
        return true
    }
}
