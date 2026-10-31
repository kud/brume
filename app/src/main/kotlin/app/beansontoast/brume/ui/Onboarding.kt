package app.beansontoast.brume.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R

/*
 * The introduction (Iris, 2026-10-31). Five quiet pages in night mist, following the splash:
 * what Brume is, that everything stays on the phone, how to listen safely, when to see a doctor,
 * and, optionally, the pitch for the tone pattern. It plays no sound: the pitch is set in Tones,
 * where the level, the dock and Too loud already are, so nothing is heard before the safety page
 * has been read in the place where it applies.
 *
 *  - Skippable from every page but the last, which has its own two ways out; Back steps back.
 *  - Where you are is told in words ("2 of 5") and by the marks' size and fill, never by a hue.
 *  - Pages crossfade; with reduced motion (animator scale 0) they change at once.
 *  - The disclaimer, the privacy text and the red flags are About's own strings, so the two
 *    places can never say different things.
 */
const val INTRO_PAGES = 5

@Composable
fun Onboarding(onFinish: (setPitch: Boolean) -> Unit, startPage: Int = 0) {
    val context = LocalContext.current
    val still = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var page by rememberSaveable { mutableIntStateOf(startPage) }
    BackHandler(enabled = page > 0) { page-- }
    val last = page == INTRO_PAGES - 1

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding()) {
        Row(gutter.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            Steps(page, Modifier.weight(1f))
            if (!last) TextButton({ onFinish(false) }, Modifier.heightIn(min = 48.dp)) { Text(stringResource(R.string.intro_skip)) }
        }
        Crossfade(page, Modifier.weight(1f), animationSpec = if (still) snap() else tween(280), label = "intro") { p ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                when (p) {
                    0 -> Welcome()
                    1 -> Privacy()
                    2 -> Safely()
                    3 -> Doctor()
                    else -> Pitch()
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Row(gutter.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val tall = Modifier.weight(1f).heightIn(min = 52.dp)
            if (last) {
                OutlinedButton({ onFinish(false) }, tall) { Text(stringResource(R.string.intro_pitch_later)) }
                Button({ onFinish(true) }, tall) { Text(stringResource(R.string.intro_pitch_now)) }
            } else {
                if (page > 0) OutlinedButton({ page-- }, tall) { Text(stringResource(R.string.intro_back)) } else Spacer(Modifier.weight(1f))
                Button({ page++ }, tall) { Text(stringResource(R.string.intro_next)) }
            }
        }
    }
}

/** Five marks: the current one long and filled, those read short and filled, those ahead outlined. */
@Composable
private fun Steps(page: Int, modifier: Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val text = stringResource(R.string.intro_step, page + 1, INTRO_PAGES)
    Row(modifier.clearAndSetSemantics { contentDescription = text }, verticalAlignment = Alignment.CenterVertically) {
        repeat(INTRO_PAGES) { i ->
            val shape = RoundedCornerShape(50)
            val mark = Modifier.padding(end = 6.dp).height(6.dp).width(if (i == page) 22.dp else 6.dp)
            Box(
                when {
                    i == page -> mark.background(ink, shape)
                    i < page -> mark.background(muted, shape)
                    else -> mark.border(1.dp, muted, shape)
                },
            )
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = muted, modifier = Modifier.padding(start = 8.dp))
    }
}

/** The mist band every page opens on, its icon and title at the foot, like About's. */
@Composable
private fun Header(@StringRes title: Int, icon: Painter) {
    Box(Modifier.fillMaxWidth().height(148.dp)) {
        Mist(Modifier.fillMaxSize(), strength = 0.5f)
        Column(gutter.align(Alignment.BottomStart).padding(bottom = 4.dp)) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Text(stringResource(title), style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        }
    }
}

@Composable
private fun Body(@StringRes text: Int, modifier: Modifier = Modifier, muted: Boolean = false) =
    Text(stringResource(text), style = if (muted) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
        color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        modifier = gutter.then(modifier))

/** The splash's Horizon and the wordmark, centred in the mist, then what Brume is and is not. */
@Composable
private fun Welcome() {
    Box(Modifier.fillMaxWidth().height(260.dp)) {
        Mist(Modifier.fillMaxSize(), strength = 0.6f)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(R.drawable.ic_horizon), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(96.dp))
            Wordmark()
        }
    }
    Body(R.string.about_what, Modifier.padding(top = 8.dp))
    Body(R.string.about_disclaimer, Modifier.padding(top = 16.dp), muted = true)
}

@Composable
private fun Privacy() {
    Header(R.string.about_privacy_title, rememberVector(Icons.Filled.Lock))
    Body(R.string.about_privacy, Modifier.padding(top = 16.dp))
    Body(R.string.journal_export_body, Modifier.padding(top = 16.dp), muted = true)
}

@Composable
private fun Safely() {
    Header(R.string.intro_safe_title, painterResource(R.drawable.ic_horizon))
    Column(gutter.padding(top = 12.dp)) {
        Card {
            listOf(R.string.intro_safe_level, R.string.intro_safe_fade, R.string.intro_safe_too_loud, R.string.intro_safe_volume)
                .forEachIndexed { i, text ->
                    if (i > 0) HorizontalDivider(Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(stringResource(text), style = if (i == 0) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (i == 0) FontWeight.Medium else FontWeight.Normal)
                }
        }
    }
}

/** The red flags, calmly: About's list, then the one step worth taking anyway, then the support line. */
@Composable
private fun Doctor() {
    Header(R.string.doctor_title, rememberVector(Icons.Filled.Info))
    Body(R.string.intro_doctor_intro, Modifier.padding(top = 16.dp))
    Column(gutter.padding(top = 4.dp)) {
        listOf(R.string.doctor_1, R.string.doctor_2, R.string.doctor_3, R.string.doctor_4, R.string.doctor_5, R.string.doctor_6).forEach {
            Row(Modifier.padding(top = 6.dp)) {
                Text("–", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                Text(stringResource(it), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Card(Modifier.padding(top = 16.dp)) {
            Text(stringResource(R.string.intro_hearing_title), style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Text(stringResource(R.string.intro_hearing), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
    Body(R.string.support_line, Modifier.padding(top = 16.dp), muted = true)
}

@Composable
private fun Pitch() {
    Header(R.string.intro_pitch_title, painterResource(R.drawable.ic_tones))
    Body(R.string.intro_pitch_body, Modifier.padding(top = 16.dp))
    Body(R.string.intro_pitch_optional, Modifier.padding(top = 12.dp), muted = true)
}

@Composable
private fun rememberVector(vector: androidx.compose.ui.graphics.vector.ImageVector): Painter =
    androidx.compose.ui.graphics.vector.rememberVectorPainter(vector)
