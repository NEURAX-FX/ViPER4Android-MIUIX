package com.llsl.viper4android.ui.components.viper

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.TabRowWithContour

@Composable
fun ViperTabs(
    tabs: List<String>,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tabs.isEmpty()) return

    TabRowWithContour(
        tabs = tabs,
        selectedTabIndex = selectedTabIndex.coerceIn(tabs.indices),
        onTabSelected = onTabSelected,
        modifier = modifier,
    )
}
