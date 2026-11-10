package com.lyricstage.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lyricstage.ui.theme.Ui

/** A rounded group of rows or controls. */
@Composable
fun UiCard(modifier: Modifier = Modifier, padding: Int = 16, content: @Composable ColumnScope.() -> Unit) {
    // with the glass on, a card is the content layer: a quiet frosted pane over the
    // dimmed background, thicker as the frost rises, never glass itself
    val glass = LocalGlass.current
    val shape = RoundedCornerShape(24.dp)
    val surface = if (glass.on) {
        Modifier.background(Color(0xFF0C0E1C).copy(alpha = 0.44f + 0.32f * glass.frost)).border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), shape)
    } else {
        Modifier.background(Ui.Card)
    }
    Column(
        modifier.fillMaxWidth().clip(shape).then(surface).padding(horizontal = 16.dp, vertical = padding.dp),
        content = content,
    )
}

/** Small capitals heading above a card. */
@Composable
fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = Ui.Muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 24.dp, bottom = 10.dp),
    )
}

enum class PillStyle { PRIMARY, TONAL, OUTLINE }

/** Rounded button, at least 48 dp tall. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: PillStyle = PillStyle.TONAL,
    accent: Color = Ui.Default,
    enabled: Boolean = true,
    height: Int = 48,
) {
    val (bg, fg) = when (style) {
        PillStyle.PRIMARY -> accent to Ui.OnAccent
        PillStyle.TONAL -> Ui.Raised to Ui.Text
        PillStyle.OUTLINE -> Color.Transparent to Ui.Text
    }
    val glass = LocalGlass.current
    val surface = when {
        style == PillStyle.OUTLINE -> Modifier.clip(CircleShape).border(BorderStroke(1.dp, if (glass.on) Color.White.copy(alpha = 0.28f) else Ui.Line), CircleShape)
        glass.on && style == PillStyle.PRIMARY -> Modifier.glassSurface(CircleShape, glass, bright = 0.25f, tint = bg)
        glass.on -> Modifier.glassSurface(CircleShape, glass, bright = 0.2f)
        else -> Modifier.clip(CircleShape).background(bg)
    }
    Box(
        modifier
            .heightIn(min = height.dp)
            .then(surface)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) fg else fg.copy(alpha = 0.4f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** One choice in a set of pills (layouts). */
@Composable
fun ChoicePill(text: String, selected: Boolean, accent: Color, onClick: () -> Unit, onStage: Boolean = false) {
    val glass = LocalGlass.current
    val bg by animateColorAsState(
        when {
            glass.on && !selected -> Color.White.copy(alpha = if (onStage) 0.14f else 0.09f)
            glass.on -> Color.Transparent
            selected && onStage -> Ui.Text
            selected -> accent
            onStage -> Color.Black.copy(alpha = 0.35f)
            else -> Color.Transparent
        },
        label = "pill",
    )
    val fg = when {
        glass.on -> Ui.Text
        selected && onStage -> Ui.Ground
        selected -> Ui.OnAccent
        else -> Ui.Text
    }
    Box(
        Modifier
            .height(44.dp)
            .then(if (glass.on && selected) Modifier.glassSurface(CircleShape, glass, bright = 0.8f) else Modifier.clip(CircleShape))
            .background(bg)
            .then(if (!selected && !onStage && !glass.on) Modifier.border(BorderStroke(1.dp, Ui.Line), CircleShape) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Two or three options in one rounded track; the chosen one is a light pill. */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val glass = LocalGlass.current
    Row(
        Modifier.fillMaxWidth().clip(CircleShape).background(if (glass.on) Color.Black.copy(alpha = 0.26f) else Ui.Ground).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, label) in options) {
            val on = value == selected
            val bg by animateColorAsState(if (on && !glass.on) Ui.Text else Color.Transparent, label = "segment")
            Box(
                Modifier
                    .weight(1f)
                    .height(44.dp)
                    .then(if (on && glass.on) Modifier.glassSurface(CircleShape, glass, bright = 0.8f) else Modifier.clip(CircleShape))
                    .background(bg)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (glass.on) (if (on) Ui.Text else Ui.Text.copy(alpha = 0.74f)) else if (on) Ui.Ground else Ui.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

/** A setting with a switch: the whole row toggles, at least 72 dp tall. */
@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    accent: Color,
    onChange: (Boolean) -> Unit,
    hint: String? = null,
    enabled: Boolean = true,
    divider: Boolean = true,
) {
    if (divider) HorizontalDivider(color = Ui.Raised)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 16.dp)) {
            Text(label, color = if (enabled) Ui.Text else Ui.Muted, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            if (hint != null) Text(hint, color = Ui.Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
        }
        SwitchTrack(checked, accent, enabled)
    }
}

@Composable
private fun SwitchTrack(checked: Boolean, accent: Color, enabled: Boolean) {
    if (LocalGlass.current.on) {
        GlassSwitch(checked, accent, enabled)
        return
    }
    val track by animateColorAsState(if (checked) accent else Ui.Line, label = "track")
    val knob by animateColorAsState(if (checked) Ui.OnAccent else Ui.Text, label = "knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "knobX")
    Box(
        Modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(CircleShape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(CircleShape).background(knob))
    }
}

/** A row that opens another screen. */
@Composable
fun LinkRow(label: String, onClick: () -> Unit, divider: Boolean = true) {
    if (divider) HorizontalDivider(color = Ui.Raised)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Ui.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        DrawnIcon(Glyph.CHEVRON, null, Ui.Muted, 20)
    }
}

/** Label on the left, value on the right. */
@Composable
fun LabelValue(label: String, value: String, valueColour: Color = Ui.Muted) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = Ui.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text(value, color = valueColour, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Clip)
    }
}

/** A small rounded tag. */
@Composable
fun Tag(text: String) {
    Text(
        text,
        color = Ui.Text,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(CircleShape).background(Ui.Raised).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Round 48 dp icon button. */
@Composable
fun CircleIconButton(glyph: Glyph, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, background: Color = Ui.Card) {
    val glass = LocalGlass.current
    Box(
        modifier.size(48.dp)
            .then(if (glass.on) Modifier.glassSurface(CircleShape, glass, bright = 0.15f) else Modifier.clip(CircleShape).background(background))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        DrawnIcon(glyph, description, Ui.Text, 22)
    }
}

/** Lays out [content] in a row with [gap] between items. */
@Composable
fun GapRow(gap: Int, modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(gap.dp), verticalAlignment = Alignment.CenterVertically, content = content)
}
