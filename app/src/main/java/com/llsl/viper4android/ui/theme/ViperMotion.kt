package com.llsl.viper4android.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput

object ViperMotion {
    val responsiveSpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.75f,
        stiffness = Spring.StiffnessMediumLow,
    )

    val snappySpring: AnimationSpec<Float> = spring(
        dampingRatio = 0.7f,
        stiffness = Spring.StiffnessMedium,
    )

    val contentFade: AnimationSpec<Float> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )

    val layoutExpand: AnimationSpec<Float> = spring(
        dampingRatio = 0.82f,
        stiffness = Spring.StiffnessLow,
    )
}

/**
 * Adds a responsive, iOS/MIUI-style spring scale bounce effect when pressed.
 */
fun Modifier.viperBounce(
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: (() -> Unit)? = null,
): Modifier = composed {
    if (!enabled) return@composed this

    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = ViperMotion.snappySpring,
        label = "viper_bounce_scale",
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .pointerInput(enabled) {
            while (true) {
                awaitPointerEventScope {
                    awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    val up = waitForUpOrCancellation()
                    isPressed = false
                }
            }
        }
        .then(
            if (onClick != null) {
                Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
            } else {
                Modifier
            }
        )
}
