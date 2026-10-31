package app.beansontoast.brume.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.beansontoast.brume.Modules
import app.beansontoast.brume.R
import app.beansontoast.brume.core.sound.Ambience
import app.beansontoast.brume.core.sound.LevelRules
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.SoundEngine.Mode
import app.beansontoast.brume.core.sound.Volume
import app.beansontoast.brume.playback.PlayerUi
import app.beansontoast.brume.ui.launch.LocalLaunchAnchors
import app.beansontoast.brume.ui.launch.MistAnchor
import app.beansontoast.design.tabular
import java.util.Locale

/*
 * The scene (Erwann chose « A avec les scènes de C », 2026-10-31; Iris built it the same day).
 * Listen is one now-playing screen: full-bleed mist, what plays in a large serif, one disc. The
 * scenes Sleep, Settle, Breathe and Tones lie side by side and change with a sideways swipe, or a
 * tap on their name. Breathe and Tones are scenes, not top-level pages, so nothing is there twice.
 *  - Nothing chosen for him: Brume reopens on the scene last used; never one picked by the clock.
 *  - Volume is a slide up or down anywhere on the page, told in words, never a number.
 *  - Too loud is always on screen.
 *  - Sacha's art drops into [art] and [thumb]; until then the mist stands in.
 */

enum class Scene(@StringRes val title: Int, val sounds: Boolean) {
    SLEEP(R.string.scene_sleep, true),
    SETTLE(R.string.scene_settle, true),
    BREATHE(R.string.scene_breathe, false),
    TONES(R.string.scene_tones, false),
}

/** The scenes the modules leave on: Breathe while Breathe or Meditation is on, Tones while Tones is. */
fun Modules.scenes(): List<Scene> = Scene.entries.filter {
    when (it) { Scene.BREATHE -> calm; Scene.TONES -> tones; else -> true }
}

/**
 * Sacha's scene art: one image per scene, drawn full-bleed behind the page (cropped from the centre,
 * dimmed at night). Null until his images land; the mist stands in.
 */
@DrawableRes internal fun Scene.art(): Int? = null

/** Sacha's sound thumbnails: a square image per sound, in the pills and the tiles. Null until then. */
@DrawableRes internal fun NoiseColour.thumb(): Int? = null
@DrawableRes internal fun Ambience.thumb(): Int? = null

/** The mix a sound scene remembers: its noise, its nature sound, its timer. Levels stay per headphones. */
data class SceneMix(val noise: NoiseColour?, val ambience: Ambience?, val timer: Int?) {
    fun encode() = listOf(noise?.name ?: NONE, ambience?.name ?: NONE, timer?.toString() ?: NONE).joinToString("|")

    companion object {
        private const val NONE = "-"
        fun of(ui: PlayerUi) = SceneMix(ui.noise, ui.ambience, ui.timerMinutes)
        fun decode(text: String?): SceneMix? {
            val p = text?.split('|')?.takeIf { it.size == 3 } ?: return null
            return SceneMix(NoiseColour.entries.firstOrNull { it.name == p[0] }, Ambience.entries.firstOrNull { it.name == p[1] }, p[2].toIntOrNull())
        }
    }
}

/** What the Breathe scene needs: the practices left on, the one chosen, its length and the sound under it. */
class BreatheScene(
    val practices: List<Practice> = Practice.entries,
    val practice: Practice = Practice.BREATHE,
    val onPractice: (Practice) -> Unit = {},
    val minutes: Int = 5,
    val soundName: String? = null,
    val onStart: () -> Unit = {},
    val onSetup: () -> Unit = {},
)

/**
 * The Listen page. [peek] offsets the pager by a fraction of a page, for the swipe renders only.
 */
