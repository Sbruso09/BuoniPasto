package com.mario.buonipasto.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Effetto pressione iOS: il controllo si rimpicciolisce e si scurisce leggermente. */
@Composable
fun Modifier.premibile(enabled: Boolean = true, onClick: () -> Unit): Modifier {
    val interazione = remember { MutableInteractionSource() }
    val premuto by interazione.collectIsPressedAsState()
    val scala by animateFloatAsState(if (premuto && enabled) 0.96f else 1f, label = "premuto")
    return this
        .scale(scala)
        .clickable(interactionSource = interazione, indication = null, enabled = enabled, onClick = onClick)
}

@Composable
fun LargeTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = IosText.largeTitle,
        color = Ios.textPrimary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 8.dp),
    )
}

@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = IosText.groupHeader,
        color = Ios.textSecondary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 7.dp),
    )
}

@Composable
fun GroupFooter(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = IosText.footnote,
        color = Ios.textSecondary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 7.dp),
    )
}

/** Gruppo "inset grouped" di iOS: scheda arrotondata su sfondo nero. */
@Composable
fun GroupCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(0.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Ios.surface)
            .padding(padding),
        content = content,
    )
}

@Composable
fun RowSeparator(start: Dp = 16.dp) {
    Box(
        Modifier
            .padding(start = start)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(Ios.separator),
    )
}

/** Riquadro colorato con icona bianca, come nelle Impostazioni di iOS. */
@Composable
fun IconTile(color: Color, icon: ImageVector? = null, painter: Painter? = null, size: Dp = 29.dp) {
    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(7.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) Icon(icon, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
        if (painter != null) Icon(painter, null, tint = Color.White, modifier = Modifier.size(size * 0.62f))
    }
}

/** Riga di un gruppo: icona, titolo, valore e freccia. */
@Composable
fun IosRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    value: String? = null,
    titleColor: Color = Ios.textPrimary,
    leading: (@Composable () -> Unit)? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(13.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = IosText.body, color = titleColor)
            if (subtitle != null) Text(subtitle, style = IosText.footnote, color = Ios.textSecondary)
        }
        if (value != null) {
            Text(value, style = IosText.body, color = Ios.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        if (chevron) {
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = Ios.textTertiary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
fun IosSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Ios.green,
            checkedBorderColor = Ios.green,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Ios.fillSecondary,
            uncheckedBorderColor = Color.Transparent,
        ),
    )
}

/** Pulsante principale pieno, alto 50 dp, come i pulsanti di sistema iOS. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Ios.accent,
) {
    val sfondo by animateColorAsState(if (enabled) color else Ios.fillTertiary, label = "sfondo")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .premibile(enabled, onClick)
            .clip(RoundedCornerShape(14.dp))
            .background(sfondo),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.headline, color = if (enabled) Color.White else Ios.textTertiary)
    }
}

/** Pulsante a pillola grigio con testo colorato. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Ios.accent,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .premibile(enabled, onClick)
            .clip(RoundedCornerShape(50))
            .background(Ios.fillTertiary)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = IosText.subhead.copy(fontWeight = FontWeight.SemiBold), color = if (enabled) color else Ios.textTertiary)
    }
}

/** Campo di testo iOS: riempito, angoli arrotondati, senza bordo. */
@Composable
fun IosField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    password: Boolean = false,
    error: Boolean = false,
    background: Color = Ios.surfaceElevated,
    textStyle: TextStyle = IosText.body,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        keyboardOptions = keyboardOptions,
        textStyle = textStyle.copy(color = Ios.textPrimary),
        cursorBrush = SolidColor(Ios.accent),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        modifier = modifier.fillMaxWidth(),
        decorationBox = { campo ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(background)
                    .then(
                        if (error) Modifier.background(Ios.red.copy(alpha = 0.12f)) else Modifier,
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                if (value.isEmpty()) Text(placeholder, style = textStyle, color = Ios.textTertiary)
                campo()
            }
        },
    )
}

/** Controllo segmentato di iOS. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Ios.fillTertiary)
            .padding(2.dp),
    ) {
        options.forEachIndexed { i, testo ->
            val attivo = i == selected
            val sfondo by animateColorAsState(if (attivo) Ios.surfaceElevated else Color.Transparent, label = "seg")
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(sfondo)
                    .clickable { onSelect(i) }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    testo,
                    style = IosText.footnote.copy(fontWeight = if (attivo) FontWeight.SemiBold else FontWeight.Medium),
                    color = Ios.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Contatore − n + in stile iOS. */
@Composable
fun Stepper(value: Int, onMinus: () -> Unit, onPlus: () -> Unit, minusEnabled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).premibile(minusEnabled, onMinus).clip(CircleShape).background(Ios.fillTertiary),
            contentAlignment = Alignment.Center,
        ) { Text("−", style = IosText.title3, color = if (minusEnabled) Ios.accent else Ios.textTertiary) }
        Text(
            value.toString(),
            style = IosText.title2,
            color = Ios.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(52.dp),
        )
        Box(
            Modifier.size(36.dp).premibile(true, onPlus).clip(CircleShape).background(Ios.fillTertiary),
            contentAlignment = Alignment.Center,
        ) { Text("+", style = IosText.title3, color = Ios.accent) }
    }
}

/** Avviso iOS con pulsanti di testo. */
@Composable
fun IosAlert(
    title: String,
    text: String?,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismiss: String,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Ios.surfaceElevated,
        shape = RoundedCornerShape(16.dp),
        title = { Text(title, style = IosText.headline, color = Ios.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = text?.let {
            { Text(it, style = IosText.footnote, color = Ios.textPrimary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, style = IosText.headline, color = if (destructive) Ios.red else Ios.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismiss, style = IosText.body, color = Ios.accent) }
        },
    )
}

/** Testo vuoto/centrato per gli stati senza contenuto. */
@Composable
fun EmptyState(title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = IosText.title3, color = Ios.textPrimary, textAlign = TextAlign.Center)
        Text(text, style = IosText.subhead, color = Ios.textSecondary, textAlign = TextAlign.Center)
    }
}
