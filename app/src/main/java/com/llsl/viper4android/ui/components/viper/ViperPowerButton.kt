package com.llsl.viper4android.ui.components.viper

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.pow

internal fun powerRingProgress(activation: Float): Float = activation.coerceIn(0f, 1f).pow(0.62f)

internal fun powerIconProgress(activation: Float): Float =
    ((activation.coerceIn(0f, 1f) - 0.45f) / 0.55f).coerceIn(0f, 1f)

@Composable
fun ViperPowerButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "powerPressScale",
    )
    val activation = remember { Animatable(if (checked) 1f else 0f) }
    val ringScale = remember { Animatable(1f) }

    LaunchedEffect(checked) {
        val target = if (checked) 1f else 0f
        if (activation.value == target) return@LaunchedEffect
        coroutineScope {
            launch {
                activation.animateTo(
                    targetValue = target,
                    animationSpec = tween(durationMillis = if (checked) 160 else 110),
                )
            }
            launch {
                if (checked) {
                    ringScale.snapTo(0.94f)
                    ringScale.animateTo(
                        targetValue = 1f,
                        animationSpec = keyframes {
                            durationMillis = 160
                            0.94f at 0
                            1f at 60
                            1.06f at 120
                            1f at 160
                        },
                    )
                } else {
                    ringScale.animateTo(1f, tween(durationMillis = 110))
                }
            }
        }
    }

    val ringProgress = powerRingProgress(activation.value)
    val iconProgress = powerIconProgress(activation.value)
    val offRingColor = MiuixTheme.colorScheme.outline.copy(alpha = 0.7f)
    val onRingColor = MiuixTheme.colorScheme.primary
    val coreColor = MiuixTheme.colorScheme.surfaceContainerHigh
    val offIconColor = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.72f)
    val iconColor = lerp(offIconColor, onRingColor, iconProgress)

    Box(
        modifier =
            modifier
                .size(48.dp)
                .semantics { this.contentDescription = contentDescription }
                .toggleable(
                    value = checked,
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                )
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(48.dp)) {
            val ringStroke = 3.dp.toPx()
            val ringRadius = (18.dp.toPx() - ringStroke / 2f) * ringScale.value
            val haloRadius = 21.dp.toPx() * ringScale.value
            drawCircle(
                color = onRingColor.copy(alpha = 0.09f * ringProgress),
                radius = haloRadius,
                style = Stroke(width = ringStroke),
            )
            drawCircle(color = coreColor, radius = 13.dp.toPx())
            drawCircle(
                color = lerp(offRingColor, onRingColor, ringProgress),
                radius = ringRadius,
                style = Stroke(width = ringStroke),
            )
        }
        Icon(
            imageVector = Icons.Default.PowerSettingsNew,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(17.dp).alpha(0.38f + 0.62f * iconProgress),
        )
    }
}
