package app.beansontoast.brume.ui

import app.beansontoast.design.calmAlpha
import app.beansontoast.design.WeightSwap
import app.beansontoast.design.Motion
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import app.beansontoast.brume.core.breath.Pattern
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
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
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
import app.beansontoast.design.rememberReducedMotion
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
import app.beansontoast.brume.core.sound.Mix
import app.beansontoast.brume.core.sound.MixLayer
import app.beansontoast.brume.core.sound.NoiseColour
import app.beansontoast.brume.core.sound.Sound
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
 *  - Sacha's art lies behind each scene ([art], ui/SceneArt), slowly alive; the drawn mist stands in
 *    only for a scene without art. His sound thumbnails wait for a redo ([thumb]).
 */

enum class Scene(@StringRes val title: Int, val sounds: Boolean, @StringRes val purpose: Int) {
    SLEEP(R.string.scene_sleep, true, R.string.scene_sleep_purpose),
    SETTLE(R.string.scene_settle, true, R.string.scene_settle_purpose),
    BREATHE(R.string.scene_breathe, false, R.string.scene_breathe_purpose),
    TONES(R.string.scene_tones, false, R.string.scene_tones_purpose),
}

/**
 * What an empty sound scene offers (Iris, 2026-11-01, from Erwann on dev.15: « ça peut être
 * improve »): one tap on the disc plays it, so the first visit is never a dead end, and Sleep and
 * Settle no longer look the same. Sleep is deep and stops by itself; Settle is lighter, with no
 * timer. Nothing plays without that tap, levels stay his own, and the start fades in as always.
 */
fun Scene.suggestion(): Mix? = when (this) {
    Scene.SLEEP -> Mix(listOf(MixLayer(Sound.Noise(NoiseColour.BROWN)), MixLayer(Sound.Recorded(Ambience.RAIN))), 45)
    Scene.SETTLE -> Mix(listOf(MixLayer(Sound.Noise(NoiseColour.PINK)), MixLayer(Sound.Recorded(Ambience.SEA))), null)
    else -> null
}

/** The scenes the modules leave on: Breathe while Breathe or Meditation is on, Tones while Tones is. */
fun Modules.scenes(): List<Scene> = Scene.entries.filter {
    when (it) { Scene.BREATHE -> calm; Scene.TONES -> tones; else -> true }
}

/** Sacha's scene art: three layers per scene, drawn full-bleed behind the page ([SceneBackdrop]). */
internal fun Scene.art(): SceneLayers? = sceneArt[this]

/**
 * Sacha's sound thumbnails: a square image per sound, in the pills and the tiles. His first set (PR
 * #3) stays out (Iris, 2026-11-01): brown, pink, grey and wind are near-identical horizontal lines,
 * so they fail "distinguishable without colour". The pills keep their drawn textures until a redo.
 */
@DrawableRes internal fun NoiseColour.thumb(): Int? = null
@DrawableRes internal fun Ambience.thumb(): Int? = null

/** The mix a sound scene remembers: its layers and its timer. Levels stay per headphones. */
fun Mix.Companion.of(ui: PlayerUi) = Mix(ui.mix, ui.timerMinutes)

