package com.llsl.viper4android.ui.screens.device

import android.text.format.DateUtils
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperTextFieldDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DeviceDialog(
    devices: List<DeviceSettings>,
    activeDeviceId: String,
    onRename: (String, String) -> Unit,
    onLoad: (String) -> Unit,
    onSave: (String) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedDeviceId by remember { mutableStateOf<String?>(null) }
    var renamingDeviceId by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf(TextFieldValue("")) }

    val selectedDevice = selectedDeviceId?.let { id -> devices.find { it.deviceId == id } }

    if (renamingDeviceId != null) {
        ViperTextFieldDialog(
            show = true,
            onDismissRequest = { renamingDeviceId = null },
            title = stringResource(R.string.device_rename_title),
            value = renameInput,
            onValueChange = { renameInput = it },
            label = stringResource(R.string.device_rename_hint),
            confirmText = stringResource(R.string.action_rename),
            onConfirm = {
                val name = renameInput.text.trim()
                val deviceId = renamingDeviceId
                if (name.isNotBlank() && deviceId != null) {
                    onRename(deviceId, name)
                    renamingDeviceId = null
                }
            },
            confirmEnabled = renameInput.text.isNotBlank(),
            dismissText = stringResource(R.string.action_cancel),
            onDismiss = { renamingDeviceId = null },
        )
        return
    }

    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = if (selectedDevice != null) "" else stringResource(R.string.device_dialog_title),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            if (selectedDevice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    ViperIconButton(onClick = { selectedDeviceId = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                    Text(
                        text = selectedDevice.deviceName,
                        style = MiuixTheme.textStyles.title3,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ViperIconButton(onClick = {
                        renameInput = TextFieldValue(selectedDevice.deviceName)
                        renamingDeviceId = selectedDevice.deviceId
                    }) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_rename),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                DeviceDetailView(
                    device = selectedDevice,
                    isActive = selectedDevice.deviceId == activeDeviceId,
                    onLoad = { onLoad(selectedDevice.deviceId) },
                    onSave = { onSave(selectedDevice.deviceId) },
                    onDelete = {
                        onDelete(selectedDevice.deviceId)
                        selectedDeviceId = null
                    },
                )
            } else {
                DeviceListView(
                    devices = devices,
                    activeDeviceId = activeDeviceId,
                    onSelect = { selectedDeviceId = it.deviceId },
                )
            }
        },
    )
}

@Composable
private fun DeviceListView(
    devices: List<DeviceSettings>,
    activeDeviceId: String,
    onSelect: (DeviceSettings) -> Unit,
) {
    if (devices.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.device_no_devices),
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
        }
        return
    }

    val sorted =
        remember(devices, activeDeviceId) {
            devices.sortedWith(
                compareByDescending<DeviceSettings> { it.deviceId == activeDeviceId }
                    .thenByDescending { it.lastConnected },
            )
        }

    LazyColumn {
        items(sorted, key = { it.deviceId }) { device ->
            val isActive = device.deviceId == activeDeviceId
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(device) }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = deviceIcon(device),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(Color(0xFF4CAF50))
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            text = device.deviceName,
                            style = MiuixTheme.textStyles.body2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isActive) {
                        Text(
                            text =
                                DateUtils
                                    .getRelativeTimeSpanString(
                                        device.lastConnected,
                                        System.currentTimeMillis(),
                                        DateUtils.MINUTE_IN_MILLIS,
                                    ).toString(),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                    }
                }
                ViperIconButton(onClick = { onSelect(device) }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            }
            HorizontalDivider()
        }
    }
}

private val BUILTIN_DEVICE_IDS = setOf("speaker", "wired_headphone")

@Composable
private fun DeviceDetailView(
    device: DeviceSettings,
    isActive: Boolean,
    onLoad: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
) {
    val isBuiltIn = device.deviceId in BUILTIN_DEVICE_IDS
    val canDelete = !isActive && !isBuiltIn
    Column {
        StatusRow(
            label = stringResource(R.string.device_label_type),
            value = deviceTypeName(device),
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.device_label_address),
            value = if (device.deviceId == "speaker" || device.deviceId == "wired_headphone") "-" else device.deviceId,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.label_mode),
            value =
                if (device.isHeadphone) {
                    stringResource(R.string.device_mode_headphone)
                } else {
                    stringResource(R.string.device_mode_speaker)
                },
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.device_label_last_conn),
            value =
                if (isActive) {
                    "-"
                } else {
                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        .format(Date(device.lastConnected))
                },
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ActionItem(
                icon = Icons.Default.SettingsBackupRestore,
                label = stringResource(R.string.action_load),
                onClick = onLoad,
            )
            ActionItem(
                icon = Icons.Default.Sync,
                label = stringResource(R.string.action_update),
                onClick = onSave,
            )
            ActionItem(
                icon = Icons.Default.Delete,
                label = stringResource(R.string.action_delete),
                onClick = onDelete,
                enabled = canDelete,
                tint =
                    if (!canDelete) {
                        MiuixTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MiuixTheme.colorScheme.error
                    },
            )
        }
    }
}

@Composable
private fun StatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Text(
            text = value,
            style = MiuixTheme.textStyles.body2,
        )
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    tint: Color = MiuixTheme.colorScheme.onSurface,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clickable(enabled = enabled) { onClick() }
                .padding(8.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = tint)
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MiuixTheme.textStyles.body2, color = tint)
    }
}

private fun deviceIcon(device: DeviceSettings) =
    when {
        device.isHeadphone -> Icons.Default.Headphones
        else -> Icons.Default.Speaker
    }

@Composable
private fun deviceTypeName(device: DeviceSettings): String =
    when {
        device.deviceId == "speaker" -> stringResource(R.string.device_type_speaker)
        device.deviceId == "wired_headphone" -> stringResource(R.string.device_type_wired)
        device.isHeadphone -> stringResource(R.string.device_type_bluetooth)
        else -> stringResource(R.string.device_type_speaker)
    }
