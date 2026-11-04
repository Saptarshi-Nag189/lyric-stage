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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import com.lyricstage.R
import com.lyricstage.audio.AudioOnsets
import com.lyricstage.background.BackgroundClock
import com.lyricstage.background.RippleEmitter
import com.lyricstage.background.StageBackground
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.lyrics.LyricsState
import com.lyricstage.stage.LyricStage
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

    // audio sync: listen only while it is on, allowed, and a song is playing
    val audio = remember { AudioOnsets() }
    val audioActive = settings.audioSync && np?.isPlaying == true &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    DisposableEffect(audioActive) {
        if (audioActive) audio.start() else audio.stop()
        onDispose { audio.stop() }
    }

    val bgClock = remember { BackgroundClock() }
    val ripples = remember { RippleEmitter() }
    LaunchedEffect(np?.trackKey) { ripples.clear() }

    var controlsVisible by remember { mutableStateOf(false) }
    var interactions by remember { mutableIntStateOf(0) }
    var visibleTemplate by remember { mutableStateOf<TemplateId?>(null) }
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

    ImmersiveStage()
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                        interactions++
                    },
                    onLongPress = {
                        // lock whatever template is on screen now, or go back to Auto
                        val current = vm.settings.value.template
                        if (current != null) {
                            vm.setTemplate(null)
                            Toast.makeText(context, R.string.template_unlocked, Toast.LENGTH_SHORT).show()
                        } else {
                            visibleTemplate?.let {
                                vm.setTemplate(it)
                                Toast.makeText(context, context.getString(R.string.template_locked, it.label), Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            },
    ) {
        StageBackground(settings.background, palette, np?.art, ripples, bgClock, reduceMotion)

        val current = np
        val pill: Pair<Int, Boolean>? = when {
            current == null || !current.hasTrack -> R.string.state_no_session to false
            else -> when (val s = state) {
                LyricsState.Idle, LyricsState.Loading -> R.string.state_loading to false
                LyricsState.NotFound -> R.string.state_no_lyrics to true
                LyricsState.Unavailable -> R.string.state_unavailable to true
                is LyricsState.Ready -> when {
                    s.lyrics.kind == LyricsKind.INSTRUMENTAL -> R.string.state_instrumental to false
                    s.lyrics.kind != LyricsKind.PLAIN && s.lyrics.lines.isEmpty() -> R.string.state_no_lyrics to true
                    else -> null
                }
            }
        }
        val ready = state as? LyricsState.Ready
        when {
            current == null || ready == null -> Unit
            ready.lyrics.kind == LyricsKind.PLAIN -> PlainLyricsView(ready.lyrics.plainText, vm.clock, current.durationMs)
            ready.lyrics.isSynced && ready.lyrics.lines.isNotEmpty() -> {
                val s = ready
                LyricStage(
                        lyrics = s.lyrics,
                        clock = vm.clock,
                        palette = palette,
                        playSeed = seed,
                        lockedTemplate = settings.template,
                        fontScale = settings.fontScale,
                        highlightIntensity = settings.highlightIntensity,
                        reduceMotion = reduceMotion,
                        isPlaying = current.isPlaying,
                        dimmed = controlsVisible,
                        showFps = settings.showFps,
                        onRipple = { ripples.emit(it, bgClock.seconds()) },
                        onVisibleTemplate = { visibleTemplate = it },
                        audio = if (audioActive) audio else null,
                    )
            }
        }
        if (pill != null) {
            StagePill(
                stringResource(pill.first),
                action = if (pill.second) stringResource(R.string.import_lrc) else null,
                onAction = pickLrc,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
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

private const val CONTROLS_MS = 3_000L
