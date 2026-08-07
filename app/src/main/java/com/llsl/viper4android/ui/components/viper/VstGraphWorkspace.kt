package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private val UtilityRailWidth = 56.dp
private val RailBreakpoint = 480.dp
private val SideBySideBreakpoint = 720.dp

/**
 * Graph-first workspace: the response graph owns the dominant area, an optional utility
 * rail sits beside it only when both the rail exists and the width allows it, and the
 * remaining controls stack underneath.
 *
 * When no [utilityRail] is supplied the graph consumes the full available width; no empty
 * column is reserved for data the app does not have.
 */
@Composable
fun VstGraphWorkspace(
    modifier: Modifier = Modifier,
    utilityRail: (@Composable ColumnScope.() -> Unit)? = null,
    sideBySideAtWideWidth: Boolean = false,
    scrollContent: Boolean = false,
    graph: @Composable BoxScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        if (sideBySideAtWideWidth && maxWidth >= SideBySideBreakpoint) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1.15f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                ) {
                    Box(modifier = Modifier.fillMaxWidth(), content = graph)
                }
                Column(
                    modifier =
                        Modifier
                            .weight(0.85f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    content = content,
                )
            }
        } else {
            val columnModifier =
                if (scrollContent) {
                    Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                } else {
                    Modifier.fillMaxWidth()
                }
            Column(
                modifier = columnModifier,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val rail = utilityRail
                    if (rail != null && maxWidth >= RailBreakpoint) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(modifier = Modifier.weight(1f), content = graph)
                            Column(
                                modifier = Modifier.width(UtilityRailWidth),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                content = rail,
                            )
                        }
                    } else {
                        Box(modifier = Modifier.fillMaxWidth(), content = graph)
                    }
                }
                content()
            }
        }
    }
}
