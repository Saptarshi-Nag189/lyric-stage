package com.lyricstage.core.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import java.util.zip.Inflater

/**
 * Kugou Music. Its "KRC" lyrics carry word-by-word timing for a large catalogue, including
 * many English, Hindi and Japanese songs. Unofficial endpoints, used by desktop lyric tools
 * for years; any failure falls through to the other sources.
 *
 * HTTPS is tried first; the service has long answered plain HTTP only on some edges, so
 * HTTP is the fallback (the app allows cleartext for this one host).
 */
class KugouClient(
    private val http: OkHttpClient,
    private val baseUrls: List<HttpUrl> = listOf("https://lyrics.kugou.com/".toHttpUrl(), "http://lyrics.kugou.com/".toHttpUrl()),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(title: String, artist: String, durationMs: Long?): LyricsLookup {
        for (base in baseUrls) {
            try {
                return findAt(base, title, artist, durationMs)
            } catch (e: IOException) {
                // try the next address
            }
        }
        return LyricsLookup.Unavailable
    }

    private suspend fun findAt(base: HttpUrl, title: String, artist: String, durationMs: Long?): LyricsLookup {
        val search = base.newBuilder().addPathSegment("search")
            .addQueryParameter("ver", "1").addQueryParameter("man", "yes").addQueryParameter("client", "pc")
            .addQueryParameter("keyword", "${TitleCleaner.cleanArtist(artist)} - ${TitleCleaner.cleanTitle(title)}")
            .apply { durationMs?.takeIf { it > 0 }?.let { addQueryParameter("duration", it.toString()) } }
            .addQueryParameter("hash", "")
            .build()
        val candidates = getObject(search)?.get("candidates") as? JsonArray ?: return LyricsLookup.NotFound
        val pick = candidates.asSequence().mapNotNull { it as? JsonObject }.firstOrNull { c ->
            val song = c.str("song").orEmpty()
            val singers = c.str("singer").orEmpty().split('、', '&', ',', '/').map { it.trim() }
            val dur = (c["duration"] as? JsonPrimitive)?.longOrNull
            Matching.titleMatches(title, song) && Matching.artistMatches(artist, singers) &&
                Matching.durationMatches(durationMs, dur, slackMs = 3_000)
        } ?: return LyricsLookup.NotFound
        val id = pick.str("id") ?: return LyricsLookup.NotFound
        val key = pick.str("accesskey") ?: return LyricsLookup.NotFound

        val download = base.newBuilder().addPathSegment("download")
            .addQueryParameter("ver", "1").addQueryParameter("client", "pc")
            .addQueryParameter("id", id).addQueryParameter("accesskey", key)
            .addQueryParameter("fmt", "krc").addQueryParameter("charset", "utf8")
            .build()
        val body = getObject(download) ?: return LyricsLookup.NotFound
        if ((body["status"] as? JsonPrimitive)?.intOrNull.let { it != null && it != 200 }) return LyricsLookup.NotFound
        val content = body.str("content")?.takeIf { it.isNotBlank() } ?: return LyricsLookup.NotFound
        val krc = runCatching { decodeKrc(content) }.getOrNull() ?: return LyricsLookup.NotFound
        val lines = parseKrc(krc)
        if (lines.isEmpty()) return LyricsLookup.NotFound
        return LyricsLookup.Found(true, EnhancedLrc.write(lines), false, SOURCE)
    }

    private suspend fun getObject(url: HttpUrl): JsonObject? {
        val req = Request.Builder().url(url).header("User-Agent", "Android14-AndroidPhone-11451-376-0-Lyric-wifi").build()
        val (code, text) = http.fetch(req)
        if (code !in 200..299 || text.isNullOrBlank()) throw IOException("Kugou HTTP $code")
        return runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()
    }

    companion object {
        const val SOURCE = "kugou"

        private val KEY = intArrayOf(64, 71, 97, 119, 94, 50, 116, 71, 81, 54, 49, 45, 206, 210, 110, 105)
        private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val WORD = Regex("""<(\d+),(\d+),\d+>""")

        private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.content

        /** base64, then a 4-byte "krc1" header, a 16-byte XOR key and zlib. */
        fun decodeKrc(content: String): String {
            val bytes = Base64.getDecoder().decode(content.trim())
            require(bytes.size > 4) { "short KRC" }
            val data = ByteArray(bytes.size - 4) { i -> (bytes[i + 4].toInt() xor KEY[i % KEY.size]).toByte() }
            val inflater = Inflater()
            inflater.setInput(data)
            val out = ByteArrayOutputStream(data.size * 4)
            val buf = ByteArray(8192)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) break
                out.write(buf, 0, n)
            }
            inflater.end()
            return out.toString(Charsets.UTF_8.name()).removePrefix("﻿")
        }

        /**
         * KRC rows: `[lineStart,lineDuration]<offset,duration,0>word<offset,duration,0>word`,
         * word offsets relative to the line start. Rewritten as NetEase yrc (absolute word
         * times) so both share one word builder.
         */
        fun parseKrc(krc: String): List<TimedLine> {
            val yrc = krc.lineSequence().mapNotNull { row ->
                val m = LINE.find(row.trim()) ?: return@mapNotNull null
                val start = m.groupValues[1].toLong()
                val body = WORD.replace(m.groupValues[3]) { w ->
                    "(${start + w.groupValues[1].toLong()},${w.groupValues[2]},0)"
                }
                "[${m.groupValues[1]},${m.groupValues[2]}]$body"
            }.joinToString("\n")
            return NeteaseClient.parseYrc(yrc)
        }
    }
}
