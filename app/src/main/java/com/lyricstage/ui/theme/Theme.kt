package com.lyricstage.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.lyricstage.stage.AppFont
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
fun LyricStageTheme(appFont: AppFont = AppFont.INSTRUMENT, content: @Composable () -> Unit) {
    val base = Typography()
    // every text style in the app uses the font picked in Settings
    val family = StageFonts.app(appFont)
    fun TextStyle.ui() = merge(TextStyle(fontFamily = family))
    MaterialTheme(
        colorScheme = Scheme,
        typography = Typography(
            displayLarge = base.displayLarge.ui(), displayMedium = base.displayMedium.ui(), displaySmall = base.displaySmall.ui(),
            headlineLarge = base.headlineLarge.ui(), headlineMedium = base.headlineMedium.ui(), headlineSmall = base.headlineSmall.ui(),
            titleLarge = base.titleLarge.ui(), titleMedium = base.titleMedium.ui(), titleSmall = base.titleSmall.ui(),
            bodyLarge = base.bodyLarge.ui(), bodyMedium = base.bodyMedium.ui(), bodySmall = base.bodySmall.ui(),
            labelLarge = base.labelLarge.ui(), labelMedium = base.labelMedium.ui(), labelSmall = base.labelSmall.ui(),
        ),
    ) {
        // gives every screen light text on the dark background by default
        Surface(color = Scheme.background, contentColor = Scheme.onBackground, content = content)
    }
}
