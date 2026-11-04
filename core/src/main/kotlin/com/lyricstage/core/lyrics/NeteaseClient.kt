package com.lyricstage.core.lyrics

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * NetEase Cloud Music. Many songs, including English, Japanese and Hindi ones, have word-level
 * timings there ("yrc"). This uses the public web endpoints, which are unofficial and may
 * change; any failure simply falls through to the other sources.
 */
class NeteaseClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://music.163.com/".toHttpUrl(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun find(title: String, artist: String, durationMs: Long?): LyricsLookup {
        val id = try {
            search(title, artist, durationMs)
        } catch (e: IOException) {
            return LyricsLookup.Unavailable
        } ?: return LyricsLookup.NotFound
        return try {
            lyrics(id)
        } catch (e: IOException) {
            LyricsLookup.Unavailable
        }
    }

    private fun request(url: HttpUrl) = Request.Builder().url(url)
        .header("User-Agent", BROWSER_UA)
        .header("Referer", "https://music.163.com/")
        .header("Cookie", "os=pc; appver=2.10.13")
        .build()

    private suspend fun getObject(url: HttpUrl): JsonObject? {
        val (code, body) = http.fetch(request(url))
        if (code !in 200..299 || body.isNullOrBlank()) throw IOException("NetEase HTTP $code")
        return runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
    }

    /** Song id of the best match, or null. */
    private suspend fun search(title: String, artist: String, durationMs: Long?): Long? {
        val q = "${TitleCleaner.cleanArtist(artist)} ${TitleCleaner.cleanTitle(title)}"
        val url = baseUrl.newBuilder().addPathSegments("api/search/get")
            .addQueryParameter("s", q).addQueryParameter("type", "1")
            .addQueryParameter("limit", "10").addQueryParameter("offset", "0").build()
        val songs = getObject(url)?.get("result")?.let { it as? JsonObject }?.get("songs") as? JsonArray ?: return null
        return songs.asSequence().mapNotNull { it as? JsonObject }.firstOrNull { s ->
            val name = s["name"]?.jsonPrimitive?.content.orEmpty()
            val artists = (s["artists"] as? JsonArray ?: s["ar"] as? JsonArray)
                ?.mapNotNull { (it as? JsonObject)?.get("name")?.jsonPrimitive?.content }.orEmpty()
            val dur = (s["duration"] ?: s["dt"])?.jsonPrimitive?.longOrNull
            Matching.titleMatches(title, name) && Matching.artistMatches(artist, artists) && Matching.durationMatches(durationMs, dur)
        }?.get("id")?.jsonPrimitive?.longOrNull
    }

    private suspend fun lyrics(id: Long): LyricsLookup {
        val url = baseUrl.newBuilder().addPathSegments("api/song/lyric/v1")
            .addQueryParameter("id", id.toString())
            .apply { for (k in listOf("lv", "kv", "tv", "rv", "yv", "ytv", "yrv")) addQueryParameter(k, if (k == "lv" || k == "kv" || k == "yv") "1" else "-1") }
            .build()
        val o = getObject(url) ?: return LyricsLookup.NotFound
        if (o["pureMusic"]?.jsonPrimitive?.booleanOrNull == true) {
            return LyricsLookup.Found(synced = false, raw = "", instrumental = true, source = SOURCE)
        }
        fun text(key: String) = (o[key] as? JsonObject)?.get("lyric")?.jsonPrimitive?.content.orEmpty()
        val roma = romanisation(text("romalrc"))
        val yrc = text("yrc")
        if (yrc.isNotBlank()) {
            val lines = parseYrc(yrc)
            if (lines.isNotEmpty()) {
                val extra = roma.mapNotNull { (t, r) -> lines.minByOrNull { kotlin.math.abs(it.startMs - t) }?.takeIf { kotlin.math.abs(it.startMs - t) < 400 }?.let { it.startMs to r } }
                return LyricsLookup.Found(true, EnhancedLrc.write(lines, extra), false, SOURCE)
            }
        }
        val lrc = cleanLrc(text("lrc"))
        if (LrcParser.isSynced(lrc) && LrcParser.parse(lrc).any { it.text.isNotBlank() }) {
            val withRoma = if (roma.isEmpty()) lrc else lrc + "\n" + roma.joinToString("\n") { (t, r) -> "[${EnhancedLrc.stamp(t)}]$r" }
            return LyricsLookup.Found(true, withRoma, false, SOURCE)
        }
        return LyricsLookup.NotFound
    }

    /** Romanised rows (time, text) from NetEase's romalrc, used as subtitles. */
    private fun romanisation(lrc: String): List<Pair<Long, String>> =
        LrcParser.parse(cleanLrc(lrc)).filter { it.text.isNotBlank() }.map { it.startMs to it.text }

    private fun cleanLrc(lrc: String): String =
        lrc.lineSequence().filter { line -> !line.trimStart().startsWith("{") && !Matching.isCredit(line.replace(Regex("""^\s*(\[[^]]*])+"""), "")) }
            .joinToString("\n")

    companion object {
        const val SOURCE = "netease"
        private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36"
        private val LINE = Regex("""^\[(\d+),(\d+)](.*)$""")
        private val WORD = Regex("""\((\d+),(\d+),\d+\)""")

        /**
         * yrc: `[lineStart,lineDuration](wordStart,wordDuration,0)word(wordStart,...)word`,
         * all in milliseconds. Credit lines are JSON objects and are skipped.
         */
        fun parseYrc(yrc: String): List<TimedLine> = yrc.lineSequence().mapNotNull { row ->
            val m = LINE.find(row.trim()) ?: return@mapNotNull null
            val body = m.groupValues[3]
            val tags = WORD.findAll(body).toList()
            val words = tags.mapIndexedNotNull { i, t ->
                val end = if (i + 1 < tags.size) tags[i + 1].range.first else body.length
                val text = body.substring(t.range.last + 1, end)
                if (text.isBlank()) return@mapIndexedNotNull null
                val s = t.groupValues[1].toLong()
                TimedWord(text, s, s + t.groupValues[2].toLong())
            }
            val joined = words.joinToString("") { it.text }
            if (words.isEmpty() || Matching.isCredit(joined)) return@mapNotNull null
            // tokens carry their own spaces ("Never ", "gonna "); merge pieces of one word
            val merged = ArrayList<TimedWord>()
            var pending: TimedWord? = null
            for (w in words) {
                val p = pending
                pending = if (p == null) w else TimedWord(p.text + w.text, p.startMs, w.endMs)
                if (w.text.endsWith(" ")) {
                    merged += pending!!.copy(text = pending!!.text.trim())
                    pending = null
                }
            }
            pending?.let { merged += it.copy(text = it.text.trim()) }
            val spaced = joined.contains(' ')
            TimedLine(m.groupValues[1].toLong(), if (spaced) merged else words, joinWithSpaces = spaced)
        }.toList()
    }
}