@Composable
fun SceneScreen(
    scenes: List<Scene>,
    scene: Scene,
    onScene: (Scene) -> Unit,
    ui: PlayerUi,
    actions: ListenActions,
    onSounds: () -> Unit = {},
    onTonesSetup: () -> Unit = {},
    breathe: BreatheScene = BreatheScene(),
    night: Boolean = false,
    peek: Float = 0f,
) {
    val scheme = MaterialTheme.colorScheme
    val start = scenes.indexOf(scene).coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = start, initialPageOffsetFraction = peek) { scenes.size }
    val current by rememberUpdatedState(scene)
    val change by rememberUpdatedState(onScene)
    // A swipe that settles on another scene makes it the scene; a tap on a name scrolls there.
    LaunchedEffect(pager, scenes) {
        snapshotFlow { pager.settledPage }.collect { i -> scenes.getOrNull(i)?.let { if (it != current) change(it) } }
    }
    LaunchedEffect(scene, scenes) {
        val i = scenes.indexOf(scene)
        if (i >= 0 && i != pager.currentPage && peek == 0f) pager.animateScrollToPage(i)
    }
    val shown = scenes.getOrElse(pager.currentPage) { scene }

    Box(Modifier.fillMaxSize()) {
        // The launch intro lands Horizon's lines on this mist (ui/launch), as it did on the old dock.
        val anchors = LocalLaunchAnchors.current
        val strength = if (ui.playing) 1f else 0.35f
        val mistInk = scheme.onSurfaceVariant
        Mist(Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.BottomCenter)
            .onGloballyPositioned { anchors?.mist = MistAnchor(it.boundsInWindow(), strength, mistInk) }, strength = strength, color = mistInk)
        Column(Modifier.fillMaxSize()) {
            SceneNames(scenes, shown) { onScene(it) }
            Text(stringResource(R.string.scene_swipe), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp))
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { scenes[it].name }) { page ->
                ScenePage(scenes[page], ui, actions, onSounds, onTonesSetup, breathe, night)
            }
            val (view, mine) = forMode(ui, actions, if (shown == Scene.TONES) Mode.TONES else Mode.SOUNDS)
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (shown != Scene.BREATHE) TimerWord(view, mine.timer) else Spacer(Modifier.height(48.dp))
                Spacer(Modifier.weight(1f))
                TooLoudWord(actions.tooLoud)
            }
        }
    }
}

