package com.lyricstage.ui

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lyricstage.AppContainer
import com.lyricstage.core.palette.StagePalette
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.lyrics.LyricsState
import com.lyricstage.lyrics.TrackQuery
import com.lyricstage.media.NowPlaying
import com.lyricstage.palette.ArtworkPalette
import com.lyricstage.settings.StageSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

@OptIn(ExperimentalCoroutinesApi::class)
class StageViewModel(private val c: AppContainer) : ViewModel() {
    val sessions = c.sessions
    val clock = c.sessions.clock
    val nowPlaying: StateFlow<NowPlaying?> = c.sessions.nowPlaying
    val settings: StateFlow<StageSettings> = c.settings.settings

    private val isPlaying: StateFlow<Boolean> = nowPlaying.map { it?.isPlaying == true }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val query: StateFlow<TrackQuery?> = nowPlaying
        .map { np -> np?.takeIf { it.hasTrack }?.let { TrackQuery(it.trackKey, it.title, it.artist, it.album, it.durationMs) } }
        .distinctUntilChanged { a, b -> a?.key == b?.key }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val lyrics: StateFlow<LyricsState> = query
        .flatMapLatest { q -> if (q == null) flowOf(LyricsState.Idle) else c.lyrics.observe(q, isPlaying) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, LyricsState.Idle)

    /** Per-track sync offset; also pushed into the clock. */
    val offset: StateFlow<Long> = query
        .flatMapLatest { q -> if (q == null) flowOf(0L) else c.lyrics.offset(q.key) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    /** Whether the Bluetooth delay applies right now. */
    val bluetooth: StateFlow<Boolean> = c.output.bluetooth

    init {
        // the clock's offset: this song's sync, minus the Bluetooth delay while it applies
        // (a positive offset shows lyrics earlier; Bluetooth audio arrives later)
        viewModelScope.launch {
            combine(offset, settings, bluetooth) { o, s, bt -> o - if (bt) s.bluetoothDelayMs else 0 }
                .collect { clock.userOffsetMs = it }
        }
    }

    /** New random seed each time a track starts: highlights and templates vary per play. */
    val playSeed: StateFlow<Int> = query.map { Random.nextInt() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Random.nextInt())

    val palette: StateFlow<StagePalette> = nowPlaying.map { it?.art }.distinctUntilChanged()
        .combine(playSeed) { art, seed -> art to seed }
        .mapLatest { (art, seed) ->
            // players often send a new song's title a moment before its artwork: keep the last
            // colours for that moment instead of flashing made-up ones
            if (art == null) delay(ART_WAIT_MS)
            ArtworkPalette.from(art, seed)
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StagePalette.fallback(0))

    fun nudge(deltaMs: Long) {
        val key = query.value?.key ?: return
        val next = (offset.value + deltaMs).coerceIn(-10_000, 10_000)
        viewModelScope.launch { c.lyrics.setOffset(key, next) }
    }

    fun resetOffset() {
        val key = query.value?.key ?: return
        viewModelScope.launch { c.lyrics.setOffset(key, 0) }
    }

    fun setTemplate(id: TemplateId?) = c.settings.update { it.copy(template = id) }

    fun updateSettings(transform: (StageSettings) -> StageSettings) = c.settings.update(transform)

    fun systemReducesMotion(): Boolean = c.settings.systemReducesMotion()

    /** Read a picked .lrc and bind it to the current track. */
    fun import(uri: Uri, resolver: ContentResolver, onDone: (Boolean) -> Unit) {
        val q = query.value ?: return onDone(false)
        viewModelScope.launch {
            val ok = runCatching {
                val text = withContext(Dispatchers.IO) {
                    resolver.openInputStream(uri)?.use { it.readNBytesCompat(MAX_IMPORT_BYTES).toString(Charsets.UTF_8) }
                }
                text != null && c.lyrics.import(q, text)
            }.getOrDefault(false)
            onDone(ok)
        }
    }

    suspend fun cachedSongs() = c.lyrics.cached()

    val cachedCount: StateFlow<Int> = c.lyrics.cachedCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val cachedBytes: StateFlow<Long> = c.lyrics.cachedBytes().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun clearFetchedLyrics(onDone: () -> Unit) {
        viewModelScope.launch {
            c.lyrics.clearFetched()
            onDone()
        }
    }

    private fun java.io.InputStream.readNBytesCompat(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (out.size() < max) {
            val n = read(buf, 0, minOf(buf.size, max - out.size()))
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private companion object {
        const val ART_WAIT_MS = 1_500L
        const val MAX_IMPORT_BYTES = 512 * 1024
    }
}
