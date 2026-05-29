package com.llsl.viper4android.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.viper.ViperDialog
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LabeledDropdown(
    label: String,
    selectedValue: String,
    options: List<String>,
    onOptionSelected: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onDeleteOption: ((Int, String) -> Unit)? = null,
) {
    var deleteTarget by remember { mutableStateOf<Pair<Int, String>?>(null) }
    val selectedIndex = options.indexOf(selectedValue).takeIf { it >= 0 } ?: 0
    val canDeleteSelected = onDeleteOption != null && selectedIndex > 0 && selectedValue.isNotEmpty()

    OverlayDropdownPreference(
        title = label,
        items = options,
        selectedIndex = selectedIndex,
        onSelectedIndexChange = { index ->
            onOptionSelected(index, options[index])
        },
        enabled = enabled,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
        bottomAction =
            if (canDeleteSelected) {
                {
                    TextButton(
                        text = stringResource(R.string.action_delete),
                        onClick = { deleteTarget = selectedIndex to selectedValue },
                        enabled = enabled,
                        colors = ButtonDefaults.textButtonColors(
                            textColor = MiuixTheme.colorScheme.error,
                        ),
                    )
                }
            } else {
                null
            },
    )

    deleteTarget?.let { (index, name) ->
        ViperDialog(
            show = true,
            onDismissRequest = { deleteTarget = null },
            title = stringResource(R.string.delete_file_title),
            content = {
                Text(
                    text = stringResource(R.string.delete_file_message, name),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.body2,
                )
            },
            confirmText = stringResource(R.string.action_delete),
            onConfirm = {
                onDeleteOption?.invoke(index, name)
                deleteTarget = null
            },
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { deleteTarget = null },
        )
    }
}
