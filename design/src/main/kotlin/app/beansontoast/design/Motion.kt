package app.beansontoast.design

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

/*
 * Brume's one motion spec (Iris, 2026-11-01, from Erwann on dev.21: « extrêmement douce… smooth…
 * pas trop flashy… je veux pas des jumping de layout »). Written out in docs/motion.md.
 *  - One curve: a slow, symmetric ease-in-out ([Ease]). No spring, no overshoot, nothing bounces.
 *  - Three lengths: [QUICK] for a word or a mark changing, [CALM] for content swapping or a row
 *    growing, [SLOW] for a whole screen or scene giving way to another.
 *  - Navigation slides (Erwann, 2026-11-01: « je préfère un slide »): a screen opened comes in
 *    from the right while the one under it moves a quarter left, and leaves the same way ([push]).
 *    Everything else cross-fades in place; nothing pops in scale.
 *  - Space is reserved: a state that comes and goes keeps its room, so nothing around it moves.
 *    Where room really has to change (the mini player joining the bar), it grows over [CALM].
 *  - Ticking numbers use tabular figures ([tabular]), so their width never wobbles.
 *  - Reduced motion: opacity only, resting on the final state; sizes snap, nothing travels.
 */
object Motion {
    /** easeInOutSine: as slow leaving as arriving, never past the target. */
    val Ease = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)

    const val QUICK = 320
    const val CALM = 560
    const val SLOW = 900

    fun <T> tween(ms: Int = CALM): FiniteAnimationSpec<T> = androidx.compose.animation.core.tween(ms, easing = Ease)

    /** A size or a place: eased, or snapped with reduced motion (opacity is the only thing that moves then). */
    fun <T> travel(reduced: Boolean, ms: Int = CALM): FiniteAnimationSpec<T> = if (reduced) snap() else tween(ms)

    /** A screen pushed or popped: long enough to follow, short enough not to wait on. */
    const val PUSH = 700

    /**
     * Opening a screen ([forward]) slides it in from the right over the one before, which moves a
     * quarter of the width left; going back reverses it, the leaving screen on top. Eased, never a
     * spring. With reduced motion it is a cross-fade: nothing travels.
     */
    fun push(reduced: Boolean, forward: Boolean): ContentTransform = when {
        reduced -> crossfade(CALM)
        forward -> (androidx.compose.animation.slideInHorizontally(tween(PUSH)) { it } togetherWith
            androidx.compose.animation.slideOutHorizontally(tween(PUSH)) { -it / 4 }).apply { targetContentZIndex = 1f }
        else -> (androidx.compose.animation.slideInHorizontally(tween(PUSH)) { -it / 4 } togetherWith
            androidx.compose.animation.slideOutHorizontally(tween(PUSH)) { it }).apply { targetContentZIndex = -1f }
    }

    /** A cross-fade, the same with reduced motion: it is opacity alone. */
    fun crossfade(ms: Int = CALM): ContentTransform = fadeIn(tween(ms)) togetherWith fadeOut(tween(ms))

    /** Something that has to take room arriving: it grows and fades in; with reduced motion it only fades. */
    fun enter(reduced: Boolean): EnterTransition =
        if (reduced) fadeIn(tween(CALM)) else fadeIn(tween(CALM)) + expandVertically(tween(CALM), expandFrom = Alignment.Top)

    fun exit(reduced: Boolean): ExitTransition =
        if (reduced) fadeOut(tween(CALM)) else fadeOut(tween(CALM)) + shrinkVertically(tween(CALM), shrinkTowards = Alignment.Top)
}

/** For AnimatedContent: every change is a calm cross-fade, the old and the new in the same place. */
fun <S> AnimatedContentTransitionScope<S>.calmCrossfade(ms: Int = Motion.CALM): ContentTransform = Motion.crossfade(ms)

/** A column or box whose height follows its content smoothly (snapped with reduced motion). */
@Composable
fun Modifier.calmSize(): Modifier = animateContentSize(Motion.travel(LocalReducedMotion.current))

/** Opacity eased towards [shown]: for a reserved place whose content comes and goes. */
@Composable
fun calmAlpha(shown: Boolean, ms: Int = Motion.CALM): Float =
    animateFloatAsState(if (shown) 1f else 0f, Motion.tween(ms), label = "calmAlpha").value

/**
 * A word that is chosen or not, told by weight (and size): both looks are laid out on top of each
 * other and cross-faded, so the box never changes width and nothing beside it moves. TalkBack reads it once.
 */
@Composable
fun WeightSwap(
    text: String,
    on: Boolean,
    onStyle: TextStyle,
    offStyle: TextStyle = onStyle,
    onColor: Color = Color.Unspecified,
    offColor: Color = onColor,
    onWeight: FontWeight = FontWeight.SemiBold,
    offWeight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
    align: Alignment = Alignment.Center,
    textAlign: TextAlign? = null,
) {
    val a by animateFloatAsState(if (on) 1f else 0f, Motion.tween(Motion.QUICK), label = "weight")
    Box(modifier, contentAlignment = align) {
        Text(text, style = onStyle, color = onColor, fontWeight = onWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = textAlign, modifier = Modifier.alpha(a))
        Text(text, style = offStyle, color = offColor, fontWeight = offWeight, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = textAlign, modifier = Modifier.alpha(1f - a).clearAndSetSemantics { })
    }
}