/** What the Breathe scene needs: the practices left on, the one chosen, its length and the sound under it. */
class BreatheScene(
    val practices: List<Practice> = Practice.entries,
    val practice: Practice = Practice.BREATHE,
    val onPractice: (Practice) -> Unit = {},
    val minutes: Int = 5,
    /** Set when the rhythm runs by count (4-7-8), shown instead of the minutes. */
    val cycles: Int? = null,
    val pattern: Pattern = Pattern.DEFAULT,
    val onPattern: (Pattern) -> Unit = {},
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
    /** Plays an empty scene's [suggestion]: the disc's one tap on a first visit. */
    onSuggest: (Mix) -> Unit = {},
    /** The swipe hint shows until the scenes have been changed once, then never again. */
    swipeHint: Boolean = true,
    /** A fixed scene clock in milliseconds, for the drift renders only; the app runs its own. */
    sceneClock: (() -> Long)? = null,
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
        if (i >= 0 && i != pager.currentPage && peek == 0f) pager.animateScrollToPage(i, animationSpec = Motion.tween(Motion.SLOW))
    }
    val shown = scenes.getOrElse(pager.currentPage) { scene }
    val still = sceneClock == null && rememberReducedMotion()
    val clock = sceneClock ?: rememberSceneClock(still)
    // Only the shown scene and its neighbours are decoded, ahead of the swipe that reaches them.
    val context = LocalContext.current
    LaunchedEffect(pager, scenes) {
        snapshotFlow { pager.settledPage }.collect { i ->
            listOf(i, i + 1, i - 1).forEach { scenes.getOrNull(it)?.let { s -> SceneArtCache.preload(context, s.art()) } }
        }
    }

    Box(Modifier.fillMaxSize()) {
        // The launch intro lands Horizon's lines on this mist (ui/launch), as it did on the old dock.
        val anchors = LocalLaunchAnchors.current
        val strength = if (ui.playing) 1f else 0.35f
        val mistInk = scheme.onSurfaceVariant
        val mistArea = Modifier.fillMaxWidth().fillMaxHeight(0.62f).align(Alignment.BottomCenter)
            .onGloballyPositioned { anchors?.mist = MistAnchor(it.boundsInWindow(), strength, mistInk) }
        // The drawn mist stands in only where a scene has no art; the intro keeps its landing place.
        if (shown.art() == null) Mist(mistArea, strength = strength, color = mistInk) else Box(mistArea)
        // The art runs edge to edge, under the status and navigation bars (Erwann, dev.19: « pas
        // coupé en haut et en bas »): the pager fills the screen, and the names above and the
        // timer row below float over it on transparent ground. Each page is padded by both.
        val density = LocalDensity.current
        var headPx by remember { mutableIntStateOf(0) }
        var footPx by remember { mutableIntStateOf(0) }
        val insets = with(density) { PaddingValues(top = headPx.toDp(), bottom = footPx.toDp()) }
        HorizontalPager(pager, Modifier.fillMaxSize(), key = { scenes[it].name },
            flingBehavior = PagerDefaults.flingBehavior(pager, snapAnimationSpec = Motion.tween(Motion.CALM))) { page ->
            val offset = { (pager.currentPage - page) + pager.currentPageOffsetFraction }
            ScenePage(scenes[page], ui, actions, onSounds, onTonesSetup, breathe, night, onSuggest, still, clock, offset, insets)
        }
        Column(Modifier.fillMaxWidth().onSizeChanged { headPx = it.height }.statusBarsPadding()) {
            SceneNames(scenes, shown) { onScene(it) }
            // What the scene is for, so Sleep and Settle read apart before anything is chosen.
            // Two lines kept for every purpose and the hint's line kept once shown, so the header's height
            // never changes with the scene and the pages under it never move (Iris, 2026-11-01).
            Crossfade(shown.purpose, animationSpec = Motion.tween(Motion.CALM), label = "purpose") { purpose ->
                Text(stringResource(purpose), style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, minLines = 2,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
            }
            val hintRoom = remember { swipeHint }
            if (hintRoom) Text(stringResource(R.string.scene_swipe), style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp).alpha(calmAlpha(swipeHint))
                    .then(if (swipeHint) Modifier else Modifier.clearAndSetSemantics { }))
        }
        // Right above the floating bar: the length bottom left, Too loud bottom right, always one tap away.
        val (view, mine) = forMode(ui, actions, if (shown == Scene.TONES) Mode.TONES else Mode.SOUNDS)
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().onSizeChanged { footPx = it.height }
            .padding(bottom = LocalChromeBottom.current).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Every scene has its length bottom left: the timer, or Breathe's minutes, which open its setup.
            Crossfade(shown == Scene.BREATHE, animationSpec = Motion.tween(Motion.CALM), label = "length") { breathing ->
                if (!breathing) TimerWord(view, mine.timer) else LengthWord(breathe)
            }
            Spacer(Modifier.weight(1f))
            TooLoudWord(actions.tooLoud)
        }
    }
}

