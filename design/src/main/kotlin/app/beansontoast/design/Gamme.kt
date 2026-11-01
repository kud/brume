package app.beansontoast.design

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.indication
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.RippleConfiguration
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** An app's two schemes. An app with no brand passes Material's baseline. */
@Immutable
class GammeColors(val light: ColorScheme, val dark: ColorScheme)

val BaselineColors = GammeColors(lightColorScheme(), darkColorScheme())

val GammeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private const val TABULAR = "tnum"

fun gammeTypography(base: Typography = Typography()): Typography = base.copy(
    labelLarge = base.labelLarge.copy(fontFeatureSettings = TABULAR),
    labelMedium = base.labelMedium.copy(fontFeatureSettings = TABULAR),
    labelSmall = base.labelSmall.copy(fontFeatureSettings = TABULAR),
    bodySmall = base.bodySmall.copy(fontFeatureSettings = TABULAR),
)

/** Tabular figures for a larger number that changes under a finger (a level). */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = TABULAR)

val LocalReducedMotion = compositionLocalOf { false }

/**
 * Dynamic colour off (the ground must be predictable at 3 a.m.). [trueBlack] puts the dark ground
 * on pure black, containers keep their steps. [dim] is Brume's night tier: it lowers the
 * brightness of body text to onSurfaceVariant, never its contrast, and never with alpha.
 */
@OptIn(ExperimentalMaterial3Api::class)
private val SoftRipple = RippleConfiguration(rippleAlpha = RippleAlpha(draggedAlpha = 0.08f, focusedAlpha = 0.08f, hoveredAlpha = 0.04f, pressedAlpha = 0.07f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GammeTheme(
    colors: GammeColors = BaselineColors,
    darkTheme: Boolean,
    trueBlack: Boolean = true,
    dim: Boolean = false,
    typography: Typography = gammeTypography(),
    content: @Composable () -> Unit,
) {
    val scheme = gammeScheme(colors, darkTheme, trueBlack, dim)
    MaterialTheme(colorScheme = scheme, shapes = GammeShapes, typography = typography) {
        CompositionLocalProvider(
            LocalContentColor provides scheme.onSurface,
            LocalReducedMotion provides rememberReducedMotion(),
            // Ripples stay, faint: they say the tap landed without flashing (docs/motion.md).
            LocalRippleConfiguration provides SoftRipple,
            content = content,
        )
    }
}

fun gammeScheme(colors: GammeColors, darkTheme: Boolean, trueBlack: Boolean, dim: Boolean): ColorScheme {
    if (!darkTheme) return colors.light
    val dark = if (trueBlack) colors.dark.copy(background = Color.Black, surface = Color.Black, surfaceContainerLowest = Color.Black) else colors.dark
    // At night the ink action steps down with the text: still the strongest thing by lightness, less glare.
    return if (dim) dark.copy(onBackground = dark.onSurfaceVariant, onSurface = dark.onSurfaceVariant, inverseSurface = dark.onSurfaceVariant) else dark
}

/** True while the system's animator duration scale is 0, read live. */
@Composable
fun rememberReducedMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    fun read() = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    var reduced by remember { mutableStateOf(read()) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { reduced = read() }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    return reduced
}

/**
 * A chip. Selection is carried by a check glyph, weight and a firmer edge, never by hue alone
 * (Erwann is colourblind; Gamme's colour rules). Light and precise (Iris, 2026-11-01, phase 2): a
 * 36 dp hairline surface with small corners inside a 48 dp target, laid out at its chosen width
 * from the first frame, so choosing never moves its neighbours (docs/motion.md).
 */
@Composable
fun SelectChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val source = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    androidx.compose.foundation.layout.Box(
        modifier.heightIn(min = 48.dp)
            .chipSelectable(selected, source, onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.heightIn(min = 36.dp).clip(shape)
                .background(if (selected) scheme.secondaryContainer.copy(alpha = 0.72f) else scheme.surfaceContainerLowest.copy(alpha = 0.55f))
                .border(if (selected) 1.5.dp else 1.dp, if (selected) scheme.onSurface.copy(alpha = 0.72f) else scheme.outlineVariant, shape)
                .indication(source, androidx.compose.material3.ripple())
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChipFace(label, true, Modifier.alpha(0f).clearAndSetSemantics {})
            ChipFace(label, selected)
        }
    }
}

private fun Modifier.chipSelectable(selected: Boolean, source: androidx.compose.foundation.interaction.MutableInteractionSource, onClick: () -> Unit) =
    selectable(selected = selected, interactionSource = source, indication = null, role = androidx.compose.ui.semantics.Role.Checkbox, onClick = onClick)

@Composable
private fun ChipFace(label: String, selected: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (selected) Icon(androidx.compose.ui.res.painterResource(R.drawable.ic_check), contentDescription = null,
            modifier = Modifier.padding(end = 6.dp).size(16.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
    }
}

/** A heading in onSurface, then its rows on one surfaceContainer card (large corners). */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 4.dp).semantics { heading() })
        Column(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.large)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
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
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
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
