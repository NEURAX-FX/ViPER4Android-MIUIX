package com.llsl.viper4android.ui.screens.main

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.ui.screens.debug.DebugLogDialog
import com.llsl.viper4android.ui.screens.device.DeviceDialog
import com.llsl.viper4android.ui.screens.preset.PresetDialog
import com.llsl.viper4android.ui.screens.settings.SettingsDialog
import com.llsl.viper4android.ui.screens.status.DriverStatusDialog
import kotlinx.coroutines.delay
import com.llsl.viper4android.ui.components.viper.ViperBottomBar
import com.llsl.viper4android.ui.components.viper.ViperIconButton
import com.llsl.viper4android.ui.components.viper.ViperScaffold
import com.llsl.viper4android.ui.components.viper.ViperTopBar
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.window.WindowListPopup

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

    var showPresetDialog by remember { mutableStateOf(false) }
    var showDriverStatusDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showDebugLog by remember { mutableStateOf(false) }
    var showDeviceDialog by remember { mutableStateOf(false) }
    var showTopBarMenu by remember { mutableStateOf(false) }
    var pendingTopBarMenuAction by remember { mutableStateOf<TopBarMenuAction?>(null) }
    var debugLogClearTime by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val appVersionName =
        remember {
            try {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            } catch (_: Exception) {
                ""
            }
        }

    LaunchedEffect(showTopBarMenu, pendingTopBarMenuAction) {
        val action = pendingTopBarMenuAction
        if (!showTopBarMenu && action != null) {
            pendingTopBarMenuAction = null
            when (action) {
                TopBarMenuAction.Devices -> showDeviceDialog = true
                TopBarMenuAction.DriverStatus -> showDriverStatusDialog = true
                TopBarMenuAction.Settings -> showSettingsDialog = true
                TopBarMenuAction.DebugLog -> showDebugLog = true
            }
        }
    }

    val importSuccessStr = stringResource(R.string.import_success)
    val importFailedStr = stringResource(R.string.import_failed)
    val importPresetLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
        ) { uri ->
            if (uri != null) {
                val success = viewModel.importPresetFile(uri)
                val msg = if (success) importSuccessStr else importFailedStr
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            }
        }

    val importKernelStr = stringResource(R.string.settings_import_kernel)
    val importKernelLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importKernels(uris, notificationTitle = importKernelStr, successStr = importSuccessStr) { success ->
                    val msg = if (success) importSuccessStr else importFailedStr
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val importVdcStr = stringResource(R.string.settings_import_vdc)
    val importVdcLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenMultipleDocuments(),
        ) { uris ->
            if (uris.isNotEmpty()) {
                viewModel.importVdcs(uris, notificationTitle = importVdcStr, successStr = importSuccessStr) { success ->
                    val msg = if (success) importSuccessStr else importFailedStr
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }

    val selectedTab = if (state.fxType == ViperParams.FX_TYPE_SPEAKER) 1 else 0
    val isSpkMode = selectedTab == 1

    ViperScaffold(
        topBar = {
            val topBarMenuItems =
                buildList {
                    add(stringResource(R.string.menu_devices) to TopBarMenuAction.Devices)
                    add(stringResource(R.string.menu_driver_status) to TopBarMenuAction.DriverStatus)
                    add(stringResource(R.string.menu_settings) to TopBarMenuAction.Settings)
                    if (debugMode) {
                        add(stringResource(R.string.debug_log_title) to TopBarMenuAction.DebugLog)
                    }
                }
            ViperTopBar(
                title = stringResource(R.string.app_name),
                actions = {
                    ViperIconButton(onClick = { showPresetDialog = true }) {
                        Icon(
                            Icons.Default.LibraryMusic,
                            contentDescription = stringResource(R.string.menu_presets),
                        )
                    }
                    ViperIconButton(onClick = { showTopBarMenu = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.action_more),
                        )
                        WindowListPopup(
                            show = showTopBarMenu,
                            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showTopBarMenu = false },
                        ) {
                            ListPopupColumn {
                                topBarMenuItems.forEachIndexed { index, item ->
                                    DropdownImpl(
                                        text = item.first,
                                        optionSize = topBarMenuItems.size,
                                        isSelected = false,
                                        index = index,
                                        onSelectedIndexChange = {
                                            pendingTopBarMenuAction = item.second
                                            showTopBarMenu = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            )
        },
        bottomBar = {
            ViperBottomBar(
                deviceName = state.activeDeviceName,
                firstLabel = stringResource(R.string.tab_headphone),
                firstIcon = if (selectedTab == 0) Icons.Filled.Headphones else Icons.Outlined.Headphones,
                firstSelected = selectedTab == 0,
                onFirstClick = { viewModel.setFxType(ViperParams.FX_TYPE_HEADPHONE) },
                secondLabel = stringResource(R.string.tab_speaker),
                secondIcon = if (selectedTab == 1) Icons.Filled.Speaker else Icons.Outlined.Speaker,
                secondSelected = selectedTab == 1,
                onSecondClick = { viewModel.setFxType(ViperParams.FX_TYPE_SPEAKER) },
            )
        },
    ) { paddingValues ->
        EffectList(
            state = state,
            viewModel = viewModel,
            isSpkMode = isSpkMode,
            modifier = Modifier.padding(paddingValues),
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
                onDismiss = { showPresetDialog = false },
            )
        }

        if (showDeviceDialog) {
            DeviceDialog(
                devices = deviceSettings,
                activeDeviceId = state.activeDeviceId,
                onRename = viewModel::renameDevice,
                onLoad = viewModel::loadDevicePreset,
                onSave = viewModel::saveDevicePreset,
                onDelete = viewModel::deleteDeviceSettings,
                onDismiss = { showDeviceDialog = false },
            )
        }

        if (showDebugLog) {
            DebugLogDialog(
                clearTimestamp = debugLogClearTime,
                onClear = { debugLogClearTime = System.currentTimeMillis() },
                onDisableDebug = {
                    viewModel.disableDebugMode()
                    showDebugLog = false
                },
                onDismiss = { showDebugLog = false },
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

        if (showSettingsDialog) {
            LaunchedEffect(Unit) { viewModel.queryDriverStatus() }
            SettingsDialog(
                autoStartEnabled = autoStart,
                globalModeEnabled = globalMode,
                aidlModeActive = aidlMode,
                onGlobalModeChanged = viewModel::toggleGlobalMode,
                driverStatus = driverStatus,
                appVersionName = appVersionName,
                onAutoStartChanged = viewModel::toggleAutoStart,
                onImportPreset = { importPresetLauncher.launch(arrayOf("application/json", "*/*")) },
                onImportKernel = {
                    importKernelLauncher.launch(
                        arrayOf(
                            "audio/*",
                            "application/octet-stream",
                            "*/*",
                        ),
                    )
                },
                onDebugUnlocked = viewModel::enableDebugMode,
                onImportVdc = { importVdcLauncher.launch(arrayOf("*/*")) },
                onDismiss = { showSettingsDialog = false },
            )
        }
    }
}

private enum class TopBarMenuAction {
    Devices,
    DriverStatus,
    Settings,
    DebugLog,
}

@Composable
private fun EffectList(
    state: MainUiState,
    viewModel: MainViewModel,
    isSpkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }
        item { MasterLimiterSection(state, viewModel, isSpkMode) }
        item { PlaybackGainSection(state, viewModel, isSpkMode) }
        item { LUFSTargetingSection(state, viewModel, isSpkMode) }
        item { MultibandCompressorSection(state, viewModel, isSpkMode) }
        item { FetCompressorSection(state, viewModel, isSpkMode) }
        item { DdcSection(state, viewModel, isSpkMode) }
        item { SpectrumExtensionSection(state, viewModel, isSpkMode) }
        item { EqualizerSection(state, viewModel, isSpkMode) }
        item { DynamicEqSection(state, viewModel, isSpkMode) }
        item { ConvolverSection(state, viewModel, isSpkMode) }
        item { FieldSurroundSection(state, viewModel, isSpkMode) }
        item { DiffSurroundSection(state, viewModel, isSpkMode) }
        item { StereoImagerSection(state, viewModel, isSpkMode) }
        item { HeadphoneSurroundSection(state, viewModel, isSpkMode) }
        item { ReverberationSection(state, viewModel, isSpkMode) }
        item { DynamicSystemSection(state, viewModel, isSpkMode) }
        item { TubeSimulatorSection(state, viewModel, isSpkMode) }
        item { PsychoacousticBassSection(state, viewModel, isSpkMode) }
        item { ViperBassSection(state, viewModel, isSpkMode) }
        item { ViperBassMonoSection(state, viewModel, isSpkMode) }
        item { ViperClaritySection(state, viewModel, isSpkMode) }
        item { AuditoryProtectionSection(state, viewModel, isSpkMode) }
        item { AnalogXSection(state, viewModel, isSpkMode) }
        if (isSpkMode) {
            item { SpeakerOptSection(state, viewModel) }
        }
        item { Spacer(modifier = Modifier.height(16.dp)) }
    }
}
