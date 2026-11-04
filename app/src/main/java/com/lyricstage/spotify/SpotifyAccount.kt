package com.lyricstage.spotify

import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * The linked Spotify account: sign-in with PKCE (no client secret exists or is needed),
 * token refresh and unlinking. Tokens are kept in [SecureStore]. Only the Web API is used,
 * with read access to Liked Songs and playlists.
 */
class SpotifyAccount(private val http: OkHttpClient, private val store: SecureStore) {
    private val _name = MutableStateFlow(if (store.get(K_REFRESH) != null) store.getPlain(K_NAME) ?: "" else null)
    /** The linked account's display name; null when not linked. */
    val name: StateFlow<String?> = _name.asStateFlow()
    val linked: Boolean get() = _name.value != null

    private val lock = Mutex()

    /** The Spotify sign-in page to open in the browser. */
    fun signInUri(): Uri {
        val v = random(64)
        val s = random(16)
        // kept on disk: the app may be closed while the browser is open
        store.put(K_VERIFIER, v)
        store.putPlain(K_STATE, s)
        val challenge = b64(MessageDigest.getInstance("SHA-256").digest(v.toByteArray(Charsets.US_ASCII)))
        return Uri.parse(AUTHORIZE).buildUpon()
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("state", s)
            .build()
    }

    /** True for the redirect Spotify sends back to the app after sign-in. */
    fun isCallback(uri: Uri?): Boolean = uri != null && uri.scheme == "lyricstage" && uri.host == "spotify-callback"

    /** Finishes sign-in from the redirect. Returns false when it was refused or failed. */
    suspend fun complete(uri: Uri): Boolean = withContext(Dispatchers.IO) {
        val code = uri.getQueryParameter("code") ?: return@withContext false
        val v = store.get(K_VERIFIER) ?: return@withContext false
        if (uri.getQueryParameter("state") != store.getPlain(K_STATE)) return@withContext false
        val ok = runCatching {
            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT)
                .add("client_id", CLIENT_ID)
                .add("code_verifier", v)
                .build()
            saveTokens(post(body))
            val me = get("$API/me")
            val display = me.optString("display_name").ifBlank { me.optString("id") }
            store.putPlain(K_NAME, display)
            _name.value = display
            true
        }.getOrDefault(false)
        store.put(K_VERIFIER, null)
        store.putPlain(K_STATE, null)
        ok
    }

    fun unlink() {
        store.clear()
        _name.value = null
    }

    /** GET a Web API URL with a fresh token. */
    suspend fun get(url: String): JSONObject = withContext(Dispatchers.IO) {
        val first = call(url, token())
        if (first.first != 401) return@withContext parse(first)
        // token revoked or expired early: refresh once and retry
        lock.withLock { store.putLong(K_EXPIRES, 0) }
        parse(call(url, token()))
    }

    private fun call(url: String, token: String): Pair<Int, String> {
        val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
        http.newCall(req).execute().use { r ->
            if (r.code == 429) throw RateLimited(r.header("Retry-After")?.toLongOrNull() ?: 30)
            return r.code to (r.body?.string() ?: "")
        }
    }

    private fun parse(reply: Pair<Int, String>): JSONObject {
        if (reply.first !in 200..299) throw IOException("Spotify HTTP ${reply.first}")
        return JSONObject(reply.second)
    }

    private suspend fun token(): String = lock.withLock {
        val access = store.get(K_ACCESS)
        if (access != null && System.currentTimeMillis() < store.getLong(K_EXPIRES) - 60_000) return access
        val refresh = store.get(K_REFRESH) ?: throw IOException("Spotify not linked")
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .add("client_id", CLIENT_ID)
            .build()
        val reply = runCatching { post(body) }.getOrElse { e ->
            if (e is Refused) withContext(Dispatchers.Main) { unlink() }
            throw IOException("Spotify refresh failed", e)
        }
        saveTokens(reply)
        store.get(K_ACCESS) ?: throw IOException("Spotify token missing")
    }

    private fun post(body: FormBody): JSONObject {
        val req = Request.Builder().url(TOKEN).post(body).build()
        http.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            // a refused grant (revoked access): the account has to be linked again
            if (r.code == 400 && text.contains("invalid_grant")) throw Refused()
            if (!r.isSuccessful) throw IOException("Spotify token HTTP ${r.code}")
            return JSONObject(text)
        }
    }

    private fun saveTokens(o: JSONObject) {
        store.put(K_ACCESS, o.getString("access_token"))
        // Spotify may rotate the refresh token; keep the old one when it doesn't
        o.optString("refresh_token").takeIf { it.isNotBlank() }?.let { store.put(K_REFRESH, it) }
        store.putLong(K_EXPIRES, System.currentTimeMillis() + o.optLong("expires_in", 3600) * 1000)
    }

    class RateLimited(val retryAfterSeconds: Long) : IOException("Spotify rate limit")
    private class Refused : IOException("Spotify refused the grant")

    companion object {
        const val CLIENT_ID = "f64735d0c8694a96832a16bc4c4ba45a"
        const val REDIRECT = "lyricstage://spotify-callback"
        const val API = "https://api.spotify.com/v1"
        private const val AUTHORIZE = "https://accounts.spotify.com/authorize"
        private const val TOKEN = "https://accounts.spotify.com/api/token"
        private const val SCOPES = "user-library-read playlist-read-private"
        private const val K_ACCESS = "access"
        private const val K_REFRESH = "refresh"
        private const val K_EXPIRES = "expires"
        private const val K_NAME = "name"
        private const val K_VERIFIER = "verifier"
        private const val K_STATE = "state"

        private val rnd = SecureRandom()
        private fun random(bytes: Int): String = b64(ByteArray(bytes).also(rnd::nextBytes))
        private fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
