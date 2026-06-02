package com.llsl.viper4android.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.screens.main.DriverStatus
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SettingsDialog(
    autoStartEnabled: Boolean,
    globalModeEnabled: Boolean,
    showCurvePreviews: Boolean,
    aidlModeActive: Boolean,
    driverStatus: DriverStatus,
    appVersionName: String,
    onAutoStartChanged: (Boolean) -> Unit,
    onGlobalModeChanged: (Boolean) -> Unit,
    onShowCurvePreviewsChanged: (Boolean) -> Unit,
    onImportPreset: () -> Unit,
    onImportKernel: () -> Unit,
    onImportVdc: () -> Unit,
    onDebugUnlocked: () -> Unit,
    onDismiss: () -> Unit,
) {
    var driverTapCount by remember { mutableIntStateOf(0) }
    val unknown = stringResource(R.string.status_unknown)
    val driverVersion = driverStatus.versionName.ifBlank { unknown }
    val driverArch = driverStatus.architecture.ifBlank { unknown }
    val aidlStatus = stringResource(if (aidlModeActive) R.string.status_active else R.string.status_inactive)

    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.menu_settings),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroupCard(title = stringResource(R.string.settings_playback_section)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_auto_start),
                    summary = stringResource(R.string.settings_auto_start_summary),
                    checked = autoStartEnabled,
                    onCheckedChange = onAutoStartChanged,
                )
                SettingsRowDivider()
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_global_mode),
                    summary = stringResource(R.string.settings_global_mode_summary),
                    checked = globalModeEnabled,
                    onCheckedChange = onGlobalModeChanged,
                )
            }

            SettingsGroupCard(title = stringResource(R.string.settings_display_section)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_show_curve_previews),
                    summary = stringResource(R.string.settings_show_curve_previews_summary),
                    checked = showCurvePreviews,
                    onCheckedChange = onShowCurvePreviewsChanged,
                )
            }

            SettingsGroupCard(title = stringResource(R.string.settings_files_section)) {
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_preset),
                    summary = stringResource(R.string.settings_import_preset_summary),
                    onClick = onImportPreset,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_kernel),
                    summary = stringResource(R.string.settings_import_kernel_summary),
                    onClick = onImportKernel,
                )
                SettingsRowDivider()
                SettingsActionRow(
                    title = stringResource(R.string.settings_import_vdc),
                    summary = stringResource(R.string.settings_import_vdc_summary),
                    onClick = onImportVdc,
                )
            }

            SettingsGroupCard(title = stringResource(R.string.settings_about_section)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_driver_version),
                    value = driverVersion,
                    onClick = {
                        driverTapCount++
                        if (driverTapCount >= 7) {
                            driverTapCount = 0
                            onDebugUnlocked()
                        }
                    },
                )
                SettingsRowDivider()
                SettingsInfoRow(
                    title = stringResource(R.string.settings_driver_arch),
                    value = driverArch,
                )
                SettingsRowDivider()
                SettingsInfoRow(
                    title = stringResource(R.string.settings_aidl_mode),
                    value = aidlStatus,
                    statusColor = if (aidlModeActive) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.outline,
                )
                SettingsRowDivider()
                SettingsInfoRow(
                    title = stringResource(R.string.settings_app_version),
                    value = appVersionName,
                )
            }
        }
        },
    )
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SettingsSectionTitle(title)
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        color = MiuixTheme.colorScheme.primary,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(horizontal = 6.dp),
    )
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextBlock(
            title = title,
            summary = summary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null,
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    summary: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SettingsTextBlock(
            title = title,
            summary = summary,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = ">",
            color = MiuixTheme.colorScheme.outline,
            fontSize = 15.sp,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}

@Composable
private fun SettingsInfoRow(
    title: String,
    value: String,
    statusColor: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    } else {
        Modifier.fillMaxWidth()
    }

    Row(
        modifier = rowModifier.padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        if (statusColor != null) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = value,
            color = MiuixTheme.colorScheme.outline,
            fontSize = 13.sp,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}

@Composable
private fun SettingsTextBlock(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            color = MiuixTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = summary,
            color = MiuixTheme.colorScheme.outline,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun SettingsRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 18.dp))
}
