package com.lyricstage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lyricstage.BuildConfig
import com.lyricstage.R
import com.lyricstage.ui.theme.Ui

private const val REPO = "https://github.com/Saptarshi-Nag189/lyric-stage"
private const val OFL = "https://openfontlicense.org"
private const val APACHE = "https://www.apache.org/licenses/LICENSE-2.0"

/** App name and version, licences, credits, links and the privacy note. */
@Composable
fun AboutScreen(vm: StageViewModel, onBack: () -> Unit) {
    val palette by vm.palette.collectAsStateWithLifecycle()
    val accent = Ui.accent(palette)
    val uri = LocalUriHandler.current
    val open: (String) -> Unit = { runCatching { uri.openUri(it) } }

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
                stringResource(R.string.about),
                color = Ui.Text,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
        Spacer(Modifier.height(16.dp))

        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(36.dp)).background(accent).padding(horizontal = 26.dp, vertical = 30.dp),
        ) {
            Text(stringResource(R.string.app_name), color = Ui.OnAccent, fontSize = 38.sp, fontWeight = FontWeight.Black, letterSpacing = (-1).sp)
            Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), color = Ui.OnAccent, fontSize = 17.sp, modifier = Modifier.padding(top = 6.dp))
        }

        SectionHeader(stringResource(R.string.about_licences))
        UiCard(padding = 0) {
            InfoRow("Inter", stringResource(R.string.licence_ofl), divider = false) { open(OFL) }
            InfoRow("Noto Sans Devanagari, Bengali and JP", stringResource(R.string.licence_ofl)) { open(OFL) }
            InfoRow("Kotlin, kotlinx", stringResource(R.string.licence_apache)) { open(APACHE) }
            InfoRow("AndroidX and Jetpack Compose", stringResource(R.string.licence_apache)) { open(APACHE) }
            InfoRow("OkHttp", stringResource(R.string.licence_apache)) { open(APACHE) }
        }

        SectionHeader(stringResource(R.string.about_credits))
        UiCard(padding = 0) {
            InfoRow("LyricalStudio", stringResource(R.string.credit_lyricalstudio), divider = false) { open("https://github.com/arijit-debug/LyricalStudio") }
            InfoRow("LRCLIB", stringResource(R.string.credit_lrclib)) { open("https://lrclib.net") }
            InfoRow("NetEase Cloud Music, Musixmatch, Kugou", stringResource(R.string.credit_sources))
        }

        SectionHeader(stringResource(R.string.about_links))
        UiCard(padding = 0) {
            InfoRow(stringResource(R.string.link_latest), "LyricStage.apk", divider = false) { open("$REPO/releases/latest/download/LyricStage.apk") }
            InfoRow(stringResource(R.string.link_releases), stringResource(R.string.link_releases_hint)) { open("$REPO/releases") }
            InfoRow(stringResource(R.string.link_source), "GitHub") { open(REPO) }
        }

        SectionHeader(stringResource(R.string.about_privacy))
        UiCard {
            Text(stringResource(R.string.privacy_note), color = Ui.Text, fontSize = 15.sp, lineHeight = 22.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** A title and a muted line under it; opens [onClick] when given, with an arrow. */
@Composable
private fun InfoRow(title: String, subtitle: String, divider: Boolean = true, onClick: (() -> Unit)? = null) {
    if (divider) HorizontalDivider(color = Ui.Raised)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Ui.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        if (onClick != null) DrawnIcon(Glyph.CHEVRON, null, Ui.Muted, 20)
    }
}
