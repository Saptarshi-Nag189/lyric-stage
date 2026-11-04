package com.lyricstage.ui

import android.content.ActivityNotFoundException
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.R
import com.lyricstage.media.NotificationAccess
import com.lyricstage.ui.theme.Ui

/** Shown until notification access is granted; deep-links to the system toggle. */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val accent = Ui.Default
    Box(Modifier.fillMaxSize().background(Ui.Ground)) {
        // a soft light in the corner, in the accent
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width * 1.0f, size.height * 0.05f)
            val r = size.width * 0.9f
            drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent), c, r), r, c)
        }
        Column(
            Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            // the text scrolls on small screens; the button stays at the bottom
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(48.dp))
                Text(
                    stringResource(R.string.onboarding_title),
                    color = Ui.Text,
                    fontSize = 44.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.onboarding_body), color = Ui.Muted, fontSize = 17.sp, lineHeight = 25.sp)
                Spacer(Modifier.height(28.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Step(1, stringResource(R.string.onboarding_step1), accent, current = true)
                    Step(2, stringResource(R.string.onboarding_step2), accent, current = false)
                }
                Spacer(Modifier.height(20.dp))
                Text(stringResource(R.string.onboarding_privacy), color = Ui.Muted, fontSize = 14.sp, lineHeight = 21.sp)
                Spacer(Modifier.height(24.dp))
            }
            PillButton(
                stringResource(R.string.onboarding_grant),
                {
                    try {
                        context.startActivity(NotificationAccess.settingsIntent(context))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(NotificationAccess.fallbackIntent())
                    }
                },
                Modifier.fillMaxWidth(),
                style = PillStyle.PRIMARY,
                accent = accent,
                height = 56,
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String, accent: Color, current: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Ui.Card).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(36.dp).clip(CircleShape).background(if (current) accent else Ui.Raised),
            contentAlignment = Alignment.Center,
        ) {
            Text(n.toString(), color = if (current) Ui.OnAccent else Ui.Text, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
        Text(text, color = Ui.Text, fontSize = 16.sp, lineHeight = 22.sp, modifier = Modifier.padding(start = 14.dp))
    }
}
