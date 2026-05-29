package com.llsl.viper4android.ui.components.viper

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ViperBottomBar(
    deviceName: String,
    firstLabel: String,
    firstIcon: ImageVector,
    firstSelected: Boolean,
    onFirstClick: () -> Unit,
    secondLabel: String,
    secondIcon: ImageVector,
    secondSelected: Boolean,
    onSecondClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val outerBottomPadding = if (bottomPadding > 0.dp) bottomPadding + 12.dp else 20.dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = outerBottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (deviceName.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(Color(0xFF4CAF50))
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = deviceName,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        ViperFloatingCapsule(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .widthIn(max = 360.dp),
            firstLabel = firstLabel,
            firstIcon = firstIcon,
            firstSelected = firstSelected,
            onFirstClick = onFirstClick,
            secondLabel = secondLabel,
            secondIcon = secondIcon,
            secondSelected = secondSelected,
            onSecondClick = onSecondClick,
        )
    }
}

@Composable
private fun ViperFloatingCapsule(
    firstLabel: String,
    firstIcon: ImageVector,
    firstSelected: Boolean,
    onFirstClick: () -> Unit,
    secondLabel: String,
    secondIcon: ImageVector,
    secondSelected: Boolean,
    onSecondClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(28.dp)
    val selectedProgress by animateFloatAsState(
        targetValue = if (secondSelected && !firstSelected) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f),
        label = "viperBottomBarSelection",
    )

    BoxWithConstraints(
        modifier = modifier
            .height(64.dp)
            .shadow(elevation = 10.dp, shape = shape, clip = false)
            .clip(shape)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.96f))
            .padding(4.dp),
    ) {
        val itemWidth = maxWidth / 2

        Box(
            modifier = Modifier
                .offset(x = itemWidth * selectedProgress)
                .width(itemWidth)
                .height(56.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ViperFloatingCapsuleItem(
                label = firstLabel,
                icon = firstIcon,
                selected = firstSelected,
                onClick = onFirstClick,
                modifier = Modifier.weight(1f),
            )
            ViperFloatingCapsuleItem(
                label = secondLabel,
                icon = secondIcon,
                selected = secondSelected,
                onClick = onSecondClick,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ViperFloatingCapsuleItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contentColor =
        if (selected) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantActions
        }

    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(24.dp))
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = contentColor,
                style = MiuixTheme.textStyles.body2,
            )
        }
    }
}
