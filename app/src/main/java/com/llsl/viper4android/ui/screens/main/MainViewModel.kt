package com.llsl.viper4android.ui.screens.main

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.ByteArrayParam
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.model.DsPreset
import com.llsl.viper4android.data.model.EqPreset
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.zip.CRC32
import javax.inject.Inject

data class DriverStatus(
    val installed: Boolean = false,
    val versionCode: Int = -1,
    val versionName: String = "",
    val architecture: String = "",
    val streaming: Boolean = false,
    val samplingRate: Int = 0
)

data class MainUiState(
    val fxType: Int = ViperParams.FX_TYPE_HEADPHONE,

    val masterEnabled: Boolean = false,

    val outputVolume: Int = 11,
    val channelPan: Int = 0,
    val limiter: Int = 5,

    val agcEnabled: Boolean = false,
    val agcStrength: Int = 0,
    val agcMaxGain: Int = 3,
    val agcOutputThreshold: Int = 3,

    val fetEnabled: Boolean = false,
    val fetThreshold: Int = 100,
    val fetRatio: Int = 100,
    val fetAutoKnee: Boolean = true,
    val fetKnee: Int = 0,
    val fetKneeMulti: Int = 0,
    val fetAutoGain: Boolean = true,
    val fetGain: Int = 0,
    val fetAutoAttack: Boolean = true,
    val fetAttack: Int = 20,
    val fetMaxAttack: Int = 80,
    val fetAutoRelease: Boolean = true,
    val fetRelease: Int = 50,
    val fetMaxRelease: Int = 100,
    val fetCrest: Int = 100,
    val fetAdapt: Int = 50,
    val fetNoClip: Boolean = true,

    val ddcEnabled: Boolean = false,
    val ddcDevice: String = "",

    val vseEnabled: Boolean = false,
    val vseStrength: Int = 10,
    val vseExciter: Int = 0,

    val eqEnabled: Boolean = false,
    val eqBandCount: Int = 10,
    val eqPresetId: Long? = null,
    val eqBands: String = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
    val eqBandsMap: Map<Int, String> = mapOf(10 to "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"),
    val eqPresets: List<EqPreset> = emptyList(),

    val convolverEnabled: Boolean = false,
    val convolverKernel: String = "",
    val convolverCrossChannel: Int = 0,

    val fieldSurroundEnabled: Boolean = false,
    val fieldSurroundWidening: Int = 0,
    val fieldSurroundMidImage: Int = 5,
    val fieldSurroundDepth: Int = 0,

    val diffSurroundEnabled: Boolean = false,
    val diffSurroundDelay: Int = 4,
    val diffSurroundReverse: Boolean = false,

    val vheEnabled: Boolean = false,
    val vheQuality: Int = 0,

    val reverbEnabled: Boolean = false,
    val reverbRoomSize: Int = 0,
    val reverbWidth: Int = 0,
    val reverbDampening: Int = 0,
    val reverbWet: Int = 0,
    val reverbDry: Int = 50,

    val dynamicSystemEnabled: Boolean = false,
    val dynamicSystemDevice: Int = 0,
    val dynamicSystemStrength: Int = 50,
    val dsPresetId: Long? = null,
    val dsPresets: List<DsPreset> = emptyList(),
    val dsXLow: Int = 100,
    val dsXHigh: Int = 5600,
    val dsYLow: Int = 40,
    val dsYHigh: Int = 80,
    val dsSideGainLow: Int = 50,
    val dsSideGainHigh: Int = 50,

    val tubeSimulatorEnabled: Boolean = false,

    val bassEnabled: Boolean = false,
    val bassMode: Int = 0,
    val bassFrequency: Int = 55,
    val bassGain: Int = 0,
    val bassAntiPop: Boolean = true,

    val bassMonoEnabled: Boolean = false,
    val bassMonoMode: Int = 0,
    val bassMonoFrequency: Int = 55,
    val bassMonoGain: Int = 0,
    val bassMonoAntiPop: Boolean = true,

    val clarityEnabled: Boolean = false,
    val clarityMode: Int = 0,
    val clarityGain: Int = 1,

    val cureEnabled: Boolean = false,
    val cureStrength: Int = 0,

    val analogxEnabled: Boolean = false,
    val analogxMode: Int = 0,

    val spkMasterEnabled: Boolean = false,

    val spkOutputVolume: Int = 11,
    val spkChannelPan: Int = 0,
    val spkLimiter: Int = 5,

    val spkAgcEnabled: Boolean = false,
    val spkAgcStrength: Int = 0,
    val spkAgcMaxGain: Int = 3,
    val spkAgcOutputThreshold: Int = 3,

    val spkFetEnabled: Boolean = false,
    val spkFetThreshold: Int = 100,
    val spkFetRatio: Int = 100,
    val spkFetAutoKnee: Boolean = true,
    val spkFetKnee: Int = 0,
    val spkFetKneeMulti: Int = 0,
    val spkFetAutoGain: Boolean = true,
    val spkFetGain: Int = 0,
    val spkFetAutoAttack: Boolean = true,
    val spkFetAttack: Int = 20,
    val spkFetMaxAttack: Int = 80,
    val spkFetAutoRelease: Boolean = true,
    val spkFetRelease: Int = 50,
    val spkFetMaxRelease: Int = 100,
    val spkFetCrest: Int = 100,
    val spkFetAdapt: Int = 50,
    val spkFetNoClip: Boolean = true,

    val spkDdcEnabled: Boolean = false,
    val spkDdcDevice: String = "",

    val spkVseEnabled: Boolean = false,
    val spkVseStrength: Int = 10,
    val spkVseExciter: Int = 0,

    val spkEqEnabled: Boolean = false,
    val spkEqBandCount: Int = 10,
    val spkEqPresetId: Long? = null,
    val spkEqBands: String = "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;",
    val spkEqBandsMap: Map<Int, String> = mapOf(10 to "0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;0.0;"),
    val spkEqPresets: List<EqPreset> = emptyList(),

    val spkConvolverEnabled: Boolean = false,
    val spkConvolverKernel: String = "",
    val spkConvolverCrossChannel: Int = 0,

    val spkFieldSurroundEnabled: Boolean = false,
    val spkFieldSurroundWidening: Int = 0,
    val spkFieldSurroundMidImage: Int = 5,
    val spkFieldSurroundDepth: Int = 0,

    val spkDiffSurroundEnabled: Boolean = false,
    val spkDiffSurroundDelay: Int = 4,
    val spkDiffSurroundReverse: Boolean = false,

    val spkVheEnabled: Boolean = false,
    val spkVheQuality: Int = 0,

    val spkReverbEnabled: Boolean = false,
    val spkReverbRoomSize: Int = 0,
    val spkReverbWidth: Int = 0,
    val spkReverbDampening: Int = 0,
    val spkReverbWet: Int = 0,
    val spkReverbDry: Int = 50,

    val spkDynamicSystemEnabled: Boolean = false,
    val spkDynamicSystemDevice: Int = 0,
    val spkDynamicSystemStrength: Int = 50,
    val spkDsPresetId: Long? = null,
    val spkDsPresets: List<DsPreset> = emptyList(),
    val spkDsXLow: Int = 100,
    val spkDsXHigh: Int = 5600,
    val spkDsYLow: Int = 40,
    val spkDsYHigh: Int = 80,
    val spkDsSideGainLow: Int = 50,
    val spkDsSideGainHigh: Int = 50,

    val spkTubeSimulatorEnabled: Boolean = false,

    val spkBassEnabled: Boolean = false,
    val spkBassMode: Int = 0,
    val spkBassFrequency: Int = 55,
    val spkBassGain: Int = 0,
    val spkBassAntiPop: Boolean = true,

    val spkBassMonoEnabled: Boolean = false,
    val spkBassMonoMode: Int = 0,
    val spkBassMonoFrequency: Int = 55,
    val spkBassMonoGain: Int = 0,
    val spkBassMonoAntiPop: Boolean = true,

    val spkClarityEnabled: Boolean = false,
    val spkClarityMode: Int = 0,
    val spkClarityGain: Int = 1,

    val spkCureEnabled: Boolean = false,
    val spkCureStrength: Int = 0,

    val spkAnalogxEnabled: Boolean = false,
    val spkAnalogxMode: Int = 0,

    val speakerOptEnabled: Boolean = false,

    val activeDeviceName: String = "",
    val activeDeviceId: String = "",
)

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val repository: ViperRepository
) : AndroidViewModel(application) {

    companion object {
        val OUTPUT_VOLUME_VALUES get() = EffectDispatcher.OUTPUT_VOLUME_VALUES
        val OUTPUT_DB_VALUES get() = EffectDispatcher.OUTPUT_DB_VALUES
        val PLAYBACK_GAIN_RATIO_VALUES get() = EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES
        val MULTI_FACTOR_VALUES get() = EffectDispatcher.MULTI_FACTOR_VALUES
        val VSE_BARK_VALUES get() = EffectDispatcher.VSE_BARK_VALUES
        val DIFF_SURROUND_DELAY_VALUES get() = EffectDispatcher.DIFF_SURROUND_DELAY_VALUES
        val FIELD_SURROUND_WIDENING_VALUES get() = EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES
        val BASS_GAIN_DB_LABELS get() = EffectDispatcher.BASS_GAIN_DB_LABELS
        val BASS_SUBWOOFER_GAIN_DB_LABELS get() = EffectDispatcher.BASS_SUBWOOFER_GAIN_DB_LABELS
        val CLARITY_GAIN_DB_LABELS get() = EffectDispatcher.CLARITY_GAIN_DB_LABELS

        const val PREF_AUTO_START = "auto_start"
        const val PREF_AIDL_MODE = "aidl_mode"
        const val PREF_GLOBAL_MODE = "global_mode"
        const val PREF_DEBUG_MODE = "debug_mode"
    }

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    val presetList: StateFlow<List<Preset>> = repository.getAllPresets()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val deviceSettingsList: StateFlow<List<DeviceSettings>> = repository.getAllDeviceSettings()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _driverStatus = MutableStateFlow(DriverStatus())
    val driverStatus: StateFlow<DriverStatus> = _driverStatus.asStateFlow()

    private val _vdcFileList = MutableStateFlow<List<String>>(emptyList())
    val vdcFileList: StateFlow<List<String>> = _vdcFileList.asStateFlow()

    private val _kernelFileList = MutableStateFlow<List<String>>(emptyList())
    val kernelFileList: StateFlow<List<String>> = _kernelFileList.asStateFlow()

    private val _autoStartEnabled = MutableStateFlow(false)
    val autoStartEnabled: StateFlow<Boolean> = _autoStartEnabled.asStateFlow()


    private val _aidlModeEnabled = MutableStateFlow(false)
    val aidlModeEnabled: StateFlow<Boolean> = _aidlModeEnabled.asStateFlow()

    private val _globalModeEnabled = MutableStateFlow(false)
    val globalModeEnabled: StateFlow<Boolean> = _globalModeEnabled.asStateFlow()

    private val _debugModeEnabled = MutableStateFlow(false)
    val debugModeEnabled: StateFlow<Boolean> = _debugModeEnabled.asStateFlow()

    private var viperService: ViperService? = null
    private var serviceBound = false
    private val audioOutputDetector = AudioOutputDetector(application)
    private var activeDeviceType: Int = ViperParams.FX_TYPE_HEADPHONE
    private var currentDeviceId: String = AudioDevice.ID_SPEAKER
    private var eqPresetsJob: Job? = null
    private var spkEqPresetsJob: Job? = null
    private var dsPresetsJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as? ViperService.LocalBinder ?: return
            viperService = localBinder.service
            serviceBound = true
            applyFullState()
            queryDriverStatus()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            viperService = null
            serviceBound = false
        }
    }

    init {
        loadSettingsPreferences()
        refreshFileLists()
        val initialDevice = audioOutputDetector.activeDevice.value
        currentDeviceId = initialDevice.id
        val initialFxType =
            if (initialDevice.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
        activeDeviceType = initialFxType
        viewModelScope.launch {
            loadInitialState()
            val initialDbName =
                repository.getDeviceSettings(initialDevice.id)?.deviceName ?: initialDevice.name
            _uiState.update {
                it.copy(
                    fxType = initialFxType,
                    activeDeviceName = initialDbName,
                    activeDeviceId = initialDevice.id
                )
            }
            loadEqPresetsForBandCount(_uiState.value.eqBandCount, isSpk = false)
            loadEqPresetsForBandCount(_uiState.value.spkEqBandCount, isSpk = true)
            loadDsPresets()
            loadDeviceSettings(initialDevice)
            ensureDeviceEntry(initialDevice)
            bindToService()
            audioOutputDetector.activeDevice.collect { device ->
                val detectedType =
                    if (device.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
                if (device.id != currentDeviceId) {
                    saveCurrentDeviceSettings()
                    activeDeviceType = detectedType
                    currentDeviceId = device.id
                    val dbName = repository.getDeviceSettings(device.id)?.deviceName ?: device.name
                    _uiState.update {
                        it.copy(
                            fxType = detectedType,
                            activeDeviceName = dbName,
                            activeDeviceId = device.id
                        )
                    }
                    repository.setIntPreference(ViperRepository.PREF_FX_TYPE, detectedType)
                    ConfigChannel.setActiveFxType(detectedType)
                    loadDeviceSettings(device)
                }
                ensureDeviceEntry(device)
            }
        }
    }

    private fun loadDsPresets() {
        dsPresetsJob?.cancel()
        dsPresetsJob = viewModelScope.launch {
            repository.getAllDsPresets().collect { presets ->
                _uiState.update { it.copy(dsPresets = presets, spkDsPresets = presets) }
            }
        }
    }

    private fun bindToService() {
        val intent = Intent(getApplication(), ViperService::class.java)
        getApplication<Application>().bindService(
            intent,
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    override fun onCleared() {
        super.onCleared()
        runBlocking(Dispatchers.IO) { saveCurrentDeviceSettings() }
        audioOutputDetector.stop()
        if (serviceBound) {
            getApplication<Application>().unbindService(serviceConnection)
            serviceBound = false
        }
        viperService = null
    }

    private suspend fun loadInitialState() {
        loadHeadphonePreferences()
        loadSpeakerPreferences()
    }

    private fun loadEqPresetsForBandCount(bandCount: Int, isSpk: Boolean) {
        if (isSpk) {
            spkEqPresetsJob?.cancel()
            spkEqPresetsJob = viewModelScope.launch {
                repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                    _uiState.update { it.copy(spkEqPresets = presets) }
                }
            }
        } else {
            eqPresetsJob?.cancel()
            eqPresetsJob = viewModelScope.launch {
                repository.getEqPresetsByBandCount(bandCount).collect { presets ->
                    _uiState.update { it.copy(eqPresets = presets) }
                }
            }
        }
    }

    private suspend fun loadHeadphonePreferences() {
        val fxType =
            repository.getIntPreference(ViperRepository.PREF_FX_TYPE, ViperParams.FX_TYPE_HEADPHONE)
                .first()
        val state = loadEffectPrefs(repository, isSpk = false, state = _uiState.value)

        val eqBandCount = state.eqBandCount
        val rawEqBands = state.eqBands
        val parsedBandCount = rawEqBands.split(";").count { it.isNotBlank() }
        val eqBands = if (parsedBandCount != eqBandCount) {
            List(eqBandCount) { 0f }.joinToString(";") {
                String.format(Locale.US, "%.1f", it)
            } + ";"
        } else {
            rawEqBands
        }
        val eqBandsMap = mutableMapOf<Int, String>()
        for (bc in listOf(10, 15, 25, 31)) {
            val defaultBands =
                List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            eqBandsMap[bc] = repository.getStringPreference("eq_bands_$bc", defaultBands).first()
        }
        eqBandsMap[eqBandCount] = eqBands

        _uiState.update {
            state.copy(
                fxType = fxType,
                eqBands = eqBands,
                eqBandsMap = eqBandsMap
            )
        }
    }

    private suspend fun loadSpeakerPreferences() {
        val state = loadEffectPrefs(repository, isSpk = true, state = _uiState.value)

        val spkEqBandCount = state.spkEqBandCount
        val rawSpkEqBands = state.spkEqBands
        val parsedSpkBandCount = rawSpkEqBands.split(";").count { it.isNotBlank() }
        val spkEqBands = if (parsedSpkBandCount != spkEqBandCount) {
            List(spkEqBandCount) { 0f }.joinToString(";") {
                String.format(Locale.US, "%.1f", it)
            } + ";"
        } else {
            rawSpkEqBands
        }
        val spkEqBandsMap = mutableMapOf<Int, String>()
        for (bc in listOf(10, 15, 25, 31)) {
            val defaultBands =
                List(bc) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
            spkEqBandsMap[bc] =
                repository.getStringPreference("spk_eq_bands_$bc", defaultBands).first()
        }
        spkEqBandsMap[spkEqBandCount] = spkEqBands

        _uiState.update {
            state.copy(
                spkEqBands = spkEqBands,
                spkEqBandsMap = spkEqBandsMap
            )
        }
    }

    private fun applyFullState() {
        val service = viperService ?: return
        val state = _uiState.value
        ConfigChannel.setActiveFxType(activeDeviceType)
        val isMasterOn =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkMasterEnabled else state.masterEnabled
        val mode = if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.d(
            "ViewModel",
            "Dispatch: applyFullState mode=$mode master=${if (isMasterOn) "ON" else "OFF"}"
        )

        val byteArrayParams = mutableListOf<ByteArrayParam>()

        val ddcEnabled =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkDdcEnabled else state.ddcEnabled
        val ddcDevice =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkDdcDevice else state.ddcDevice
        FileLogger.i("ViewModel", "applyFullState: ddcEnabled=$ddcEnabled ddcDevice='$ddcDevice'")
        if (ddcEnabled && ddcDevice.isNotEmpty()) {
            val ba = prepareDdcByteArray(ddcDevice)
            FileLogger.i("ViewModel", "applyFullState: DDC byteArray=${ba?.data?.size ?: "null"}")
            ba?.let { byteArrayParams.add(it) }
        }

        val convolverEnabled =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkConvolverEnabled else state.convolverEnabled
        val kernel =
            if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) state.spkConvolverKernel else state.convolverKernel
        FileLogger.i(
            "ViewModel",
            "applyFullState: convolverEnabled=$convolverEnabled kernel='$kernel'"
        )
        if (convolverEnabled && kernel.isNotEmpty()) {
            val ba = prepareConvolverByteArray(kernel)
            FileLogger.i(
                "ViewModel",
                "applyFullState: convolver byteArray=${ba?.data?.size ?: "null"}"
            )
            ba?.let { byteArrayParams.add(it) }
        }

        service.dispatchFullState(
            state.copy(fxType = activeDeviceType),
            isMasterOn,
            byteArrayParams.ifEmpty { null }
        )
    }

    private fun prepareDdcByteArray(name: String): ByteArrayParam? {
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            FileLogger.i(
                "ViewModel",
                "prepareDdc: file=${file.absolutePath} exists=${file.exists()}"
            )
            if (!file.exists()) return null
            val lines = file.readLines()
            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null
            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 = trimmed.removePrefix("SR_44100:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 = trimmed.removePrefix("SR_48000:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }
                }
            }
            if (coeffs44100 == null || coeffs48000 == null) return null
            if (coeffs44100.size != coeffs48000.size) return null
            if (coeffs44100.size % 5 != 0) return null
            val arrSize = coeffs44100.size
            val naturalSize = 4 + arrSize * 4 * 2
            val wireSize = when {
                naturalSize <= 256 -> 256
                naturalSize <= 1024 -> 1024
                else -> return null
            }
            val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)
            ByteArrayParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to prepare DDC: $name", e)
            null
        }
    }

    private fun prepareConvolverByteArray(fileName: String): ByteArrayParam? {
        if (!_aidlModeEnabled.value) return null
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            FileLogger.i(
                "ViewModel",
                "prepareConvolver: file=${file.absolutePath} exists=${file.exists()}"
            )
            if (!file.exists()) return null
            val safeName = fileName.replace("'", "")
            val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            FileLogger.i("ViewModel", "Kernel copied to $kernelPath (for full state)")
            val param = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to prepare kernel: $fileName", e)
            null
        }
    }

    fun setMasterEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (headphone)")
        _uiState.update { it.copy(masterEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(ViperRepository.PREF_MASTER_ENABLE, enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.setEffectEnabled(enabled)
            if (enabled) applyFullState()
        }
    }

    fun setSpkMasterEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Master: ${if (enabled) "ON" else "OFF"} (speaker)")
        _uiState.update { it.copy(spkMasterEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperRepository.PREF_MASTER_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.setEffectEnabled(enabled)
            if (enabled) applyFullState()
        }
    }

    fun setFxType(type: Int) {
        val mode = if (type == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
        FileLogger.i("ViewModel", "Dispatch: fxType=$mode")
        _uiState.update { it.copy(fxType = type) }
        viewModelScope.launch {
            repository.setIntPreference(ViperRepository.PREF_FX_TYPE, type)
        }
        ConfigChannel.setActiveFxType(type)
        applyFullState()
    }

    fun setOutputVolume(value: Int) {
        _uiState.update { it.copy(outputVolume = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_OUTPUT_VOLUME}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(value) { 100 })
    }

    fun setChannelPan(value: Int) {
        _uiState.update { it.copy(channelPan = value) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CHANNEL_PAN}",
            ViperParams.PARAM_HP_CHANNEL_PAN,
            value
        )
    }

    fun setLimiter(value: Int) {
        _uiState.update { it.copy(limiter = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_LIMITER}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_LIMITER, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setAgcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "AGC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(agcEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_AGC_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_AGC_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_RATIO,
                        intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(s.agcStrength) { 50 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_MAX_SCALER,
                        intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(s.agcMaxGain) { 100 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_AGC_VOLUME,
                        intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(s.agcOutputThreshold) { 100 })
                    )
                )
            )
        }
    }

    fun setAgcStrength(value: Int) {
        _uiState.update { it.copy(agcStrength = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_RATIO}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(value) { 50 })
    }

    fun setAgcMaxGain(value: Int) {
        _uiState.update { it.copy(agcMaxGain = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_MAX_SCALER}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(value) { 100 })
    }

    fun setAgcOutputThreshold(value: Int) {
        _uiState.update { it.copy(agcOutputThreshold = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_AGC_VOLUME}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_AGC_VOLUME, OUTPUT_DB_VALUES.getOrElse(value) { 100 })
    }

    fun setFetEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "FET Compressor: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fetEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
                        intArrayOf(if (enabled) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
                        intArrayOf(s.fetThreshold)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO, intArrayOf(s.fetRatio)),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
                        intArrayOf(if (s.fetAutoKnee) 100 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE, intArrayOf(s.fetKnee)),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
                        intArrayOf(s.fetKneeMulti)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
                        intArrayOf(if (s.fetAutoGain) 100 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN, intArrayOf(s.fetGain)),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
                        intArrayOf(if (s.fetAutoAttack) 100 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK, intArrayOf(s.fetAttack)),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
                        intArrayOf(s.fetMaxAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
                        intArrayOf(if (s.fetAutoRelease) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
                        intArrayOf(s.fetRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
                        intArrayOf(s.fetMaxRelease)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_CREST, intArrayOf(s.fetCrest)),
                    ParamEntry(ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT, intArrayOf(s.fetAdapt)),
                    ParamEntry(
                        ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
                        intArrayOf(if (s.fetNoClip) 100 else 0)
                    )
                )
            )
        }
    }

    fun setFetThreshold(v: Int) {
        _uiState.update { it.copy(fetThreshold = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
            v
        )
    }

    fun setFetRatio(v: Int) {
        _uiState.update { it.copy(fetRatio = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
            v
        )
    }

    fun setFetAutoKnee(v: Boolean) {
        _uiState.update { it.copy(fetAutoKnee = v) }; saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
            v
        )
    }

    fun setFetKnee(v: Int) {
        _uiState.update { it.copy(fetKnee = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE,
            v
        )
    }

    fun setFetKneeMulti(v: Int) {
        _uiState.update { it.copy(fetKneeMulti = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
            v
        )
    }

    fun setFetAutoGain(v: Boolean) {
        _uiState.update { it.copy(fetAutoGain = v) }; saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
            v
        )
    }

    fun setFetGain(v: Int) {
        _uiState.update { it.copy(fetGain = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN,
            v
        )
    }

    fun setFetAutoAttack(v: Boolean) {
        _uiState.update { it.copy(fetAutoAttack = v) }; saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
            v
        )
    }

    fun setFetAttack(v: Int) {
        _uiState.update { it.copy(fetAttack = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
            v
        )
    }

    fun setFetMaxAttack(v: Int) {
        _uiState.update { it.copy(fetMaxAttack = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
            v
        )
    }

    fun setFetAutoRelease(v: Boolean) {
        _uiState.update { it.copy(fetAutoRelease = v) }; saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
            v
        )
    }

    fun setFetRelease(v: Int) {
        _uiState.update { it.copy(fetRelease = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
            v
        )
    }

    fun setFetMaxRelease(v: Int) {
        _uiState.update { it.copy(fetMaxRelease = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
            v
        )
    }

    fun setFetCrest(v: Int) {
        _uiState.update { it.copy(fetCrest = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_CREST}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
            v
        )
    }

    fun setFetAdapt(v: Int) {
        _uiState.update { it.copy(fetAdapt = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
            v
        )
    }

    fun setFetNoClip(v: Boolean) {
        _uiState.update { it.copy(fetNoClip = v) }; saveAndDispatchFetBool(
            "${ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP}",
            ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
            v
        )
    }

    fun setDdcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "DDC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(ddcEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DDC_ENABLE}",
                enabled
            )
        }
        val device = _uiState.value.ddcDevice
        val effectiveEnabled = enabled && device.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            loadVdcByName(device, ViperParams.PARAM_HP_DDC_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchInt(ViperParams.PARAM_HP_DDC_ENABLE, 0)
        }
    }

    fun setDdcDevice(device: String) {
        FileLogger.i("ViewModel", "DDC selected: $device")
        _uiState.update { it.copy(ddcDevice = device) }
        viewModelScope.launch {
            repository.setStringPreference(
                ViperRepository.PREF_DDC_DEVICE,
                device
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            if (device.isEmpty()) {
                dispatchInt(ViperParams.PARAM_HP_DDC_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.ddcEnabled) ViperParams.PARAM_HP_DDC_ENABLE else null
                loadVdcByName(device, enableParam)
            }
        }
    }

    fun setVseEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "VSE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vseEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
                        intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(s.vseStrength) { 7600 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                        intArrayOf((s.vseExciter * 5.6).toInt())
                    )
                )
            )
        }
    }

    fun setVseStrength(value: Int) {
        _uiState.update { it.copy(vseStrength = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(value) { 7600 })
    }

    fun setVseExciter(value: Int) {
        _uiState.update { it.copy(vseExciter = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (value * 5.6).toInt()
        )
    }

    fun setEqEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "EQ: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(eqEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_EQ_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_EQ_ENABLE, intArrayOf(if (enabled) 1 else 0))
                )
            )
        }
    }

    fun setEqPreset(presetId: Long) {
        val state = _uiState.value
        val preset = state.eqPresets.find { it.id == presetId } ?: return
        val bands = preset.bands
        val bandCount = state.eqBandCount
        _uiState.update { s ->
            val updatedMap = s.eqBandsMap.toMutableMap().apply { put(bandCount, bands) }
            s.copy(eqPresetId = presetId, eqBands = bands, eqBandsMap = updatedMap)
        }
        viewModelScope.launch {
            repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, presetId.toInt())
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$bandCount", bands)
        }
        hpDispatchEqBands(bands)
    }

    fun setEqBands(bands: String) {
        val bandCount = _uiState.value.eqBandCount
        _uiState.update { state ->
            val updatedMap = state.eqBandsMap.toMutableMap().apply { put(bandCount, bands) }
            state.copy(eqBands = bands, eqBandsMap = updatedMap)
        }
        viewModelScope.launch {
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$bandCount", bands)
        }
        hpDispatchEqBands(bands)
    }

    fun setEqBandCount(count: Int) {
        val currentState = _uiState.value
        val oldCount = currentState.eqBandCount
        FileLogger.d("ViewModel", "EQ band count: $oldCount -> $count")
        val updatedMap = currentState.eqBandsMap.toMutableMap().apply {
            put(oldCount, currentState.eqBands)
        }
        val defaultBands =
            List(count) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        val bands = updatedMap[count] ?: defaultBands
        _uiState.update {
            it.copy(
                eqBandCount = count,
                eqBands = bands,
                eqPresetId = null,
                eqBandsMap = updatedMap
            )
        }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_EQ_BAND_COUNT}", count)
            repository.setStringPreference("${ViperParams.PARAM_HP_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("eq_bands_$oldCount", currentState.eqBands)
            repository.setStringPreference("eq_bands_$count", bands)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchEqBands(
                ViperParams.PARAM_HP_EQ_BAND_LEVEL,
                bands,
                ViperParams.PARAM_HP_EQ_BAND_COUNT,
                count
            )
        }
        loadEqPresetsForBandCount(count, isSpk = false)
    }

    fun addEqPreset(name: String) {
        val state = _uiState.value
        val preset = EqPreset(name = name, bandCount = state.eqBandCount, bands = state.eqBands)
        viewModelScope.launch {
            val id = repository.saveEqPreset(preset)
            _uiState.update { it.copy(eqPresetId = id) }
            repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, id.toInt())
        }
    }

    fun deleteEqPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteEqPresetById(presetId)
            if (_uiState.value.eqPresetId == presetId) {
                _uiState.update { it.copy(eqPresetId = null) }
                repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, -1)
            }
        }
    }

    fun resetEqBands() {
        val bandCount = _uiState.value.eqBandCount
        val flatBands =
            List(bandCount) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        setEqBands(flatBands)
        _uiState.update { it.copy(eqPresetId = null) }
        viewModelScope.launch { repository.setIntPreference(ViperRepository.PREF_EQ_PRESET_ID, -1) }
    }

    fun setConvolverEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Convolver: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(convolverEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_CONVOLVER_ENABLE}",
                enabled
            )
        }
        val kernel = _uiState.value.convolverKernel
        val effectiveEnabled = enabled && kernel.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            loadKernelByName(kernel, ViperParams.PARAM_HP_CONVOLVER_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            dispatchInt(ViperParams.PARAM_HP_CONVOLVER_ENABLE, 0)
        }
    }

    fun setConvolverKernel(kernel: String) {
        FileLogger.i("ViewModel", "Convolver kernel selected: $kernel")
        _uiState.update { it.copy(convolverKernel = kernel) }
        viewModelScope.launch {
            repository.setStringPreference(
                "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                kernel
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            if (kernel.isEmpty()) {
                dispatchInt(ViperParams.PARAM_HP_CONVOLVER_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.convolverEnabled) ViperParams.PARAM_HP_CONVOLVER_ENABLE else null
                loadKernelByName(kernel, enableParam)
            }
        }
    }

    fun setConvolverCrossChannel(value: Int) {
        _uiState.update { it.copy(convolverCrossChannel = value) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL}",
            ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
            value
        )
    }

    fun setFieldSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Field Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(fieldSurroundEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
                        intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(s.fieldSurroundWidening) { 0 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
                        intArrayOf(s.fieldSurroundMidImage * 10 + 100)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
                        intArrayOf(s.fieldSurroundDepth * 75 + 200)
                    )
                )
            )
        }
    }

    fun setFieldSurroundWidening(value: Int) {
        _uiState.update { it.copy(fieldSurroundWidening = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(value) { 0 })
    }

    fun setFieldSurroundMidImage(value: Int) {
        _uiState.update { it.copy(fieldSurroundMidImage = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE, value * 10 + 100)
    }

    fun setFieldSurroundDepth(value: Int) {
        _uiState.update { it.copy(fieldSurroundDepth = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH}",
                value
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH, value * 75 + 200)
    }

    fun setDiffSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Diff Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(diffSurroundEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
                        intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(s.diffSurroundDelay) { 500 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE,
                        intArrayOf(if (s.diffSurroundReverse) 1 else 0)
                    )
                )
            )
        }
    }

    fun setDiffSurroundDelay(value: Int) {
        _uiState.update { it.copy(diffSurroundDelay = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DIFF_SURROUND_DELAY}",
                value
            )
        }
        hpDispatchInt(
            ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(value) { 500 })
    }

    fun setDiffSurroundReverse(reverse: Boolean) {
        _uiState.update { it.copy(diffSurroundReverse = reverse) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE}",
                reverse
            )
        }
        hpDispatchInt(ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE, if (reverse) 1 else 0)
    }

    fun setVheEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "VHE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(vheEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
                        intArrayOf(s.vheQuality)
                    )
                )
            )
        }
    }

    fun setVheQuality(value: Int) {
        _uiState.update { it.copy(vheQuality = value) }
        saveAndDispatchInt(
            "${ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH}",
            ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
            value
        )
    }

    fun setReverbEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Reverb: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(reverbEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_REVERB_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_SIZE,
                        intArrayOf(s.reverbRoomSize * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_WIDTH,
                        intArrayOf(s.reverbWidth * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING,
                        intArrayOf(s.reverbDampening)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL,
                        intArrayOf(s.reverbWet)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL, intArrayOf(s.reverbDry))
                )
            )
        }
    }

    fun setReverbRoomSize(v: Int) {
        _uiState.update { it.copy(reverbRoomSize = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_SIZE}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_SIZE, v * 10)
    }

    fun setReverbWidth(v: Int) {
        _uiState.update { it.copy(reverbWidth = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_WIDTH}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_WIDTH, v * 10)
    }

    fun setReverbDampening(v: Int) {
        _uiState.update { it.copy(reverbDampening = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING, v)
    }

    fun setReverbWet(v: Int) {
        _uiState.update { it.copy(reverbWet = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL, v)
    }

    fun setReverbDry(v: Int) {
        _uiState.update { it.copy(reverbDry = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL}",
            ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
            v
        )
    }

    fun setDynamicSystemEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Dynamic System: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(dynamicSystemEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE}",
                enabled
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setDynamicSystemStrength(value: Int) {
        _uiState.update { it.copy(dynamicSystemStrength = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH}",
                value
            )
        }
        hpDispatchDynamicSystem()
    }

    private fun hpDispatchDynamicSystem() {
        if (activeDeviceType != ViperParams.FX_TYPE_HEADPHONE) return
        val s = _uiState.value
        viperService?.dispatchParamsBatch(
            listOf(
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
                    intArrayOf(if (s.dynamicSystemEnabled) 1 else 0)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
                    intArrayOf(s.dynamicSystemStrength * 20 + 100)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                    intArrayOf(s.dsXLow, s.dsXHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                    intArrayOf(s.dsYLow, s.dsYHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN,
                    intArrayOf(s.dsSideGainLow, s.dsSideGainHigh)
                )
            )
        )
    }

    fun setDsPreset(presetId: Long) {
        val preset = _uiState.value.dsPresets.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                dsPresetId = presetId,
                dsXLow = preset.xLow, dsXHigh = preset.xHigh,
                dsYLow = preset.yLow, dsYHigh = preset.yHigh,
                dsSideGainLow = preset.sideGainLow, dsSideGainHigh = preset.sideGainHigh
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
                presetId.toInt()
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                preset.xLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                preset.xHigh
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                preset.yLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                preset.yHigh
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                preset.sideGainLow
            )
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                preset.sideGainHigh
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setDsXLow(v: Int) {
        _uiState.update { it.copy(dsXLow = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                v
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDsXHigh(v: Int) {
        _uiState.update { it.copy(dsXHigh = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                v
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDsYLow(v: Int) {
        _uiState.update { it.copy(dsYLow = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                v
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDsYHigh(v: Int) {
        _uiState.update { it.copy(dsYHigh = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                v
            )
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDsSideGainLow(v: Int) {
        _uiState.update { it.copy(dsSideGainLow = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low", v)
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun setDsSideGainHigh(v: Int) {
        _uiState.update { it.copy(dsSideGainHigh = v, dsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference("${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high", v)
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
        }
        hpDispatchDynamicSystem()
    }

    fun addDsPreset(name: String) {
        val s = _uiState.value
        val preset = DsPreset(
            name = name,
            xLow = s.dsXLow, xHigh = s.dsXHigh,
            yLow = s.dsYLow, yHigh = s.dsYHigh,
            sideGainLow = s.dsSideGainLow, sideGainHigh = s.dsSideGainHigh
        )
        viewModelScope.launch {
            val id = repository.saveDsPreset(preset)
            _uiState.update { it.copy(dsPresetId = id) }
            repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, id.toInt())
        }
    }

    fun deleteDsPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteDsPresetById(presetId)
            if (_uiState.value.dsPresetId == presetId) {
                _uiState.update { it.copy(dsPresetId = null) }
                repository.setIntPreference(ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID, -1)
            }
        }
    }

    fun resetDsCoefficients() {
        _uiState.update {
            it.copy(
                dsXLow = 100, dsXHigh = 5600,
                dsYLow = 40, dsYHigh = 80,
                dsSideGainLow = 50, dsSideGainHigh = 50,
                dsPresetId = null
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID,
                -1
            )
        }
        hpDispatchDynamicSystem()
    }

    fun setTubeSimulatorEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Tube Simulator: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(tubeSimulatorEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Bass: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bassEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_BASS_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(ViperParams.PARAM_HP_BASS_MODE, intArrayOf(s.bassMode)),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_FREQUENCY,
                        intArrayOf(s.bassFrequency + 15)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_BASS_GAIN, intArrayOf(s.bassGain * 50 + 50)),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_ANTI_POP,
                        intArrayOf(if (s.bassAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassMode(mode: Int) {
        _uiState.update { it.copy(bassMode = mode) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_BASS_MODE}",
            ViperParams.PARAM_HP_BASS_MODE,
            mode
        )
    }

    fun setBassFrequency(v: Int) {
        _uiState.update { it.copy(bassFrequency = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_BASS_FREQUENCY}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_BASS_FREQUENCY, v + 15)
    }

    fun setBassGain(v: Int) {
        _uiState.update { it.copy(bassGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_BASS_GAIN}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_BASS_GAIN, v * 50 + 50)
    }

    fun setBassAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bassAntiPop = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_ANTI_POP}", enabled)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setBassMonoEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Bass Mono: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(bassMonoEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_MONO_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_BASS_MONO_MODE, intArrayOf(s.bassMonoMode)),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_FREQUENCY,
                        intArrayOf(s.bassMonoFrequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_GAIN,
                        intArrayOf(s.bassMonoGain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_HP_BASS_MONO_ANTI_POP,
                        intArrayOf(if (s.bassMonoAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setBassMonoMode(mode: Int) {
        _uiState.update { it.copy(bassMonoMode = mode) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_BASS_MONO_MODE}",
            ViperParams.PARAM_HP_BASS_MONO_MODE,
            mode
        )
    }

    fun setBassMonoFrequency(v: Int) {
        _uiState.update { it.copy(bassMonoFrequency = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_BASS_MONO_FREQUENCY}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_FREQUENCY, v + 15)
    }

    fun setBassMonoGain(v: Int) {
        _uiState.update { it.copy(bassMonoGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_BASS_MONO_GAIN}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_GAIN, v * 50 + 50)
    }

    fun setBassMonoAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(bassMonoAntiPop = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_BASS_MONO_ANTI_POP}", enabled)
        }
        hpDispatchInt(ViperParams.PARAM_HP_BASS_MONO_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setClarityEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Clarity: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(clarityEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_CLARITY_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_CLARITY_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_CLARITY_MODE, intArrayOf(s.clarityMode)),
                    ParamEntry(ViperParams.PARAM_HP_CLARITY_GAIN, intArrayOf(s.clarityGain * 50))
                )
            )
        }
    }

    fun setClarityMode(mode: Int) {
        _uiState.update { it.copy(clarityMode = mode) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CLARITY_MODE}",
            ViperParams.PARAM_HP_CLARITY_MODE,
            mode
        )
    }

    fun setClarityGain(v: Int) {
        _uiState.update { it.copy(clarityGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_HP_CLARITY_GAIN}",
                v
            )
        }; hpDispatchInt(ViperParams.PARAM_HP_CLARITY_GAIN, v * 50)
    }

    fun setCureEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Cure: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(cureEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_CURE_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_HP_CURE_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(ViperParams.PARAM_HP_CURE_STRENGTH, intArrayOf(s.cureStrength))
                )
            )
        }
    }

    fun setCureStrength(v: Int) {
        _uiState.update { it.copy(cureStrength = v) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_CURE_STRENGTH}",
            ViperParams.PARAM_HP_CURE_STRENGTH,
            v
        )
    }

    fun setAnalogxEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "AnalogX: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(analogxEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_HP_ANALOGX_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_HP_ANALOGX_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_HP_ANALOGX_MODE, intArrayOf(s.analogxMode))
                )
            )
        }
    }

    fun setAnalogxMode(mode: Int) {
        _uiState.update { it.copy(analogxMode = mode) }; saveAndDispatchInt(
            "${ViperParams.PARAM_HP_ANALOGX_MODE}",
            ViperParams.PARAM_HP_ANALOGX_MODE,
            mode
        )
    }

    fun setSpeakerOptEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "Speaker Optimization: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(speakerOptEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE}",
                enabled
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE, if (enabled) 1 else 0)
    }

    fun setSpkConvolverEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Convolver: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkConvolverEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_SPK_CONVOLVER_ENABLE}",
                enabled
            )
        }
        val kernel = _uiState.value.spkConvolverKernel
        val effectiveEnabled = enabled && kernel.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            loadKernelByName(kernel, ViperParams.PARAM_SPK_CONVOLVER_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchInt(ViperParams.PARAM_SPK_CONVOLVER_ENABLE, 0)
        }
    }

    fun setSpkConvolverKernel(kernel: String) {
        FileLogger.i("ViewModel", "[Spk] Convolver kernel selected: $kernel")
        _uiState.update { it.copy(spkConvolverKernel = kernel) }
        viewModelScope.launch {
            repository.setStringPreference(
                "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                kernel
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            if (kernel.isEmpty()) {
                dispatchInt(ViperParams.PARAM_SPK_CONVOLVER_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.spkConvolverEnabled) ViperParams.PARAM_SPK_CONVOLVER_ENABLE else null
                loadKernelByName(kernel, enableParam)
            }
        }
    }

    fun setSpkConvolverCrossChannel(value: Int) {
        _uiState.update { it.copy(spkConvolverCrossChannel = value) }
        spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL}",
            ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
            value
        )
    }

    fun setSpkEqEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] EQ: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkEqEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_EQ_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_SPK_EQ_ENABLE, intArrayOf(if (enabled) 1 else 0))
                )
            )
        }
    }

    fun setSpkEqPreset(presetId: Long) {
        val state = _uiState.value
        val preset = state.spkEqPresets.find { it.id == presetId } ?: return
        val bands = preset.bands
        val bandCount = state.spkEqBandCount
        _uiState.update { s ->
            val updatedMap = s.spkEqBandsMap.toMutableMap().apply { put(bandCount, bands) }
            s.copy(spkEqPresetId = presetId, spkEqBands = bands, spkEqBandsMap = updatedMap)
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
                presetId.toInt()
            )
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$bandCount", bands)
        }
        spkDispatchEqBands(bands)
    }

    fun setSpkEqBands(bands: String) {
        val bandCount = _uiState.value.spkEqBandCount
        _uiState.update { state ->
            val updatedMap = state.spkEqBandsMap.toMutableMap().apply { put(bandCount, bands) }
            state.copy(spkEqBands = bands, spkEqBandsMap = updatedMap)
        }
        viewModelScope.launch {
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$bandCount", bands)
        }
        spkDispatchEqBands(bands)
    }

    fun setSpkEqBandCount(count: Int) {
        val currentState = _uiState.value
        val oldCount = currentState.spkEqBandCount
        FileLogger.d("ViewModel", "[Spk] EQ band count: $oldCount -> $count")
        val updatedMap = currentState.spkEqBandsMap.toMutableMap().apply {
            put(oldCount, currentState.spkEqBands)
        }
        val defaultBands =
            List(count) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        val bands = updatedMap[count] ?: defaultBands
        _uiState.update {
            it.copy(
                spkEqBandCount = count,
                spkEqBands = bands,
                spkEqPresetId = null,
                spkEqBandsMap = updatedMap
            )
        }
        viewModelScope.launch {
            repository.setIntPreference("spk_${ViperParams.PARAM_HP_EQ_BAND_COUNT}", count)
            repository.setStringPreference("${ViperParams.PARAM_SPK_EQ_BAND_LEVEL}", bands)
            repository.setStringPreference("spk_eq_bands_$oldCount", currentState.spkEqBands)
            repository.setStringPreference("spk_eq_bands_$count", bands)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchEqBands(
                ViperParams.PARAM_SPK_EQ_BAND_LEVEL,
                bands,
                ViperParams.PARAM_SPK_EQ_BAND_COUNT,
                count
            )
        }
        loadEqPresetsForBandCount(count, isSpk = true)
    }

    fun addSpkEqPreset(name: String) {
        val state = _uiState.value
        val preset =
            EqPreset(name = name, bandCount = state.spkEqBandCount, bands = state.spkEqBands)
        viewModelScope.launch {
            val id = repository.saveEqPreset(preset)
            _uiState.update { it.copy(spkEqPresetId = id) }
            repository.setIntPreference("spk_${ViperRepository.PREF_EQ_PRESET_ID}", id.toInt())
        }
    }

    fun deleteSpkEqPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteEqPresetById(presetId)
            if (_uiState.value.spkEqPresetId == presetId) {
                _uiState.update { it.copy(spkEqPresetId = null) }
                repository.setIntPreference("spk_${ViperRepository.PREF_EQ_PRESET_ID}", -1)
            }
        }
    }

    fun resetSpkEqBands() {
        val bandCount = _uiState.value.spkEqBandCount
        val flatBands =
            List(bandCount) { 0f }.joinToString(";") { String.format(Locale.US, "%.1f", it) } + ";"
        setSpkEqBands(flatBands)
        _uiState.update { it.copy(spkEqPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PREF_EQ_PRESET_ID}",
                -1
            )
        }
    }

    fun setSpkReverbEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Reverb: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkReverbEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_REVERB_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_SIZE,
                        intArrayOf(s.spkReverbRoomSize * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH,
                        intArrayOf(s.spkReverbWidth * 10)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING,
                        intArrayOf(s.spkReverbDampening)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL,
                        intArrayOf(s.spkReverbWet)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
                        intArrayOf(s.spkReverbDry)
                    )
                )
            )
        }
    }

    fun setSpkReverbRoomSize(v: Int) {
        _uiState.update { it.copy(spkReverbRoomSize = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_SIZE}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_SIZE, v * 10)
    }

    fun setSpkReverbWidth(v: Int) {
        _uiState.update { it.copy(spkReverbWidth = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH, v * 10)
    }

    fun setSpkReverbDampening(v: Int) {
        _uiState.update { it.copy(spkReverbDampening = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING, v)
    }

    fun setSpkReverbWet(v: Int) {
        _uiState.update { it.copy(spkReverbWet = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL, v)
    }

    fun setSpkReverbDry(v: Int) {
        _uiState.update { it.copy(spkReverbDry = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL}",
            ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
            v
        )
    }

    fun setSpkAgcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] AGC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkAgcEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("${ViperParams.PARAM_SPK_AGC_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(ViperParams.PARAM_SPK_AGC_ENABLE, intArrayOf(if (enabled) 1 else 0)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_RATIO,
                        intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(s.spkAgcStrength) { 50 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_MAX_SCALER,
                        intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(s.spkAgcMaxGain) { 100 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_AGC_VOLUME,
                        intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(s.spkAgcOutputThreshold) { 100 })
                    )
                )
            )
        }
    }

    fun setSpkAgcStrength(v: Int) {
        _uiState.update { it.copy(spkAgcStrength = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_RATIO}",
                v
            )
        }; spkDispatchInt(
            ViperParams.PARAM_SPK_AGC_RATIO,
            PLAYBACK_GAIN_RATIO_VALUES.getOrElse(v) { 50 })
    }

    fun setSpkAgcMaxGain(v: Int) {
        _uiState.update { it.copy(spkAgcMaxGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_MAX_SCALER}",
                v
            )
        }; spkDispatchInt(
            ViperParams.PARAM_SPK_AGC_MAX_SCALER,
            MULTI_FACTOR_VALUES.getOrElse(v) { 100 })
    }

    fun setSpkAgcOutputThreshold(v: Int) {
        _uiState.update { it.copy(spkAgcOutputThreshold = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_AGC_VOLUME}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_AGC_VOLUME, OUTPUT_DB_VALUES.getOrElse(v) { 100 })
    }

    fun setSpkFetEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] FET Compressor: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkFetEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
                        intArrayOf(if (enabled) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
                        intArrayOf(s.spkFetThreshold)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
                        intArrayOf(s.spkFetRatio)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
                        intArrayOf(if (s.spkFetAutoKnee) 100 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE, intArrayOf(s.spkFetKnee)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
                        intArrayOf(s.spkFetKneeMulti)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
                        intArrayOf(if (s.spkFetAutoGain) 100 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN, intArrayOf(s.spkFetGain)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
                        intArrayOf(if (s.spkFetAutoAttack) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
                        intArrayOf(s.spkFetAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
                        intArrayOf(s.spkFetMaxAttack)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
                        intArrayOf(if (s.spkFetAutoRelease) 100 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
                        intArrayOf(s.spkFetRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
                        intArrayOf(s.spkFetMaxRelease)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
                        intArrayOf(s.spkFetCrest)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
                        intArrayOf(s.spkFetAdapt)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
                        intArrayOf(if (s.spkFetNoClip) 100 else 0)
                    )
                )
            )
        }
    }

    fun setSpkFetThreshold(v: Int) {
        _uiState.update { it.copy(spkFetThreshold = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
            v
        )
    }

    fun setSpkFetRatio(v: Int) {
        _uiState.update { it.copy(spkFetRatio = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
            v
        )
    }

    fun setSpkFetAutoKnee(v: Boolean) {
        _uiState.update { it.copy(spkFetAutoKnee = v) }; spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
            v
        )
    }

    fun setSpkFetKnee(v: Int) {
        _uiState.update { it.copy(spkFetKnee = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
            v
        )
    }

    fun setSpkFetKneeMulti(v: Int) {
        _uiState.update { it.copy(spkFetKneeMulti = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
            v
        )
    }

    fun setSpkFetAutoGain(v: Boolean) {
        _uiState.update { it.copy(spkFetAutoGain = v) }; spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
            v
        )
    }

    fun setSpkFetGain(v: Int) {
        _uiState.update { it.copy(spkFetGain = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
            v
        )
    }

    fun setSpkFetAutoAttack(v: Boolean) {
        _uiState.update { it.copy(spkFetAutoAttack = v) }; spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
            v
        )
    }

    fun setSpkFetAttack(v: Int) {
        _uiState.update { it.copy(spkFetAttack = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
            v
        )
    }

    fun setSpkFetMaxAttack(v: Int) {
        _uiState.update { it.copy(spkFetMaxAttack = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
            v
        )
    }

    fun setSpkFetAutoRelease(v: Boolean) {
        _uiState.update { it.copy(spkFetAutoRelease = v) }; spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
            v
        )
    }

    fun setSpkFetRelease(v: Int) {
        _uiState.update { it.copy(spkFetRelease = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
            v
        )
    }

    fun setSpkFetMaxRelease(v: Int) {
        _uiState.update { it.copy(spkFetMaxRelease = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
            v
        )
    }

    fun setSpkFetCrest(v: Int) {
        _uiState.update { it.copy(spkFetCrest = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
            v
        )
    }

    fun setSpkFetAdapt(v: Int) {
        _uiState.update { it.copy(spkFetAdapt = v) }; spkSaveAndDispatchInt(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
            v
        )
    }

    fun setSpkFetNoClip(v: Boolean) {
        _uiState.update { it.copy(spkFetNoClip = v) }; spkSaveAndDispatchFetBool(
            "${ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP}",
            ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
            v
        )
    }

    fun setSpkOutputVolume(v: Int) {
        _uiState.update { it.copy(spkOutputVolume = v) }
        viewModelScope.launch {
            repository.setIntPreference(
                "${ViperParams.PARAM_SPK_OUTPUT_VOLUME}",
                v
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_OUTPUT_VOLUME,
            OUTPUT_VOLUME_VALUES.getOrElse(v) { 100 })
    }

    fun setSpkLimiter(v: Int) {
        _uiState.update { it.copy(spkLimiter = v) }
        viewModelScope.launch { repository.setIntPreference("${ViperParams.PARAM_SPK_LIMITER}", v) }
        spkDispatchInt(ViperParams.PARAM_SPK_LIMITER, OUTPUT_DB_VALUES.getOrElse(v) { 100 })
    }

    fun setSpkDdcEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] DDC: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkDdcEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DDC_ENABLE}",
                enabled
            )
        }
        val device = _uiState.value.spkDdcDevice
        val effectiveEnabled = enabled && device.isNotEmpty()
        if (effectiveEnabled && activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            loadVdcByName(device, ViperParams.PARAM_SPK_DDC_ENABLE)
        } else if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            dispatchInt(ViperParams.PARAM_SPK_DDC_ENABLE, 0)
        }
    }

    fun setSpkDdcDevice(device: String) {
        FileLogger.i("ViewModel", "[Spk] DDC selected: $device")
        _uiState.update { it.copy(spkDdcDevice = device) }
        viewModelScope.launch {
            repository.setStringPreference(
                "spk_${ViperRepository.PREF_DDC_DEVICE}",
                device
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            if (device.isEmpty()) {
                dispatchInt(ViperParams.PARAM_SPK_DDC_ENABLE, 0)
            } else {
                val enableParam =
                    if (_uiState.value.spkDdcEnabled) ViperParams.PARAM_SPK_DDC_ENABLE else null
                loadVdcByName(device, enableParam)
            }
        }
    }

    fun setSpkVseEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] VSE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkVseEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
                        intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(s.spkVseStrength) { 7600 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                        intArrayOf((s.spkVseExciter * 5.6).toInt())
                    )
                )
            )
        }
    }

    fun setSpkVseStrength(value: Int) {
        _uiState.update { it.copy(spkVseStrength = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
            VSE_BARK_VALUES.getOrElse(value) { 7600 })
    }

    fun setSpkVseExciter(value: Int) {
        _uiState.update { it.copy(spkVseExciter = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
            (value * 5.6).toInt()
        )
    }

    fun setSpkFieldSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Field Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkFieldSurroundEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
                        intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(s.spkFieldSurroundWidening) { 0 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
                        intArrayOf(s.spkFieldSurroundMidImage * 10 + 100)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
                        intArrayOf(s.spkFieldSurroundDepth * 75 + 200)
                    )
                )
            )
        }
    }

    fun setSpkFieldSurroundWidening(value: Int) {
        _uiState.update { it.copy(spkFieldSurroundWidening = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
            FIELD_SURROUND_WIDENING_VALUES.getOrElse(value) { 0 })
    }

    fun setSpkFieldSurroundMidImage(value: Int) {
        _uiState.update { it.copy(spkFieldSurroundMidImage = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE, value * 10 + 100)
    }

    fun setSpkFieldSurroundDepth(value: Int) {
        _uiState.update { it.copy(spkFieldSurroundDepth = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH}",
                value
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH, value * 75 + 200)
    }

    fun setSpkDiffSurroundEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Diff Surround: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkDiffSurroundEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
                        intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(s.spkDiffSurroundDelay) { 500 })
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE,
                        intArrayOf(if (s.spkDiffSurroundReverse) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkDiffSurroundDelay(value: Int) {
        _uiState.update { it.copy(spkDiffSurroundDelay = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY}",
                value
            )
        }
        spkDispatchInt(
            ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
            DIFF_SURROUND_DELAY_VALUES.getOrElse(value) { 500 })
    }

    fun setSpkDiffSurroundReverse(reverse: Boolean) {
        _uiState.update { it.copy(spkDiffSurroundReverse = reverse) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE}",
                reverse
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE, if (reverse) 1 else 0)
    }

    fun setSpkVheEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] VHE: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkVheEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
                        intArrayOf(s.spkVheQuality)
                    )
                )
            )
        }
    }

    fun setSpkVheQuality(value: Int) {
        _uiState.update { it.copy(spkVheQuality = value) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH}",
            ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
            value
        )
    }

    fun setSpkDynamicSystemEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Dynamic System: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkDynamicSystemEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE}",
                enabled
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDynamicSystemStrength(value: Int) {
        _uiState.update { it.copy(spkDynamicSystemStrength = value) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH}",
                value
            )
        }
        spkDispatchDynamicSystem()
    }

    private fun spkDispatchDynamicSystem() {
        if (activeDeviceType != ViperParams.FX_TYPE_SPEAKER) return
        val s = _uiState.value
        viperService?.dispatchParamsBatch(
            listOf(
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
                    intArrayOf(if (s.spkDynamicSystemEnabled) 1 else 0)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
                    intArrayOf(s.spkDynamicSystemStrength * 20 + 100)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
                    intArrayOf(s.spkDsXLow, s.spkDsXHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
                    intArrayOf(s.spkDsYLow, s.spkDsYHigh)
                ),
                ParamEntry(
                    ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN,
                    intArrayOf(s.spkDsSideGainLow, s.spkDsSideGainHigh)
                )
            )
        )
    }

    fun setSpkDsPreset(presetId: Long) {
        val preset = _uiState.value.spkDsPresets.find { it.id == presetId } ?: return
        _uiState.update {
            it.copy(
                spkDsPresetId = presetId,
                spkDsXLow = preset.xLow, spkDsXHigh = preset.xHigh,
                spkDsYLow = preset.yLow, spkDsYHigh = preset.yHigh,
                spkDsSideGainLow = preset.sideGainLow, spkDsSideGainHigh = preset.sideGainHigh
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                presetId.toInt()
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                preset.xLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                preset.xHigh
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                preset.yLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                preset.yHigh
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                preset.sideGainLow
            )
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                preset.sideGainHigh
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsXLow(v: Int) {
        _uiState.update { it.copy(spkDsXLow = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_low",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsXHigh(v: Int) {
        _uiState.update { it.copy(spkDsXHigh = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS}_high",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsYLow(v: Int) {
        _uiState.update { it.copy(spkDsYLow = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_low",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsYHigh(v: Int) {
        _uiState.update { it.copy(spkDsYHigh = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS}_high",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsSideGainLow(v: Int) {
        _uiState.update { it.copy(spkDsSideGainLow = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_low",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkDsSideGainHigh(v: Int) {
        _uiState.update { it.copy(spkDsSideGainHigh = v, spkDsPresetId = null) }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN}_high",
                v
            )
            repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
        }
        spkDispatchDynamicSystem()
    }

    fun addSpkDsPreset(name: String) {
        val s = _uiState.value
        val preset = DsPreset(
            name = name,
            xLow = s.spkDsXLow, xHigh = s.spkDsXHigh,
            yLow = s.spkDsYLow, yHigh = s.spkDsYHigh,
            sideGainLow = s.spkDsSideGainLow, sideGainHigh = s.spkDsSideGainHigh
        )
        viewModelScope.launch {
            val id = repository.saveDsPreset(preset)
            _uiState.update { it.copy(spkDsPresetId = id) }
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                id.toInt()
            )
        }
    }

    fun deleteSpkDsPreset(presetId: Long) {
        viewModelScope.launch {
            repository.deleteDsPresetById(presetId)
            if (_uiState.value.spkDsPresetId == presetId) {
                _uiState.update { it.copy(spkDsPresetId = null) }
                repository.setIntPreference("spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}", -1)
            }
        }
    }

    fun resetSpkDsCoefficients() {
        _uiState.update {
            it.copy(
                spkDsXLow = 100, spkDsXHigh = 5600,
                spkDsYLow = 40, spkDsYHigh = 80,
                spkDsSideGainLow = 50, spkDsSideGainHigh = 50,
                spkDsPresetId = null
            )
        }
        viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperRepository.PERF_DYNAMIC_SYS_PRESET_ID}",
                -1
            )
        }
        spkDispatchDynamicSystem()
    }

    fun setSpkTubeSimulatorEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Tube Simulator: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkTubeSimulatorEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Bass: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkBassEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_BASS_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_BASS_MODE, intArrayOf(s.spkBassMode)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_FREQUENCY,
                        intArrayOf(s.spkBassFrequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_GAIN,
                        intArrayOf(s.spkBassGain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_ANTI_POP,
                        intArrayOf(if (s.spkBassAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassMode(mode: Int) {
        _uiState.update { it.copy(spkBassMode = mode) }; spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_BASS_MODE}",
            ViperParams.PARAM_SPK_BASS_MODE,
            mode
        )
    }

    fun setSpkBassFrequency(v: Int) {
        _uiState.update { it.copy(spkBassFrequency = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_FREQUENCY}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_BASS_FREQUENCY, v + 15)
    }

    fun setSpkBassGain(v: Int) {
        _uiState.update { it.copy(spkBassGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_GAIN}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_BASS_GAIN, v * 50 + 50)
    }

    fun setSpkBassAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(spkBassAntiPop = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_BASS_ANTI_POP}", enabled)
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setSpkBassMonoEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Bass Mono: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkBassMonoEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_ENABLE}",
                enabled
            )
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_BASS_MONO_MODE, intArrayOf(s.spkBassMonoMode)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY,
                        intArrayOf(s.spkBassMonoFrequency + 15)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_GAIN,
                        intArrayOf(s.spkBassMonoGain * 50 + 50)
                    ),
                    ParamEntry(
                        ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP,
                        intArrayOf(if (s.spkBassMonoAntiPop) 1 else 0)
                    )
                )
            )
        }
    }

    fun setSpkBassMonoMode(mode: Int) {
        _uiState.update { it.copy(spkBassMonoMode = mode) }; spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_BASS_MONO_MODE}",
            ViperParams.PARAM_SPK_BASS_MONO_MODE,
            mode
        )
    }

    fun setSpkBassMonoFrequency(v: Int) {
        _uiState.update { it.copy(spkBassMonoFrequency = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY, v + 15)
    }

    fun setSpkBassMonoGain(v: Int) {
        _uiState.update { it.copy(spkBassMonoGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_GAIN}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_GAIN, v * 50 + 50)
    }

    fun setSpkBassMonoAntiPop(enabled: Boolean) {
        _uiState.update { it.copy(spkBassMonoAntiPop = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference(
                "spk_${ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP}",
                enabled
            )
        }
        spkDispatchInt(ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP, if (enabled) 1 else 0)
    }

    fun setSpkClarityEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Clarity: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkClarityEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_CLARITY_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_CLARITY_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_CLARITY_MODE, intArrayOf(s.spkClarityMode)),
                    ParamEntry(
                        ViperParams.PARAM_SPK_CLARITY_GAIN,
                        intArrayOf(s.spkClarityGain * 50)
                    )
                )
            )
        }
    }

    fun setSpkClarityMode(mode: Int) {
        _uiState.update { it.copy(spkClarityMode = mode) }; spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CLARITY_MODE}",
            ViperParams.PARAM_SPK_CLARITY_MODE,
            mode
        )
    }

    fun setSpkClarityGain(v: Int) {
        _uiState.update { it.copy(spkClarityGain = v) }; viewModelScope.launch {
            repository.setIntPreference(
                "spk_${ViperParams.PARAM_SPK_CLARITY_GAIN}",
                v
            )
        }; spkDispatchInt(ViperParams.PARAM_SPK_CLARITY_GAIN, v * 50)
    }

    fun setSpkCureEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] Cure: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkCureEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_CURE_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_CURE_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_CURE_STRENGTH, intArrayOf(s.spkCureStrength))
                )
            )
        }
    }

    fun setSpkCureStrength(v: Int) {
        _uiState.update { it.copy(spkCureStrength = v) }; spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CURE_STRENGTH}",
            ViperParams.PARAM_SPK_CURE_STRENGTH,
            v
        )
    }

    fun setSpkAnalogxEnabled(enabled: Boolean) {
        FileLogger.i("ViewModel", "[Spk] AnalogX: ${if (enabled) "ON" else "OFF"}")
        _uiState.update { it.copy(spkAnalogxEnabled = enabled) }
        viewModelScope.launch {
            repository.setBooleanPreference("spk_${ViperParams.PARAM_SPK_ANALOGX_ENABLE}", enabled)
        }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) {
            val s = _uiState.value
            viperService?.dispatchParamsBatch(
                listOf(
                    ParamEntry(
                        ViperParams.PARAM_SPK_ANALOGX_ENABLE,
                        intArrayOf(if (enabled) 1 else 0)
                    ),
                    ParamEntry(ViperParams.PARAM_SPK_ANALOGX_MODE, intArrayOf(s.spkAnalogxMode))
                )
            )
        }
    }

    fun setSpkAnalogxMode(mode: Int) {
        _uiState.update { it.copy(spkAnalogxMode = mode) }; spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_ANALOGX_MODE}",
            ViperParams.PARAM_SPK_ANALOGX_MODE,
            mode
        )
    }

    fun setSpkChannelPan(value: Int) {
        _uiState.update { it.copy(spkChannelPan = value) }
        spkSaveAndDispatchInt(
            "spk_${ViperParams.PARAM_SPK_CHANNEL_PAN}",
            ViperParams.PARAM_SPK_CHANNEL_PAN,
            value
        )
    }

    private suspend fun ensureDeviceEntry(device: AudioDevice) {
        val existing = repository.getDeviceSettings(device.id)
        if (existing == null) {
            val state = _uiState.value
            val isSpk = !device.isHeadphone
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = device.id,
                    deviceName = device.name,
                    isHeadphone = device.isHeadphone,
                    settingsJson = serializeEffectPrefs(state, isSpk).toString()
                )
            )
        } else {
            repository.updateDeviceLastConnected(device.id)
        }
    }

    private suspend fun saveCurrentDeviceSettings() {
        val state = _uiState.value
        val isSpk = activeDeviceType == ViperParams.FX_TYPE_SPEAKER
        val json = serializeEffectPrefs(state, isSpk).toString()
        val existing = repository.getDeviceSettings(currentDeviceId)
        repository.saveDeviceSettings(
            DeviceSettings(
                deviceId = currentDeviceId,
                deviceName = existing?.deviceName ?: state.activeDeviceName,
                isHeadphone = existing?.isHeadphone ?: !isSpk,
                settingsJson = json
            )
        )
    }

    private suspend fun loadDeviceSettings(device: AudioDevice) {
        val saved = repository.getDeviceSettings(device.id) ?: return
        val isSpk = !device.isHeadphone
        val json = JSONObject(saved.settingsJson)
        _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
        saveEffectPrefs(repository, _uiState.value, isSpk)
        applyFullState()
    }

    fun renameDevice(deviceId: String, name: String) {
        viewModelScope.launch {
            repository.renameDevice(deviceId, name)
            if (deviceId == currentDeviceId) {
                _uiState.update { it.copy(activeDeviceName = name) }
            }
        }
    }

    fun deleteDeviceSettings(deviceId: String) {
        viewModelScope.launch { repository.deleteDeviceSettings(deviceId) }
    }

    fun saveDevicePreset(deviceId: String) {
        viewModelScope.launch {
            val existing = repository.getDeviceSettings(deviceId) ?: return@launch
            val state = _uiState.value
            val isSpk = !existing.isHeadphone
            val json = serializeEffectPrefs(state, isSpk).toString()
            repository.saveDeviceSettings(existing.copy(settingsJson = json))
        }
    }

    fun loadDevicePreset(deviceId: String) {
        viewModelScope.launch {
            val saved = repository.getDeviceSettings(deviceId) ?: return@launch
            val isSpk = !saved.isHeadphone
            val json = JSONObject(saved.settingsJson)
            _uiState.update { deserializeEffectPrefs(json, it, isSpk) }
            saveEffectPrefs(repository, _uiState.value, isSpk)
            applyFullState()
        }
    }

    private fun getFilesDir(subDir: String): File {
        val dir = File(getApplication<Application>().getExternalFilesDir(null), subDir)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun copyUriToFile(uri: Uri, destDir: File, fallbackName: String): File? {
        val context = getApplication<Application>()
        val fileName = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex >= 0) cursor.getString(nameIndex) else null
        } ?: fallbackName
        val destFile = File(destDir, fileName)
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
            destFile
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to copy file", e)
            null
        }
    }

    fun importPresetFile(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("Preset")
            val destFile = copyUriToFile(uri, destDir, "preset.json") ?: return false
            val json = destFile.readText()
            val obj = JSONObject(json)
            val isSpk = obj.has("spkMasterEnabled") && !obj.has("masterEnabled")
            val fxType = if (isSpk) ViperParams.FX_TYPE_SPEAKER else ViperParams.FX_TYPE_HEADPHONE
            deserializeAndApplyStateForMode(json, fxType)
            viewModelScope.launch { persistStateForMode(fxType) }
            if (fxType == activeDeviceType) {
                applyFullState()
            }
            val presetName = destFile.nameWithoutExtension
            viewModelScope.launch {
                val existing = repository.getPresetByNameAndFxType(presetName, fxType)
                if (existing != null) {
                    repository.updatePreset(
                        existing.copy(
                            settingsJson = json,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    repository.savePreset(
                        Preset(
                            name = presetName,
                            fxType = fxType,
                            settingsJson = json
                        )
                    )
                }
            }
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import preset", e)
            false
        }
    }

    fun importKernel(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("Kernel")
            copyUriToFile(uri, destDir, "kernel.wav") ?: return false
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import kernel", e)
            false
        }
    }

    fun importVdc(uri: Uri): Boolean {
        return try {
            val destDir = getFilesDir("DDC")
            copyUriToFile(uri, destDir, "imported.vdc") ?: return false
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to import VDC", e)
            false
        }
    }

    fun refreshFileLists() {
        val ddcDir = getFilesDir("DDC")
        _vdcFileList.value = ddcDir.listFiles()
            ?.filter { it.extension == "vdc" }
            ?.map { it.nameWithoutExtension }
            ?.sorted() ?: emptyList()

        val kernelDir = getFilesDir("Kernel")
        _kernelFileList.value = kernelDir.listFiles()
            ?.map { it.name }
            ?.sorted() ?: emptyList()
    }

    fun deleteVdcFile(name: String): Boolean {
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            if (!file.exists()) return false
            file.delete()
            val state = _uiState.value
            if (state.ddcDevice == name) {
                _uiState.update { it.copy(ddcDevice = "") }
                viewModelScope.launch {
                    repository.setStringPreference(
                        ViperRepository.PREF_DDC_DEVICE,
                        ""
                    )
                }
            }
            if (state.spkDdcDevice == name) {
                _uiState.update { it.copy(spkDdcDevice = "") }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "spk_${ViperRepository.PREF_DDC_DEVICE}",
                        ""
                    )
                }
            }
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to delete VDC: $name", e)
            false
        }
    }

    fun deleteKernelFile(fileName: String): Boolean {
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            if (!file.exists()) return false
            file.delete()
            val state = _uiState.value
            if (state.convolverKernel == fileName) {
                _uiState.update { it.copy(convolverKernel = "") }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                        ""
                    )
                }
            }
            if (state.spkConvolverKernel == fileName) {
                _uiState.update { it.copy(spkConvolverKernel = "") }
                viewModelScope.launch {
                    repository.setStringPreference(
                        "spk_${ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL}",
                        ""
                    )
                }
            }
            refreshFileLists()
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to delete kernel: $fileName", e)
            false
        }
    }

    fun loadVdcByName(name: String, enableParam: Int? = null): Boolean {
        FileLogger.i("ViewModel", "Loading DDC: $name")
        return try {
            val file = File(getFilesDir("DDC"), "$name.vdc")
            if (!file.exists()) return false
            val lines = file.readLines()

            var coeffs44100: FloatArray? = null
            var coeffs48000: FloatArray? = null

            for (line in lines) {
                val trimmed = line.trim()
                when {
                    trimmed.startsWith("SR_44100:") -> {
                        coeffs44100 = trimmed.removePrefix("SR_44100:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }

                    trimmed.startsWith("SR_48000:") -> {
                        coeffs48000 = trimmed.removePrefix("SR_48000:")
                            .split(",").map { it.trim().toFloat() }.toFloatArray()
                    }
                }
            }

            if (coeffs44100 == null || coeffs48000 == null) return false
            if (coeffs44100.size != coeffs48000.size) return false
            if (coeffs44100.size % 5 != 0) return false

            val arrSize = coeffs44100.size
            val naturalSize = 4 + arrSize * 4 * 2
            val wireSize = when {
                naturalSize <= 256 -> 256
                naturalSize <= 1024 -> 1024
                else -> return false
            }
            val buffer = ByteBuffer.allocate(wireSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)

            val service = viperService ?: return false
            val extras =
                if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
            service.dispatchParam(ViperParams.PARAM_HP_DDC_COEFFICIENTS, buffer.array(), extras)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load VDC: $name", e)
            false
        }
    }

    fun loadKernelByName(fileName: String, enableParam: Int? = null): Boolean {
        FileLogger.i("ViewModel", "Loading convolver kernel: $fileName")
        return try {
            val file = File(getFilesDir("Kernel"), fileName)
            if (!file.exists()) return false

            if (_aidlModeEnabled.value) {
                return loadKernelViaFile(file, fileName, enableParam)
            }

            val wavBytes = file.readBytes()
            val floatSamples = decodeWavToFloat(wavBytes) ?: return false
            val channelCount = getWavChannelCount(wavBytes)
            val totalFloats = floatSamples.size
            FileLogger.i(
                "ViewModel",
                "Kernel loaded: $fileName samples=$totalFloats ch=$channelCount"
            )

            val service = viperService ?: return false

            val prepareParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_PREPARE_BUFFER else ViperParams.PARAM_HP_CONVOLVER_PREPARE_BUFFER
            val setParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_BUFFER else ViperParams.PARAM_HP_CONVOLVER_SET_BUFFER
            val commitParam = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_COMMIT_BUFFER else ViperParams.PARAM_HP_CONVOLVER_COMMIT_BUFFER

            service.dispatchParam(prepareParam, totalFloats, channelCount, 0)

            val floatBytes = ByteBuffer.allocate(totalFloats * 4).order(ByteOrder.LITTLE_ENDIAN)
            for (f in floatSamples) floatBytes.putFloat(f)
            val rawBytes = floatBytes.array()

            val crc = CRC32()
            crc.update(rawBytes)
            val crcValue = crc.value.toInt()

            val maxFloatsPerChunk = 2046
            var offset = 0
            var chunkIndex = 0
            while (offset < totalFloats) {
                val remaining = totalFloats - offset
                val floatsInChunk = minOf(remaining, maxFloatsPerChunk)
                val chunkByteCount = floatsInChunk * 4

                val chunkBuffer = ByteBuffer.allocate(8192).order(ByteOrder.LITTLE_ENDIAN)
                chunkBuffer.putInt(chunkIndex)
                chunkBuffer.putInt(floatsInChunk)
                chunkBuffer.put(rawBytes, offset * 4, chunkByteCount)

                service.dispatchParam(setParam, chunkBuffer.array())
                offset += floatsInChunk
                chunkIndex++
            }

            val kernelId = fileName.hashCode()
            service.dispatchParam(commitParam, totalFloats, crcValue, kernelId)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load kernel: $fileName", e)
            false
        }
    }

    private fun loadKernelViaFile(file: File, fileName: String, enableParam: Int? = null): Boolean {
        return try {
            val safeName = fileName.replace("'", "")
            val subDir = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            FileLogger.i("ViewModel", "Kernel copied to $kernelPath")

            val param = if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = ByteBuffer.allocate(256).order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            val service = viperService ?: return false
            val extras =
                if (enableParam != null) listOf(ParamEntry(enableParam, intArrayOf(1))) else null
            service.dispatchParam(param, buffer.array(), extras)
            true
        } catch (e: Exception) {
            FileLogger.e("ViewModel", "Failed to load kernel via file: $fileName", e)
            false
        }
    }

    private fun getWavChannelCount(wavBytes: ByteArray): Int {
        if (wavBytes.size < 44) return 1
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(22)
        return buf.short.toInt()
    }

    private fun decodeWavToFloat(wavBytes: ByteArray): FloatArray? {
        if (wavBytes.size < 44) return null
        val buf = ByteBuffer.wrap(wavBytes).order(ByteOrder.LITTLE_ENDIAN)

        val riff = ByteArray(4)
        buf.get(riff)
        if (String(riff) != "RIFF") return null
        buf.int
        val wave = ByteArray(4)
        buf.get(wave)
        if (String(wave) != "WAVE") return null

        var audioFormat = 0
        var numChannels = 0
        var bitsPerSample = 0
        var dataBytes: ByteArray? = null

        while (buf.remaining() >= 8) {
            val chunkId = ByteArray(4)
            buf.get(chunkId)
            val chunkSize = buf.int
            val chunkIdStr = String(chunkId)

            when (chunkIdStr) {
                "fmt " -> {
                    val fmtStart = buf.position()
                    audioFormat = buf.short.toInt() and 0xFFFF
                    numChannels = buf.short.toInt() and 0xFFFF
                    buf.int
                    buf.int
                    buf.short
                    bitsPerSample = buf.short.toInt() and 0xFFFF
                    buf.position(fmtStart + chunkSize)
                }

                "data" -> {
                    val safeSize = minOf(chunkSize, buf.remaining())
                    dataBytes = ByteArray(safeSize)
                    buf.get(dataBytes)
                }

                else -> {
                    val skip = minOf(chunkSize, buf.remaining())
                    buf.position(buf.position() + skip)
                }
            }
        }

        val data = dataBytes ?: return null
        if (numChannels < 1 || numChannels > 2) return null

        val dataBuf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = bitsPerSample / 8
        if (bytesPerSample == 0) return null
        val totalSamples = data.size / bytesPerSample
        val result = FloatArray(totalSamples)

        when {
            audioFormat == 1 && bitsPerSample == 16 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.short.toFloat() / 32768f
            }

            audioFormat == 1 && bitsPerSample == 24 -> {
                for (i in 0 until totalSamples) {
                    val b0 = dataBuf.get().toInt() and 0xFF
                    val b1 = dataBuf.get().toInt() and 0xFF
                    val b2 = dataBuf.get().toInt()
                    result[i] = ((b2 shl 16) or (b1 shl 8) or b0).toFloat() / 8388608f
                }
            }

            audioFormat == 1 && bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.int.toFloat() / 2147483648f
            }

            audioFormat == 3 && bitsPerSample == 32 -> {
                for (i in 0 until totalSamples) result[i] = dataBuf.float
            }

            else -> return null
        }

        return result
    }

    private suspend fun persistStateForMode(fxType: Int) {
        val s = _uiState.value
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        saveEffectPrefs(repository, s, isSpk)
        if (isSpk) {
            for ((bc, bands) in s.spkEqBandsMap) {
                repository.setStringPreference("spk_eq_bands_$bc", bands)
            }
        } else {
            for ((bc, bands) in s.eqBandsMap) {
                repository.setStringPreference("eq_bands_$bc", bands)
            }
        }
    }

    private fun loadSettingsPreferences() {
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_AUTO_START).collect { v ->
                _autoStartEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_AIDL_MODE).collect { v ->
                _aidlModeEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_GLOBAL_MODE).collect { v ->
                _globalModeEnabled.value = v
            }
        }
        viewModelScope.launch {
            repository.getBooleanPreference(PREF_DEBUG_MODE).collect { v ->
                _debugModeEnabled.value = v
            }
        }
    }

    fun enableDebugMode() {
        _debugModeEnabled.value = true
        viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, true) }
    }

    fun disableDebugMode() {
        _debugModeEnabled.value = false
        viewModelScope.launch { repository.setBooleanPreference(PREF_DEBUG_MODE, false) }
    }

    fun savePreset(name: String) {
        viewModelScope.launch {
            val state = _uiState.value
            val fxType = state.fxType
            val mode = if (fxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.i("ViewModel", "Dispatch: savePreset name=$name mode=$mode")
            val json = serializeStateForMode(state, fxType)
            val preset = Preset(
                name = name,
                fxType = fxType,
                settingsJson = json
            )
            repository.savePreset(preset)
            try {
                val presetDir = getFilesDir("Preset")
                File(presetDir, "$name.json").writeText(json)
            } catch (e: Exception) {
                FileLogger.e("ViewModel", "Failed to write preset file", e)
            }
        }
    }

    fun loadPreset(id: Long) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            val targetFxType = preset.fxType
            val mode = if (targetFxType == ViperParams.FX_TYPE_HEADPHONE) "Headphone" else "Speaker"
            FileLogger.i("ViewModel", "Dispatch: loadPreset name=${preset.name} mode=$mode")
            deserializeAndApplyStateForMode(preset.settingsJson, targetFxType)
            persistStateForMode(targetFxType)
            if (targetFxType == activeDeviceType) {
                applyFullState()
            }
        }
    }

    fun deletePreset(id: Long) {
        viewModelScope.launch {
            repository.deletePresetById(id)
        }
    }

    fun renamePreset(id: Long, newName: String) {
        viewModelScope.launch {
            val preset = repository.getPresetById(id) ?: return@launch
            repository.updatePreset(
                preset.copy(
                    name = newName,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun queryDriverStatus() {
        if (_aidlModeEnabled.value) {
            queryDriverStatusFromFile()
            return
        }
        val effect = viperService?.getGlobalEffect()
        if (effect != null && effect.isCreated) {
            queryDriverStatusFrom(effect)
            return
        }
        val typeUuid = ViperEffect.EFFECT_TYPE_UUID
        val probe = ViperEffect(0, typeUuid)
        if (!probe.create()) {
            _driverStatus.value = DriverStatus(installed = false)
            probe.release()
            return
        }
        queryDriverStatusFrom(probe)
        probe.release()
    }

    private fun queryDriverStatusFromFile() {
        val status = ConfigChannel.readStatus()
        if (status == null || status.versionCode <= 0) {
            if (_driverStatus.value.installed) return
            _driverStatus.value = DriverStatus(installed = false)
            return
        }
        _driverStatus.value = DriverStatus(
            installed = true,
            versionCode = status.versionCode,
            versionName = status.versionName,
            architecture = status.architecture,
            streaming = status.streaming,
            samplingRate = status.sampleRate
        )
    }

    private fun queryDriverStatusFrom(effect: ViperEffect) {
        val versionCode = effect.getDriverVersionCode()
        val archName = effect.getArchitectureString()
        val streaming = effect.isStreaming()
        val samplingRate = effect.getParameter(ViperParams.PARAM_GET_SAMPLING_RATE)

        val versionBytes = effect.getParameter(ViperParams.PARAM_GET_DRIVER_VERSION_NAME, 256)
        val versionName = if (versionBytes.isNotEmpty()) {
            val nullIdx = versionBytes.indexOf(0.toByte())
            if (nullIdx >= 0) String(versionBytes, 0, nullIdx) else String(versionBytes)
        } else {
            versionCode.toString()
        }

        _driverStatus.value = DriverStatus(
            installed = true,
            versionCode = versionCode,
            versionName = versionName,
            architecture = archName,
            streaming = streaming,
            samplingRate = samplingRate
        )
    }

    fun toggleAutoStart(enabled: Boolean) {
        _autoStartEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_AUTO_START, enabled)
        }
    }

    fun toggleAidlMode(enabled: Boolean) {
        _aidlModeEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_AIDL_MODE, enabled)
        }
        viperService?.recreateGlobalEffect(enabled)
    }

    fun toggleGlobalMode(enabled: Boolean) {
        _globalModeEnabled.value = enabled
        viewModelScope.launch {
            repository.setBooleanPreference(PREF_GLOBAL_MODE, enabled)
        }
        viperService?.setGlobalMode(enabled)
    }

    private fun serializeStateForMode(state: MainUiState, fxType: Int): String {
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        val obj = serializeEffectPrefs(state, isSpk)
        return obj.toString()
    }

    private fun deserializeAndApplyStateForMode(json: String, fxType: Int) {
        val obj = JSONObject(json)
        val isSpk = fxType != ViperParams.FX_TYPE_HEADPHONE
        _uiState.update { state ->
            deserializeEffectPrefs(obj, state, isSpk)
        }
    }

    private fun saveAndDispatchInt(prefKey: String, param: Int, value: Int) {
        viewModelScope.launch { repository.setIntPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(param, value)
    }

    private fun saveAndDispatchFetBool(prefKey: String, param: Int, value: Boolean) {
        viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(
            param,
            if (value) 100 else 0
        )
    }

    private fun spkSaveAndDispatchInt(prefKey: String, param: Int, value: Int) {
        viewModelScope.launch { repository.setIntPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(param, value)
    }

    private fun spkSaveAndDispatchFetBool(prefKey: String, param: Int, value: Boolean) {
        viewModelScope.launch { repository.setBooleanPreference(prefKey, value) }
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(
            param,
            if (value) 100 else 0
        )
    }

    private fun dispatchInt(param: Int, value: Int) {
        viperService?.dispatchParam(param, value)
    }

    private fun hpDispatchInt(param: Int, value: Int) {
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchInt(param, value)
    }

    private fun spkDispatchInt(param: Int, value: Int) {
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchInt(param, value)
    }

    private fun dispatchEqBands(
        param: Int,
        bandsString: String,
        bandCountParam: Int = 0,
        bandCount: Int = 0
    ) {
        viperService?.dispatchEqBands(param, bandsString, bandCountParam, bandCount)
    }

    private fun hpDispatchEqBands(bandsString: String) {
        if (activeDeviceType == ViperParams.FX_TYPE_HEADPHONE) dispatchEqBands(
            ViperParams.PARAM_HP_EQ_BAND_LEVEL,
            bandsString
        )
    }

    private fun spkDispatchEqBands(bandsString: String) {
        if (activeDeviceType == ViperParams.FX_TYPE_SPEAKER) dispatchEqBands(
            ViperParams.PARAM_SPK_EQ_BAND_LEVEL,
            bandsString
        )
    }
}
