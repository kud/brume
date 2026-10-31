package app.beansontoast.brume.ui.launch

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.colorResource
import androidx.core.splashscreen.SplashScreenViewProvider
import app.beansontoast.brume.R
import app.beansontoast.design.rememberReducedMotion

/*
 * The launch intro's overlay (timing in Choreography.kt, geometry in Lockup.kt). It covers the app,
 * which is composed underneath and never held back, for at most 800 ms; a tap anywhere skips it.
 * Everything it draws is the splash's colours (values/, values-night/) until it hands over to the
 * screen's own: the mist's ink on Listen, the introduction's on the first launch.
 */

/** Where the screen underneath wants the lockup to land, in window pixels. Set by the screens, read by the overlay. */
class LaunchAnchors {
    var mist by mutableStateOf<MistAnchor?>(null)
    var welcome by mutableStateOf<WelcomeAnchor?>(null)
}

/** Listen's dock mist: its box, its strength and its ink, exactly as `Mist` draws it. */
data class MistAnchor(val bounds: Rect, val strength: Float, val ink: Color)

/**
 * The introduction's lockup: Horizon's icon box and tint, and the wordmark's left edge, baseline,
 * width (its advance) and colour.
 */
data class WelcomeAnchor(val icon: Rect, val iconInk: Color, val wordLeft: Float, val wordBaseline: Float, val wordWidth: Float, val wordInk: Color)

val LocalLaunchAnchors = staticCompositionLocalOf<LaunchAnchors?> { null }

/** Hand-over from the system splash. MainActivity passes [takeOver] to setOnExitAnimationListener. */
class LaunchIntro(val kind: IntroKind) {
    /** The splash icon's 288 dp box in window pixels, once the splash has handed over; null when there is nothing to play. */
    internal var icon by mutableStateOf<Rect?>(null)
        private set
    private var provider: SplashScreenViewProvider? = null

    fun takeOver(splash: SplashScreenViewProvider) {
        if (kind == IntroKind.NONE) {
            splash.remove()
            return
        }
        provider = splash
        val metrics = splash.view.resources.displayMetrics
        val half = Lockup.SPLASH_ICON_DP * metrics.density / 2f
        // The system centres the icon; an icon view not yet laid out falls back to the splash's
        // centre, then the screen's (tutti's lesson: a warm start can hand over before layout).
        val centre = splash.iconView.centreInWindow() ?: splash.view.centreInWindow()
            ?: Offset(metrics.widthPixels / 2f, metrics.heightPixels / 2f)
        icon = Rect(centre.x - half, centre.y - half, centre.x + half, centre.y + half)
    }

    /** The overlay has drawn the lockup over the system splash: the swap is invisible. */
    internal fun covered() {
        provider?.remove()
        provider = null
    }

    internal fun finish() {
        covered()
        icon = null
    }

    companion object {
        // Process-wide: savedInstanceState is null after Back too, so it cannot tell cold from warm.
        private val launches = LaunchCounter()

        fun kindFor(restored: Boolean): IntroKind = launches.kindFor(restored)
    }
}

private fun android.view.View.centreInWindow(): Offset? {
    if (!isLaidOut || width == 0 || height == 0) return null
    val at = IntArray(2).also(::getLocationInWindow)
    return Offset(at[0] + width / 2f, at[1] + height / 2f)
}

/**
 * The overlay. [firstLaunch]: the introduction is showing underneath. It draws the resting lockup
 * on its first frame, removes the system splash once that is on screen, gives the screen one more
 * frame to lay out its anchor, then plays.
 */
@Composable
fun LaunchIntroOverlay(intro: LaunchIntro, firstLaunch: Boolean) {
    val icon = intro.icon ?: return
    val anchors = LocalLaunchAnchors.current
    val reduced = rememberReducedMotion()
    var elapsed by remember { mutableFloatStateOf(0f) }
    var script by remember { mutableStateOf<Choreography?>(null) }
    LaunchedEffect(intro) {
        withFrameNanos { }
        withFrameNanos { }
        intro.covered()
        withFrameNanos { }
        val landing = when {
            firstLaunch && anchors?.welcome != null -> Landing.WELCOME
            !firstLaunch && anchors?.mist != null -> Landing.MIST
            else -> Landing.NOWHERE
        }
        val chosen = choreographyFor(intro.kind, reduced, landing)
        if (chosen != null) {
            script = chosen
            val start = withFrameNanos { it }
            while (elapsed < chosen.durationMs) withFrameNanos { now -> elapsed = (now - start) / 1_000_000f }
        }
        intro.finish()
    }
    LaunchCanvas(
        script, elapsed, icon,
        Modifier.pointerInput(intro) {
            // A tap skips; nothing reaches the screen underneath while the intro is up.
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false).consume()
                intro.finish()
            }
        },
    )
}

