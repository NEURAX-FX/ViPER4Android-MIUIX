package com.llsl.viper4android.ui.screens.preset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperTextFieldDialog
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PresetDialog(
    presets: List<Preset>,
    onSave: (String) -> Unit,
    onLoad: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showSaveInput by remember { mutableStateOf(false) }
    var saveInputName by remember { mutableStateOf(TextFieldValue("")) }
    var renamingId by remember { mutableLongStateOf(-1L) }
    var renameInputName by remember { mutableStateOf(TextFieldValue("")) }
    var pendingDeletePreset by remember { mutableStateOf<Preset?>(null) }

    fun commitPendingDelete() {
        pendingDeletePreset?.let { onDelete(it.id) }
        pendingDeletePreset = null
    }

    val visiblePresets =
        remember(presets, pendingDeletePreset) {
            if (pendingDeletePreset != null) {
                presets.filter { it.id != pendingDeletePreset!!.id }
            } else {
                presets
            }
        }

    if (showSaveInput) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { showSaveInput = false },
            title = stringResource(R.string.preset_save_title),
            value = saveInputName,
            onValueChange = { saveInputName = it },
            label = stringResource(R.string.preset_name_hint),
            confirmText = stringResource(R.string.action_save),
            onConfirm = {
                val name = saveInputName.text.trim()
                if (name.isNotBlank()) {
                    onSave(name)
                    saveInputName = TextFieldValue("")
                    showSaveInput = false
                }
            },
            confirmEnabled = saveInputName.text.isNotBlank(),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { showSaveInput = false },
        )
        return
    }

    if (renamingId >= 0) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { renamingId = -1L },
            title = stringResource(R.string.preset_rename_title),
            value = renameInputName,
            onValueChange = { renameInputName = it },
            label = stringResource(R.string.preset_name_hint),
            confirmText = stringResource(R.string.action_rename),
            onConfirm = {
                val name = renameInputName.text.trim()
                if (name.isNotBlank()) {
                    onRename(renamingId, name)
                    renamingId = -1L
                }
            },
            confirmEnabled = renameInputName.text.isNotBlank(),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { renamingId = -1L },
        )
        return
    }

    ViperDialog(
        show = true,
        onDismissRequest = {
            commitPendingDelete()
            onDismiss()
        },
        title = stringResource(R.string.menu_presets),
        confirmText = stringResource(R.string.preset_save_current),
        onConfirm = {
            showSaveInput = true
            saveInputName = TextFieldValue("")
        },
        dismissText = stringResource(R.string.action_close),
        onDismiss = {
            commitPendingDelete()
            onDismiss()
        },
        content = {
            Column {
                if (visiblePresets.isEmpty() && pendingDeletePreset == null) {
                    Text(
                        text = stringResource(R.string.preset_empty),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 300.dp),
                    ) {
                        items(visiblePresets, key = { it.id }) { preset ->
                            PresetItem(
                                preset = preset,
                                onLoad = {
                                    commitPendingDelete()
                                    onLoad(preset.id)
                                },
                                onDelete = {
                                    commitPendingDelete()
                                    pendingDeletePreset = preset
                                },
                                onRename = {
                                    renameInputName = TextFieldValue(preset.name)
                                    renamingId = preset.id
                                },
                            )
                            HorizontalDivider()
                        }
                        pendingDeletePreset?.let { deleted ->
                            item(key = "deleted_${deleted.id}") {
                                DeletedPresetItem(
                                    preset = deleted,
                                    onRestore = {
                                        pendingDeletePreset = null
                                    },
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PresetItem(
    preset: Preset,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onLoad)
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(if (preset.fxType == 1) R.string.tab_headphone else R.string.tab_speaker),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        Row {
            ViperIconButton(onClick = onRename) {
                Icon(
                    Icons.Default.Edit,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            ViperIconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun DeletedPresetItem(
    preset: Preset,
    onRestore: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = preset.name,
                style = MiuixTheme.textStyles.body1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.label_deleted),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.error.copy(alpha = 0.7f),
            )
        }
        ViperIconButton(onClick = onRestore) {
            Icon(
                Icons.Default.Restore,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
            )
        }
    }
}