/** The scenes by name, in the serif: the one you are on larger and heavier, the others there to tap. */
@Composable
private fun SceneNames(scenes: List<Scene>, shown: Scene, onScene: (Scene) -> Unit) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Keep the shown name in view as the swipe moves on.
    LaunchedEffect(shown) { scroll.animateScrollTo((scenes.indexOf(shown) * with(density) { 96.dp.toPx() }).toInt().coerceAtMost(scroll.maxValue)) }
    Row(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = 12.dp).selectableGroup(),
        verticalAlignment = Alignment.Bottom) {
        scenes.forEach { s ->
            val on = s == shown
            Text(stringResource(s.title),
                style = if (on) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (on) FontWeight.Normal else FontWeight.Light, maxLines = 1,
                modifier = Modifier.heightIn(min = 48.dp).selectable(on, role = Role.Tab) { onScene(s) }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

@Composable
private fun ScenePage(
    scene: Scene, all: PlayerUi, allActions: ListenActions,
    onSounds: () -> Unit, onTonesSetup: () -> Unit, breathe: BreatheScene, night: Boolean,
) {
    val (ui, actions) = forMode(all, allActions, if (scene == Scene.TONES) Mode.TONES else Mode.SOUNDS)
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The layers the slide moves, each with its own setter: the tone pattern alone, or noise and nature.
    val layers: List<Pair<Float, (Float) -> Unit>> = when (scene) {
        Scene.TONES -> listOf(ui.toneLevel to actions.tones.level)
        else -> listOfNotNull(ui.noise?.let { ui.noiseLevel to actions.noiseLevel }, ui.ambience?.let { ui.ambienceLevel to actions.ambienceLevel })
    }
    val levels = layers.map { it.first }
    val set: (List<Float>) -> Unit = { new -> layers.forEachIndexed { i, layer -> new.getOrNull(i)?.let(layer.second) } }
    val word = stringResource(volumeWord(Volume.band(levels)))
    val title: String
    val line: String
    when (scene) {
        Scene.TONES -> {
            title = stringResource(R.string.ux_tone_kind)
            line = listOf(stringResource(R.string.tones_pitch_value, ui.tones.pitch), stateWord(ui), word).joinToString(" · ")
        }
        Scene.BREATHE -> {
            title = stringResource(breathe.practice.label())
            line = breathe.soundName?.let { stringResource(R.string.scene_minutes_with, breathe.minutes, it) }
                ?: stringResource(R.string.scene_minutes, breathe.minutes)
        }
        else -> {
            title = mixName(context, ui) ?: stringResource(R.string.scene_no_sound)
            line = if (!ui.hasSound) stringResource(R.string.scene_no_sound_line) else listOf(stateWord(ui), word).joinToString(" · ")
        }
    }
    Box(Modifier.fillMaxSize()) {
        scene.art()?.let {
            Image(painterResource(it), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = if (night) 0.35f else 0.7f)
        }
        Column(
            Modifier.fillMaxSize().volumeSlide(levels, set).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            Text(title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().semantics { heading() })
            Text(line, style = MaterialTheme.typography.bodyLarge.tabular(), color = muted, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f).height(1.dp))
                Spacer(Modifier.width(56.dp))
                if (scene == Scene.BREATHE) Disc(ui.copy(playing = false), ring = null, word = R.string.scene_start, onClick = breathe.onStart, always = true)
                else Disc(ui, ring(ui), word = null, onClick = if (ui.hasSound) actions.toggle else onSounds)
                VolumeTrack(levels, set, word, Modifier.width(56.dp))
                Spacer(Modifier.weight(1f).height(1.dp))
            }
            if (levels.isNotEmpty()) Text(stringResource(R.string.scene_volume_hint), style = MaterialTheme.typography.bodySmall, color = muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
            Spacer(Modifier.weight(1f))
            when (scene) {
                Scene.TONES -> QuietMore(stringResource(R.string.scene_tones_setup), onTonesSetup)
                Scene.BREATHE -> {
                    if (breathe.practices.size > 1) Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        breathe.practices.forEach { p ->
                            Pill(stringResource(p.label()), on = p == breathe.practice, thumb = null, role = Role.RadioButton,
                                label = null) { breathe.onPractice(p) }
                        }
                    }
                    QuietMore(stringResource(R.string.scene_breathe_setup), breathe.onSetup)
                }
                else -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically) {
                    ui.noise?.let { n -> val name = stringResource(n.label())
                        Pill(name, on = true, thumb = { texture(n, null, it) }, label = stringResource(R.string.scene_in_mix, name), onClick = onSounds) }
                    ui.ambience?.let { a -> val name = stringResource(a.label())
                        Pill(name, on = true, thumb = { texture(null, a, it) }, label = stringResource(R.string.scene_in_mix, name), onClick = onSounds) }
                    val add = stringResource(R.string.scene_add)
                    Box(Modifier.size(52.dp).clip(CircleShape).border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                        .clickable(role = Role.Button, onClickLabel = add, onClick = onSounds).semantics { contentDescription = add },
                        contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_plus), null, modifier = Modifier.size(22.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** "Bruit brun et pluie": the mix as words, the second name in lower case as in a sentence. */
internal fun mixName(context: android.content.Context, ui: PlayerUi): String? {
    val a = ui.noise?.let { context.getString(it.label()) }
    val b = ui.ambience?.let { context.getString(it.label()) }
    return when {
        a != null && b != null -> context.getString(R.string.scene_mix_and, a, b.lowercase(Locale.getDefault()))
        else -> a ?: b
    }
}

@Composable
private fun stateWord(ui: PlayerUi): String = when {
    ui.status != null && !ui.playing -> stringResource(ui.status.message())
    ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.play_state_playing) + " · " + stringResource(R.string.timer_left, ui.timerRemainingMinutes)
    ui.playing -> stringResource(R.string.play_state_playing)
    else -> stringResource(R.string.scene_ready)
}

@StringRes private fun volumeWord(band: Int) = when (band) {
    0 -> R.string.volume_0
    1 -> R.string.volume_1
    2 -> R.string.volume_2
    3 -> R.string.volume_3
    else -> R.string.volume_4
}

@StringRes internal fun Practice.label() = when (this) {
    Practice.BREATHE -> R.string.scene_practice_breathe
    Practice.MEDITATE -> R.string.scene_practice_meditate
}

/** The timer's share left, for the ring: null with no timer; whole until it runs. */
private fun ring(ui: PlayerUi): Float? {
    val total = ui.timerMinutes ?: return null
    val left = ui.timerRemainingMinutes ?: return 1f
    return (left.toFloat() / total).coerceIn(0f, 1f)
}

/**
 * Up is louder, down softer, anywhere on the page: every layer moves together from where the
 * slide began (so a dropped frame never loses a step). A slide is the user's own action, the only
 * thing that raises a level here.
 */
@Composable
private fun Modifier.volumeSlide(levels: List<Float>, set: (List<Float>) -> Unit): Modifier {
    if (levels.isEmpty()) return this
    val latest by rememberUpdatedState(levels)
    val apply by rememberUpdatedState(set)
    val span = with(LocalDensity.current) { 360.dp.toPx() }
    val drag = remember { floatArrayOf(0f) }
    val base = remember { mutableListOf<Float>() }
    val state = rememberDraggableState { dy ->
        drag[0] -= dy / span
        apply(Volume.shift(base.ifEmpty { latest }, drag[0]))
    }
    return draggable(state, Orientation.Vertical,
        onDragStarted = { drag[0] = 0f; base.clear(); base.addAll(latest) },
        onDragStopped = { base.clear() })
}

/** The disc: play or pause, in a ring that empties as the timer runs. The ring is shape, never a colour. */
@Composable
private fun Disc(ui: PlayerUi, ring: Float?, @StringRes word: Int?, onClick: () -> Unit, always: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val has = always || ui.hasSound
    val label = stringResource(word ?: when {
        !has -> R.string.action_pick
        ui.playing -> R.string.action_pause
        else -> R.string.action_play
    })
    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        val track = scheme.outline.copy(alpha = 0.35f)
        val ink = scheme.onSurface.copy(alpha = 0.8f)
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            val inset = stroke / 2
            val box = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), box, style = Stroke(stroke))
            if (ring != null) drawArc(ink, -90f, 360f * ring, false, Offset(inset, inset), box, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Box(
            Modifier.size(112.dp).clip(CircleShape)
                .then(if (has) Modifier.background(scheme.inverseSurface) else Modifier.border(2.dp, scheme.outline, CircleShape))
                .clickable(role = Role.Button, onClickLabel = label, onClick = onClick).semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            when {
                !has -> Icon(painterResource(R.drawable.ic_plus), null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(32.dp))
                ui.playing -> Icon(painterResource(R.drawable.ic_pause), null, tint = scheme.inverseOnSurface, modifier = Modifier.size(40.dp))
                else -> Icon(painterResource(R.drawable.ic_play), null, tint = scheme.inverseOnSurface, modifier = Modifier.size(44.dp))
            }
        }
    }
}

/**
 * Where the volume stands: "loud" above, "soft" below, a thin bar between. For TalkBack it is a
 * named control with Louder and Softer, one step each, since a slide is not reachable there.
 */
@Composable
private fun VolumeTrack(levels: List<Float>, set: (List<Float>) -> Unit, word: String, modifier: Modifier) {
    if (levels.isEmpty()) { Spacer(modifier); return }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ink = MaterialTheme.colorScheme.onSurface
    val loudest = levels.max()
    val name = stringResource(R.string.volume_label)
    val up = stringResource(R.string.volume_up)
    val down = stringResource(R.string.volume_down)
    Column(
        modifier.semantics(mergeDescendants = true) {
            contentDescription = name; stateDescription = word
            customActions = listOf(
                CustomAccessibilityAction(up) { set(Volume.shift(levels, LevelRules.STEP)); true },
                CustomAccessibilityAction(down) { set(Volume.shift(levels, -LevelRules.STEP)); true },
            )
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.volume_3), style = MaterialTheme.typography.labelMedium, color = muted)
        Canvas(Modifier.padding(vertical = 6.dp).width(8.dp).height(120.dp)) {
            val x = size.width / 2; val w = 3.dp.toPx()
            drawLine(muted.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), w, StrokeCap.Round)
            drawLine(ink.copy(alpha = 0.8f), Offset(x, size.height * (1 - loudest)), Offset(x, size.height), w, StrokeCap.Round)
        }
        Text(stringResource(R.string.volume_1), style = MaterialTheme.typography.labelMedium, color = muted)
    }
}

/** A sound in the mix: its thumbnail, a check and its name. Tapping opens the sounds. */
@Composable
private fun Pill(
    name: String, on: Boolean, thumb: (androidx.compose.ui.graphics.drawscope.DrawScope.(Color) -> Unit)?,
    label: String?, role: Role = Role.Button, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (on) scheme.onSecondaryContainer else scheme.onSurface
    Row(
        Modifier.heightIn(min = 52.dp).clip(CircleShape)
            .background(if (on) scheme.secondaryContainer else scheme.surfaceContainer)
            .then(if (role == Role.RadioButton) Modifier.selectable(on, role = role, onClick = onClick) else Modifier.clickable(role = role, onClick = onClick))
            .then(if (label != null) Modifier.semantics(mergeDescendants = true) { contentDescription = label } else Modifier)
            .padding(start = if (thumb != null) 6.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) Canvas(Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) { thumb(ink.copy(alpha = 0.7f)) }
        if (on) Icon(painterResource(app.beansontoast.design.R.drawable.ic_check), null, tint = ink,
            modifier = Modifier.padding(start = if (thumb != null) 8.dp else 0.dp, end = 6.dp).size(16.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, color = ink, maxLines = 1,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.padding(start = if (!on && thumb != null) 8.dp else 0.dp))
    }
}

/** A quiet way on: words and a chevron in the muted ink, 48 dp tall. */
@Composable
private fun QuietMore(text: String, onClick: () -> Unit) {
    Row(Modifier.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("$text  ›", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The timer in a word: the hourglass and its value; one tap steps Off, 20, 45, 90, Off. */
@Composable
private fun TimerWord(ui: PlayerUi, onTimer: (Int?) -> Unit) {
    val options = listOf<Int?>(null) + LevelRules.SLEEP_MINUTES
    val next = options[(options.indexOf(ui.timerMinutes) + 1) % options.size]
    val value = when {
        ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.timer_left, ui.timerRemainingMinutes)
        ui.timerMinutes == null -> stringResource(R.string.timer_off)
        else -> stringResource(R.string.timer_minutes, ui.timerMinutes)
    }
    val title = stringResource(R.string.listen_timer)
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.timer_change)) { onTimer(next) }
            .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text(value, style = MaterialTheme.typography.labelLarge.tabular(), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Always on screen, playing or not: it cuts to silence and lowers the next start. */
@Composable
private fun TooLoudWord(onTooLoud: () -> Unit) {
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onTooLoud).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_too_loud), null, modifier = Modifier.size(20.dp))
        Text(stringResource(R.string.action_too_loud), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 8.dp))
    }
}
