package com.llsl.viper4android.ui.components.viper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.llsl.viper4android.ui.theme.viperBounce
import top.yukonga.miuix.kmp.basic.IconButton

@Composable
fun ViperIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.viperBounce(enabled = enabled, pressedScale = 0.90f),
        enabled = enabled,
        content = content,
    )
}
