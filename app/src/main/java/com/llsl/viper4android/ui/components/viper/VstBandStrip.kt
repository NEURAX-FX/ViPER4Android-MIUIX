package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.ui.theme.ViperType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One selectable entry in a [VstBandStrip]. */
data class VstBandItem(
    val id: String,
    val title: String,
    val value: String,
    val color: Color? = null,
    val enabled: Boolean = true,
)

/**
 * Explicit, touch-friendly horizontal band/filter selector.
 *
 * Selection is always a visible single tap; there are no hidden long-press actions.
 */
@Composable
fun VstBandStrip(
    items: List<VstBandItem>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val selected = selectedIndex.coerceIn(items.indices)

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
            val isSelected = index == selected
            val accent = item.color ?: MiuixTheme.colorScheme.primary
            Column(
                modifier = Modifier
                    .defaultMinSize(minWidth = 68.dp, minHeight = 48.dp)
                    .background(
                        color = if (isSelected) {
                            accent.copy(alpha = 0.18f)
                        } else {
                            MiuixTheme.colorScheme.surfaceContainerHigh
                        },
                        shape = RoundedCornerShape(10.dp),
                    )
                    .semantics {
                        this.selected = isSelected
                        contentDescription = "${item.title} ${item.value}"
                    }
                    .then(
                        if (item.enabled) {
                            Modifier.clickable { onSelected(index) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = item.title,
                    style = ViperType.caption,
                    color = if (isSelected) accent else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = item.value,
                    style = ViperType.value,
                    color = if (isSelected) {
                        MiuixTheme.colorScheme.onSurface
                    } else {
                        MiuixTheme.colorScheme.onSurfaceVariantSummary
                    },
                )
            }
        }
    }
}