/** The scenes by name, in the serif: the one you are on larger and heavier, the others there to tap. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SceneNames(scenes: List<Scene>, shown: Scene, onScene: (Scene) -> Unit) {
    val scroll = rememberScrollState()
    // Keep the shown name whole, with the row's margin beside it, scrolling no further than it
    // needs: a fixed step per name cut « Dormir » in half on « Se poser » in French, and asking
    // for the name alone left Tones flush against the edge, or cut on a first frame (Iris,
    // 2026-11-01). The names' widths are measured, so the target is plain arithmetic.
    val margin = with(LocalDensity.current) { 12.dp.toPx() }
    var viewport by remember { mutableIntStateOf(0) }
    val widths = remember(scenes) { mutableStateMapOf<Scene, Int>() }
    LaunchedEffect(shown, scenes, viewport, widths.size == scenes.size) {
        if (viewport == 0 || widths.size < scenes.size) return@LaunchedEffect
        val target = nameScroll(scenes.map { widths.getValue(it) }, scenes.indexOf(shown), margin, viewport, scroll.value)
        if (target != scroll.value) scroll.animateScrollTo(target, Motion.tween(Motion.CALM))
    }
    Row(Modifier.fillMaxWidth().onSizeChanged { viewport = it.width }.horizontalScroll(scroll).padding(horizontal = 12.dp).selectableGroup(),
        verticalAlignment = Alignment.Bottom) {
        scenes.forEach { s ->
            val on = s == shown
            // Each name holds the room of its larger look, both looks cross-faded on its foot, so
            // changing scene never pushes the other names along (Iris, 2026-11-01).
            WeightSwap(stringResource(s.title), on,
                onStyle = MaterialTheme.typography.headlineMedium, offStyle = MaterialTheme.typography.headlineSmall,
                onColor = MaterialTheme.colorScheme.onSurface, offColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onWeight = FontWeight.Normal, offWeight = FontWeight.Light, align = Alignment.BottomStart,
                modifier = Modifier.onSizeChanged { widths[s] = it.width }.heightIn(min = 48.dp).selectable(on, role = Role.Tab) { onScene(s) }.padding(horizontal = 8.dp, vertical = 6.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScenePage(
    scene: Scene, all: PlayerUi, allActions: ListenActions,
    onSounds: () -> Unit, onTonesSetup: () -> Unit, breathe: BreatheScene, night: Boolean,
    onSuggest: (Mix) -> Unit,
    still: Boolean = true,
    clock: () -> Long = { 0L },
    offset: () -> Float = { 0f },
    insets: PaddingValues = PaddingValues(),
) {
    val (ui, actions) = forMode(all, allActions, if (scene == Scene.TONES) Mode.TONES else Mode.SOUNDS)
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    // The layers the slide moves, each with its own setter: the tone pattern alone, or noise and nature.
    val layers: List<Pair<Float, (Float) -> Unit>> = when (scene) {
        Scene.TONES -> listOf(ui.toneLevel to actions.tones.level)
        else -> ui.sounds.map { s -> ui.level(s) to { v: Float -> actions.level(s, v) } }
    }
    val levels = layers.map { it.first }
    val set: (List<Float>) -> Unit = { new -> layers.forEachIndexed { i, layer -> new.getOrNull(i)?.let(layer.second) } }
    val word = stringResource(volumeWord(Volume.band(levels)))
    // An empty sound scene shows its suggestion, ready to play; once anything is chosen, the mix is his.
    val suggestion = scene.suggestion()?.takeIf { !ui.hasSound }
    val title: String
    val line: String
    // "Ready" said nothing: the line now holds only what is true and useful (Erwann, dev.15).
    when (scene) {
        Scene.TONES -> {
            title = stringResource(R.string.ux_tone_kind)
            line = listOfNotNull(stateWord(ui), stringResource(R.string.tones_at, ui.tones.pitch), word).joinToString(" · ")
        }
        Scene.BREATHE -> {
            // The rhythm is the title: « Breathing » already sits on its own toggle below.
            val breathing = breathe.practice == Practice.BREATHE
            title = stringResource(if (breathing) breathe.pattern.title() else breathe.practice.label())
            line = listOfNotNull(
                if (breathing) stringResource(breathe.pattern.phases()) else null,
                breathe.soundName?.let { stringResource(R.string.scene_with, it) },
            ).joinToString(" · ").ifEmpty { stringResource(R.string.scene_breathe_quiet) }
        }
        else -> if (suggestion != null) {
            title = mixName(context, PlayerUi(mix = suggestion.layers)) ?: ""
            line = suggestion.timer?.let { stringResource(R.string.scene_suggest_timer, it) } ?: stringResource(R.string.scene_suggest_open)
        } else {
            title = mixName(context, ui) ?: stringResource(R.string.scene_no_sound)
            line = listOfNotNull(stateWord(ui), word).joinToString(" · ")
        }
    }
    Box(Modifier.fillMaxSize()) {
        scene.art()?.let {
            // Breathe's swell previews the rhythm chosen for the session; the others breathe at rest.
            SceneBackdrop(scene, it, night, still, clock, offset,
                breathe = if (scene == Scene.BREATHE && breathe.practice == Practice.BREATHE) breathe.pattern else null,
                modifier = Modifier.fillMaxSize())
        }
        Column(
            Modifier.fillMaxSize().volumeSlide(levels, set).padding(insets).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.5f))
            // The title keeps room for two lines and sits on their foot, so a one-line name and a
            // two-line mix put the disc in the same place; both words cross-fade (Iris, 2026-11-01).
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                Text(" ", style = MaterialTheme.typography.displaySmall, minLines = 2, modifier = Modifier.clearAndSetSemantics { })
                Crossfade(title, animationSpec = Motion.tween(Motion.CALM), label = "title") { t ->
                    Text(t, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center, maxLines = 2,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().semantics { heading() })
                }
            }
            Crossfade(line, Modifier.fillMaxWidth().padding(top = 6.dp), animationSpec = Motion.tween(Motion.CALM), label = "line") { l ->
                Text(l, style = MaterialTheme.typography.bodyLarge.tabular(), color = muted, textAlign = TextAlign.Center, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite })
            }
            Row(Modifier.fillMaxWidth().padding(top = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f).height(1.dp))
                Spacer(Modifier.width(56.dp))
                if (scene == Scene.BREATHE) Disc(ui.copy(playing = false), ring = null, word = R.string.scene_start, onClick = breathe.onStart, always = true)
                else if (suggestion != null) Disc(ui, ring = suggestion.timer?.let { 1f }, word = R.string.scene_suggest_play,
                    onClick = { onSuggest(suggestion) }, always = true)
                else Disc(ui, ring(ui), word = null, onClick = if (ui.hasSound) actions.toggle else onSounds)
                VolumeTrack(levels, set, word, Modifier.width(56.dp))
                Spacer(Modifier.weight(1f).height(1.dp))
            }
            // Always laid out, faded in once there is something to slide, so the disc never rises.
            Text(stringResource(R.string.scene_volume_hint), style = MaterialTheme.typography.bodySmall, color = muted,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp).alpha(calmAlpha(levels.isNotEmpty()))
                    .then(if (levels.isNotEmpty()) Modifier else Modifier.clearAndSetSemantics { }))
            Spacer(Modifier.weight(1f))
            // The foot keeps one height whatever it holds (a suggestion link or the mix's pills).
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (scene) {
                Scene.TONES -> QuietMore(stringResource(R.string.scene_tones_setup), onTonesSetup)
                Scene.BREATHE -> {
                    // Two levels, two looks (Iris, 2026-11-01): the practice is a pair of words, like
                    // the scene names; only the rhythms are pills. Selection is a check and weight.
                    if (breathe.practices.size > 1) Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        breathe.practices.forEach { p -> PracticeWord(stringResource(p.label()), on = p == breathe.practice) { breathe.onPractice(p) } }
                    }
                    // The rhythm is chosen here, on the scene, not behind the link (Erwann, 2026-10-31).
                    // Under Meditate the rhythms keep their room, faded out and out of reach, so the
                    // practice words and the disc above never move (Iris, 2026-11-01).
                    val rhythms = breathe.practice == Practice.BREATHE
                    run {
                        FlowRow(Modifier.fillMaxWidth().padding(top = 6.dp).alpha(calmAlpha(rhythms))
                            .then(if (rhythms) Modifier.selectableGroup() else Modifier.clearAndSetSemantics { }),
                            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pattern.entries.forEach { p ->
                                Pill(stringResource(p.title()), on = p == breathe.pattern, thumb = null, role = Role.RadioButton, label = null,
                                    enabled = rhythms) { breathe.onPattern(p) }
                            }
                        }
                    }
                }
                else -> if (suggestion != null) QuietMore(stringResource(R.string.scene_choose_other), onSounds)
                else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
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
            } }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Where the scene names' row should scroll so name [i] is whole with [margin] on either side,
 * moving as little as it can from [current]. [widths] are the names' widths in the row, which
 * starts with [margin] of its own; [viewport] is the row's visible width.
 */
