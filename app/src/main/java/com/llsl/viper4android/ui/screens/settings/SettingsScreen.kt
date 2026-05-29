package com.llsl.viper4android.ui.screens.settings

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.screens.main.DriverStatus
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsDialog(
    autoStartEnabled: Boolean,
    globalModeEnabled: Boolean,
    aidlModeActive: Boolean,
    driverStatus: DriverStatus,
    appVersionName: String,
    onAutoStartChanged: (Boolean) -> Unit,
    onGlobalModeChanged: (Boolean) -> Unit,
    onImportPreset: () -> Unit,
    onImportKernel: () -> Unit,
    onDebugUnlocked: () -> Unit,
    onImportVdc: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val tapCount = remember { mutableIntStateOf(0) }
    val debugModeEnabledStr = stringResource(R.string.debug_mode_enabled)

    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.menu_settings),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            Column {
                SettingsToggleRow(
                    label = stringResource(R.string.settings_auto_start),
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartChanged,
                )
                SettingsDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsToggleRow(
                    label = stringResource(R.string.settings_global_mode),
                    checked = globalModeEnabled,
                    onCheckedChange = onGlobalModeChanged,
                )
                SettingsDivider(modifier = Modifier.padding(vertical = 8.dp))
                Text(
                    text = stringResource(R.string.settings_files_section),
                    style = MiuixTheme.textStyles.title4,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                SettingsActionRow(
                    label = stringResource(R.string.settings_import_preset),
                    onClick = onImportPreset,
                )
                SettingsActionRow(
                    label = stringResource(R.string.settings_import_kernel),
                    onClick = onImportKernel,
                )
                SettingsActionRow(
                    label = stringResource(R.string.settings_import_vdc),
                    onClick = onImportVdc,
                )
                SettingsDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                tapCount.intValue++
                                if (tapCount.intValue >= 7) {
                                    tapCount.intValue = 0
                                    onDebugUnlocked()
                                    Toast
                                        .makeText(
                                            context,
                                            debugModeEnabledStr,
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                }
                            }.padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.settings_driver_version),
                        style = MiuixTheme.textStyles.body2,
                        color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Text(
                        text = if (driverStatus.installed) driverStatus.versionName else "-",
                        style = MiuixTheme.textStyles.body2,
                    )
                }
                SettingsDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_driver_arch),
                    value = if (driverStatus.installed) driverStatus.architecture else "-",
                )
                if (!aidlModeActive) {
                    SettingsDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.settings_aidl_mode),
                            style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                        )
                        Canvas(modifier = Modifier.size(6.dp)) {
                            drawCircle(Color(0xFF4CAF50))
                        }
                    }
                }
                SettingsDivider(modifier = Modifier.padding(vertical = 4.dp))
                SettingsInfoRow(
                    label = stringResource(R.string.settings_app_version),
                    value = appVersionName,
                )
            }
        },
    )
}

@Composable
private fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

@Composable
private fun SettingsInfoRow(
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
private fun SettingsActionRow(
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.body1,
            color = MiuixTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SettingsDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
    )
}
