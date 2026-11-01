package app.beansontoast.brume.ui

import androidx.annotation.StringRes
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.R
import app.beansontoast.brume.core.finder.Character
import app.beansontoast.brume.core.finder.ComfortBand
import app.beansontoast.brume.core.finder.Ear
import app.beansontoast.brume.core.finder.OctaveCheck
import app.beansontoast.brume.core.finder.PitchLadder
import app.beansontoast.brume.core.finder.PitchMatch
import app.beansontoast.brume.core.finder.Probe
import app.beansontoast.brume.core.finder.Rhythm
import app.beansontoast.brume.core.finder.Side
import app.beansontoast.brume.core.finder.Signpost
import app.beansontoast.brume.core.finder.TinnitusProfile
import app.beansontoast.design.Motion
import app.beansontoast.design.SelectChip
import app.beansontoast.design.tabular
import java.time.LocalDate
import java.util.UUID
import kotlin.math.roundToInt

/** What « Mon acouphène » can ask of the app. */
class FinderActions(
    val probe: (Probe?) -> Unit = {},
    val tooLoud: () -> Unit = {},
    val save: (TinnitusProfile) -> Unit = {},
    val delete: (String) -> Unit = {},
    val comfort: (ComfortBand) -> Unit = {},
)

/**
 * The journey's steps, each one idea on a fixed screen (no free scroll: Erwann dislikes scrolling).
 * A first search walks all of them; a repeat on a saved profile walks only the search.
 */
enum class FinderStep(val sound: Boolean = false) {
    WELCOME, CONDITIONS, EAR, LEVEL(true), SOUND(true), COARSE(true), FINE(true), OCTAVE(true), DESCRIBE, RATE, SAFETY, NAME;

    companion object {
        val first = entries.toList()
        val repeat = listOf(CONDITIONS, EAR, LEVEL, SOUND, COARSE, FINE, OCTAVE)
    }
}

/** Everything the journey has gathered so far: one draft, saved only at the end. */
data class FinderDraft(
    val ear: Ear = Ear.BOTH,
    val level: Float = 0f,
    val sound: Character = Character.TONE,
    val coarse: Int = 4000,
    val hz: Int = 4000,
    val searched: Boolean = false,
    val octave: OctaveCheck.Choice? = null,
    /** The candidate being heard during the octave check. */
    val listening: Int? = null,
    val side: Side? = null,
    val rhythm: Rhythm? = null,
    val loudness: Int? = null,
    val bother: Int? = null,
    val name: String = "",
)

/** Where the screen opens: the list, a step of a journey, or a saved profile. For renders and entry points. */
sealed interface FinderPlace {
    data object Home : FinderPlace
    data class Journey(val step: FinderStep, val draft: FinderDraft = FinderDraft(), val profileId: String? = null) : FinderPlace
    data class Profile(val id: String) : FinderPlace
}

@Composable
fun FinderScreen(
    profiles: List<TinnitusProfile>,
    actions: FinderActions,
    onBack: () -> Unit,
    start: FinderPlace = FinderPlace.Home,
) {
    var place by remember { mutableStateOf(start) }
    // The search plays only on its own steps; leaving one goes quiet.
    val journey = place as? FinderPlace.Journey
    LaunchedEffect(journey?.step, journey?.draft) {
        val d = journey?.draft
        actions.probe(if (journey == null || d == null || !journey.step.sound) null else Probe(d.listening ?: d.hz, d.sound, d.ear, d.level))
    }
    Crossfade(place, Modifier.fillMaxSize(), animationSpec = Motion.tween(Motion.CALM), label = "finder") { p ->
        when (p) {
            FinderPlace.Home -> FinderHome(profiles, onBack = onBack,
                onStart = { place = FinderPlace.Journey(FinderStep.WELCOME) },
                onProfile = { place = FinderPlace.Profile(it) })
            is FinderPlace.Journey -> Journey(p, profiles, actions,
                onPlace = { place = it },
                onLeave = { place = if (p.profileId != null) FinderPlace.Profile(p.profileId) else FinderPlace.Home })
            is FinderPlace.Profile -> {
                val profile = profiles.firstOrNull { it.id == p.id }
                if (profile == null) LaunchedEffect(p) { place = FinderPlace.Home }
                else ProfileView(profile, actions,
                    onBack = { place = FinderPlace.Home },
                    onRepeat = {
                        val near = PitchLadder.nearestCoarse(profile.range?.centre ?: 4000)
                        place = FinderPlace.Journey(FinderStep.CONDITIONS,
                            FinderDraft(sound = profile.character ?: Character.TONE, coarse = near, hz = near), profile.id)
                    })
            }
        }
    }
}

