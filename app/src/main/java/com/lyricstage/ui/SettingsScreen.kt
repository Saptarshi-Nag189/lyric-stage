package com.lyricstage.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricstage.BuildConfig
import com.lyricstage.R
import com.lyricstage.core.lyrics.LyricsKind
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.lyrics.LyricsState
import com.lyricstage.settings.BackgroundMode
import com.lyricstage.settings.RippleMode
import com.lyricstage.stage.LyricFont
import com.lyricstage.ui.theme.Ui
import com.lyricstage.spotify.PauseReason
import com.lyricstage.spotify.SyncStatus
import android.text.format.DateUtils
import androidx.compose.ui.platform.LocalUriHandler
import kotlin.math.roundToInt

/**
 * Settings, split into pages: the root is the current song and one grouped list of
 * destinations. Every page is its own destination on the back stack, so the arrow and the
 * system back gesture return here from anywhere, previews included.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StageViewModel, systemReduceMotion: Boolean, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val track by vm.query.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val offset by vm.offset.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.import(uri, context.contentResolver) { ok ->
                Toast.makeText(context, if (ok) R.string.import_done else R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    // on tablets the page sits in a centred column, not stretched edge to edge
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 16.dp)) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Glyph.BACK, stringResource(R.string.back), onBack)
                Column(Modifier.padding(start = 16.dp)) {
                    Text(
                        stringResource(R.string.settings),
                        color = Ui.Text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = Ui.Muted, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            val current = track
            if (current != null) {
                ThisSongCard(current, np?.art, lyrics as? LyricsState.Ready, offset, accent, { vm.nudge(250) }, { vm.resetOffset() }, { vm.nudge(-250) }, {
                    importLauncher.launch(arrayOf("*/*"))
                })
            }

            Spacer(Modifier.height(24.dp))
            UiCard(padding = 0) {
                LinkRow(stringResource(R.string.settings_look), { onOpen(Screen.LOOK) }, divider = false)
                LinkRow(stringResource(R.string.settings_fonts), { onOpen(Screen.FONTS) })
                LinkRow(stringResource(R.string.settings_playback), { onOpen(Screen.PLAYBACK) })
                LinkRow(stringResource(R.string.settings_data), { onOpen(Screen.DATA) })
                LinkRow(stringResource(R.string.settings_spotify), { onOpen(Screen.SPOTIFY) })
                LinkRow(stringResource(R.string.about), { onOpen(Screen.ABOUT) })
                LinkRow(stringResource(R.string.settings_debug), { onOpen(Screen.DEVELOPER) })
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Header with a working back arrow, shared by every Settings page. */
@Composable
private fun SettingsPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(Modifier.fillMaxWidth().widthIn(max = 640.dp).padding(horizontal = 16.dp)) {
            Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleIconButton(Glyph.BACK, stringResource(R.string.back), onBack)
                Text(
                    title,
                    Modifier.padding(start = 16.dp),
                    color = Ui.Text,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                )
            }
            Spacer(Modifier.height(16.dp))
            content()
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** The song playing now: artwork, source tags, sync buttons and .lrc import. */
@Composable
private fun ThisSongCard(
    track: com.lyricstage.lyrics.TrackQuery,
    art: android.graphics.Bitmap?,
    ready: LyricsState.Ready?,
    offset: Long,
    accent: Color,
    onEarlier: () -> Unit,
    onReset: () -> Unit,
    onLater: () -> Unit,
    onImport: () -> Unit,
) {
    UiCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val artBitmap = remember(art) { art?.asImageBitmap() }
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(accent), contentAlignment = Alignment.Center) {
                if (artBitmap != null) {
                    Image(artBitmap, stringResource(R.string.artwork), contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(track.title, color = Ui.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.artist, color = Ui.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (ready != null) {
                    GapRow(6, Modifier.padding(top = 6.dp)) {
                        Tag(sourceName(ready.source))
                        timingLabel(ready.lyrics.kind)?.let { Tag(stringResource(it)) }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        // a positive offset shows the lyrics earlier
        val syncText = when {
            offset > 0 -> stringResource(R.string.sync_value_earlier, offset / 1000f)
            offset < 0 -> stringResource(R.string.sync_value_later, -offset / 1000f)
            else -> stringResource(R.string.sync_value_none)
        }
        LabelValue(stringResource(R.string.sync_title), syncText, valueColour = accent)
        Spacer(Modifier.height(10.dp))
        GapRow(8) {
            PillButton(stringResource(R.string.sync_earlier), onEarlier, Modifier.weight(1f))
            PillButton(stringResource(R.string.sync_reset), onReset, Modifier.weight(1f), style = PillStyle.OUTLINE, enabled = offset != 0L)
            PillButton(stringResource(R.string.sync_later), onLater, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        PillButton(
            stringResource(R.string.import_lrc_file),
            onImport,
            Modifier.fillMaxWidth(),
            style = PillStyle.PRIMARY,
            accent = accent,
        )
    }
}

/** Look: layouts, background with its live preview, pools, ripples, glass and text size. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LookPage(vm: StageViewModel, systemReduceMotion: Boolean, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val context = LocalContext.current
    // beat ripples work without the microphone too (gentle pace), so a refusal keeps the choice
    val beatPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    SettingsPage(stringResource(R.string.settings_look), onBack) {
        UiCard {
            Text(stringResource(R.string.settings_template), color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChoicePill(stringResource(R.string.template_auto), s.template == null, accent, { vm.setTemplate(null) })
                TemplateId.entries.forEach { id -> ChoicePill(id.label, s.template == id, accent, { vm.setTemplate(id) }) }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_background), color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Segmented(
                listOf(
                    BackgroundMode.LIQUID to stringResource(R.string.background_liquid),
                    BackgroundMode.ARTWORK to stringResource(R.string.background_artwork),
                    BackgroundMode.SOLID to stringResource(R.string.background_solid),
                ),
                s.background,
            ) { mode -> vm.updateSettings { it.copy(background = mode) } }
            Spacer(Modifier.height(14.dp))
            BackgroundPreview(s.background, palette, np?.art, s.pools, s.rippleStrength, s.glass, s.frost, s.lyricFont, s.reduceMotion || systemReduceMotion)
            Spacer(Modifier.height(14.dp))
            PercentSlider(stringResource(R.string.settings_pools), s.pools, 0f..2f, 0, accent) { v -> vm.updateSettings { it.copy(pools = v) } }
            Spacer(Modifier.height(12.dp))
            PercentSlider(stringResource(R.string.settings_ripple_strength), s.rippleStrength, 0f..2f, 0, accent) { v -> vm.updateSettings { it.copy(rippleStrength = v) } }
            Spacer(Modifier.height(12.dp))
            PercentSlider(stringResource(R.string.settings_glass), s.glass, 0f..1f, 0, accent) { v -> vm.updateSettings { it.copy(glass = v) } }
            if (s.glass > 0.01f) {
                Spacer(Modifier.height(12.dp))
                PercentSlider(stringResource(R.string.settings_frost), s.frost, 0f..1f, 0, accent) { v -> vm.updateSettings { it.copy(frost = v) } }
            }
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.settings_ripples), color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            Segmented(
                listOf(
                    RippleMode.WORDS to stringResource(R.string.ripples_words),
                    RippleMode.BEATS to stringResource(R.string.ripples_beats),
                ),
                s.ripples,
            ) { mode ->
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                vm.updateSettings { it.copy(ripples = mode) }
                if (mode == RippleMode.BEATS && !granted) beatPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            if (s.ripples == RippleMode.BEATS) {
                Text(stringResource(R.string.hint_ripples_beats), color = Ui.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
            Spacer(Modifier.height(20.dp))
            PercentSlider(stringResource(R.string.settings_font_scale), s.fontScale, 0.6f..1.4f, 7, accent) { v -> vm.updateSettings { it.copy(fontScale = v) } }
            Spacer(Modifier.height(12.dp))
            PercentSlider(stringResource(R.string.settings_highlight), s.highlightIntensity, 0f..1f, 0, accent) { v -> vm.updateSettings { it.copy(highlightIntensity = v) } }
        }
    }
}

/** Fonts: the stage's lyric typeface. The app's own screens stay on Bricolage. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FontsPage(vm: StageViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)

    SettingsPage(stringResource(R.string.settings_fonts), onBack) {
        UiCard {
            Text(stringResource(R.string.settings_lyric_font), color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricFont.entries.forEach { f -> ChoicePill(f.label, s.lyricFont == f, accent, { vm.updateSettings { it.copy(lyricFont = f) } }) }
            }
            Spacer(Modifier.height(16.dp))
            FontPreview(s.lyricFont, palette)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.hint_lyric_font),
            color = Ui.Muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

/** Playback: motion, battery, the microphone listener and the Bluetooth delay. */
@Composable
fun PlaybackPage(vm: StageViewModel, systemReduceMotion: Boolean, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(audioSync = granted) }
    }

    SettingsPage(stringResource(R.string.settings_playback), onBack) {
        UiCard(padding = 0) {
            ToggleRow(stringResource(R.string.settings_battery_saver), s.batterySaver, accent, { on -> vm.updateSettings { it.copy(batterySaver = on) } },
                hint = stringResource(R.string.hint_battery_saver), divider = false)
            ToggleRow(stringResource(R.string.settings_reduce_motion), s.reduceMotion || systemReduceMotion, accent, { on -> vm.updateSettings { it.copy(reduceMotion = on) } },
                hint = stringResource(R.string.hint_reduce_motion), enabled = !systemReduceMotion)
            ToggleRow(stringResource(R.string.settings_audio_sync), s.audioSync, accent, { on ->
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                if (on && !granted) micPermission.launch(Manifest.permission.RECORD_AUDIO) else vm.updateSettings { it.copy(audioSync = on) }
            }, hint = stringResource(R.string.hint_audio_sync))
        }
        Spacer(Modifier.height(16.dp))
        UiCard {
            Column(Modifier.padding(top = 6.dp, bottom = 6.dp)) {
                LabelValue(stringResource(R.string.settings_bt_delay), stringResource(R.string.bt_delay_value, s.bluetoothDelayMs))
                Slider(
                    s.bluetoothDelayMs.toFloat(),
                    onValueChange = { v -> vm.updateSettings { it.copy(bluetoothDelayMs = (v / 25f).roundToInt() * 25) } },
                    valueRange = 0f..500f,
                    steps = 19,
                    colors = SliderDefaults.colors(
                        thumbColor = Ui.Text, activeTrackColor = accent, inactiveTrackColor = Ui.Track,
                        activeTickColor = Ui.OnAccent.copy(alpha = 0.4f), inactiveTickColor = Ui.Muted.copy(alpha = 0.4f),
                    ),
                )
            }
        }
    }
}

/** Lyrics and storage: prefetching, the cache and the Delete cache button. */
@Composable
fun DataPage(vm: StageViewModel, onBack: () -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val cached by vm.cachedCount.collectAsStateWithLifecycle()
    val cacheBytes by vm.cachedBytes.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val context = LocalContext.current

    SettingsPage(stringResource(R.string.settings_data), onBack) {
        UiCard(padding = 0) {
            ToggleRow(stringResource(R.string.settings_prefetch), s.backgroundPrefetch, accent, { on -> vm.updateSettings { it.copy(backgroundPrefetch = on) } },
                hint = stringResource(R.string.hint_prefetch), divider = false)
            ToggleRow(stringResource(R.string.settings_lookahead_wifi), s.lookAheadWifiOnly, accent, { on -> vm.updateSettings { it.copy(lookAheadWifiOnly = on) } },
                hint = stringResource(R.string.hint_lookahead), enabled = s.backgroundPrefetch)
            ToggleRow(stringResource(R.string.settings_unsynced), s.paragraphLyrics, accent, { on -> vm.updateSettings { it.copy(paragraphLyrics = on) } },
                hint = stringResource(R.string.hint_unsynced))
        }
        Spacer(Modifier.height(16.dp))
        UiCard {
            Row(Modifier.fillMaxWidth().heightIn(min = 72.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_saved_lyrics), color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (cached == 0) stringResource(R.string.settings_cache_empty)
                        else pluralStringResource(R.plurals.settings_cache, cached, formatBytes(cacheBytes), cached),
                        color = Ui.Muted,
                        fontSize = 13.sp,
                    )
                }
                PillButton(
                    stringResource(R.string.settings_delete_cache),
                    { vm.clearFetchedLyrics { Toast.makeText(context, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show() } },
                    style = PillStyle.OUTLINE,
                    enabled = cached > 0,
                    height = 44,
                )
            }
        }
    }
}

/** Spotify link and library sync. */
@Composable
fun SpotifyPage(vm: StageViewModel, onBack: () -> Unit) {
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    SettingsPage(stringResource(R.string.settings_spotify), onBack) {
        SpotifyCard(vm, accent)
    }
}

/** Debugging: the FPS counter and the inspection screens. */
@Composable
fun DeveloperPage(vm: StageViewModel, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    SettingsPage(stringResource(R.string.settings_debug), onBack) {
        UiCard(padding = 0) {
            ToggleRow(stringResource(R.string.settings_fps), s.showFps, accent, { on -> vm.updateSettings { it.copy(showFps = on) } }, divider = false)
            LinkRow(stringResource(R.string.debug_now_playing), { onOpen(Screen.DEBUG_NOW_PLAYING) })
            LinkRow(stringResource(R.string.debug_lyrics), { onOpen(Screen.DEBUG_LYRICS) })
        }
    }
}

/** Heading with its value in per cent on the right, slider below. */
@Composable
private fun PercentSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    accent: Color,
    onChange: (Float) -> Unit,
) {
    LabelValue(title, "%.0f %%".format(value * 100))
    Slider(
        value,
        onValueChange = onChange,
        valueRange = range,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = Ui.Text,
            activeTrackColor = accent,
            inactiveTrackColor = Ui.Track,
            activeTickColor = Ui.OnAccent.copy(alpha = 0.4f),
            inactiveTickColor = Ui.Muted.copy(alpha = 0.4f),
        ),
    )
}

/** "0 KB", "84 KB", "1.4 MB". Lyrics are text, so a cache rarely passes a few megabytes. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L * 1024 -> "%d KB".format((bytes + 1023) / 1024)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}

/** The pop-up and tag wording for how lyrics are timed; null for none worth naming. */
fun timingLabel(kind: LyricsKind): Int? = when (kind) {
    LyricsKind.WORD_SYNCED -> R.string.tag_word
    LyricsKind.LINE_SYNCED -> R.string.tag_line
    LyricsKind.PLAIN -> R.string.tag_none
    LyricsKind.INSTRUMENTAL -> null
}

/** Display name of a lyrics source. */
fun sourceName(source: String?): String = when (source) {
    "lrclib" -> "LRCLIB"
    "netease" -> "NetEase"
    "musixmatch" -> "Musixmatch"
    "kugou" -> "Kugou"
    "import" -> "Imported"
    null -> ""
    else -> source
}

/** Link Spotify, then the library sync: progress, Sync now or Stop, and Unlink. */
@Composable
private fun SpotifyCard(vm: StageViewModel, accent: Color) {
    val name by vm.spotifyName.collectAsStateWithLifecycle()
    val sync by vm.librarySync.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    UiCard {
        val linkedAs = name
        if (linkedAs == null) {
            Text(stringResource(R.string.spotify_pitch), color = Ui.Muted, fontSize = 14.sp, lineHeight = 20.sp)
            Spacer(Modifier.height(12.dp))
            PillButton(
                stringResource(R.string.spotify_link),
                { runCatching { uri.openUri(vm.spotifySignInUri().toString()) } },
                Modifier.fillMaxWidth(),
                style = PillStyle.PRIMARY,
                accent = accent,
            )
        } else {
            Text(stringResource(R.string.spotify_linked_as, linkedAs.ifBlank { "Spotify" }), color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val status = when (val st = sync) {
                SyncStatus.Idle -> stringResource(R.string.sync_status_idle)
                SyncStatus.Reading -> stringResource(R.string.sync_status_reading)
                is SyncStatus.Saving -> stringResource(R.string.sync_status_saving, st.done, st.total)
                is SyncStatus.Done -> stringResource(R.string.sync_status_done, st.total,
                    DateUtils.getRelativeTimeSpanString(st.at, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
                is SyncStatus.Paused -> {
                    val reason = when (st.reason) {
                        PauseReason.WIFI -> stringResource(R.string.sync_reason_wifi)
                        PauseReason.SAVER -> stringResource(R.string.sync_reason_saver)
                        PauseReason.OFFLINE -> stringResource(R.string.sync_reason_offline)
                        PauseReason.SPOTIFY -> null
                    }
                    when {
                        reason == null -> stringResource(R.string.sync_paused_spotify)
                        st.total > 0 -> stringResource(R.string.sync_paused_at, st.done, st.total, reason)
                        else -> reason.replaceFirstChar { it.uppercase() }
                    }
                }
            }
            Text(status, color = Ui.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(12.dp))
            val busy = sync is SyncStatus.Reading || sync is SyncStatus.Saving
            GapRow(8) {
                if (busy) {
                    PillButton(stringResource(R.string.spotify_stop), { vm.stopLibrarySync() }, Modifier.weight(1f))
                } else {
                    PillButton(stringResource(R.string.spotify_sync_now), { vm.syncLibrary() }, Modifier.weight(1f), style = PillStyle.PRIMARY, accent = accent)
                }
                PillButton(stringResource(R.string.spotify_unlink), { vm.unlinkSpotify() }, Modifier.weight(1f), style = PillStyle.OUTLINE)
            }
        }
    }
}
