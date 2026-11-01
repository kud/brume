package app.beansontoast.brume.ui

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R
import app.beansontoast.design.LocalReducedMotion
import app.beansontoast.design.Motion
import app.beansontoast.design.WeightSwap
import app.beansontoast.design.calmAlpha
import kotlin.math.PI
import kotlin.math.sin

/*
 * The chrome (Iris, 2026-11-01, from Erwann on dev.19: « faire un menu… en bas, un peu en format
 * floating… ça donnerait du cachet », and the art « pas coupé en haut et en bas »).
 *  - Nothing paints a band any more: the art runs under the status and navigation bars, and every
 *    row over it sits on transparent ground.
 *  - Listen, Journal and Learn move from the top words to a floating bar near the bottom, inset
 *    from the edges, in the ink and mist language: the serif of the scene names, a hairline, and
 *    the page you are on told by weight and a small mist wave under its name, never by a hue.
 *  - The sound playing docks onto the bar on Journal and Learn: one floating piece, not two.
 *  - Off Listen, the scene last used stays behind the page, dimmer and still, so reading never moves.
 */

/** The height the floating bar (and the nav bar under it) takes at the bottom: pages pad by it so nothing ends under the bar. */
val LocalChromeBottom = compositionLocalOf { 0.dp }

/** Bottom padding inside a scrolling page, so its end scrolls clear of the floating bar. */
@Composable
fun Modifier.underChrome(): Modifier = padding(bottom = LocalChromeBottom.current + 8.dp)

/** How far behind a reading page the scene art sits, against its dim on Listen (35% at night, 70% by day). */
internal const val READING_VEIL = 0.45f

/**
 * Every tab's frame. [bleed]: the page draws under the status bar itself (Listen's scenes); the
 * others are kept below it. [backdrop]: the scene art shown, still and veiled, behind a reading page.
 */
@Composable
fun BrumeScaffold(
    tab: Tab,
    onTab: (Tab) -> Unit,
    modules: Modules = Modules(),
    mini: (@Composable () -> Unit)? = null,
    backdrop: Scene? = null,
    night: Boolean = false,
    bleed: Boolean = false,
    content: @Composable () -> Unit,
) {
    // Breathe and Tones are scenes inside Listen, so they are not here twice.
    val here = when (tab) { Tab.CALM, Tab.TONES -> Tab.LISTEN; else -> tab }
    val density = LocalDensity.current
    var dockPx by remember { mutableIntStateOf(0) }
    // Until the bar is measured, a fair guess: the bar, its margin and a gesture bar.
    val chromeBottom = if (dockPx > 0) with(density) { dockPx.toDp() } else if (mini != null) 160.dp else 104.dp
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // The veiled scene fades in and out behind reading pages instead of appearing at once.
        var lastBackdrop by remember { mutableStateOf(backdrop) }
        if (backdrop != null) lastBackdrop = backdrop
        val backdropAlpha = calmAlpha(backdrop != null)
        lastBackdrop?.let { b -> b.art()?.takeIf { backdropAlpha > 0f }?.let {
            SceneBackdrop(b, it, night, still = true, clock = { 0L }, offset = { 0f }, breathe = null,
                veil = READING_VEIL, modifier = Modifier.fillMaxSize().alpha(backdropAlpha))
        } }
        CompositionLocalProvider(LocalChromeBottom provides chromeBottom) {
            Box(Modifier.fillMaxSize().then(if (bleed) Modifier else Modifier.statusBarsPadding())) { content() }
        }
        FloatingDock(here, onTab, mini, Modifier.align(Alignment.BottomCenter).onSizeChanged { dockPx = it.height })
    }
}

/**
 * The floating bar: one rounded piece of the night's own surface with a hairline, opaque, since
 * a page scrolling under a translucent bar ghosted its words through the menu's. The mini player, when a sound plays off Listen, is
 * its upper row. Inset 16 dp from the sides, 12 dp above the gesture bar.
 */
@Composable
private fun FloatingDock(here: Tab, onTab: (Tab) -> Unit, mini: (@Composable () -> Unit)?, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    Box(modifier.fillMaxWidth().navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 440.dp).fillMaxWidth().clip(shape)
                .background(scheme.surfaceContainer)
                .border(1.dp, scheme.outlineVariant.copy(alpha = 0.7f), shape),
        ) {
            // The mini player grows into the bar slowly and fades, never a jump (docs/motion.md); the
            // last one shown stays drawn while it leaves.
            val reduced = LocalReducedMotion.current
            var last by remember { mutableStateOf(mini) }
            if (mini != null) last = mini
            AnimatedVisibility(mini != null, enter = Motion.enter(reduced), exit = Motion.exit(reduced), label = "mini") {
                Column {
                    last?.invoke()
                    Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = 0.6f)))
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp).selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
                listOf(Tab.LISTEN to R.string.tab_listen, Tab.JOURNAL to R.string.tab_journal, Tab.LEARN to R.string.tab_learn).forEach { (t, label) ->
                    MenuWord(label, here == t, Modifier.weight(1f)) { onTab(t) }
                }
            }
        }
    }
}

/**
 * A page in the bar: its name in the serif of the scene names. The one you are on is heavier and
 * sits on a short mist wave, the brand's own line; the others are the full ink, a little lighter.
 * Shape and weight, never a hue (Erwann is colourblind). 56 dp tall, a third of the bar wide.
 */
@Composable
private fun MenuWord(@StringRes text: Int, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ink = MaterialTheme.colorScheme.onSurface
    Column(
        modifier.heightIn(min = 56.dp).clip(CircleShape).selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Both weights are laid out at once and cross-faded, so the word never changes width (docs/motion.md).
        WeightSwap(stringResource(text), on = selected, onStyle = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Serif),
            onColor = ink, offColor = ink.copy(alpha = 0.78f), modifier = Modifier.padding(top = 4.dp))
        MistWave(ink.copy(alpha = 0.9f), Modifier.padding(top = 3.dp).width(30.dp).height(7.dp).alpha(calmAlpha(selected, Motion.QUICK)))
    }
}

/** One and a half soft waves, tapered at the ends, the mark under the page you are on. Nothing is drawn for the others, but it keeps its place. */
@Composable
private fun MistWave(color: androidx.compose.ui.graphics.Color?, modifier: Modifier) {
    Canvas(modifier) {
        if (color == null) return@Canvas
        val path = Path()
        val steps = 24
        for (i in 0..steps) {
            val f = i / steps.toFloat()
            val x = f * size.width
            val y = size.height / 2 + sin(f * 3 * PI).toFloat() * size.height * 0.32f
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
    }
}

/**
 * The sound playing, on every tab but Listen (Erwann, 2026-10-31): its name and Pause, as the
 * floating bar's upper row. Nothing else; Listen has the rest.
 */
@Composable
fun MiniPlayer(name: String, onPause: () -> Unit) {
    val pause = stringResource(R.string.action_pause)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 22.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onPause).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pause), null, modifier = Modifier.size(20.dp))
            Text(pause, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** For pages whose last row is pinned (the Tones dock): the space the floating bar takes. */
@Composable
internal fun chromeBottom(): Dp = LocalChromeBottom.current
