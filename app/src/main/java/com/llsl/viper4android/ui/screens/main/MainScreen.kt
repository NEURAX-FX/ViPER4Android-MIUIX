package com.llsl.viper4android.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.effect.EffectState
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperPowerButton
import com.llsl.viper4android.ui.components.viper.ViperScaffold
import com.llsl.viper4android.ui.components.viper.ViperTopBar
import com.llsl.viper4android.ui.EffectEditorActivity
import com.llsl.viper4android.ui.screens.debug.DebugLogDialog
import com.llsl.viper4android.ui.screens.device.DeviceDialog
import com.llsl.viper4android.ui.screens.preset.PresetDialog
import com.llsl.viper4android.ui.screens.settings.SettingsDialog
import com.llsl.viper4android.ui.screens.status.DriverStatusDialog
import com.llsl.viper4android.ui.screens.editor.EditorKind
import com.llsl.viper4android.ui.theme.ViperType
import com.llsl.viper4android.ui.theme.viperBounce
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.saveSettingsOnBackground()
    }

    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val presets by viewModel.presetList.collectAsStateWithLifecycle()
    val deviceSettings by viewModel.deviceSettingsList.collectAsStateWithLifecycle()
    val driverStatus by viewModel.driverStatus.collectAsStateWithLifecycle()
    val autoStart by viewModel.autoStartEnabled.collectAsStateWithLifecycle()
    val globalMode by viewModel.globalModeEnabled.collectAsStateWithLifecycle()
    val aidlMode by viewModel.aidlModeEnabled.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugModeEnabled.collectAsStateWithLifecycle()
    val showCurvePreviews by viewModel.showCurvePreviews.collectAsStateWithLifecycle()

    var showPresetDialog by remember { mutableStateOf(false) }
    var showDriverStatusDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val appVersionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
        }
    val clearAllProgressStr = stringResource(R.string.preset_clear_all_progress)
    val clearedStr = stringResource(R.string.preset_cleared)
    val importSuccessStr = stringResource(R.string.import_success)
    val importFailedStr = stringResource(R.string.import_failed)

    val importPresetStr = stringResource(R.string.settings_import_preset)
    val importPresetLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importPresetFiles(uris, importPresetStr, importSuccessStr) { success ->
                    Toast.makeText(context, if (success) importSuccessStr else importFailedStr, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importKernelStr = stringResource(R.string.settings_import_kernel)
    val importKernelLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importKernels(uris, importKernelStr, importSuccessStr) { success ->
                    Toast.makeText(context, if (success) importSuccessStr else importFailedStr, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importVdcStr = stringResource(R.string.settings_import_vdc)
    val importVdcLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importVdcs(uris, importVdcStr, importSuccessStr) { success ->
                    Toast.makeText(context, if (success) importSuccessStr else importFailedStr, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val scrollBehavior = MiuixScrollBehavior()
    ViperScaffold(
        topBar = {
            ViperTopBar(
                title = stringResource(R.string.app_name),
                largeTitle = stringResource(R.string.app_expanded_name),
                deviceName = state.activeDeviceName,
                scrollBehavior = scrollBehavior,
                actions = {
                    if (debugMode) {
                        ViperIconButton(onClick = { showDebugLog = true }) {
                            Icon(
                                imageVector = Icons.Default.BugReport,
                                contentDescription = stringResource(R.string.debug_log_title),
                            )
                        }
                    }
                    ViperPowerButton(
                        checked = state.masterEnable,
                        onCheckedChange = viewModel::setMasterEnabled,
                        contentDescription = stringResource(R.string.master_enable),
                    )
                },
            )
        },
    ) { paddingValues ->
        EffectList(
            headerContent = {
                MainActionRow(
                    onPresetClick = { showPresetDialog = true },
                    onDevicesClick = { showDeviceDialog = true },
                    onDriverStatusClick = { showDriverStatusDialog = true },
                    onSettingsClick = { showSettingsDialog = true },
                )
            },
            state = state,
            viewModel = viewModel,
            aidlModeActive = aidlMode,
            showCurvePreviews = showCurvePreviews,
            onOpenEditor = { kind ->
                context.startActivity(EffectEditorActivity.createIntent(context, kind))
            },
            modifier =
                Modifier
                    .padding(paddingValues)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
        )

        if (showPresetDialog) {
            PresetDialog(
                presets = presets,
                onSave = viewModel::savePreset,
                onLoad = { id ->
                    viewModel.loadPreset(id)
                    showPresetDialog = false
                },
                onDelete = viewModel::deletePreset,
                onRename = viewModel::renamePreset,
                onUpdate = viewModel::updatePreset,
                onClearAll = {
                    viewModel.clearAllPresets(clearAllProgressStr, clearedStr) { count ->
                        Toast.makeText(context, "$clearedStr: $count", Toast.LENGTH_SHORT).show()
                    }
                },
                onDismiss = { showPresetDialog = false },
            )
        }

        if (showDriverStatusDialog) {
            LaunchedEffect(Unit) {
                while (true) {
                    viewModel.queryDriverStatus()
                    delay(500)
                }
            }
            DriverStatusDialog(
                driverStatus = driverStatus,
                onDismiss = { showDriverStatusDialog = false },
            )
        }

        if (showDebugLog) {
            DebugLogDialog(
                onDisableDebug = {
                    viewModel.disableDebugMode()
                    showDebugLog = false
                },
                onDismiss = { showDebugLog = false },
            )
        }

        if (showDeviceDialog) {
            DeviceDialog(
                devices = deviceSettings,
                activeDeviceId = state.activeDeviceId,
                onRename = viewModel::renameDevice,
                onLoad = viewModel::loadDevicePreset,
                onUpdate = viewModel::saveDevicePreset,
                onDelete = viewModel::deleteDeviceSettings,
                onDismiss = { showDeviceDialog = false },
            )
        }

        if (showSettingsDialog) {
            LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
            SettingsDialog(
                autoStartEnabled = autoStart,
                globalModeEnabled = globalMode,
                showCurvePreviews = showCurvePreviews,
                aidlModeActive = aidlMode,
                driverStatus = driverStatus,
                appVersionName = appVersionName,
                onAutoStartChanged = viewModel::toggleAutoStart,
                onGlobalModeChanged = viewModel::toggleGlobalMode,
                onShowCurvePreviewsChanged = viewModel::setShowCurvePreviews,
                onImportPreset = { importPresetLauncher.launch(arrayOf("application/json", "*/*")) },
                onImportKernel = {
                    importKernelLauncher.launch(arrayOf("audio/*", "application/octet-stream", "*/*"))
                },
                onImportVdc = { importVdcLauncher.launch(arrayOf("*/*")) },
                onDebugUnlocked = viewModel::enableDebugMode,
                onDismiss = { showSettingsDialog = false },
            )
        }
    }
}

@Composable
private fun MainActionRow(
    onPresetClick: () -> Unit,
    onDevicesClick: () -> Unit,
    onDriverStatusClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MainActionButton(Icons.Default.LibraryMusic, stringResource(R.string.menu_presets), onPresetClick, Modifier.weight(1f))
        MainActionButton(Icons.Default.Devices, stringResource(R.string.menu_devices), onDevicesClick, Modifier.weight(1f))
        MainActionButton(Icons.Default.Info, stringResource(R.string.menu_driver_status), onDriverStatusClick, Modifier.weight(1f))
        MainActionButton(Icons.Default.Settings, stringResource(R.string.menu_settings), onSettingsClick, Modifier.weight(1f))
    }
}

@Composable
private fun MainActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.86f))
                .viperBounce(pressedScale = 0.94f, onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantActions,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ViperType.caption,
        )
    }
}