/**
 * One frame of the intro: [script] at [ms], or the resting lockup when [script] is null. [icon] is
 * the splash icon's box in window pixels; null centres it in this canvas (the renders).
 */
@Composable
fun LaunchCanvas(script: Choreography?, ms: Float, icon: Rect?, modifier: Modifier = Modifier) {
    val anchors = LocalLaunchAnchors.current
    val ground = colorResource(R.color.splash_ground)
    val ink = colorResource(R.color.splash_mark)
    val art = remember { LockupArt() }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Canvas(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInWindow() }) {
        val box = icon?.translate(-origin) ?: run {
            val half = Lockup.SPLASH_ICON_DP * density / 2f
            Rect(center.x - half, center.y - half, center.x + half, center.y + half)
        }
        val pose = script?.pose(ms) ?: LaunchPose.Resting
        val landing = script?.landing ?: Landing.NOWHERE
        drawLaunch(
            pose, landing, box, ground, ink, art,
            mist = anchors?.mist?.let { it.copy(bounds = it.bounds.translate(-origin)) },
            welcome = anchors?.welcome?.let {
                it.copy(icon = it.icon.translate(-origin), wordLeft = it.wordLeft - origin.x, wordBaseline = it.wordBaseline - origin.y)
            },
        )
    }
}

/** The lockup's outlines, parsed once. */
internal class LockupArt {
    val marks: List<Path> = Lockup.MARK_PATHS.map { PathParser().parsePathString(it).toPath() }
    val word: Path = PathParser().parsePathString(Lockup.WORD_PATH).toPath()
}

/** A uniform-or-not scale then a translation: where a path's own units land on screen. */
private data class Place(val sx: Float, val sy: Float, val tx: Float, val ty: Float) {
    fun x(v: Float) = tx + sx * v
    fun y(v: Float) = ty + sy * v
    fun towards(to: Place, t: Float) = Place(lerp(sx, to.sx, t), lerp(sy, to.sy, t), lerp(tx, to.tx, t), lerp(ty, to.ty, t))
}

private fun DrawScope.drawPlaced(path: Path, place: Place, colour: Color) {
    if (colour.alpha <= 0f) return
    withTransform({ translate(place.tx, place.ty); scale(place.sx, place.sy, Offset.Zero) }) { drawPath(path, colour) }
}

internal fun DrawScope.drawLaunch(
    pose: LaunchPose,
    landing: Landing,
    box: Rect,
    ground: Color,
    ink: Color,
    art: LockupArt,
    mist: MistAnchor?,
    welcome: WelcomeAnchor?,
) {
    val u = box.width / Lockup.VIEWPORT
    drawGround(ground, pose)

    // Where the lockup rests: the splash icon's own placement.
    val markRest = Place(u * Lockup.MARK_SCALE, u * Lockup.MARK_SCALE, box.left + u * Lockup.MARK_DX, box.top + u * Lockup.MARK_DY)
    val wordRest = Place(u * Lockup.WORD_SCALE, u * Lockup.WORD_SCALE, box.left + u * Lockup.WORD_DX, box.top + u * Lockup.WORD_DY - u * pose.rise)

    if (landing == Landing.WELCOME && welcome != null) {
        // The band of ic_horizon inside its 72-unit viewport (its group: scaleY 2.2 about y 58, then -18, -22).
        val k = welcome.icon.width / 72f
        val bandLeft = welcome.icon.left + 4f * k
        val bandTop = welcome.icon.top + 16.2f * k
        val sx = 64f * k / (Lockup.BAND_RIGHT - Lockup.BAND_LEFT)
        val sy = 40.48f * k / (Lockup.BAND_BOTTOM - Lockup.BAND_TOP)
        val markTo = Place(sx, sy, bandLeft - Lockup.BAND_LEFT * sx, bandTop - Lockup.BAND_TOP * sy)
        val ws = welcome.wordWidth / Lockup.WORD_ADVANCE
        val wordTo = Place(ws, ws, welcome.wordLeft, welcome.wordBaseline)
        val t = pose.travel
        art.marks.forEachIndexed { i, path ->
            drawPlaced(path, markRest.towards(markTo, t), lerp(ink, welcome.iconInk, t).let { it.copy(alpha = it.alpha * Lockup.MARK_ALPHAS[i] * pose.mark) })
        }
        drawPlaced(art.word, wordRest.towards(wordTo, t), lerp(ink, welcome.wordInk, t).let { it.copy(alpha = it.alpha * pose.word) })
        return
    }

    if (landing == Landing.MIST && mist != null) {
        val lines = mistLines(mist.bounds.width, mist.bounds.height, mist.strength)
        // The outer two arrive on their own, already the mist's.
        for (i in listOf(0, lines.lastIndex)) {
            drawWave(lines[i], mist, mist.ink.copy(alpha = mist.ink.alpha * lines[i].alpha * pose.outer * pose.mark))
        }
        // The exact outlines give way to their simplified lines as soon as they move: same picture at rest.
        val handover = smooth(0f, 0.12f, pose.travel)
        art.marks.forEachIndexed { i, path ->
            drawPlaced(path, markRest, ink.copy(alpha = ink.alpha * Lockup.MARK_ALPHAS[i] * pose.mark * (1f - handover)))
        }
        MARK_LINES.forEachIndexed { i, line ->
            val target = lines[MIST_FIRST_LANDING + i]
            val colour = lerp(ink.copy(alpha = ink.alpha * Lockup.MARK_ALPHAS[i]), mist.ink.copy(alpha = mist.ink.alpha * target.alpha), pose.travel)
            drawMorph(line, markRest, target, mist, pose.travel, colour.copy(alpha = colour.alpha * pose.mark * handover))
        }
    } else {
        art.marks.forEachIndexed { i, path -> drawPlaced(path, markRest, ink.copy(alpha = ink.alpha * Lockup.MARK_ALPHAS[i] * pose.mark)) }
    }
    drawPlaced(art.word, wordRest, ink.copy(alpha = ink.alpha * pose.word))
}

