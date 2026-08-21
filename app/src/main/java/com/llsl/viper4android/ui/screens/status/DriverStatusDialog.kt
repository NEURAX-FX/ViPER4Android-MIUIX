package com.llsl.viper4android.ui.screens.status

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.llsl.viper4android.R
import com.llsl.viper4android.daemon.DaemonBackendStatus
import com.llsl.viper4android.daemon.DaemonConnectionState
import com.llsl.viper4android.daemon.DaemonModePreference
import com.llsl.viper4android.daemon.DaemonOwnerState
import com.llsl.viper4android.daemon.DaemonRuntimeStatus
import com.llsl.viper4android.ui.components.viper.ViperDialog
import com.llsl.viper4android.ui.screens.main.DriverStatus
import com.llsl.viper4android.ui.theme.ViperType
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Driver and root-daemon diagnostics.
 *
 * `daemonStatus` is null when the daemon's state file cannot be read, which is
 * the normal case on an install without the root daemon. `daemonLinkState` and
 * `daemonBackendStatus` are null when the service never obtained a daemon
 * backend at all; that is reported as "no backend" rather than as a
 * disconnected one, because the two are different problems for the user.
 */
@Composable
fun DriverStatusDialog(
    driverStatus: DriverStatus,
    daemonMode: DaemonModePreference,
    daemonStatus: DaemonRuntimeStatus?,
    daemonLinkState: DaemonConnectionState?,
    daemonBackendStatus: DaemonBackendStatus?,
    onDismiss: () -> Unit,
) {
    ViperDialog(
        show = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.menu_driver_status),
        confirmText = stringResource(R.string.action_close),
        onConfirm = onDismiss,
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (!driverStatus.installed) {
                    Text(
                        text = stringResource(R.string.driver_not_found),
                        style = ViperType.body,
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

                DaemonStatusSection(
                    mode = daemonMode,
                    status = daemonStatus,
                    linkState = daemonLinkState,
                    backendStatus = daemonBackendStatus,
                )
            }
        },
    )
}

@Composable
private fun DaemonStatusSection(
    mode: DaemonModePreference,
    status: DaemonRuntimeStatus?,
    linkState: DaemonConnectionState?,
    backendStatus: DaemonBackendStatus?,
) {
    val yes = stringResource(R.string.status_yes)
    val no = stringResource(R.string.status_no)

    Column {
        Text(
            text = stringResource(R.string.daemon_status_section),
            style = ViperType.section,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(Modifier.height(8.dp))

        StatusRow(
            label = stringResource(R.string.daemon_selected_mode),
            value = stringResource(daemonModeLabel(mode)),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_availability),
            value =
                stringResource(
                    if (status != null) R.string.daemon_running else R.string.daemon_not_running,
                ),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_app_link),
            value = stringResource(daemonLinkLabel(linkState)),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_backend_owner),
            value = stringResource(daemonBackendLabel(backendStatus)),
        )

        // Every field below comes from the daemon's own state file, so without it
        // there is nothing truthful to show.
        if (status == null) return@Column

        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_restore),
            value = if (status.restoreEnabled) yes else no,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_driver_attached),
            value = if (status.driverConnected) yes else no,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_route_known),
            value = if (status.routeKnown) yes else no,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_route_source),
            value =
                stringResource(
                    when {
                        !status.routeKnown -> R.string.status_unknown
                        status.routeFromApp -> R.string.daemon_route_source_app
                        else -> R.string.daemon_route_source_probe
                    },
                ),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_route_key),
            value = status.routeKeyHash.take(ROUTE_KEY_DIGITS).ifBlank { stringResource(R.string.status_unknown) },
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_app_endpoint),
            value =
                stringResource(
                    if (status.appListening) {
                        R.string.daemon_endpoint_listening
                    } else {
                        R.string.daemon_endpoint_unbound
                    },
                ),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_app_client),
            value =
                stringResource(
                    if (status.appConnected) {
                        R.string.daemon_link_connected
                    } else {
                        R.string.daemon_client_none
                    },
                ),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_app_route_reports),
            value = status.appRouteReports.toString(),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_app_snapshots),
            value = status.appSnapshotCommands.toString(),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_rejected_peers),
            value = status.appRejectedPeers.toString(),
            // Any refusal means a peer failed the daemon's uid check, which is the
            // failure that silently blocks the App entirely.
            emphasize = status.appRejectedPeers > 0,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_restores),
            value =
                stringResource(
                    R.string.daemon_restores_value,
                    status.restoresAccepted,
                    status.restoresRejected,
                    status.restoresBypassed,
                ),
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_owner_state),
            value = stringResource(daemonOwnerLabel(status.ownerState)),
            // A failed owner is the case where the daemon looks healthy but nothing
            // holds an effect handle, so it must not read as an ordinary state.
            emphasize = status.ownerState == DaemonOwnerState.Failed,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_owner_effect),
            value =
                if (status.ownerHoldsEffect) {
                    status.ownerEffectId.toString()
                } else {
                    stringResource(R.string.daemon_owner_effect_none)
                },
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_owner_restarts),
            value = status.ownerRestarts.toString(),
            emphasize = status.ownerRestarts > 0,
        )
        ViperDivider(modifier = Modifier.padding(vertical = 4.dp))
        StatusRow(
            label = stringResource(R.string.daemon_tracked_sessions),
            value = status.trackedSessions.toString(),
        )
    }
}

