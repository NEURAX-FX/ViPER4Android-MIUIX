package com.llsl.viper4android.ui.components.viper

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.ui.theme.ViperInk
import com.llsl.viper4android.ui.theme.ViperType
import top.yukonga.miuix.kmp.basic.Card as MiuixCard
import top.yukonga.miuix.kmp.basic.Icon as MiuixIcon
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ViperEffectCard(
    title: String,
    summary: String? = null,
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hasEnableSwitch: Boolean = true,
    toggleOnly: Boolean = false,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
    val headerModifier =
        if (toggleOnly) {
            Modifier
        } else {
            Modifier.clickable { expanded = !expanded }
        }

    MiuixCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
        insideMargin = PaddingValues(0.dp),
        cornerRadius = 12.dp,
    ) {
        Row(
            modifier =
                headerModifier
                    .fillMaxWidth()
                    .heightIn(min = 58.dp)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Box(
                    modifier =
                        Modifier
                            .size(32.dp)
                            .background(
                                MiuixTheme.colorScheme.primary.copy(
                                    alpha = if (enabled) 0.16f else 0.08f,
                                ),
                                RoundedCornerShape(10.dp),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    MiuixIcon(
                        imageVector = it,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                MiuixText(
                    text = title,
                    style = ViperType.body,
                    color = ViperInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                summary?.takeIf(String::isNotBlank)?.let {
                    MiuixText(
                        text = it,
                        style = ViperType.caption,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (!toggleOnly) {
                MiuixIcon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (hasEnableSwitch) {
                MiuixSwitch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                )
            }
        }

        if (!toggleOnly) {
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .alpha(if (enabled) 1f else 0.45f),
                    content = content,
                )
            }
        }
    }
}
