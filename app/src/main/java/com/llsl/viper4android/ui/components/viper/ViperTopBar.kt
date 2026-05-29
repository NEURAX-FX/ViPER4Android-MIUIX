package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * Viper-style top app bar wrapping MiuiX TopAppBar.
 * Provides consistent title and action layout.
 */
@Composable
fun ViperTopBar(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = title,
        actions = actions,
        modifier = modifier
    )
}
