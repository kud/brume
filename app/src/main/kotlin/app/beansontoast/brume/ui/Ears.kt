package app.beansontoast.brume.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Balance
import app.beansontoast.brume.playback.Output
import app.beansontoast.brume.playback.OutputKind
import app.beansontoast.design.tabular

/** "Centre", "Left 30%", "Right 100%": the words the row shows and TalkBack reads. */
@Composable
internal fun balanceText(balance: Float): String {
    val b = Balance.snap(balance)
    return when {
        b < 0f -> stringResource(R.string.balance_left, Balance.percent(b))
        b > 0f -> stringResource(R.string.balance_right, Balance.percent(b))
        else -> stringResource(R.string.balance_centre)
    }
}

@Composable
internal fun outputText(output: Output): String = output.name ?: stringResource(
    when (output.kind) {
        OutputKind.SPEAKER -> R.string.output_speaker
        OutputKind.WIRED -> R.string.output_wired
        OutputKind.BLUETOOTH -> R.string.output_bluetooth
        OutputKind.HEARING_AID -> R.string.output_hearing_aid
        OutputKind.USB -> R.string.output_usb
        OutputKind.OTHER -> R.string.output_other
    },
)

/**
 * The ears, at the foot of the "now playing" card, so one setting serves Listen and Tones alike:
 * where the sound plays, with the system's switcher one tap away, and the left/right balance.
 * The balance slider stops in tenths with one at the centre; its value is written, never a colour.
 */
@Composable
internal fun EarsRows(balance: Float, output: Output?, onBalance: (Float) -> Unit, onOutput: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    HorizontalDivider(Modifier.padding(top = 10.dp, bottom = 4.dp))
    if (output != null) {
        val label = stringResource(R.string.output_change_label)
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.output_on, outputText(output)), style = MaterialTheme.typography.bodyMedium,
                color = muted, maxLines = 1, modifier = Modifier.weight(1f))
            TextButton(onOutput, Modifier.heightIn(min = 48.dp).semantics { contentDescription = label }) {
                Text(stringResource(R.string.output_change))
            }
        }
    }
    val words = balanceText(balance)
    val title = stringResource(R.string.balance_title)
    val resetLabel = stringResource(R.string.balance_reset_label)
    Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = muted,
            modifier = Modifier.padding(end = 8.dp).semantics { heading() })
        Text(words, style = MaterialTheme.typography.bodyLarge.tabular(), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        TextButton({ onBalance(Balance.CENTRE) }, enabled = Balance.snap(balance) != Balance.CENTRE,
            modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = resetLabel }) {
            Text(stringResource(R.string.balance_reset))
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.balance_l), style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(end = 8.dp).clearAndSetSemantics {})
        Slider(
            value = Balance.snap(balance), onValueChange = { onBalance(Balance.snap(it)) },
            valueRange = -1f..1f, steps = Balance.STOPS - 1,
            modifier = Modifier.weight(1f).heightIn(min = 44.dp).semantics { contentDescription = title; stateDescription = words },
        )
        Text(stringResource(R.string.balance_r), style = MaterialTheme.typography.labelLarge, color = muted, modifier = Modifier.padding(start = 8.dp).clearAndSetSemantics {})
    }
    Text(stringResource(R.string.balance_hint), style = MaterialTheme.typography.bodySmall, color = muted)
}
