package com.lyricstage.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.lyricstage.core.palette.StagePalette

/** Colours for the app's screens around the stage (settings, controls, onboarding, pop-ups). */
object Ui {
    val Ground = Color(0xFF0B0B0D)
    val Card = Color(0xFF17181C)
    val Raised = Color(0xFF26272D)
    val Line = Color(0xFF3A3B42)
    val Track = Color(0xFF2A2B31)
    val Text = Color(0xFFF3F1EC)
    /** Secondary text: over 7:1 on [Card]. */
    val Muted = Color(0xFFA7A6AE)
    /** Text and icons on the accent. */
    val OnAccent = Color(0xFF160C07)
    /** Pop-ups and buttons that sit on the stage. */
    val Scrim = Color.Black.copy(alpha = 0.55f)
    val Default = Color(0xFFFF8A5B)

    /**
     * The accent for the screens around the stage: the album's strongest colour, lifted when
     * it is too dark for [OnAccent] text to read on it.
     */
    fun accent(palette: StagePalette?): Color {
        val c = palette?.accents?.firstOrNull()?.let { Color(it) } ?: Default
        val l = c.luminance()
        return if (l >= 0.35f) c else lerp(c, Color.White, ((0.35f - l) / 0.65f + 0.15f).coerceIn(0f, 0.7f))
    }
}
