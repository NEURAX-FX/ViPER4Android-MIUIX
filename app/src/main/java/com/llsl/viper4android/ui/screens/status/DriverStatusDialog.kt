package com.llsl.viper4android.ui.screens.status

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.screens.main.DriverStatus
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun DriverStatusDialog(
    driverStatus: DriverStatus,
    onDismiss: () -> Unit,
) {
    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.menu_driver_status),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            if (!driverStatus.installed) {
                Text(
                    text = stringResource(R.string.driver_not_found),
                    style = MiuixTheme.textStyles.body1,
                    color = MiuixTheme.colorScheme.error,
                )
            } else {
                Column {
                    StatusRow(
                        label = stringResource(R.string.driver_version_code),
                        value = driverStatus.versionCode.toString(),
                    )
                    ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_version_name),
                        value = driverStatus.versionName,
                    )
                    ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_architecture),
                        value = driverStatus.architecture,
                    )
                    ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_streaming),
                        value =
                            if (driverStatus.streaming) {
                                stringResource(R.string.status_active)
                            } else {
                                stringResource(R.string.status_inactive)
                            },
                    )
                    ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
                    StatusRow(
                        label = stringResource(R.string.driver_sampling_rate),
                        value =
                            if (driverStatus.samplingRate > 0) {
                                "${driverStatus.samplingRate} Hz"
                            } else {
                                stringResource(R.string.status_unknown)
                            },
                    )
                }
            }
        },
    )
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
private fun ViperDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.dividerLine),
    )
}