@Composable
private fun FinderHome(profiles: List<TinnitusProfile>, onBack: () -> Unit, onStart: () -> Unit, onProfile: (String) -> Unit) {
    Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
        ScreenLead(stringResource(R.string.finder_home_intro))
        Spacer(Modifier.height(12.dp))
        if (profiles.isEmpty()) Body(stringResource(R.string.finder_home_empty))
        profiles.take(MAX_LISTED).forEach { p ->
            NavRow(p.name.ifBlank { stringResource(R.string.finder_unnamed) }, rangeText(p), onClick = { onProfile(p.id) })
        }
        Spacer(Modifier.weight(1f))
        Button(onStart, Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(if (profiles.isEmpty()) R.string.finder_start else R.string.finder_start_another))
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun Journey(
    p: FinderPlace.Journey,
    profiles: List<TinnitusProfile>,
    actions: FinderActions,
    onPlace: (FinderPlace) -> Unit,
    onLeave: () -> Unit,
) {
    val steps = if (p.profileId != null) FinderStep.repeat else FinderStep.first
    val index = steps.indexOf(p.step)
    val d = p.draft
    fun go(draft: FinderDraft = d) = onPlace(p.copy(draft = draft))
    fun finish(draft: FinderDraft) {
        val match = if (draft.searched) PitchMatch(UUID.randomUUID().toString(), LocalDate.now().toString(),
            draft.hz, draft.sound, draft.octave != null) else null
        val existing = profiles.firstOrNull { it.id == p.profileId }
        val saved = existing?.copy(matches = existing.matches + listOfNotNull(match))
            ?: TinnitusProfile(
                id = UUID.randomUUID().toString(),
                name = draft.name.trim(),
                character = draft.sound.takeIf { draft.searched },
                side = draft.side, rhythm = draft.rhythm, loudness = draft.loudness, bother = draft.bother,
                matches = listOfNotNull(match),
            )
        actions.save(saved)
        onPlace(FinderPlace.Profile(saved.id))
    }
    fun next(draft: FinderDraft = d) {
        // Leaving the fine dial or the octave check settles the pitch they chose.
        val settled = when (p.step) {
            FinderStep.COARSE -> draft.copy(hz = draft.coarse, searched = true, listening = null)
            FinderStep.OCTAVE -> draft.copy(hz = draft.octave?.let { OctaveCheck.resolve(draft.hz, it) } ?: draft.hz, listening = null)
            else -> draft.copy(listening = null)
        }
        if (index == steps.lastIndex) finish(settled) else onPlace(p.copy(step = steps[index + 1], draft = settled))
    }
    fun back() = if (index == 0) onLeave() else onPlace(p.copy(step = steps[index - 1], draft = d.copy(listening = null)))

    Column(Modifier.fillMaxSize().then(gutter).underChrome()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box48(stringResource(R.string.finder_back)) { back() }
            Text(stringResource(R.string.finder_step, index + 1, steps.size), style = MaterialTheme.typography.labelLarge.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onLeave) { Text(stringResource(R.string.finder_close)) }
        }
        Text(stringResource(p.step.title()), style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 8.dp).semantics { heading() })
        Column(Modifier.weight(1f).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            StepBody(p.step, d, profiles, ::go)
        }
        // Too loud keeps its room on every step, shown only while the search can sound.
        val sounding = p.step.sound && d.level > 0f
        TextButton(
            onClick = { actions.tooLoud(); go(d.copy(level = 0f)) },
            enabled = sounding,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).graphicsAlpha(app.beansontoast.design.calmAlpha(sounding)),
        ) { Text(stringResource(R.string.finder_too_loud)) }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { next(skipped(p.step, d)) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(R.string.finder_skip))
            }
            Button(onClick = { next() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(if (index == steps.lastIndex) R.string.finder_save else R.string.finder_continue))
            }
        }
    }
}