@Composable
private fun EffectList(
    headerContent: @Composable () -> Unit,
    state: EffectState,
    viewModel: MainViewModel,
    aidlModeActive: Boolean,
    showCurvePreviews: Boolean,
    onOpenEditor: (EditorKind) -> Unit,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (state.masterEnable) 1f else 0.38f,
        animationSpec = tween(durationMillis = 200),
        label = "effectListAlpha",
    )
    LazyColumn(
        modifier = modifier.fillMaxSize().graphicsLayer { this.alpha = alpha },
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { Spacer(modifier = Modifier.height(4.dp)) }
        item { headerContent() }
        item { MasterLimiterRows(state, viewModel) }
        item { PlaybackGainSection(state, viewModel) }
        item { LUFSTargetingSection(state, viewModel) }
        item {
            MultibandCompressorSection(
                state = state,
                viewModel = viewModel,
                showCurvePreview = showCurvePreviews,
                onOpenEditor = { onOpenEditor(EditorKind.MULTIBAND_COMPRESSOR) },
            )
        }
        item { FetCompressorSection(state, viewModel) }
        item { DdcSection(state, viewModel) }
        item { SpectrumExtensionSection(state, viewModel) }
        item {
            EqualizerSection(
                state = state,
                viewModel = viewModel,
                showCurvePreview = showCurvePreviews,
                onOpenEditor = { onOpenEditor(EditorKind.FIR_EQUALIZER) },
            )
        }
        item {
            DynamicEqSection(
                state = state,
                viewModel = viewModel,
                showCurvePreview = showCurvePreviews,
                onOpenEditor = { onOpenEditor(EditorKind.DYNAMIC_EQUALIZER) },
            )
        }
        item { ConvolverSection(state, viewModel) }
        if (shouldShowIemCard(aidlModeActive)) {
            item {
                IemSection(
                    state = state,
                    viewModel = viewModel,
                    onOpenEditor = { onOpenEditor(EditorKind.IEM) },
                )
            }
        }
        item { FieldSurroundSection(state, viewModel) }
        item { DiffSurroundSection(state, viewModel) }
        item { StereoImagerSection(state, viewModel) }
        item { HeadphoneSurroundSection(state, viewModel) }
        item { ReverberationSection(state, viewModel) }
        item { DynamicSystemSection(state, viewModel) }
        item { TubeSimulatorSection(state, viewModel) }
        item { PsychoacousticBassSection(state, viewModel) }
        item { ViperBassSection(state, viewModel) }
        item { ViperBassMonoSection(state, viewModel) }
        item { ViperClaritySection(state, viewModel) }
        item { AuditoryProtectionSection(state, viewModel) }
        item { AnalogXSection(state, viewModel) }
        item { SpeakerOptSection(state, viewModel) }
    }
}