/** The splash ground at [LaunchPose.ground], lifted from the bottom by [LaunchPose.lift] through a soft edge. */
private fun DrawScope.drawGround(ground: Color, pose: LaunchPose) {
    val alpha = ground.alpha * pose.ground
    if (alpha <= 0f || pose.lift >= 1f) return
    val solid = ground.copy(alpha = alpha)
    if (pose.lift <= 0f) {
        drawRect(solid)
        return
    }
    val h = size.height
    val feather = h * 0.35f
    val edge = (1f - pose.lift) * (h + feather)
    drawRect(Brush.verticalGradient(0f to solid, 1f to solid.copy(alpha = 0f), startY = edge - feather, endY = edge))
}

private fun DrawScope.drawWave(line: WaveLine, mist: MistAnchor, colour: Color) {
    if (colour.alpha <= 0f) return
    val path = Path()
    var x = line.from
    path.moveTo(mist.bounds.left + x, mist.bounds.top + line.yAt(x))
    while (x < line.to) {
        x += 4f
        path.lineTo(mist.bounds.left + x, mist.bounds.top + line.yAt(x))
    }
    drawPath(path, colour, style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f * density, cap = androidx.compose.ui.graphics.StrokeCap.Round))
}

/**
 * A Horizon line on its way to a mist line, at [t]: every point, and the thickness, go from the
 * tapered mark to the mist's even 1.5 dp line. Drawn as one filled outline, so a translucent line
 * has no darker joints.
 */
private fun DrawScope.drawMorph(line: MarkLine, rest: Place, target: WaveLine, mist: MistAnchor, t: Float, colour: Color) {
    if (colour.alpha <= 0f) return
    val n = 40
    val xs = FloatArray(n + 1)
    val ys = FloatArray(n + 1)
    val half = FloatArray(n + 1)
    val thin = 1.5f * density
    for (k in 0..n) {
        val s = k / n.toFloat()
        val fromX = rest.x(lerp(line.x0, line.x1, s))
        val fromY = rest.y(lerp(line.y0, line.y1, s))
        val tx = lerp(target.from, target.to, s)
        val toX = mist.bounds.left + tx
        val toY = mist.bounds.top + target.yAt(tx)
        xs[k] = lerp(fromX, toX, t)
        ys[k] = lerp(fromY, toY, t)
        half[k] = lerp(rest.sy * line.thick * taper(s), thin * (0.4f + 0.6f * taper(s).coerceAtLeast(0.6f)), t) / 2f
    }
    val path = Path()
    path.moveTo(xs[0], ys[0] - half[0])
    for (k in 1..n) path.lineTo(xs[k], ys[k] - half[k])
    for (k in n downTo 0) path.lineTo(xs[k], ys[k] + half[k])
    path.close()
    drawPath(path, colour)
}