/** Passer leaves the step's choice unset: the search keeps no pitch from a skipped step, the octave check stays undone. */
private fun skipped(step: FinderStep, d: FinderDraft) = when (step) {
    FinderStep.COARSE -> d.copy(searched = false)
    FinderStep.OCTAVE -> d.copy(octave = null)
    FinderStep.DESCRIBE -> d.copy(side = null, rhythm = null)
    FinderStep.RATE -> d.copy(loudness = null, bother = null)
    FinderStep.NAME -> d.copy(name = "")
    else -> d
}

@StringRes private fun FinderStep.title() = when (this) {
    FinderStep.WELCOME -> R.string.finder_welcome_title
    FinderStep.CONDITIONS -> R.string.finder_conditions_title
    FinderStep.EAR -> R.string.finder_ear_title
    FinderStep.LEVEL -> R.string.finder_level_title
    FinderStep.SOUND -> R.string.finder_sound_title
    FinderStep.COARSE -> R.string.finder_coarse_title
    FinderStep.FINE -> R.string.finder_fine_title
    FinderStep.OCTAVE -> R.string.finder_octave_title
    FinderStep.DESCRIBE -> R.string.finder_describe_title
    FinderStep.RATE -> R.string.finder_rate_title
    FinderStep.SAFETY -> R.string.finder_safety_title
    FinderStep.NAME -> R.string.finder_name_title
}

@Composable
private fun StepBody(step: FinderStep, d: FinderDraft, profiles: List<TinnitusProfile>, go: (FinderDraft) -> Unit) {
    when (step) {
        FinderStep.WELCOME -> {
            Body(stringResource(R.string.finder_welcome_1))
            Body(stringResource(R.string.finder_welcome_2))
            Muted(stringResource(R.string.finder_welcome_3))
        }
        FinderStep.CONDITIONS -> listOf(R.string.finder_cond_room, R.string.finder_cond_headphones, R.string.finder_cond_ear,
            R.string.finder_cond_silence, R.string.finder_cond_loud).forEach { Bullet(stringResource(it)) }
            .also { Muted(stringResource(R.string.finder_cond_bluetooth)) }
        FinderStep.EAR -> {
            Body(stringResource(R.string.finder_ear_body))
            Chips(listOf(Ear.LEFT to R.string.finder_ear_left, Ear.RIGHT to R.string.finder_ear_right, Ear.BOTH to R.string.finder_ear_both), d.ear) { go(d.copy(ear = it)) }
        }
        FinderStep.LEVEL -> {
            Body(stringResource(R.string.finder_level_body))
            WordSlider(stringResource(R.string.finder_level_label), d.level, levelWord(d.level)) { go(d.copy(level = it)) }
            Muted(stringResource(R.string.finder_level_help))
        }
        FinderStep.SOUND -> {
            Body(stringResource(R.string.finder_sound_body))
            Chips(listOf(Character.TONE to R.string.finder_sound_tone, Character.HISS to R.string.finder_sound_hiss), d.sound) { go(d.copy(sound = it)) }
            Muted(stringResource(R.string.finder_sound_help))
        }
        FinderStep.COARSE -> {
            Body(stringResource(R.string.finder_coarse_body))
            PitchLadder.coarse.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { hz -> SelectChip(stringResource(R.string.finder_hz, hz), hz == d.coarse,
                        { go(d.copy(coarse = hz, hz = hz)) }, Modifier.weight(1f)) }
                }
            }
            Muted(stringResource(R.string.finder_coarse_help))
        }
        FinderStep.FINE -> {
            Body(stringResource(R.string.finder_fine_body))
            val range = PitchLadder.fineRange(d.coarse)
            val text = stringResource(R.string.finder_hz, d.hz)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box48(stringResource(R.string.tones_pitch_down), plus = false) { go(d.copy(hz = PitchLadder.fine(d.hz, -1).coerceAtLeast(range.first))) }
                Text(text, style = MaterialTheme.typography.displaySmall.tabular(), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f))
                Box48(stringResource(R.string.tones_pitch_up), plus = true) { go(d.copy(hz = PitchLadder.fine(d.hz, 1).coerceAtMost(range.last))) }
            }
            val label = stringResource(R.string.finder_fine_label)
            Slider(
                value = PitchLadder.position(d.hz, range.first, range.last),
                onValueChange = { go(d.copy(hz = PitchLadder.at(it, range.first, range.last))) },
                colors = quietSlider(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label; stateDescription = text },
            )
            Row {
                Muted(stringResource(R.string.finder_lower), Modifier.weight(1f))
                Muted(stringResource(R.string.finder_higher))
            }
            Muted(stringResource(R.string.finder_fine_help))
        }
        FinderStep.OCTAVE -> {
            Body(stringResource(R.string.finder_octave_body))
            OctaveCheck.choices(d.hz).forEach { (choice, hz) ->
                val word = when (choice) {
                    OctaveCheck.Choice.LOWER -> R.string.finder_octave_lower
                    OctaveCheck.Choice.SAME -> R.string.finder_octave_same
                    OctaveCheck.Choice.HIGHER -> R.string.finder_octave_higher
                }
                SelectChip(stringResource(word, hz), d.octave == choice, { go(d.copy(octave = choice, listening = hz)) }, Modifier.fillMaxWidth())
            }
            Muted(stringResource(R.string.finder_octave_help))
        }
        FinderStep.DESCRIBE -> {
            Muted(stringResource(R.string.finder_describe_where))
            Chips(listOf(Side.LEFT to R.string.finder_side_left, Side.RIGHT to R.string.finder_side_right,
                Side.BOTH to R.string.finder_side_both, Side.HEAD to R.string.finder_side_head), d.side, columns = 2) { go(d.copy(side = it)) }
            Muted(stringResource(R.string.finder_describe_rhythm))
            Chips(listOf(Rhythm.STEADY to R.string.finder_rhythm_steady, Rhythm.PULSING to R.string.finder_rhythm_pulsing,
                Rhythm.HEARTBEAT to R.string.finder_rhythm_heartbeat), d.rhythm, columns = 1) { go(d.copy(rhythm = it)) }
        }
        FinderStep.RATE -> {
            Body(stringResource(R.string.finder_rate_body))
            RatingSlider(stringResource(R.string.finder_rate_loudness), d.loudness) { go(d.copy(loudness = it)) }
            RatingSlider(stringResource(R.string.finder_rate_bother), d.bother) { go(d.copy(bother = it)) }
        }
        FinderStep.SAFETY -> SafetyNote(Signpost.stressed(d.side, d.rhythm))
        FinderStep.NAME -> {
            Body(stringResource(R.string.finder_name_body))
            val suggestion = stringResource(R.string.finder_name_default, profiles.size + 1)
            OutlinedTextField(d.name, { go(d.copy(name = it.take(40))) }, singleLine = true,
                placeholder = { Text(suggestion) }, label = { Text(stringResource(R.string.finder_name_label)) },
                modifier = Modifier.fillMaxWidth())
            Muted(stringResource(R.string.finder_name_help))
            LaunchedEffect(Unit) { if (d.name.isEmpty()) go(d.copy(name = suggestion)) }
        }
    }
}

