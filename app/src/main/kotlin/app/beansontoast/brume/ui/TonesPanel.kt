package app.beansontoast.brume.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import app.beansontoast.design.tabular
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.TonePattern
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.design.SwitchRow
import kotlin.math.ln
import kotlin.math.exp
import kotlin.math.roundToInt

/** What the tone panel can ask of the player. Kept apart from [ListenActions] so a restyle moves it whole. */
class ToneActions(
    val pitch: (Int) -> Unit = {},
    val level: (Float) -> Unit = {},
    val trim: (tone: Int, trim: Float) -> Unit = { _, _ -> },
    val match: (Boolean) -> Unit = {},
)

private val logMin = ln(TonePattern.PITCH_MIN.toDouble())
private val logMax = ln(TonePattern.PITCH_MAX.toDouble())

/** A slider position 0..1 on a log scale, so each octave takes the same width. Rounded to 10 Hz. */
internal fun pitchAt(position: Float): Int =
    TonePattern.clampPitch((exp(logMin + (logMax - logMin) * position.coerceIn(0f, 1f)) / 10).roundToInt() * 10)

internal fun positionOf(pitch: Int): Float = ((ln(pitch.toDouble()) - logMin) / (logMax - logMin)).toFloat()

/**
 * The tone pattern's controls in night mist (Iris, 2026-10-31): what it is, then the pitch on its
 * own card with the value written large, the match tone, the level like any layer's, and the
 * four balance trims on one card. Values are always written; nothing rests on colour.
 */
@Composable
fun TonesPanel(ui: PlayerUi, actions: ToneActions) {
    val tones = ui.tones
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column {
        Text(stringResource(R.string.tones_note), style = MaterialTheme.typography.bodyMedium, color = muted,
            modifier = Modifier.padding(top = 16.dp))

        SectionTitle(stringResource(R.string.tones_pitch))
        Card {
            val pitchText = stringResource(R.string.tones_pitch_value, tones.pitch)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // ±1 %, about a sixth of a semitone: fine enough to settle a match the slider overshoots.
                Nudge("−", stringResource(R.string.tones_pitch_down)) { actions.pitch((tones.pitch * 0.99).roundToInt()) }
                Text(pitchText, style = MaterialTheme.typography.headlineSmall.tabular(), textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f))
                Nudge("+", stringResource(R.string.tones_pitch_up)) { actions.pitch((tones.pitch * 1.01).roundToInt()) }
            }
            val pitchLabel = stringResource(R.string.tones_pitch)
            Slider(
                value = positionOf(tones.pitch),
                onValueChange = { actions.pitch(pitchAt(it)) },
                colors = quietSlider(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics {
                    contentDescription = pitchLabel; stateDescription = pitchText
                },
            )
            Text(stringResource(R.string.tones_pitch_help), style = MaterialTheme.typography.bodySmall, color = muted)
            SwitchRow(stringResource(R.string.tones_match), stringResource(R.string.tones_match_summary), tones.match, actions.match)
        }

        SectionTitle(stringResource(R.string.listen_levels))
        LevelRow(stringResource(R.string.tones_level_label), null, ui.toneLevel, actions.level)

        SectionTitle(stringResource(R.string.tones_balance))
        Card {
            Text(stringResource(R.string.tones_balance_help), style = MaterialTheme.typography.bodySmall, color = muted)
            val hz = TonePattern.frequencies(tones.pitch)
            tones.trims.forEachIndexed { i, trim ->
                val percent = (trim * 100).roundToInt()
                val trimText = stringResource(R.string.tones_trim, i + 1, hz[i], percent)
                val trimLabel = stringResource(R.string.tones_trim_label, i + 1)
                Text(trimText, style = MaterialTheme.typography.bodyMedium.tabular(), modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = trim,
                    onValueChange = { actions.trim(i, it) },
                    steps = 19,
                    colors = quietSlider(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics {
                        contentDescription = trimLabel; stateDescription = trimText
                    },
                )
            }
        }
    }
}

/** A quiet round step button, 48 dp, its meaning in its label for TalkBack. */
@Composable
private fun Nudge(sign: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(sign, style = MaterialTheme.typography.titleLarge) }
}
