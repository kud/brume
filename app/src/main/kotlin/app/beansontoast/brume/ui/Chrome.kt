package app.beansontoast.brume.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R

/*
 * The frame (Iris, 2026-11-01, from Erwann: « des second screens pour chaque fonction… on revient
 * vers la home grâce à une croix »). No dock, no tabs: home lists every function, each opens full
 * screen over its landscape, and a ✕ top-left (the same place on every screen) or system back
 * returns home. Screens give way to each other by the slow cross-fade (docs/motion.md).
 */

/** The room the navigation bar takes at the bottom: pages pad by it so nothing ends under it. */
val LocalChromeBottom = compositionLocalOf { 0.dp }

/** Bottom padding inside a scrolling page, so its end clears the navigation bar. */
@Composable
fun Modifier.underChrome(): Modifier = padding(bottom = LocalChromeBottom.current + 8.dp)

/** For pages whose last row is pinned (the Tones dock): the room the navigation bar takes. */
@Composable
internal fun chromeBottom(): Dp = LocalChromeBottom.current

/**
 * A function's own screen: its [land] behind, the ✕ (or ‹ [onBack] for a page inside it) and the
 * [title] on one 56 dp row below the status bar, then [content]. Neither draws no row (a session).
 */
@Composable
fun FnFrame(
    onClose: (() -> Unit)?,
    title: String? = null,
    onBack: (() -> Unit)? = null,
    land: Land = Land.QUIET,
    content: @Composable () -> Unit,
) {
    val nav = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val inner = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<InnerClaim?>(null) }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Landscape(land)
        CompositionLocalProvider(LocalChromeBottom provides nav, LocalInnerBack provides inner) {
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                // A view inside the screen that has claimed the frame's corner turns ✕ into ‹ back to it
                // (or keeps a ✕ that leaves that view, on its first step).
                val claimed = inner.value
                if (claimed != null) CloseBar(title, claimed.onBack, back = !claimed.close)
                else if (onClose != null) CloseBar(title, onClose)
                else if (onBack != null) CloseBar(title, onBack, back = true)
                Box(Modifier.fillMaxWidth().weight(1f)) { content() }
            }
        }
    }
}

/**
 * The ✕ home (or ‹ back, for a page inside a function), top-left, a 48 dp target, with the screen's
 * name centred on the same row. That name is the screen's only title (Iris, 2026-11-01, phase 2).
 */
@Composable
fun CloseBar(title: String?, onClose: () -> Unit, back: Boolean = false) {
    val close = stringResource(if (back) R.string.learn_back else R.string.home_close)
    Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
        Box(Modifier.align(Alignment.CenterStart).size(48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = close, onClick = onClose).semantics { contentDescription = close },
            contentAlignment = Alignment.Center) {
            Icon(painterResource(if (back) R.drawable.ic_back else R.drawable.ic_close), null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(22.dp))
        }
        if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.align(Alignment.Center).padding(horizontal = 56.dp).semantics { heading() })
    }
}

/**
 * The sound playing, on home (Erwann, 2026-10-31, kept for the new home): what it is and Pause.
 * A light row with a hairline, not a slab; tapping the name opens its screen.
 */
@Composable
fun MiniPlayer(name: String, onPause: () -> Unit, onOpen: () -> Unit = {}) {
    val scheme = MaterialTheme.colorScheme
    val pause = stringResource(R.string.action_pause)
    val shape = RoundedCornerShape(16.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(shape).background(scheme.surfaceContainerLowest.copy(alpha = 0.82f))
        .border(1.dp, scheme.outlineVariant, shape), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onOpen).padding(start = 16.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.home_playing), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            // A mix of three names takes a second line at large text rather than losing its last one (Iris, 2026-11-01).
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.heightIn(min = 48.dp).padding(end = 6.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onPause)
            .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_pause), null, modifier = Modifier.size(20.dp))
            Text(pause, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** A view's claim on the frame's corner: what the corner does, and whether it reads ✕ rather than ‹. */
internal class InnerClaim(val onBack: () -> Unit, val close: Boolean)

/** Where a view inside a screen puts its own back, so the frame's corner can carry it. */
internal val LocalInnerBack = compositionLocalOf<androidx.compose.runtime.MutableState<InnerClaim?>?> { null }

/**
 * A view one step inside a screen (a saved profile, a step of Mon acouphène's search): while it
 * shows, the frame's corner and system Back return to [onBack] rather than leaving for home (Iris,
 * phase 3), so the view needs no back row of its own. The corner reads ‹, or ✕ when [close] (the
 * first step of a journey, where going back means leaving it; Iris, phase 4).
 */
@Composable
internal fun InnerBack(onBack: () -> Unit, close: Boolean = false) {
    val slot = LocalInnerBack.current
    val current = androidx.compose.runtime.rememberUpdatedState(onBack)
    androidx.activity.compose.BackHandler { current.value() }
    if (slot != null) androidx.compose.runtime.DisposableEffect(slot, close) {
        val claim = InnerClaim({ current.value() }, close)
        slot.value = claim
        onDispose { if (slot.value === claim) slot.value = null }
    }
}