/** The signpost, calm: when to see a doctor, and the same day for sudden hearing loss (NICE NG155). */
@Composable
internal fun SafetyNote(stressed: Boolean) {
    Body(stringResource(R.string.finder_safety_body))
    listOf(R.string.finder_safety_heartbeat, R.string.finder_safety_one_ear, R.string.finder_safety_sudden).forEach { Bullet(stringResource(it)) }
    Body(stringResource(R.string.finder_safety_same_day), weight = FontWeight.Medium)
    // The line about their own description keeps its room either way.
    Text(stringResource(R.string.finder_safety_yours), style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.graphicsAlpha(if (stressed) 1f else 0f))
}

@Composable
private fun ProfileView(profile: TinnitusProfile, actions: FinderActions, onBack: () -> Unit, onRepeat: () -> Unit) {
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().then(gutter).underChrome(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        InnerBack(onBack)
        Text(profile.name.ifBlank { stringResource(R.string.finder_unnamed) }, style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() })
        Card {
            Text(rangeText(profile), style = MaterialTheme.typography.titleMedium.tabular())
            profile.matches.takeLast(MAX_MATCHES).reversed().forEach { m ->
                Muted(stringResource(R.string.finder_match_row, m.day, m.hz,
                    stringResource(if (m.sound == Character.TONE) R.string.finder_sound_tone else R.string.finder_sound_hiss)))
            }
            Muted(stringResource(R.string.finder_profile_vary), Modifier.padding(top = 4.dp))
        }
        describeText(profile)?.let { Body(it) }
        Spacer(Modifier.weight(1f))
        Muted(stringResource(R.string.finder_comfort_body))
        val band = profile.comfort
        Button({ band?.let(actions.comfort) }, enabled = band != null, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
            Text(stringResource(R.string.finder_comfort))
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onRepeat, Modifier.weight(1f).heightIn(min = 56.dp)) { Text(stringResource(R.string.finder_repeat)) }
            TextButton({ if (confirm) actions.delete(profile.id) else confirm = true }, Modifier.weight(1f).heightIn(min = 56.dp)) {
                Text(stringResource(if (confirm) R.string.finder_delete_confirm else R.string.finder_delete))
            }
        }
    }
}

