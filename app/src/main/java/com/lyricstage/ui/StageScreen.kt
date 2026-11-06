package com.lyricstage.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.core.content.ContextCompat
import com.lyricstage.R
import com.lyricstage.audio.AudioOnsets
import com.lyricstage.background.BackgroundClock
import com.lyricstage.background.RippleEmitter
import com.lyricstage.background.StageBackground
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.stage.Ripple
import com.lyricstage.lyrics.LyricsState
import com.lyricstage.settings.RippleMode
import com.lyricstage.stage.LyricStage
import com.lyricstage.ui.theme.Ui
import kotlinx.coroutines.delay

@Composable
fun StageScreen(vm: StageViewModel, systemReduceMotion: Boolean, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val state by vm.lyrics.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val seed by vm.playSeed.collectAsStateWithLifecycle()
    val reduceMotion = settings.reduceMotion || systemReduceMotion

    val saver = settings.batterySaver
    // in the background nothing is seen: stop listening and animating, but keep the lyrics,
    // palette and prepared layouts in memory so the stage is back at once on return
    val foreground = LocalLifecycleOwner.current.lifecycle.currentStateAsState().value.isAtLeast(Lifecycle.State.STARTED)
    val playing = np?.isPlaying == true && foreground
    val readyLyrics = (state as? LyricsState.Ready)?.lyrics

    // audio sync: listen only when it can help, line-timed lyrics on stage while playing
    val audio = remember { AudioOnsets() }
    val audioActive = settings.audioSync && !saver && playing && readyLyrics?.kind == LyricsKind.LINE_SYNCED &&
        readyLyrics.lines.isNotEmpty() &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    // re-read the player's position now and then while the stage is in front
    LaunchedEffect(playing) {
        while (playing) {
            delay(CLOCK_CHECK_MS)
            vm.sessions.refresh()
        }
    }

    // keep the screen on while playing, and for two minutes after a pause
    var keepAwake by remember { mutableStateOf(true) }
    LaunchedEffect(playing) {
        if (playing) {
            keepAwake = true
        } else {
            delay(SCREEN_PAUSE_MS)
            keepAwake = false
        }
    }
    DisposableEffect(audioActive) {
        if (audioActive) audio.start() else audio.stop()
        onDispose { audio.stop() }
    }

    // with nothing sung to make ripples (no lyrics, instrumental, unsynced), listen for beats
    // when audio sync is allowed; otherwise ambient ripples run at a gentle pace
    val hasTrack = np?.hasTrack == true
    val ambient = playing && !saver && hasTrack
    val beats = remember { AudioOnsets.beats() }
    val beatRipples = settings.ripples == RippleMode.BEATS
    val beatsActive = ambient &&
        (beatRipples || (settings.audioSync && !audioActive && wantsAmbientRipples(readyLyrics))) &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    DisposableEffect(beatsActive) {
        if (beatsActive) beats.start() else beats.stop()
        onDispose { beats.stop() }
    }
    // say once per song when audio sync hears nothing (Bluetooth output, usually)
    var audioDeaf by remember { mutableStateOf(false) }
    LaunchedEffect(audioActive, np?.trackKey) {
        audioDeaf = false
        if (!audioActive) return@LaunchedEffect
        delay(AUDIO_CHECK_MS)
        if (audio.isDeaf()) {
            audioDeaf = true
            delay(INFO_PILL_MS)
            audioDeaf = false
        }
    }

    // where this song's lyrics came from and how they are timed, shown briefly as a song starts
    val readySource = (state as? LyricsState.Ready)?.source
    var sourceInfo by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(np?.trackKey, readySource, readyLyrics?.kind, settings.paragraphLyrics) {
        sourceInfo = null
        val lyrics = readyLyrics ?: return@LaunchedEffect
        val timing = when (lyrics.kind) {
            LyricsKind.WORD_SYNCED -> R.string.timing_word
            LyricsKind.LINE_SYNCED -> R.string.timing_line
            LyricsKind.PLAIN -> R.string.timing_none
            LyricsKind.INSTRUMENTAL -> return@LaunchedEffect
        }
        if (lyrics.kind != LyricsKind.PLAIN && lyrics.lines.isEmpty()) return@LaunchedEffect
        // hidden unsynced text says so with its own pill; no second notice on top of it
        if (lyrics.kind == LyricsKind.PLAIN && !settings.paragraphLyrics) return@LaunchedEffect
        sourceInfo = context.getString(timing, sourceName(readySource))
        delay(INFO_PILL_MS)
        sourceInfo = null
    }

    val bgClock = remember { BackgroundClock() }
    val ripples = remember { RippleEmitter() }
    LaunchedEffect(np?.trackKey) { ripples.clear() }

    var controlsVisible by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    // controls poll the position while open; close them when the app goes to the background
    LaunchedEffect(foreground) { if (!foreground) controlsVisible = false }
    LaunchedEffect(controlsVisible, interactions) {
        if (controlsVisible) {
            delay(CONTROLS_MS)
            controlsVisible = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.import(uri, context.contentResolver) { ok ->
                Toast.makeText(context, if (ok) R.string.import_done else R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val pickLrc = { importLauncher.launch(arrayOf("*/*")) }

    ImmersiveStage(keepOn = keepAwake)
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                // a tap only: no long-press or swipe handling, so system gestures (swipe up
                // to go home or to recents) never change anything on the stage
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        interactions++
                    },
                )
            },
    ) {
        StageBackground(
            settings.background, palette, np?.art, ripples, bgClock, reduceMotion,
            // keeps moving with no lyrics too, so a song without them doesn't look frozen
            animate = playing && hasTrack && !saver,
            black = saver,
            pools = settings.pools,
            rippleStrength = settings.rippleStrength,
        )

        val current = np
        AmbientRipples(ambient, readyLyrics, vm.clock, if (beatsActive) beats else null, always = beatRipples) { ripples.emit(it, bgClock.seconds()) }

        val accent = Ui.accent(palette)
        // every notice pill goes away by itself, its border running down as the timer, so the
        // bottom of the stage is never occupied for the whole song
        val pill: StatePill? = when {
            current == null || !current.hasTrack -> StatePill(R.string.state_no_session)
            else -> when (val s = state) {
                LyricsState.Idle, LyricsState.Loading -> StatePill(R.string.state_loading, glyph = Glyph.SEARCHING)
                LyricsState.NotFound -> StatePill(R.string.state_no_lyrics, import = true, timed = true)
                LyricsState.Unavailable -> StatePill(R.string.state_unavailable, import = true, timed = true)
                LyricsState.Offline -> StatePill(R.string.state_offline, glyph = Glyph.OFFLINE, timed = true)
                is LyricsState.Ready -> when {
                    s.lyrics.kind == LyricsKind.INSTRUMENTAL -> StatePill(R.string.state_instrumental, glyph = Glyph.NOTE, timed = true)
                    // unsynced text is clutter by default; a short notice says so instead
                    s.lyrics.kind == LyricsKind.PLAIN && !settings.paragraphLyrics -> StatePill(R.string.state_unsynced, import = true, timed = true)
                    s.lyrics.kind != LyricsKind.PLAIN && s.lyrics.lines.isEmpty() -> StatePill(R.string.state_no_lyrics, import = true, timed = true)
                    else -> null
                }
            }
        }
        val ready = state as? LyricsState.Ready
        when {
            current == null || ready == null -> Unit
            ready.lyrics.kind == LyricsKind.PLAIN && settings.paragraphLyrics -> PlainLyricsView(ready.lyrics.plainText, vm.clock, current.durationMs)
            ready.lyrics.isSynced && ready.lyrics.lines.isNotEmpty() -> {
                val s = ready
                LyricStage(
                        lyrics = s.lyrics,
                        clock = vm.clock,
                        palette = palette,
                        playSeed = seed,
                        lockedTemplate = settings.template,
                        fontScale = settings.fontScale,
                        glass = settings.glass,
                        frost = settings.frost,
                        highlightIntensity = settings.highlightIntensity,
                        reduceMotion = reduceMotion || saver,
                        isPlaying = current.isPlaying,
                        anchor = current.positionMs xor current.positionUpdateTimeMs,
                        simple = saver,
                        dimmed = controlsVisible,
                        showFps = settings.showFps,
                        onRipple = {
                            // with beat ripples, sung words keep only the light sweep on a new line
                            if (!beatRipples) ripples.emit(it, bgClock.seconds())
                            else if (it.newLine) ripples.emit(Ripple(it.x, it.y, 0f, true), bgClock.seconds())
                        },
                        onVisibleTemplate = {},
                        audio = if (audioActive) audio else null,
                    )
            }
        }
        // a timed pill (instrumental, no lyrics, offline) goes away by itself, once per song
        val pillKey = current?.let { it.title + "|" + it.artist } to pill
        var pillGone by remember(pillKey) { mutableStateOf(false) }
        if (pill != null) {
            AnimatedVisibility(!pillGone, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                StagePill(
                    stringResource(pill.text),
                    action = if (pill.import) stringResource(R.string.import_lrc) else null,
                    onAction = pickLrc,
                    glyph = pill.glyph,
                    accent = accent,
                    timerMs = if (pill.timed) TIMED_PILL_MS else 0L,
                    onTimeout = { pillGone = true },
                )
            }
        } else if (!controlsVisible) {
            val info = if (audioDeaf) stringResource(R.string.audio_sync_silent) else sourceInfo
            var shown by remember { mutableStateOf("") }
            if (info != null) shown = info
            var deafShown by remember { mutableStateOf(false) }
            if (info != null) deafShown = audioDeaf
            AnimatedVisibility(info != null, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                StagePill(shown, glyph = if (deafShown) Glyph.MIC_OFF else null, dot = if (deafShown) null else accent, accent = accent)
            }
        }

        // without the kinetic stage (nothing playing, no lyrics) Settings stays one tap away
        val stageShowing = current != null && current.hasTrack &&
            (state as? LyricsState.Ready)?.lyrics?.let { it.isSynced && it.lines.isNotEmpty() } == true
        if (!stageShowing && !(controlsVisible && current != null)) {
            MoreButton(onOpenSettings, Modifier.align(Alignment.TopEnd))
        }

        AnimatedVisibility(controlsVisible && current != null, enter = fadeIn(), exit = fadeOut()) {
            if (current != null) {
                ControlsOverlay(
                    nowPlaying = current,
                    clock = vm.clock,
                    settings = settings,
                    accent = accent,
                    onInteract = { interactions++ },
                    onPlayPause = { if (current.isPlaying) vm.sessions.pause() else vm.sessions.play() },
                    onPrevious = { vm.sessions.previous() },
                    onNext = { vm.sessions.next() },
                    onSeek = { vm.sessions.seekTo(it) },
                    onTemplate = { vm.setTemplate(it) },
                    onSettings = onOpenSettings,
                )
            }
        }
    }
}

/** A status pop-up: its text, an icon, and whether it offers Import. */
private data class StatePill(val text: Int, val glyph: Glyph? = null, val import: Boolean = false, val timed: Boolean = false)

private const val CONTROLS_MS = 3_000L
private const val CLOCK_CHECK_MS = 5_000L
private const val INFO_PILL_MS = 3_500L
private const val TIMED_PILL_MS = 5_000L
private const val AUDIO_CHECK_MS = 8_000L

private const val SCREEN_PAUSE_MS = 120_000L
