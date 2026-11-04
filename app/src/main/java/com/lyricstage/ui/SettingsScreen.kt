package com.lyricstage.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricstage.R
import com.lyricstage.core.stage.TemplateId
import com.lyricstage.settings.BackgroundMode

private val Muted = Color.White.copy(alpha = 0.6f)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(vm: StageViewModel, systemReduceMotion: Boolean, onBack: () -> Unit, onOpen: (Screen) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val track by vm.query.collectAsStateWithLifecycle()
    val offset by vm.offset.collectAsStateWithLifecycle()
    val cached by vm.cachedCount.collectAsStateWithLifecycle()
    val cacheBytes by vm.cachedBytes.collectAsStateWithLifecycle()
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

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back), color = Color.White) }
            Text(stringResource(R.string.settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
        }

        val current = track
        if (current != null) {
            Section(stringResource(R.string.settings_this_song))
            Text(current.title + " · " + current.artist, color = Muted, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { vm.nudge(-250) }) { Text(stringResource(R.string.sync_earlier)) }
                Text(stringResource(R.string.sync_label, offset.toInt()), color = Color.White, modifier = Modifier.padding(horizontal = 4.dp))
                OutlinedButton(onClick = { vm.nudge(250) }) { Text(stringResource(R.string.sync_later)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { vm.resetOffset() }, enabled = offset != 0L) { Text(stringResource(R.string.sync_reset)) }
                TextButton(onClick = { importLauncher.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.import_lrc)) }
            }
        }

        Section(stringResource(R.string.settings_template))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(s.template == null, onClick = { vm.setTemplate(null) }, label = { Text(stringResource(R.string.template_auto)) })
            TemplateId.entries.forEach { id ->
                FilterChip(s.template == id, onClick = { vm.setTemplate(id) }, label = { Text(id.label) })
            }
        }

        Section(stringResource(R.string.settings_background))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                BackgroundMode.LIQUID to R.string.background_liquid,
                BackgroundMode.ARTWORK to R.string.background_artwork,
                BackgroundMode.SOLID to R.string.background_solid,
            ).forEach { (mode, label) ->
                FilterChip(s.background == mode, onClick = { vm.updateSettings { it.copy(background = mode) } }, label = { Text(stringResource(label)) })
            }
        }

        SliderRow(stringResource(R.string.settings_font_scale), s.fontScale, 0.6f..1.4f, steps = 7) { v -> vm.updateSettings { it.copy(fontScale = v) } }
        SliderRow(stringResource(R.string.settings_highlight), s.highlightIntensity, 0f..1f) { v -> vm.updateSettings { it.copy(highlightIntensity = v) } }

        Spacer(Modifier.height(8.dp))
        ToggleRow(stringResource(R.string.settings_battery_saver), s.batterySaver) { vm.updateSettings { st -> st.copy(batterySaver = it) } }
        ToggleRow(stringResource(R.string.settings_reduce_motion), s.reduceMotion || systemReduceMotion, enabled = !systemReduceMotion) {
            vm.updateSettings { st -> st.copy(reduceMotion = it) }
        }
        ToggleRow(stringResource(R.string.settings_prefetch), s.backgroundPrefetch) { vm.updateSettings { st -> st.copy(backgroundPrefetch = it) } }
        ToggleRow(stringResource(R.string.settings_lookahead_wifi), s.lookAheadWifiOnly, enabled = s.backgroundPrefetch) {
            vm.updateSettings { st -> st.copy(lookAheadWifiOnly = it) }
        }
        ToggleRow(stringResource(R.string.settings_audio_sync), s.audioSync) { on ->
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (on && !granted) micPermission.launch(Manifest.permission.RECORD_AUDIO)
            else vm.updateSettings { it.copy(audioSync = on) }
        }
        ToggleRow(stringResource(R.string.settings_fps), s.showFps) { vm.updateSettings { st -> st.copy(showFps = it) } }

        Section(stringResource(R.string.settings_lyrics))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pluralStringResource(R.plurals.settings_cache, cached, formatBytes(cacheBytes), cached), color = Color.White, modifier = Modifier.weight(1f))
            TextButton(onClick = {
                vm.clearFetchedLyrics { Toast.makeText(context, R.string.settings_cache_cleared, Toast.LENGTH_SHORT).show() }
            }, enabled = cached > 0) { Text(stringResource(R.string.settings_delete_cache)) }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Section(stringResource(R.string.settings_debug))
        LinkRow(stringResource(R.string.debug_now_playing)) { onOpen(Screen.DEBUG_NOW_PLAYING) }
        LinkRow(stringResource(R.string.debug_lyrics)) { onOpen(Screen.DEBUG_LYRICS) }
        LinkRow(stringResource(R.string.debug_fitcheck)) { onOpen(Screen.FIT_CHECK) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(22.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White)
    Spacer(Modifier.height(8.dp))
}

/** Heading with the current value on the right, slider below. */
@Composable
private fun SliderRow(title: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int = 0, onChange: (Float) -> Unit) {
    Spacer(Modifier.height(18.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.weight(1f))
        Text("%.0f %%".format(value * 100), color = Muted)
    }
    Slider(value, onValueChange = onChange, valueRange = range, steps = steps)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.weight(1f))
        Switch(checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp),
    )
}

/** "0 KB", "84 KB", "1.4 MB". Lyrics are text, so a cache rarely passes a few megabytes. */
private fun formatBytes(bytes: Long): String = when {
    bytes < 1024L * 1024 -> "%d KB".format((bytes + 1023) / 1024)
    else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
}