private fun daemonModeLabel(mode: DaemonModePreference): Int =
    when (mode) {
        DaemonModePreference.Auto -> R.string.settings_daemon_mode_auto
        DaemonModePreference.DaemonOnly -> R.string.settings_daemon_mode_daemon_only
        DaemonModePreference.DriverOnly -> R.string.settings_daemon_mode_driver_only
    }

private fun daemonOwnerLabel(state: DaemonOwnerState): Int =
    when (state) {
        DaemonOwnerState.Absent -> R.string.daemon_owner_absent
        DaemonOwnerState.Starting -> R.string.daemon_owner_starting
        DaemonOwnerState.Owned -> R.string.daemon_owner_owned
        DaemonOwnerState.Failed -> R.string.daemon_owner_failed
    }

private fun daemonLinkLabel(state: DaemonConnectionState?): Int =
    when (state) {
        null -> R.string.daemon_link_unavailable
        DaemonConnectionState.Disconnected -> R.string.daemon_link_disconnected
        DaemonConnectionState.Connecting -> R.string.daemon_link_connecting
        DaemonConnectionState.Connected -> R.string.daemon_link_connected
        DaemonConnectionState.Syncing -> R.string.daemon_link_syncing
        DaemonConnectionState.Ready -> R.string.daemon_link_ready
        DaemonConnectionState.Degraded -> R.string.daemon_link_degraded
    }

private fun daemonBackendLabel(status: DaemonBackendStatus?): Int =
    when (status) {
        null -> R.string.daemon_link_unavailable
        DaemonBackendStatus.Unknown -> R.string.status_unknown
        DaemonBackendStatus.Active -> R.string.daemon_backend_active
        DaemonBackendStatus.Fallback -> R.string.daemon_backend_fallback
    }

/** Enough SHA-256 hex to compare two routes by eye without wrapping the row. */
private const val ROUTE_KEY_DIGITS = 12

@Composable
private fun StatusRow(
    label: String,
    value: String,
    // Draws the value in the error colour. Used for counters whose non-zero value
    // is itself the fault, so it is not lost among the neutral diagnostics.
    emphasize: Boolean = false,
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
            style = ViperType.caption,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Text(
            text = value,
            style = ViperType.value,
            color =
                if (emphasize) {
                    MiuixTheme.colorScheme.error
                } else {
                    MiuixTheme.colorScheme.onSurface
                },
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
