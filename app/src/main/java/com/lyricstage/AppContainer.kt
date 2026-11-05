package com.lyricstage

import com.lyricstage.audio.AudioOutput

import android.content.Context
import com.lyricstage.core.lyrics.LrclibClient
import com.lyricstage.core.lyrics.LyricsSources
import com.lyricstage.core.lyrics.KugouClient
import com.lyricstage.core.lyrics.MusixmatchClient
import com.lyricstage.core.lyrics.NeteaseClient
import com.lyricstage.lyrics.Connectivity
import com.lyricstage.lyrics.LyricsRepository
import com.lyricstage.lyrics.MusixmatchTokenStore
import com.lyricstage.lyrics.db.LyricsDatabase
import com.lyricstage.media.ActiveSessionTracker
import com.lyricstage.settings.SettingsStore
import com.lyricstage.spotify.LibrarySync
import com.lyricstage.spotify.SecureStore
import com.lyricstage.spotify.SpotifyAccount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Hand-wired dependencies. Small enough not to need Hilt. */
class AppContainer(context: Context) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val http = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()

    val database = LyricsDatabase.create(context)
    val lrclib = LrclibClient(http)
    // created on first lookup rather than at start
    private val netease by lazy { NeteaseClient(http) }
    private val musixmatch by lazy { MusixmatchClient(http, store = MusixmatchTokenStore(context)) }
    private val kugou by lazy { KugouClient(http) }
    val connectivity = Connectivity(context)
    val output = AudioOutput(context)

    /** All sources are asked at once; LRCLIB first so it wins ties. */
    private val sources = LyricsSources(
        listOf(
            LyricsSources.Source { t, a, al, d -> lrclib.find(t, a, al, d) },
            // word timings: Musixmatch first, then NetEase, then Kugou
            LyricsSources.Source { t, a, al, d -> musixmatch.find(t, a, al, d) },
            LyricsSources.Source { t, a, _, d -> netease.find(t, a, d) },
            LyricsSources.Source { t, a, _, d -> kugou.find(t, a, d) },
        ),
    )
    val lyrics = LyricsRepository(database.lyricsDao(), database.offsetDao(), database.missDao(), sources, connectivity.online, appScope, connectivity::refresh)
    val settings = SettingsStore(context)

    private val spotifyStore by lazy { SecureStore(context) }
    val spotify by lazy { SpotifyAccount(http, spotifyStore) }
    val librarySync by lazy { LibrarySync(spotify, lyrics, settings, connectivity, spotifyStore, appScope) }
    val sessions = ActiveSessionTracker(context)
}
