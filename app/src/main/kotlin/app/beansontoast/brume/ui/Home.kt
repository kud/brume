package app.beansontoast.brume.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R
import app.beansontoast.brume.core.home.Reason
import app.beansontoast.brume.core.home.Suggestion
import app.beansontoast.brume.core.learn.Learn
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.playback.PlayerUi
import kotlin.math.roundToInt

/*
 * The home tab (Iris, 2026-10-31; Erwann chose direction B with A's folded controls): « Ce soir »
 * by night, « Aujourd'hui » by day. One suggestion with its reason and one Play, the current sound
 * folded into a line that opens the controls, one Learn card a day, and effortful things kept for
 * « Demain matin ». Nothing here sets a level: the person's own levels play, through the fade-in.
 */

@Composable
fun HomeScreen(
    night: Boolean,
    ui: PlayerUi,
    suggestion: Suggestion,
    step: Int,
    actions: ListenActions,
    onPlay: () -> Unit,
    onOtherSound: () -> Unit,
    onCard: (id: String, step: Int?) -> Unit,
    modules: Modules = Modules(),
    tonesStep: Int = 1,
    onTones: () -> Unit = {},
) {
    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollCue(scroll).verticalScroll(scroll).then(gutter)) {
        ScreenTitle(if (night) R.string.tab_home_night else R.string.tab_home_day)
        Spacer(Modifier.height(16.dp))
        if (ui.playing) NowCard(ui, actions, onOtherSound)
        else SuggestionCard(night, ui, suggestion, onPlay, onOtherSound, onCard)

        val card = Learn.atStep(step)
        Spacer(Modifier.height(14.dp))
        Panel {
            Eyebrow(stringResource(R.string.home_learn_eyebrow, step, Learn.cards.size))
            Text(cardTitle(card), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
            card.evidence?.let { EvidenceMark(it, Modifier.padding(top = 8.dp)) }
            NavRow(stringResource(R.string.learn_read)) { onCard(card.id, step) }
        }

        // The pitch finder asks for attention: at night it waits for the morning, by day it is offered.
        if (modules.tones && tonesStep <= 2) {
            Spacer(Modifier.height(14.dp))
            Panel {
                Eyebrow(stringResource(if (night) R.string.home_tomorrow else R.string.tab_tones))
                Text(stringResource(if (night) R.string.home_tomorrow_pitch else R.string.home_day_pitch), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                if (!night) NavRow(stringResource(R.string.home_open_tones), onClick = onTones)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Panel(content: @Composable () -> Unit) = Column(
    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
) { content() }

@Composable
private fun Eyebrow(text: String) =
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun SuggestionCard(
    night: Boolean, ui: PlayerUi, suggestion: Suggestion, onPlay: () -> Unit, onOtherSound: () -> Unit, onCard: (String, Int?) -> Unit,
) {
    val context = LocalContext.current
    val name = listOfNotNull(suggestion.noise?.label(), suggestion.ambience?.label()).joinToString(" + ") { context.getString(it) }
    val headline = suggestion.timerMinutes?.let { stringResource(R.string.home_sound_timer, name, it) } ?: name
    Card {
        Eyebrow(stringResource(if (night) R.string.home_for_night else R.string.home_for_day))
        Text(headline, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 6.dp))
        Text(stringResource(suggestion.reason.text()), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        ui.status?.let {
            Text(stringResource(it.message()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        // The one Play: the disc and its words are one target.
        val play = stringResource(R.string.action_play)
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClickLabel = play, onClick = onPlay).semantics(mergeDescendants = true) { contentDescription = "$play, $headline" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.inverseSurface),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center) {
                Icon(painterResource(R.drawable.ic_play), null, tint = MaterialTheme.colorScheme.inverseOnSurface, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(play, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.home_play_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        NavRow(stringResource(R.string.home_other_sound), currentLine(ui), onClick = onOtherSound)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        NavRow(stringResource(R.string.home_why)) { onCard(suggestion.reason.card, null) }
    }
}

/** While a sound plays: what it is and the time left, Pause, Too loud, and the way to the controls. */
@Composable
private fun NowCard(ui: PlayerUi, actions: ListenActions, onOtherSound: () -> Unit) {
    val context = LocalContext.current
    Card {
        Text(stringResource(R.string.listen_state_playing, soundLabel(context, ui)), style = MaterialTheme.typography.titleLarge)
        ui.timerRemainingMinutes?.let {
            Text(stringResource(R.string.timer_left, it), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(top = 14.dp).fillMaxWidth()) {
            OutlinedButton(actions.toggle, Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(painterResource(R.drawable.ic_pause), null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_pause), modifier = Modifier.padding(start = 6.dp))
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(actions.tooLoud, Modifier.weight(1f).heightIn(min = 52.dp)) {
                Icon(painterResource(R.drawable.ic_too_loud), null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.action_too_loud), modifier = Modifier.padding(start = 6.dp))
            }
        }
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        NavRow(stringResource(R.string.home_other_sound), if (ui.mode == Mode.SOUNDS) currentLine(ui) else null, onClick = onOtherSound)
    }
}

/** The current comfort sound folded into one line: each layer with its level written. */
@Composable
private fun currentLine(ui: PlayerUi): String? {
    val context = LocalContext.current
    val parts = listOfNotNull(
        ui.noise?.let { context.getString(it.label()) + " · " + stringResource(R.string.ux_level_short, (ui.noiseLevel * 20).roundToInt()) },
        ui.ambience?.let { context.getString(it.label()) + " · " + stringResource(R.string.ux_level_short, (ui.ambienceLevel * 20).roundToInt()) },
    )
    return parts.joinToString(", ").ifEmpty { null }
}

private fun Reason.text() = when (this) {
    Reason.NIGHT_AS_LAST_TIME -> R.string.home_reason_night_last
    Reason.NIGHT_SOFT_SOUND -> R.string.home_reason_night_soft
    Reason.DAY_AS_LAST_TIME -> R.string.home_reason_day_last
    Reason.DAY_BLEND -> R.string.home_reason_day_blend
}
