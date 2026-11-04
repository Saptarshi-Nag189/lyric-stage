package com.lyricstage.core.lyrics

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Musixmatch, through the endpoints its desktop app uses. It has word-by-word "richsync"
 * timings for many popular songs (the same data Spotify shows). Unofficial: the token can be
 * refused or rate limited, in which case this source rests for a while and the others carry on.
 */
class MusixmatchClient(
    private val http: OkHttpClient,
    private val baseUrl: HttpUrl = "https://apic-desktop.musixmatch.com/ws/1.1/".toHttpUrl(),
    private val now: () -> Long = System::currentTimeMillis,
    /** Keeps the token across app starts, so it is not requested on every launch. */
    private val store: TokenStore = TokenStore.InMemory(),
) {
    /** Where the user token and the rest-until time live. */
    interface TokenStore {
        var token: String?
        var restUntil: Long

        class InMemory : TokenStore {
            override var token: String? = null
            override var restUntil: Long = 0
        }
    }

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tokenLock = Mutex()
    private var token: String?
        get() = store.token
        set(v) { store.token = v }
    private var restUntil: Long
        get() = store.restUntil
        set(v) { store.restUntil = v }

    suspend fun find(title: String, artist: String, album: String?, durationMs: Long?): LyricsLookup {
        if (now() < restUntil) return LyricsLookup.Unavailable
        return try {
            findWithToken(title, artist, album, durationMs, retry = true)
        } catch (e: IOException) {
            LyricsLookup.Unavailable
        }
    }

    private suspend fun findWithToken(title: String, artist: String, album: String?, durationMs: Long?, retry: Boolean): LyricsLookup {
        val t = token() ?: return LyricsLookup.Unavailable
        val match = call("matcher.track.get", t) {
            addQueryParameter("q_track", TitleCleaner.cleanTitle(title))
            addQueryParameter("q_artist", TitleCleaner.cleanArtist(artist))
            if (!album.isNullOrBlank()) addQueryParameter("q_album", album)
            durationMs?.takeIf { it > 0 }?.let { addQueryParameter("q_duration", (it / 1000).toString()) }
        }
        when (match.status) {
            401 -> {
                token = null
                return if (retry) findWithToken(title, artist, album, durationMs, retry = false) else rest()
            }
            404 -> return LyricsLookup.NotFound
            200 -> Unit
            else -> return LyricsLookup.Unavailable
        }
        val track = match.body?.obj("track") ?: return LyricsLookup.NotFound
        val name = track.str("track_name").orEmpty()
        val artistName = track.str("artist_name").orEmpty()
        val length = track.long("track_length")?.times(1000)
        if (!Matching.titleMatches(title, name) || !Matching.artistMatches(artist, listOf(artistName)) || !Matching.durationMatches(durationMs, length)) {
            return LyricsLookup.NotFound
        }
        if (track.int("instrumental") == 1) return LyricsLookup.Found(false, "", true, SOURCE)
        val id = track.long("track_id") ?: return LyricsLookup.NotFound

        if (track.int("has_richsync") == 1) {
            val rich = call("track.richsync.get", t) { addQueryParameter("track_id", id.toString()) }
            val body = rich.body?.obj("richsync")?.str("richsync_body")
            if (rich.status == 200 && !body.isNullOrBlank()) {
                val lines = parseRichsync(body)
                if (lines.isNotEmpty()) return LyricsLookup.Found(true, EnhancedLrc.write(lines), false, SOURCE)
            }
        }
        if (track.int("has_subtitles") == 1) {
            val sub = call("track.subtitle.get", t) {
                addQueryParameter("track_id", id.toString())
                addQueryParameter("subtitle_format", "lrc")
            }
            val lrc = sub.body?.obj("subtitle")?.str("subtitle_body")
            if (sub.status == 200 && !lrc.isNullOrBlank() && LrcParser.isSynced(lrc)) return LyricsLookup.Found(true, lrc, false, SOURCE)
        }
        return LyricsLookup.NotFound
    }

    private fun rest(): LyricsLookup {
        restUntil = now() + REST_MS
        return LyricsLookup.Unavailable
    }

    private suspend fun token(): String? = tokenLock.withLock {
        token?.let { return it }
        if (now() < restUntil) return null
        val r = call("token.get", null) { addQueryParameter("user_language", "en") }
        val t = r.body?.str("user_token")
        if (r.status != 200 || t.isNullOrBlank() || t.startsWith("UpgradeOnly")) {
            // captcha or refusal: try again later instead of hammering the service
            restUntil = now() + REST_MS
            return null
        }
        token = t
        t
    }

    private class Reply(val status: Int, val body: JsonObject?)

    private suspend fun call(method: String, token: String?, params: HttpUrl.Builder.() -> Unit): Reply {
        val url = baseUrl.newBuilder().addPathSegment(method)
            .addQueryParameter("app_id", APP_ID)
            .addQueryParameter("format", "json")
            .addQueryParameter("t", now().toString())
            .apply { if (token != null) addQueryParameter("usertoken", token) }
            .apply(params)
            .build()
        val req = Request.Builder().url(url)
            .header("authority", "apic-desktop.musixmatch.com")
            .header("cookie", "AWSELBCORS=0; AWSELB=0")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Musixmatch/3.14.4564 Chrome/124.0 Electron Safari/537.36")
            .build()
        val (code, text) = http.fetch(req)
        if (code !in 200..299 || text.isNullOrBlank()) throw IOException("Musixmatch HTTP $code")
        val message = runCatching { json.parseToJsonElement(text) }.getOrNull()?.obj("message") ?: throw IOException("Musixmatch: bad reply")
        val status = message.obj("header")?.int("status_code") ?: 0
        return Reply(status, message["body"] as? JsonObject)
    }

    companion object {
        const val SOURCE = "musixmatch"
        private const val APP_ID = "web-desktop-app-v1.0"
        private const val REST_MS = 10 * 60_000L

        private val parser = Json { ignoreUnknownKeys = true; isLenient = true }

        /**
         * richsync_body: a JSON array of lines `{ts, te, l: [{c, o}], x}`. ts and te are the
         * line's start and end in seconds; each piece c starts o seconds after ts. Pieces are
         * words or the spaces between them; a word ends where the next piece starts.
         */
        fun parseRichsync(body: String): List<TimedLine> {
            val arr = runCatching { parser.parseToJsonElement(body) as? JsonArray }.getOrNull() ?: return emptyList()
            return arr.mapNotNull { el ->
                val line = el as? JsonObject ?: return@mapNotNull null
                val ts = line.dbl("ts") ?: return@mapNotNull null
                val te = line.dbl("te") ?: ts
                val pieces = (line["l"] as? JsonArray)?.mapNotNull { p ->
                    val o = p as? JsonObject ?: return@mapNotNull null
                    val c = o.str("c") ?: return@mapNotNull null
                    c to (o.dbl("o") ?: 0.0)
                }.orEmpty()
                val words = ArrayList<TimedWord>()
                for ((i, piece) in pieces.withIndex()) {
                    val (c, off) = piece
                    if (c.isBlank()) continue
                    val start = ((ts + off) * 1000).toLong()
                    val endSec = pieces.drop(i + 1).firstOrNull()?.second?.let { ts + it } ?: te
                    words += TimedWord(c, start, maxOf(start + 1, (endSec * 1000).toLong()))
                }
                if (words.isEmpty()) null else TimedLine((ts * 1000).toLong(), words, joinWithSpaces = pieces.any { it.first.isBlank() })
            }
        }

        private fun JsonElement.obj(key: String): JsonObject? = (this as? JsonObject)?.get(key) as? JsonObject
        private fun JsonObject.str(key: String): String? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.content
        private fun JsonObject.int(key: String): Int? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull
        private fun JsonObject.long(key: String): Long? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
        private fun JsonObject.dbl(key: String): Double? = (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
    }
}
