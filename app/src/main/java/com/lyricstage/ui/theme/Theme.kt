package com.lyricstage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.lyricstage.stage.StageFonts

private val Scheme = darkColorScheme(
    primary = Ui.Default,
    onPrimary = Ui.OnAccent,
    secondary = Color(0xFF7FD1FF),
    background = Ui.Ground,
    surface = Ui.Card,
    surfaceVariant = Ui.Raised,
    onBackground = Ui.Text,
    onSurface = Ui.Text,
    onSurfaceVariant = Ui.Muted,
    outline = Ui.Line,
)

@Composable
fun LyricStageTheme(content: @Composable () -> Unit) {
    val base = Typography()
    val inter = TextStyle(fontFamily = StageFonts.inter)
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(
            headlineMedium = base.headlineMedium.merge(inter),
            titleLarge = base.titleLarge.merge(inter),
            titleMedium = base.titleMedium.merge(inter),
            bodyLarge = base.bodyLarge.merge(inter),
            bodyMedium = base.bodyMedium.merge(inter),
            labelLarge = base.labelLarge.merge(inter),
        ),
    ) {
        // gives every screen light text on the dark background by default
        Surface(color = Scheme.background, contentColor = Scheme.onBackground, content = content)
    }
}
