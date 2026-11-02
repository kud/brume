package app.beansontoast.brume.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R

/**
 * Every function Brume has, in the order Erwann named them (2026-11-01). Each opens its own
 * screen from home; [SETTINGS] is reached by the quiet icon, not a row.
 */
enum class Fn(@StringRes val title: Int, @StringRes val line: Int) {
    SLEEP(R.string.scene_sleep, R.string.fn_sleep_line),
    SETTLE(R.string.scene_settle, R.string.fn_settle_line),
    BREATHE(R.string.scene_breathe, R.string.fn_breathe_line),
    MEDITATE(R.string.fn_meditate, R.string.fn_meditate_line),
    TONES(R.string.scene_tones, R.string.fn_tones_line),
    EAR(R.string.finder_title, R.string.fn_ear_line),
    JOURNAL(R.string.tab_journal, R.string.fn_journal_line),
    LEARN(R.string.tab_learn, R.string.fn_learn_line),
    SETTINGS(R.string.settings_title, R.string.settings_title),
}

/** The rows home shows: every function the modules (Settings › Activities) leave on. */
fun Modules.fns(): List<Fn> = Fn.entries.filter {
    when (it) { Fn.BREATHE -> breath; Fn.MEDITATE -> meditation; Fn.TONES -> tones; Fn.SETTINGS -> false; else -> true }
}

/** Whether [fn] can be open at all with these modules: Settings always can. */
fun Modules.allows(fn: Fn) = fn == Fn.SETTINGS || fn in fns()

/**
 * Home (Iris, 2026-11-01, phase 9 after Sacha's boards): the name large and centred over the sky
 * with a gentle line under it, then one translucent card per function (Sacha's symbol, its name,
 * what it is for, ›), the landscape showing between them, the sound playing under them. Fits
 * 360x780 without a scroll, at font scale 1.3 too: cards are 60 dp, 6 dp apart.
 */
@Composable
fun HomeScreen(fns: List<Fn>, onOpen: (Fn) -> Unit, mini: (@Composable () -> Unit)? = null) {
    val scheme = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        Landscape(Land.HOME)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displayMedium, textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() })
                    Text(stringResource(R.string.home_moment), style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center)
                }
                val settings = stringResource(R.string.settings_title)
                Box(Modifier.align(Alignment.TopEnd).size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClickLabel = settings) { onOpen(Fn.SETTINGS) }
                    .semantics { contentDescription = settings }, contentAlignment = Alignment.Center) {
                    Icon(painterResource(R.drawable.ic_tune), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                fns.forEach { fn -> FnCard(fn) { onOpen(fn) } }
            }
            if (mini != null) Box(Modifier.padding(top = 8.dp)) { mini() }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One function as a card of frosted mist: translucent ground, a pale hairline, the landscape faintly through it. */
@Composable
private fun FnCard(fn: Fn, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val night = duskTheme()
    val shape = RoundedCornerShape(18.dp)
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(shape)
        .background(scheme.surfaceContainerLowest.copy(alpha = if (night) 0.62f else 0.74f))
        .border(1.dp, if (night) scheme.outlineVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.7f), shape)
        .clickable(role = Role.Button, onClick = onClick).padding(start = 8.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        FnSymbol(fn, Modifier.size(44.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(stringResource(fn.title), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(stringResource(fn.line), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
        }
        Text("›", style = MaterialTheme.typography.titleLarge, color = scheme.onSurfaceVariant)
    }
}

/**
 * Each function's symbol: Sacha's illustrated disc (2026-11-01), day or night by the app's theme,
 * not the clock. The seam is the resource name, `symbol_<name>_{day,night}`: a new night art with
 * the same name drops in with no code change. Settings has no row, so no art of its own.
 */
@Composable
fun FnSymbol(fn: Fn, modifier: Modifier = Modifier) {
    val night = duskTheme()
    val (day, dusk) = when (fn) {
        Fn.SLEEP -> R.drawable.symbol_sleep_day to R.drawable.symbol_sleep_night
        Fn.SETTLE -> R.drawable.symbol_settle_day to R.drawable.symbol_settle_night
        Fn.BREATHE -> R.drawable.symbol_breathe_day to R.drawable.symbol_breathe_night
        Fn.MEDITATE -> R.drawable.symbol_meditate_day to R.drawable.symbol_meditate_night
        Fn.TONES -> R.drawable.symbol_tones_day to R.drawable.symbol_tones_night
        Fn.EAR -> R.drawable.symbol_finder_day to R.drawable.symbol_finder_night
        Fn.JOURNAL -> R.drawable.symbol_journal_day to R.drawable.symbol_journal_night
        Fn.LEARN, Fn.SETTINGS -> R.drawable.symbol_learn_day to R.drawable.symbol_learn_night
    }
    // Decoration: the card's name says what it is.
    Image(painterResource(if (night) dusk else day), contentDescription = null, contentScale = ContentScale.Crop,
        modifier = modifier.clip(CircleShape))
}
