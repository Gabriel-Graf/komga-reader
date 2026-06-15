package com.komgareader.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/** A reusable icon animation. New animations = a new variant + a branch in [iconAnimationPlan]. */
sealed interface IconAnimation {
    data object SpinClockwise : IconAnimation
    data object BobVertical : IconAnimation
}

/** Pure gating policy: LCD animates continuously; E-Ink plays one bounded cycle then holds. */
data class IconAnimationPlan(val continuous: Boolean, val cycleMillis: Int)

fun iconAnimationPlan(einkMode: Boolean, animation: IconAnimation): IconAnimationPlan {
    val cycle = when (animation) {
        IconAnimation.SpinClockwise -> if (einkMode) 400 else 800
        IconAnimation.BobVertical -> if (einkMode) 600 else 1200
    }
    return IconAnimationPlan(continuous = !einkMode, cycleMillis = cycle)
}

/**
 * Icon with an [animation] that runs while [running] is true. All motion is gated here by
 * [LocalEinkMode] — callers never decide motion. On LCD the animation loops; on E-Ink it plays a
 * single bounded cycle (one turn / one bob) then holds static. Nothing animates when [running] is
 * false. Flat (no elevation), per eink-design-language.
 */
@Composable
fun AnimatedAppIcon(
    imageVector: ImageVector,
    animation: IconAnimation,
    running: Boolean,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val eink = LocalEinkMode.current
    val plan = remember(eink, animation) { iconAnimationPlan(eink, animation) }

    // One unconditional Animatable driven by one unconditional LaunchedEffect — no conditional
    // composable calls (Rules of Hooks), and nothing animates while idle (the effect only runs the
    // animation when `running`). LCD loops the cycle; E-Ink plays a single bounded cycle then holds.
    val anim = remember { Animatable(0f) }
    LaunchedEffect(running, plan) {
        if (!running) { anim.snapTo(0f); return@LaunchedEffect }
        val spec = tween<Float>(plan.cycleMillis, easing = LinearEasing)
        if (plan.continuous) {
            while (true) { anim.snapTo(0f); anim.animateTo(1f, spec) }
        } else {
            anim.snapTo(0f); anim.animateTo(1f, spec)
        }
    }
    val phase: Float = anim.value

    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = modifier.graphicsLayer {
            when (animation) {
                IconAnimation.SpinClockwise -> rotationZ = phase * 360f
                IconAnimation.BobVertical -> {
                    val amp = 2.dp.toPx()
                    translationY = sin(phase * 2.0 * PI).toFloat() * amp
                }
            }
        },
    )
}
