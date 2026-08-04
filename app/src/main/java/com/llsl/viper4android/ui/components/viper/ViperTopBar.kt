package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Collapsing MiuiX top bar without the reserved split-title row.
 */
@Composable
fun ViperTopBar(
    title: String,
    largeTitle: String = title,
    modifier: Modifier = Modifier,
    deviceName: String = "",
    scrollBehavior: ScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 30.dp.toPx() }
    SideEffect {
        scrollBehavior?.state?.heightOffsetLimit = -collapseRangePx
    }

    val collapsedFraction =
        scrollBehavior
            ?.state
            ?.collapsedFraction
            ?.takeIf { it.isFinite() }
            ?.coerceIn(0f, 1f)
            ?: 0f
    val expandedTitleAlpha = (1f - collapsedFraction / 0.68f).coerceIn(0f, 1f)
    val collapsedTitleAlpha = ((collapsedFraction - 0.42f) / 0.58f).coerceIn(0f, 1f)
    val actionsAlpha = (1f - collapsedFraction).coerceIn(0f, 1f)
    val barHeight = (82f - (30f * collapsedFraction)).dp

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(barHeight)
                .padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Column(
                modifier =
                    Modifier.graphicsLayer {
                        alpha = expandedTitleAlpha
                        translationY = -4.dp.toPx() * collapsedFraction
                    },
            ) {
                Text(
                    text = largeTitle,
                    fontSize = 40.sp,
                    lineHeight = 42.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (deviceName.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = deviceName,
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                text = title,
                fontSize = 20.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier.graphicsLayer {
                        alpha = collapsedTitleAlpha
                        translationY = 6.dp.toPx() * (1f - collapsedTitleAlpha)
                    },
            )
        }
        if (actionsAlpha > 0.001f) {
            Row(
                modifier =
                    Modifier.graphicsLayer {
                        alpha = actionsAlpha
                        translationY = -4.dp.toPx() * collapsedFraction
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
    }
}