internal fun nameScroll(widths: List<Int>, i: Int, margin: Float, viewport: Int, current: Int): Int {
    if (i !in widths.indices) return current
    val content = (margin * 2 + widths.sum()).toInt()
    val max = (content - viewport).coerceAtLeast(0)
    val left = margin + widths.take(i).sum()
    val right = left + widths[i]
    val target = when {
        left - margin < current -> (left - margin).toInt()
        right + margin > current + viewport -> (right + margin - viewport).toInt()
        else -> current
    }
    return target.coerceIn(0, max)
}

/** « Brun + Pluie »: both rows of the picker, background first. */
internal fun mixName(context: android.content.Context, ui: PlayerUi): String? {
    val names = ui.sounds.map { context.getString(it.label()) }
    return when (names.size) {
        0 -> null
        1 -> names[0]
        else -> names.drop(1).fold(names[0]) { acc, n -> context.getString(R.string.scene_mix_and, acc, n) }
    }
}

@Composable
private fun stateWord(ui: PlayerUi): String? = when {
    ui.status != null && !ui.playing -> stringResource(ui.status.message())
    ui.playing && ui.timerRemainingMinutes != null -> stringResource(R.string.play_state_playing) + " · " + stringResource(R.string.timer_left, ui.timerRemainingMinutes)
    ui.playing -> stringResource(R.string.play_state_playing)
    else -> null
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
    label: String?, role: Role = Role.Button, enabled: Boolean = true, onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ink = if (on) scheme.onSecondaryContainer else scheme.onSurface
    Row(
        Modifier.heightIn(min = 52.dp).clip(CircleShape)
            .background(if (on) scheme.secondaryContainer else scheme.surfaceContainer)
            .then(if (role == Role.RadioButton) Modifier.selectable(on, enabled = enabled, role = role, onClick = onClick) else Modifier.clickable(enabled = enabled, role = role, onClick = onClick))
            .then(if (label != null) Modifier.semantics(mergeDescendants = true) { contentDescription = label } else Modifier)
            .padding(start = if (thumb != null) 6.dp else 16.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (thumb != null) Canvas(Modifier.size(40.dp).clip(CircleShape).background(scheme.surfaceContainerHigh)) { thumb(ink.copy(alpha = 0.7f)) }
        // The check keeps its room when off and the name is laid out in both weights, so choosing a
        // rhythm never changes a pill's width and the row never reflows (Iris, 2026-11-01).
        Icon(painterResource(app.beansontoast.design.R.drawable.ic_check), null, tint = ink,
            modifier = Modifier.padding(start = if (thumb != null) 8.dp else 0.dp, end = 6.dp).size(16.dp).alpha(calmAlpha(on, Motion.QUICK)))
        WeightSwap(name, on, onStyle = MaterialTheme.typography.labelLarge, onColor = ink)
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

/** Breathe's length where the other scenes keep their timer: the hourglass and the minutes (or cycles). It opens the setup. */
@Composable
private fun LengthWord(breathe: BreatheScene) {
    val value = breathe.cycles?.let { stringResource(R.string.breath_cycles, it) } ?: stringResource(R.string.scene_minutes, breathe.minutes)
    val title = stringResource(R.string.scene_breathe_setup)
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = title, onClick = breathe.onSetup)
            .semantics(mergeDescendants = true) { contentDescription = title; stateDescription = value }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_timer), null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Text("$value  ›", style = MaterialTheme.typography.labelLarge.tabular(), modifier = Modifier.padding(start = 8.dp))
    }
}

/** Breathing or Meditation, as words: the chosen one in ink with a check, the other muted. */
@Composable
private fun PracticeWord(text: String, on: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.heightIn(min = 48.dp).clip(CircleShape).selectable(on, role = Role.RadioButton, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(app.beansontoast.design.R.drawable.ic_check), null, tint = scheme.onSurface,
            modifier = Modifier.padding(end = 6.dp).size(16.dp).alpha(calmAlpha(on, Motion.QUICK)))
        WeightSwap(text, on, onStyle = MaterialTheme.typography.titleMedium, onColor = scheme.onSurface, offColor = scheme.onSurfaceVariant)
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