@Composable
private fun rangeText(p: TinnitusProfile): String {
    val r = p.range ?: return stringResource(R.string.finder_range_none)
    return if (r.single) stringResource(R.string.finder_range_one, r.low, r.count)
    else stringResource(R.string.finder_range, r.low, r.high, r.count)
}

@Composable
private fun describeText(p: TinnitusProfile): String? {
    val parts = listOfNotNull(
        p.character?.let { stringResource(if (it == Character.TONE) R.string.finder_sound_tone else R.string.finder_sound_hiss) },
        p.side?.let { stringResource(when (it) { Side.LEFT -> R.string.finder_side_left; Side.RIGHT -> R.string.finder_side_right
            Side.BOTH -> R.string.finder_side_both; Side.HEAD -> R.string.finder_side_head }) },
        p.rhythm?.let { stringResource(when (it) { Rhythm.STEADY -> R.string.finder_rhythm_steady; Rhythm.PULSING -> R.string.finder_rhythm_pulsing
            Rhythm.HEARTBEAT -> R.string.finder_rhythm_heartbeat }) },
        p.loudness?.let { stringResource(R.string.finder_rate_loudness) + " : " + stringResource(ratingWord(it)) },
        p.bother?.let { stringResource(R.string.finder_rate_bother) + " : " + stringResource(ratingWord(it)) },
    )
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

@StringRes private fun ratingWord(v: Int) = when {
    v <= 0 -> R.string.finder_rating_0
    v <= 3 -> R.string.finder_rating_1
    v <= 6 -> R.string.finder_rating_2
    v <= 9 -> R.string.finder_rating_3
    else -> R.string.finder_rating_4
}

@Composable
private fun levelWord(level: Float): String = stringResource(when {
    level <= 0f -> R.string.finder_level_silence
    level < 0.25f -> R.string.volume_0
    level < 0.5f -> R.string.volume_1
    level < 0.75f -> R.string.volume_2
    else -> R.string.volume_3
})

@Composable
private fun WordSlider(label: String, value: Float, word: String, onValue: (Float) -> Unit) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(word, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Slider(value, onValue, steps = 19, colors = quietSlider(),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).semantics { contentDescription = label; stateDescription = word })
}

/** 0..10 in words; untouched it reads « not said », never a number the person did not give. */
@Composable
private fun RatingSlider(label: String, value: Int?, onValue: (Int) -> Unit) {
    val word = value?.let { stringResource(ratingWord(it)) } ?: stringResource(R.string.finder_rating_unset)
    WordSlider(label, (value ?: 0) / 10f, word) { onValue((it * 10).roundToInt()) }
}

@Composable
private fun <T> Chips(options: List<Pair<T, Int>>, chosen: T?, columns: Int = 3, onPick: (T) -> Unit) {
    options.chunked(columns).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { (value, label) -> SelectChip(stringResource(label), value == chosen, { onPick(value) }, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun Body(text: String, weight: FontWeight? = null) = Text(text, style = MaterialTheme.typography.bodyLarge, fontWeight = weight)

@Composable
private fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)

@Composable
private fun Bullet(text: String) = Row {
    Text("–", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(18.dp))
    Text(text, style = MaterialTheme.typography.bodyLarge)
}

/** A quiet 48 dp target: a back chevron, or a minus / plus for the fine dial. */
@Composable
private fun Box48(label: String, plus: Boolean? = null, onClick: () -> Unit) {
    val glyph = when (plus) { null -> "‹"; true -> "+"; false -> "−" }
    androidx.compose.foundation.layout.Box(
        Modifier.heightIn(min = 48.dp).width(48.dp).clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Text(glyph, style = MaterialTheme.typography.titleLarge) }
}

private fun Modifier.graphicsAlpha(alpha: Float) = this.alpha(alpha)

private const val MAX_LISTED = 4
private const val MAX_MATCHES = 3
