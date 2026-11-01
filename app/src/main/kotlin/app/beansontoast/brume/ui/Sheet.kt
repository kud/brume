package app.beansontoast.brume.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.design.Motion
import app.beansontoast.design.WeightSwap
import app.beansontoast.design.tabular
import kotlinx.coroutines.launch

/*
 * Sheets and pagers for screens that fit their frame (Iris, 2026-11-01, garden second pass; Erwann:
 * « un truc vraiment carré, un peu snap », no free scroll). A sheet is drawn in the page's own
 * window rather than as Material's ModalBottomSheet: that one slides up from the edge on its own
 * spring and lives in a separate window, so it could neither follow the motion spec (a fade on
 * CALM, nothing flying in) nor stand above the floating bar. This one fades in place, rests on
 * the bar's top edge, closes on the scrim, the back gesture or its own ✕, and keeps no room when shut.
 */

/**
 * A quiet sheet over the page: a scrim, and one card resting just above the floating bar. [open]
 * fades it on [Motion.CALM]; its content is laid out at its final size from the first frame.
 */
@Composable
internal fun QuietSheet(open: Boolean, onClose: () -> Unit, title: String, content: @Composable ColumnScope.() -> Unit) {
    val shown by animateFloatAsState(if (open) 1f else 0f, Motion.tween(Motion.CALM), label = "sheet")
    if (!open && shown == 0f) return
    BackHandler(open) { onClose() }
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown }) {
        Box(
            Modifier.fillMaxSize().background(scheme.scrim.copy(alpha = 0.32f))
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, onClick = onClose),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .padding(bottom = chromeBottom() + 8.dp).padding(horizontal = 12.dp)
                .clip(MaterialTheme.shapes.large)
                .background(scheme.surfaceContainerLow)
                .border(1.dp, scheme.outlineVariant, MaterialTheme.shapes.large)
                // The card takes its own taps, so a tap between two chips never closes it.
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open) {}
                .semantics { paneTitle = title }
                .padding(start = 20.dp, end = 8.dp, bottom = 16.dp),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
                val close = stringResource(R.string.sheet_close)
                IconButton(onClose, enabled = open, modifier = Modifier.semantics { contentDescription = close }) {
                    Icon(painterResource(R.drawable.ic_close), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.padding(end = 12.dp)) { content() }
        }
    }
}

/**
 * The names of a pager's pages as a row of words: the shown one by weight and a short rule under
 * it, never by colour. A tap eases the pager there on CALM; a swipe moves it with the finger.
 */
@Composable
internal fun PagerWords(pager: PagerState, names: List<String>, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val scheme = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup()) {
        names.forEachIndexed { i, name ->
            val on = pager.targetPage == i
            Column(
                Modifier.heightIn(min = 48.dp).selectable(on, role = Role.Tab) { scope.launch { pager.animateScrollToPage(i, animationSpec = Motion.tween(Motion.CALM)) } }
                    .padding(end = 20.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                WeightSwap(name, on, MaterialTheme.typography.titleSmall, onColor = scheme.onSurface, offColor = scheme.onSurfaceVariant, align = Alignment.CenterStart)
                val rule by animateFloatAsState(if (on) 1f else 0f, Motion.tween(Motion.QUICK), label = "rule")
                Box(Modifier.padding(top = 6.dp).width(24.dp).height(2.dp).alpha(rule).background(scheme.onSurface))
            }
        }
    }
}

/**
 * ‹ 2 / 3 › : where a pager stands, written, with a step either way. It keeps its room whether or
 * not there is more than one page, faded out when there is not, so nothing beside it moves.
 */
@Composable
internal fun PageStepper(page: Int, count: Int, onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val shown by animateFloatAsState(if (count > 1) 1f else 0f, Motion.tween(Motion.QUICK), label = "stepper")
    Row(modifier.alpha(shown), verticalAlignment = Alignment.CenterVertically) {
        val back = stringResource(R.string.page_previous)
        val next = stringResource(R.string.page_next)
        IconButton({ onStep(page - 1) }, enabled = count > 1 && page > 0, modifier = Modifier.semantics { contentDescription = back }) {
            Text("‹", style = MaterialTheme.typography.titleMedium, color = muted)
        }
        Text(stringResource(R.string.page_of, page + 1, count.coerceAtLeast(1)), style = MaterialTheme.typography.labelLarge.tabular(), color = muted)
        IconButton({ onStep(page + 1) }, enabled = count > 1 && page < count - 1, modifier = Modifier.semantics { contentDescription = next }) {
            Text("›", style = MaterialTheme.typography.titleMedium, color = muted)
        }
    }
}

/**
 * Pages of rows that fit a frame: rows in order, a new page whenever the next row would pass
 * [budget] or the page already holds [most]. A row taller than the frame still gets a page.
 */
internal fun pagesOf(heights: List<Int>, budget: Int, most: Int = 4): List<List<Int>> {
    val pages = mutableListOf<MutableList<Int>>()
    var used = 0
    heights.forEachIndexed { i, h ->
        val page = pages.lastOrNull()
        if (page == null || page.size >= most || used + h > budget) {
            pages += mutableListOf(i); used = h
        } else {
            page += i; used += h
        }
    }
    return pages
}
