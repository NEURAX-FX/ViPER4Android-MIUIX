package com.llsl.viper4android.ui.screens.debug

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.theme.ViperType
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class LogLevelFilter(
    @param:StringRes val labelRes: Int,
) {
    ALL(R.string.debug_filter_all),
    INFO(R.string.debug_filter_info),
    DEBUG(R.string.debug_filter_debug),
    WARN(R.string.debug_filter_warn),
    ERROR(R.string.debug_filter_error),
    ;

    fun matches(entry: LogEntry): Boolean =
        when (this) {
            ALL -> true
            INFO -> entry.level == LogLevel.INFO
            DEBUG -> entry.level == LogLevel.DEBUG
            WARN -> entry.level == LogLevel.WARN
            ERROR -> entry.level == LogLevel.ERROR
        }
}

private enum class LogSourceFilter(
    @param:StringRes val labelRes: Int,
) {
    ALL(R.string.debug_filter_all),
    APP(R.string.debug_filter_app),
    DRIVER(R.string.debug_filter_driver),
    ;

    fun matches(entry: LogEntry): Boolean =
        when (this) {
            ALL -> true
            APP -> entry.source == LogSource.APP
            DRIVER -> entry.source == LogSource.DRIVER
        }
}

private enum class LogCategory(
    @param:StringRes val labelRes: Int,
) {
    ALL(R.string.debug_filter_all),
    EFFECT(R.string.debug_filter_effect),
    DISPATCH(R.string.debug_filter_dispatch),
    CONFIG(R.string.debug_filter_config),
    COMMAND(R.string.debug_filter_command),
    ;

    fun matches(line: String): Boolean =
        when (this) {
            ALL -> {
                true
            }

            EFFECT -> {
                line.contains(Regex("\\w+: (ON|OFF)"))
            }

            DISPATCH -> {
                line.contains("[Dispatch]") || line.contains("Dispatch:")
            }

            CONFIG -> {
                line.contains("Input ") || line.contains("Output ") ||
                    line.contains("sampling") || line.contains("format") ||
                    line.contains("channels") || line.contains("Config")
            }

            COMMAND -> {
                line.contains("handleCommand") || line.contains("EFFECT_CMD")
            }
        }
}

@Composable
fun DebugLogDialog(
    onDisableDebug: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = remember { DebugLogState() }
    val listState = rememberLazyListState()
    var selectedLevel by remember { mutableStateOf(LogLevelFilter.ALL) }
    var selectedSource by remember { mutableStateOf(LogSourceFilter.ALL) }
    var selectedCategory by remember { mutableStateOf(LogCategory.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    DisposableEffect(state) {
        state.start(includeFileHistory = true)
        onDispose { state.shutdown() }
    }

    val filteredLines by remember {
        derivedStateOf {
            state.visibleEntries.filter { entry ->
                val levelMatch = selectedLevel.matches(entry)
                val sourceMatch = selectedSource.matches(entry)
                val categoryMatch = selectedCategory.matches(entry.raw)
                val searchMatch =
                    searchQuery.isBlank() ||
                        entry.raw.contains(searchQuery, ignoreCase = true)
                levelMatch && sourceMatch && categoryMatch && searchMatch
            }.map { it.raw }
        }
    }

    LaunchedEffect(filteredLines.size) {
        if (filteredLines.isNotEmpty()) {
            listState.animateScrollToItem(filteredLines.size - 1)
        }
    }

    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.debug_log_title),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                DebugLogSearchBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )

                DebugFilterGroup(
                    title = stringResource(R.string.debug_filter_source),
                ) {
                    LogSourceFilter.entries.forEach { source ->
                        DebugFilterChip(
                            selected = selectedSource == source,
                            onClick = { selectedSource = source },
                            label = stringResource(source.labelRes),
                            accentColor = colorForSource(source),
                        )
                    }
                }

                DebugFilterGroup(
                    title = stringResource(R.string.debug_filter_level),
                ) {
                    LogLevelFilter.entries.forEach { level ->
                        DebugFilterChip(
                            selected = selectedLevel == level,
                            onClick = { selectedLevel = level },
                            label = stringResource(level.labelRes),
                            accentColor = colorForLevel(level),
                        )
                    }
                }

                DebugFilterGroup(
                    title = stringResource(R.string.debug_filter_category),
                ) {
                    LogCategory.entries.forEach { category ->
                        DebugFilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = stringResource(category.labelRes),
                        )
                    }
                }

                Text(
                    text = "${filteredLines.size} / ${state.totalCount}",
            style = ViperType.caption,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                DebugLogList(
                    lines = filteredLines,
                    listState = listState,
                )

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TextButton(
                        text = stringResource(R.string.debug_disable_debug),
                        onClick = onDisableDebug,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        text = stringResource(R.string.action_clear),
                        onClick = { state.clear() },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        },
    )
}

@Composable
private fun DebugLogSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current

    SearchBar(
        inputField = {
            InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { focusManager.clearFocus() },
                expanded = false,
                onExpandedChange = {},
                label = stringResource(R.string.debug_search_hint),
                textStyle = ViperType.body,
            )
        },
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        expanded = false,
        content = {},
    )
}

@Composable
private fun DebugFilterGroup(
    title: String,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = ViperType.section,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(0.22f),
        )
        Row(
            modifier = Modifier
                .weight(0.78f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
private fun DebugFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    accentColor: Color = Color.Unspecified,
) {
    val selectedColor = if (accentColor == Color.Unspecified) MiuixTheme.colorScheme.primary else accentColor
    Text(
        text = label,
        style = ViperType.caption,
        color = if (selected) selectedColor else MiuixTheme.colorScheme.onSurfaceVariantActions,
        modifier =
            Modifier
                .padding(horizontal = 3.dp)
                .height(30.dp)
                .clip(CircleShape)
                .background(
                    color = if (selected) selectedColor.copy(alpha = 0.16f) else Color.Transparent,
                )
                .border(
                    width = 1.dp,
                    color = if (selected) selectedColor.copy(alpha = 0.28f) else MiuixTheme.colorScheme.dividerLine,
                    shape = CircleShape,
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun DebugLogList(
    lines: List<String>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (lines.isEmpty()) {
            Text(
                text = stringResource(R.string.debug_log_empty),
                style = ViperType.caption,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.66f),
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                items(lines) { line ->
                    Text(
                        text = line,
                        style = ViperType.mono,
                        color = colorForLogLine(line),
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

private fun colorForSource(source: LogSourceFilter): Color =
    when (source) {
        LogSourceFilter.ALL -> Color.Unspecified
        LogSourceFilter.APP -> Color(0xFF66BB6A)
        LogSourceFilter.DRIVER -> Color(0xFFAB47BC)
    }

private fun colorForLevel(level: LogLevelFilter): Color =
    when (level) {
        LogLevelFilter.ALL -> Color.Unspecified
        LogLevelFilter.INFO -> Color(0xFF42A5F5)
        LogLevelFilter.DEBUG -> Color.Gray
        LogLevelFilter.WARN -> Color(0xFFFFA726)
        LogLevelFilter.ERROR -> Color(0xFFEF5350)
    }

private fun colorForLogLine(line: String): Color =
    when {
        line.contains("[ERROR]") || line.contains(" E/") -> Color(0xFFEF5350)
        line.contains("[WARN]") || line.contains(" W/") -> Color(0xFFFFA726)
        line.contains("[INFO]") || line.contains(" I/") -> Color(0xFF42A5F5)
        line.contains("[DEBUG]") || line.contains(" D/") -> Color.Gray
        else -> Color.Unspecified
    }
