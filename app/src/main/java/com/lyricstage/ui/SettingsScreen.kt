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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.lyricstage.ui.theme.Ui

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StageViewModel, systemReduceMotion: Boolean, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val track by vm.query.collectAsStateWithLifecycle()
    val np by vm.nowPlaying.collectAsStateWithLifecycle()
    val lyrics by vm.lyrics.collectAsStateWithLifecycle()
    val palette by vm.palette.collectAsStateWithLifecycle()
    val offset by vm.offset.collectAsStateWithLifecycle()
    val cached by vm.cachedCount.collectAsStateWithLifecycle()
    val cacheBytes by vm.cachedBytes.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val context = LocalContext.current
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.import(uri, context.contentResolver) { ok ->
                Toast.makeText(context, if (ok) R.string.import_done else R.string.import_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.updateSettings { it.copy(audioSync = granted) }
    }
    // beat ripples work without the microphone too (gentle pace), so a refusal keeps the choice
    val beatPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ui.Ground)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(Modifier.heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            CircleIconButton(Glyph.BACK, stringResource(R.string.back), onBack)
            Text(
                stringResource(R.string.settings),
                color = Ui.Text,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        val current = track
        if (current != null) {
            UiCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val art = remember(np?.art) { np?.art?.asImageBitmap() }
                    Box(Modifier.size(64.dp).clip(RoundedCornerShape(16.dp)).background(accent), contentAlignment = Alignment.Center) {
                        if (art != null) {
                            Image(art, stringResource(R.string.artwork), contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
                        }
                    }
                    Column(Modifier.weight(1f).padding(start = 14.dp)) {
                        Text(current.title, color = Ui.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(current.artist, color = Ui.Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val ready = lyrics as? LyricsState.Ready
                        if (ready != null) {
                            GapRow(6, Modifier.padding(top = 6.dp)) {
                                Tag(sourceName(ready.source))
                                timingLabel(ready.lyrics.kind)?.let { Tag(stringResource(it)) }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                LabelValue(stringResource(R.string.sync_title), stringResource(R.string.sync_label, offset / 1000f), valueColour = accent)
                Spacer(Modifier.height(10.dp))
                GapRow(8) {
                    PillButton(stringResource(R.string.sync_earlier), { vm.nudge(-250) }, Modifier.weight(1f))
                    PillButton(stringResource(R.string.sync_reset), { vm.resetOffset() }, Modifier.weight(1f), style = PillStyle.OUTLINE, enabled = offset != 0L)
                    PillButton(stringResource(R.string.sync_later), { vm.nudge(250) }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                PillButton(
                    stringResource(R.string.import_lrc_file),
                    { importLauncher.launch(arrayOf("*/*")) },
                    Modifier.fillMaxWidth(),
                    style = PillStyle.PRIMARY,
                    accent = accent,
                )
            }
        }

        SectionHeader(stringResource(R.string.settings_look))
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

        SectionHeader(stringResource(R.string.settings_motion))
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

        SectionHeader(stringResource(R.string.settings_lyrics))
        UiCard(padding = 0) {
            ToggleRow(stringResource(R.string.settings_prefetch), s.backgroundPrefetch, accent, { on -> vm.updateSettings { it.copy(backgroundPrefetch = on) } },
                hint = stringResource(R.string.hint_prefetch), divider = false)
            ToggleRow(stringResource(R.string.settings_lookahead_wifi), s.lookAheadWifiOnly, accent, { on -> vm.updateSettings { it.copy(lookAheadWifiOnly = on) } },
                hint = stringResource(R.string.hint_lookahead), enabled = s.backgroundPrefetch)
            HorizontalDivider(color = Ui.Raised)
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

        SectionHeader(stringResource(R.string.settings_debug))
        UiCard(padding = 0) {
            ToggleRow(stringResource(R.string.settings_fps), s.showFps, accent, { on -> vm.updateSettings { it.copy(showFps = on) } }, divider = false)
            LinkRow(stringResource(R.string.debug_now_playing), { onOpen(Screen.DEBUG_NOW_PLAYING) })
            LinkRow(stringResource(R.string.debug_lyrics), { onOpen(Screen.DEBUG_LYRICS) })
            LinkRow(stringResource(R.string.debug_fitcheck), { onOpen(Screen.FIT_CHECK) })
        }

        Text(
            stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            color = Ui.Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 24.dp),
        )
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
