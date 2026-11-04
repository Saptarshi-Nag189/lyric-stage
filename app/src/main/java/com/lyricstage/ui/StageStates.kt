package com.lyricstage.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.ui.theme.Ui

/**
 * Small status pill near the bottom of the stage: an optional icon or colour dot, the text,
 * and an optional action button in the album's accent.
 */
@Composable
fun StagePill(
    text: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
    glyph: Glyph? = null,
    dot: Color? = null,
    accent: Color = Ui.Default,
) {
    Row(
        modifier
            .windowInsetsPadding(WindowInsets.mandatorySystemGestures)
            .padding(bottom = 40.dp)
            .heightIn(min = 44.dp)
            .clip(CircleShape)
            .background(Ui.Scrim)
            .padding(start = 18.dp, end = if (action != null) 6.dp else 18.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            DrawnIcon(glyph, null, Ui.Text, 18)
            Spacer(Modifier.width(10.dp))
        } else if (dot != null) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, color = Ui.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        if (action != null) {
            Spacer(Modifier.width(10.dp))
            Box(
                Modifier
                    .heightIn(min = 36.dp)
                    .clip(CircleShape)
                    .background(accent)
                    .clickable(role = Role.Button, onClick = onAction)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(action, color = Ui.OnAccent, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
