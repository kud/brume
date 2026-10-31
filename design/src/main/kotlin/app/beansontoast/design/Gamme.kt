package app.beansontoast.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.toggleable

/**
 * Material's baseline scheme, dynamic colour off (the ground must be predictable at 3 a.m.).
 * [trueBlack] puts the dark ground on pure black. [dim] is Brume's night tier: it lowers the
 * brightness of body text to onSurfaceVariant, never its contrast, and never with alpha.
 */
@Composable
fun GammeTheme(darkTheme: Boolean, trueBlack: Boolean = true, dim: Boolean = false, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = gammeScheme(darkTheme, trueBlack, dim), content = content)
}

fun gammeScheme(darkTheme: Boolean, trueBlack: Boolean, dim: Boolean): ColorScheme {
    if (!darkTheme) return lightColorScheme()
    val base = darkColorScheme()
    val ground = if (trueBlack) Color.Black else base.background
    val dark = base.copy(
        background = ground,
        surface = ground,
        surfaceContainerLowest = ground,
        surfaceContainerLow = if (trueBlack) Color(0xFF0E0E10) else base.surfaceContainerLow,
        surfaceContainer = if (trueBlack) Color(0xFF141317) else base.surfaceContainer,
        surfaceContainerHigh = if (trueBlack) Color(0xFF1C1B1F) else base.surfaceContainerHigh,
        surfaceContainerHighest = if (trueBlack) Color(0xFF242328) else base.surfaceContainerHighest,
    )
    return if (dim) dark.copy(onBackground = dark.onSurfaceVariant, onSurface = dark.onSurfaceVariant) else dark
}

/**
 * A single-select chip. Selection is carried by a check glyph and weight, never by hue alone
 * (Erwann is colourblind; Gamme's colour rules).
 */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        modifier = modifier.heightIn(min = 48.dp),
    )
}

@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
fun SwitchRow(title: String, summary: String?, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp).semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}
