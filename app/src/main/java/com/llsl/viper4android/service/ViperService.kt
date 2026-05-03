package com.llsl.viper4android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Binder
import android.os.IBinder
import android.util.SparseArray
import androidx.core.app.NotificationCompat
import androidx.core.util.size
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.llsl.viper4android.R
import com.llsl.viper4android.audio.AudioDevice
import com.llsl.viper4android.audio.AudioOutputDetector
import com.llsl.viper4android.audio.AudioSessionMonitor
import com.llsl.viper4android.audio.ByteArrayParam
import com.llsl.viper4android.audio.ConfigChannel
import com.llsl.viper4android.audio.EffectDispatcher
import com.llsl.viper4android.audio.ParamEntry
import com.llsl.viper4android.audio.ViperEffect
import com.llsl.viper4android.audio.ViperParams
import com.llsl.viper4android.data.model.DeviceSettings
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.ui.screens.main.MainUiState
import com.llsl.viper4android.ui.screens.main.MainViewModel
import com.llsl.viper4android.ui.screens.main.deserializeEffectPrefs
import com.llsl.viper4android.ui.screens.main.serializeEffectPrefs
import com.llsl.viper4android.utils.FileLogger
import com.llsl.viper4android.utils.RootShell
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@AndroidEntryPoint
class ViperService : LifecycleService() {

    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val NOTIFICATION_CHANNEL_ID = "viper4android_service"

        const val ACTION_START = "com.llsl.viper4android.service.START"
        const val ACTION_STOP = "com.llsl.viper4android.service.STOP"

        fun startService(context: Context) {
            val intent = Intent(context, ViperService::class.java).apply {
                action = ACTION_START
            }
            context.startForegroundService(intent)
        }
    }

    inner class LocalBinder : Binder() {
        val service: ViperService get() = this@ViperService
    }

    private val binder = LocalBinder()
    private val sessions = SparseArray<ViperEffect>()
    private var globalEffect: ViperEffect? = null
    private var useAidlTypeUuid: Boolean = true
    private var globalMode: Boolean = false
    private var audioOutputDetector: AudioOutputDetector? = null
    private var sessionMonitor: AudioSessionMonitor? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        FileLogger.i("Service", "Service created")
        lifecycleScope.launch {
            useAidlTypeUuid = repository.getBooleanPreference(MainViewModel.PREF_AIDL_MODE).first()
            globalMode = repository.getBooleanPreference(MainViewModel.PREF_GLOBAL_MODE).first()
            if (globalMode) {
                initGlobalEffect()
            } else {
                startSessionMonitor()
            }
            startAudioOutputMonitor()
        }
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    private fun initGlobalEffect() {
        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(0, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create global effect")
            return
        }
        globalEffect = effect
        FileLogger.i("Service", "Global effect created (aidlType=$useAidlTypeUuid)")
        lifecycleScope.launch {
            applyFullStateToEffect(effect)
            FileLogger.i("Service", "Global effect initialized with full state")
        }
    }

    private var currentServiceDeviceId: String = AudioDevice.ID_SPEAKER

    private fun startAudioOutputMonitor() {
        val detector = AudioOutputDetector(this)
        audioOutputDetector = detector
        currentServiceDeviceId = detector.activeDevice.value.id
        lifecycleScope.launch {
            detector.activeDevice.collect { device ->
                if (device.id != currentServiceDeviceId) {
                    FileLogger.i(
                        "Service",
                        "Device changed: $currentServiceDeviceId -> ${device.id} (${device.name})"
                    )
                    saveOldDeviceToDb()
                    currentServiceDeviceId = device.id
                    reapplyForDevice(device)
                }
            }
        }
    }

    private suspend fun saveOldDeviceToDb() {
        val oldId = currentServiceDeviceId
        val existing = repository.getDeviceSettings(oldId) ?: return
        val currentState = EffectDispatcher.loadFullStateFromPrefs(repository)
        val isSpk = !existing.isHeadphone
        val json = serializeEffectPrefs(currentState, isSpk)
        repository.saveDeviceSettings(existing.copy(settingsJson = json.toString()))
        FileLogger.i("Service", "Saved current settings to DB for $oldId")
    }

    private suspend fun reapplyForDevice(device: AudioDevice) {
        val fxType =
            if (device.isHeadphone) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
        val saved = repository.getDeviceSettings(device.id)
        val state: MainUiState
        if (saved != null) {
            FileLogger.i("Service", "Loading device settings from DB for ${device.id}")
            val baseState = EffectDispatcher.loadFullStateFromPrefs(repository)
            val isSpk = !device.isHeadphone
            val json = JSONObject(saved.settingsJson)
            state = deserializeEffectPrefs(json, baseState, isSpk).copy(fxType = fxType)
            repository.updateDeviceLastConnected(device.id)
        } else {
            FileLogger.i("Service", "No DB entry for ${device.id}, using DataStore defaults")
            state = EffectDispatcher.loadFullStateFromPrefs(repository).copy(fxType = fxType)
            val isSpk = !device.isHeadphone
            val json = serializeEffectPrefs(state, isSpk)
            repository.saveDeviceSettings(
                DeviceSettings(
                    deviceId = device.id,
                    deviceName = device.name,
                    isHeadphone = device.isHeadphone,
                    settingsJson = json.toString()
                )
            )
        }

        val isMasterOn =
            if (fxType == ViperParams.FX_TYPE_SPEAKER) state.spkMasterEnabled else state.masterEnabled
        var shmWritten = false
        globalEffect?.let {
            it.enabled = isMasterOn
            if (useAidlTypeUuid) {
                dispatchFullStateViaFile(state)
                shmWritten = true
            } else {
                EffectDispatcher.dispatchFullState(it, state, isMasterOn)
            }
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = isMasterOn
            if (useAidlTypeUuid) {
                if (!shmWritten) {
                    dispatchFullStateViaFile(state)
                    shmWritten = true
                }
            } else {
                EffectDispatcher.dispatchFullState(effect, state, isMasterOn)
            }
        }
    }

    private suspend fun applyFullStateToEffect(effect: ViperEffect, skipShmWrite: Boolean = false) {
        val state = EffectDispatcher.loadFullStateFromPrefs(repository)
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        val headphoneConnected = AudioOutputDetector.isHeadphoneConnected(audioManager)
        val fxType =
            if (headphoneConnected) ViperParams.FX_TYPE_HEADPHONE else ViperParams.FX_TYPE_SPEAKER
        val activeState = state.copy(fxType = fxType)
        val isMasterOn =
            if (fxType == ViperParams.FX_TYPE_SPEAKER) activeState.spkMasterEnabled else activeState.masterEnabled
        effect.enabled = isMasterOn
        if (useAidlTypeUuid) {
            if (!skipShmWrite) {
                FileLogger.d(
                    "Service",
                    "AIDL shm apply full state (master=$isMasterOn fxType=$fxType)"
                )
                dispatchFullStateViaFile(activeState)
            }
            return
        }
        EffectDispatcher.dispatchFullState(effect, activeState, isMasterOn)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_START -> FileLogger.i("Service", "Service started")
            ACTION_STOP -> {
                releaseAllSessions()
                globalEffect?.let { it.enabled = false; it.release() }
                globalEffect = null
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        stopSessionMonitor()
        audioOutputDetector?.stop()
        audioOutputDetector = null
        globalEffect?.let { it.enabled = false; it.release() }
        globalEffect = null
        releaseAllSessions()
        FileLogger.i("Service", "Service destroyed")
        super.onDestroy()
    }

    private fun startSessionMonitor() {
        stopSessionMonitor()
        val monitor = AudioSessionMonitor(
            context = this,
            onSessionOpen = { sessionId, pkg -> openSession(sessionId, pkg) },
            onSessionClose = { sessionId -> closeSession(sessionId) }
        )
        monitor.start()
        sessionMonitor = monitor
    }

    private fun stopSessionMonitor() {
        sessionMonitor?.stop()
        sessionMonitor = null
    }

    private fun openSession(sessionId: Int, packageName: String) {
        if (globalMode) {
            FileLogger.d(
                "Service",
                "Global mode: skipping per-app session $sessionId ($packageName)"
            )
            return
        }
        if (sessions.get(sessionId) != null) {
            FileLogger.w("Service", "Session $sessionId already open")
            return
        }

        val typeUuid =
            if (useAidlTypeUuid) ViperEffect.EFFECT_TYPE_UUID_AIDL else ViperEffect.EFFECT_TYPE_UUID
        val effect = ViperEffect(sessionId, typeUuid)
        if (!effect.create()) {
            FileLogger.e("Service", "Failed to create effect for session $sessionId ($packageName)")
            return
        }

        sessions.put(sessionId, effect)
        FileLogger.i("Service", "Opened session $sessionId for $packageName")

        lifecycleScope.launch {
            applyFullStateToEffect(effect)
            FileLogger.i("Service", "Applied full state to session $sessionId")
        }
    }

    private fun closeSession(sessionId: Int) {
        val effect = sessions.get(sessionId) ?: return
        effect.enabled = false
        effect.release()
        sessions.remove(sessionId)
        FileLogger.i("Service", "Closed session $sessionId")
    }

    private fun releaseAllSessions() {
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = false
            effect.release()
        }
        sessions.clear()
    }

    fun dispatchParam(param: Int, value: Int) {
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm param=0x${param.toString(16)} value=$value")
            ConfigChannel.writeParams(listOf(ParamEntry(param, intArrayOf(value))))
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} value=$value")
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParam(param: Int, val1: Int, val2: Int, val3: Int) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm param=0x${param.toString(16)} v1=$val1 v2=$val2 v3=$val3"
            )
            ConfigChannel.writeParams(listOf(ParamEntry(param, intArrayOf(val1, val2, val3))))
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} v1=$val1 v2=$val2 v3=$val3")
        globalEffect?.setParameter(param, val1, val2, val3)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, val1, val2, val3)
        }
    }

    fun dispatchParam(param: Int, value: ByteArray, extraParams: List<ParamEntry>? = null) {
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm param=0x${param.toString(16)} bytes=${value.size}")
            ConfigChannel.writeParamsByteArray(param, value, extraParams)
            return
        }
        FileLogger.d("Service", "DSP param=0x${param.toString(16)} bytes=${value.size}")
        globalEffect?.setParameter(param, value)
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).setParameter(param, value)
        }
    }

    fun dispatchParamsBatch(entries: List<ParamEntry>) {
        if (entries.isEmpty()) return
        if (useAidlTypeUuid) {
            FileLogger.d("Service", "AIDL shm batch: ${entries.size} params")
            ConfigChannel.writeParams(entries)
            return
        }
        FileLogger.d("Service", "DSP batch: ${entries.size} params")
        for (entry in entries) {
            when (entry.values.size) {
                1 -> {
                    globalEffect?.setParameter(entry.paramId, entry.values[0])
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i).setParameter(entry.paramId, entry.values[0])
                    }
                }

                2 -> {
                    globalEffect?.setParameter(entry.paramId, entry.values[0], entry.values[1])
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i)
                            .setParameter(entry.paramId, entry.values[0], entry.values[1])
                    }
                }

                3 -> {
                    globalEffect?.setParameter(
                        entry.paramId,
                        entry.values[0],
                        entry.values[1],
                        entry.values[2]
                    )
                    for (i in 0 until sessions.size) {
                        sessions.valueAt(i).setParameter(
                            entry.paramId,
                            entry.values[0],
                            entry.values[1],
                            entry.values[2]
                        )
                    }
                }
            }
        }
    }

    fun dispatchEqBands(
        param: Int,
        bandsString: String,
        bandCountParam: Int = 0,
        bandCount: Int = 0
    ) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm EQ param=0x${param.toString(16)} bands=$bandsString bandCount=$bandCount"
            )
            val bands = bandsString.split(";").filter { it.isNotBlank() }
            val entries = mutableListOf<ParamEntry>()
            if (bandCountParam != 0) {
                entries.add(ParamEntry(bandCountParam, intArrayOf(bandCount)))
            }
            bands.forEachIndexed { index, bandStr ->
                val level = (bandStr.toFloatOrNull() ?: 0f) * 100
                entries.add(ParamEntry(param, intArrayOf(index, level.toInt())))
            }
            ConfigChannel.writeParams(entries)
            return
        }
        FileLogger.d("Service", "DSP EQ param=0x${param.toString(16)} bands=$bandsString")
        if (bandCountParam != 0) {
            globalEffect?.setParameter(bandCountParam, bandCount)
            for (i in 0 until sessions.size) {
                sessions.valueAt(i).setParameter(bandCountParam, bandCount)
            }
        }
        globalEffect?.let { EffectDispatcher.dispatchEqBands(it, param, bandsString) }
        for (i in 0 until sessions.size) {
            EffectDispatcher.dispatchEqBands(sessions.valueAt(i), param, bandsString)
        }
    }

    fun dispatchFullState(
        state: MainUiState,
        masterEnabled: Boolean,
        byteArrayParams: List<ByteArrayParam>? = null
    ) {
        if (useAidlTypeUuid) {
            FileLogger.d(
                "Service",
                "AIDL shm full state dispatch (master=$masterEnabled fxType=${state.fxType})"
            )
            dispatchFullStateViaFile(state, byteArrayParams)
            return
        }
        globalEffect?.let { effect ->
            effect.enabled = masterEnabled
            EffectDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
        for (i in 0 until sessions.size) {
            val effect = sessions.valueAt(i)
            effect.enabled = masterEnabled
            EffectDispatcher.dispatchFullState(effect, state, masterEnabled)
        }
    }

    private fun dispatchFullStateViaFile(
        state: MainUiState,
        byteArrayParams: List<ByteArrayParam>? = null
    ) {
        val params = mutableListOf<ParamEntry>()
        if (state.fxType == ViperParams.FX_TYPE_HEADPHONE) {
            collectHeadphoneParams(params, state)
        } else {
            collectSpeakerParams(params, state)
        }
        val finalByteArrays = byteArrayParams ?: prepareByteArraysForState(state)
        ConfigChannel.setActiveFxType(state.fxType)
        ConfigChannel.writeFullState(params, finalByteArrays, state.fxType)
    }

    private fun prepareByteArraysForState(state: MainUiState): List<ByteArrayParam>? {
        val result = mutableListOf<ByteArrayParam>()
        val ddcEnabled =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.ddc.spk.enabled else state.ddc.hp.enabled
        val ddcDevice =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.ddc.spk.device else state.ddc.hp.device
        if (ddcEnabled && ddcDevice.isNotEmpty()) {
            prepareDdcByteArray(ddcDevice, state.fxType)?.let { result.add(it) }
        }
        val convolverEnabled =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.convolver.spk.enabled else state.convolver.hp.enabled
        val kernel =
            if (state.fxType == ViperParams.FX_TYPE_SPEAKER) state.convolver.spk.kernel else state.convolver.hp.kernel
        if (convolverEnabled && kernel.isNotEmpty()) {
            prepareConvolverByteArray(kernel, state.fxType)?.let { result.add(it) }
        }
        return result.ifEmpty { null }
    }

    private fun prepareDdcByteArray(name: String, fxType: Int): ByteArrayParam? {
        return try {
            val ddcDir = java.io.File(getExternalFilesDir(null), "DDC")
            val file = java.io.File(ddcDir, "$name.vdc")
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
            val param = if (fxType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_DDC_COEFFICIENTS else ViperParams.PARAM_HP_DDC_COEFFICIENTS
            val buffer =
                java.nio.ByteBuffer.allocate(wireSize).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(arrSize)
            for (f in coeffs44100) buffer.putFloat(f)
            for (f in coeffs48000) buffer.putFloat(f)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to prepare DDC: $name", e)
            null
        }
    }

    private fun prepareConvolverByteArray(fileName: String, fxType: Int): ByteArrayParam? {
        if (!useAidlTypeUuid) return null
        return try {
            val kernelDir = java.io.File(getExternalFilesDir(null), "Kernel")
            val file = java.io.File(kernelDir, fileName)
            if (!file.exists()) return null
            val safeName = fileName.replace("'", "")
            val subDir = if (fxType == ViperParams.FX_TYPE_SPEAKER) "spk" else "hp"
            val kernelPath = "/data/local/tmp/v4a/$subDir/$safeName"
            RootShell.copyFile(file, kernelPath)
            val param = if (fxType == ViperParams.FX_TYPE_SPEAKER)
                ViperParams.PARAM_SPK_CONVOLVER_SET_KERNEL else ViperParams.PARAM_HP_CONVOLVER_SET_KERNEL
            val pathBytes = kernelPath.toByteArray(Charsets.UTF_8)
            val buffer = java.nio.ByteBuffer.allocate(256).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            buffer.putInt(pathBytes.size)
            buffer.put(pathBytes)
            ByteArrayParam(param, buffer.array())
        } catch (e: Exception) {
            FileLogger.e("Service", "Failed to prepare convolver: $fileName", e)
            null
        }
    }

    private fun collectHeadphoneParams(params: MutableList<ParamEntry>, state: MainUiState) {
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_OUTPUT_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_VOLUME_VALUES.getOrElse(state.out.hp.volume) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CHANNEL_PAN,
                intArrayOf(state.out.hp.channelPan)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_LIMITER,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.out.hp.limiter) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_ENABLE,
                intArrayOf(if (state.agc.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_RATIO,
                intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.hp.strength) { 50 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_MAX_SCALER,
                intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.agc.hp.maxGain) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_AGC_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.agc.hp.outputThreshold) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ENABLE,
                intArrayOf(if (state.fet.hp.enabled) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_THRESHOLD,
                intArrayOf(state.fet.hp.threshold)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_RATIO,
                intArrayOf(state.fet.hp.ratio)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_KNEE,
                intArrayOf(if (state.fet.hp.autoKnee) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE,
                intArrayOf(state.fet.hp.knee)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_KNEE_MULTI,
                intArrayOf(state.fet.hp.kneeMulti)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_GAIN,
                intArrayOf(if (state.fet.hp.autoGain) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_GAIN,
                intArrayOf(state.fet.hp.gain)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_ATTACK,
                intArrayOf(if (state.fet.hp.autoAttack) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ATTACK,
                intArrayOf(state.fet.hp.attack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_ATTACK,
                intArrayOf(state.fet.hp.maxAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_AUTO_RELEASE,
                intArrayOf(if (state.fet.hp.autoRelease) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_RELEASE,
                intArrayOf(state.fet.hp.release)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_MAX_RELEASE,
                intArrayOf(state.fet.hp.maxRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_CREST,
                intArrayOf(state.fet.hp.crest)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_ADAPT,
                intArrayOf(state.fet.hp.adapt)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FET_COMPRESSOR_NO_CLIP,
                intArrayOf(if (state.fet.hp.noClip) 100 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DDC_ENABLE,
                intArrayOf(if (state.ddc.hp.enabled && state.ddc.hp.device.isNotEmpty()) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_ENABLE,
                intArrayOf(if (state.vse.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK,
                intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.vse.hp.strength) { 7600 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                intArrayOf((state.vse.hp.exciter * 5.6).toInt())
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_EQ_BAND_COUNT,
                intArrayOf(state.eq.hp.bandCount)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_EQ_ENABLE,
                intArrayOf(if (state.eq.hp.enabled) 1 else 0)
            )
        )
        collectEqBandParams(params, ViperParams.PARAM_HP_EQ_BAND_LEVEL, state.eq.hp.bands)

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CONVOLVER_ENABLE,
                intArrayOf(if (state.convolver.hp.enabled && state.convolver.hp.kernel.isNotEmpty()) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CONVOLVER_CROSS_CHANNEL,
                intArrayOf(state.convolver.hp.crossChannel)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_ENABLE,
                intArrayOf(if (state.fieldSurround.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_WIDENING,
                intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.hp.widening) { 0 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_MID_IMAGE,
                intArrayOf(state.fieldSurround.hp.midImage * 10 + 100)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_FIELD_SURROUND_DEPTH,
                intArrayOf(state.fieldSurround.hp.depth * 75 + 200)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DIFF_SURROUND_ENABLE,
                intArrayOf(if (state.diffSurround.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DIFF_SURROUND_DELAY,
                intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.hp.delay) { 500 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_DIFF_SURROUND_REVERSE,
                intArrayOf(if (state.diffSurround.hp.reverse) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_HEADPHONE_SURROUND_ENABLE,
                intArrayOf(if (state.vhe.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_HEADPHONE_SURROUND_STRENGTH,
                intArrayOf(state.vhe.hp.quality)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ENABLE,
                intArrayOf(if (state.reverb.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_SIZE,
                intArrayOf(state.reverb.hp.roomSize * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_WIDTH,
                intArrayOf(state.reverb.hp.width * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_DAMPENING,
                intArrayOf(state.reverb.hp.dampening)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_WET_SIGNAL,
                intArrayOf(state.reverb.hp.wet)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_REVERB_ROOM_DRY_SIGNAL,
                intArrayOf(state.reverb.hp.dry)
            )
        )

        collectDynamicSystemParams(
            params,
            state.dynamicSystem.hp.enabled,
            state.dynamicSystem.hp.strength,
            state.dynamicSystem.hp.xLow, state.dynamicSystem.hp.xHigh,
            state.dynamicSystem.hp.yLow, state.dynamicSystem.hp.yHigh,
            state.dynamicSystem.hp.sideGainLow, state.dynamicSystem.hp.sideGainHigh,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_HP_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_TUBE_SIMULATOR_ENABLE,
                intArrayOf(if (state.tube.hp.enabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_ENABLE,
                intArrayOf(if (state.bass.hp.enabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_BASS_MODE, intArrayOf(state.bass.hp.mode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_FREQUENCY,
                intArrayOf(state.bass.hp.frequency + 15)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_GAIN,
                intArrayOf(state.bass.hp.gain * 50 + 50)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_ANTI_POP,
                intArrayOf(if (state.bass.hp.antiPop) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_MONO_ENABLE,
                intArrayOf(if (state.bassMono.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_MONO_MODE,
                intArrayOf(state.bassMono.hp.mode)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_MONO_FREQUENCY,
                intArrayOf(state.bassMono.hp.frequency + 15)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_MONO_GAIN,
                intArrayOf(state.bassMono.hp.gain * 50 + 50)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_BASS_MONO_ANTI_POP,
                intArrayOf(if (state.bassMono.hp.antiPop) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CLARITY_ENABLE,
                intArrayOf(if (state.clarity.hp.enabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_CLARITY_MODE, intArrayOf(state.clarity.hp.mode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CLARITY_GAIN,
                intArrayOf(state.clarity.hp.gain * 50)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CURE_ENABLE,
                intArrayOf(if (state.cure.hp.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_CURE_STRENGTH,
                intArrayOf(state.cure.hp.strength)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_HP_ANALOGX_ENABLE,
                intArrayOf(if (state.analog.hp.enabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_HP_ANALOGX_MODE, intArrayOf(state.analog.hp.mode)))
    }

    private fun collectSpeakerParams(params: MutableList<ParamEntry>, state: MainUiState) {
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_OUTPUT_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_VOLUME_VALUES.getOrElse(state.out.spk.volume) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CHANNEL_PAN,
                intArrayOf(state.out.spk.channelPan)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_LIMITER,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.out.spk.limiter) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_ENABLE,
                intArrayOf(if (state.agc.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_RATIO,
                intArrayOf(EffectDispatcher.PLAYBACK_GAIN_RATIO_VALUES.getOrElse(state.agc.spk.strength) { 50 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_MAX_SCALER,
                intArrayOf(EffectDispatcher.MULTI_FACTOR_VALUES.getOrElse(state.agc.spk.maxGain) { 100 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_AGC_VOLUME,
                intArrayOf(EffectDispatcher.OUTPUT_DB_VALUES.getOrElse(state.agc.spk.outputThreshold) { 100 })
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ENABLE,
                intArrayOf(if (state.fet.spk.enabled) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_THRESHOLD,
                intArrayOf(state.fet.spk.threshold)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_RATIO,
                intArrayOf(state.fet.spk.ratio)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_KNEE,
                intArrayOf(if (state.fet.spk.autoKnee) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE,
                intArrayOf(state.fet.spk.knee)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_KNEE_MULTI,
                intArrayOf(state.fet.spk.kneeMulti)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_GAIN,
                intArrayOf(if (state.fet.spk.autoGain) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_GAIN,
                intArrayOf(state.fet.spk.gain)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_ATTACK,
                intArrayOf(if (state.fet.spk.autoAttack) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ATTACK,
                intArrayOf(state.fet.spk.attack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_ATTACK,
                intArrayOf(state.fet.spk.maxAttack)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_AUTO_RELEASE,
                intArrayOf(if (state.fet.spk.autoRelease) 100 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_RELEASE,
                intArrayOf(state.fet.spk.release)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_MAX_RELEASE,
                intArrayOf(state.fet.spk.maxRelease)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_CREST,
                intArrayOf(state.fet.spk.crest)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_ADAPT,
                intArrayOf(state.fet.spk.adapt)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FET_COMPRESSOR_NO_CLIP,
                intArrayOf(if (state.fet.spk.noClip) 100 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CONVOLVER_ENABLE,
                intArrayOf(if (state.convolver.spk.enabled && state.convolver.spk.kernel.isNotEmpty()) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CONVOLVER_CROSS_CHANNEL,
                intArrayOf(state.convolver.spk.crossChannel)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_EQ_BAND_COUNT,
                intArrayOf(state.eq.spk.bandCount)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_EQ_ENABLE,
                intArrayOf(if (state.eq.spk.enabled) 1 else 0)
            )
        )
        collectEqBandParams(params, ViperParams.PARAM_SPK_EQ_BAND_LEVEL, state.eq.spk.bands)

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ENABLE,
                intArrayOf(if (state.reverb.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_SIZE,
                intArrayOf(state.reverb.spk.roomSize * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_WIDTH,
                intArrayOf(state.reverb.spk.width * 10)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_DAMPENING,
                intArrayOf(state.reverb.spk.dampening)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_WET_SIGNAL,
                intArrayOf(state.reverb.spk.wet)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_REVERB_ROOM_DRY_SIGNAL,
                intArrayOf(state.reverb.spk.dry)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DDC_ENABLE,
                intArrayOf(if (state.ddc.spk.enabled && state.ddc.spk.device.isNotEmpty()) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_ENABLE,
                intArrayOf(if (state.vse.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK,
                intArrayOf(EffectDispatcher.VSE_BARK_VALUES.getOrElse(state.vse.spk.strength) { 7600 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPECTRUM_EXTENSION_BARK_RECONSTRUCT,
                intArrayOf((state.vse.spk.exciter * 5.6).toInt())
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_ENABLE,
                intArrayOf(if (state.fieldSurround.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_WIDENING,
                intArrayOf(EffectDispatcher.FIELD_SURROUND_WIDENING_VALUES.getOrElse(state.fieldSurround.spk.widening) { 0 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_MID_IMAGE,
                intArrayOf(state.fieldSurround.spk.midImage * 10 + 100)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_FIELD_SURROUND_DEPTH,
                intArrayOf(state.fieldSurround.spk.depth * 75 + 200)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_SPEAKER_CORRECTION_ENABLE,
                intArrayOf(if (state.speakerCorrection.spk.enabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DIFF_SURROUND_ENABLE,
                intArrayOf(if (state.diffSurround.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DIFF_SURROUND_DELAY,
                intArrayOf(EffectDispatcher.DIFF_SURROUND_DELAY_VALUES.getOrElse(state.diffSurround.spk.delay) { 500 })
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_DIFF_SURROUND_REVERSE,
                intArrayOf(if (state.diffSurround.spk.reverse) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_ENABLE,
                intArrayOf(if (state.vhe.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_HEADPHONE_SURROUND_STRENGTH,
                intArrayOf(state.vhe.spk.quality)
            )
        )

        collectDynamicSystemParams(
            params,
            state.dynamicSystem.spk.enabled,
            state.dynamicSystem.spk.strength,
            state.dynamicSystem.spk.xLow, state.dynamicSystem.spk.xHigh,
            state.dynamicSystem.spk.yLow, state.dynamicSystem.spk.yHigh,
            state.dynamicSystem.spk.sideGainLow, state.dynamicSystem.spk.sideGainHigh,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_ENABLE,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_STRENGTH,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_X_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_Y_COEFFICIENTS,
            ViperParams.PARAM_SPK_DYNAMIC_SYSTEM_SIDE_GAIN
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_TUBE_SIMULATOR_ENABLE,
                intArrayOf(if (state.tube.spk.enabled) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_ENABLE,
                intArrayOf(if (state.bass.spk.enabled) 1 else 0)
            )
        )
        params.add(ParamEntry(ViperParams.PARAM_SPK_BASS_MODE, intArrayOf(state.bass.spk.mode)))
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_FREQUENCY,
                intArrayOf(state.bass.spk.frequency + 15)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_GAIN,
                intArrayOf(state.bass.spk.gain * 50 + 50)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_ANTI_POP,
                intArrayOf(if (state.bass.spk.antiPop) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_MONO_ENABLE,
                intArrayOf(if (state.bassMono.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_MONO_MODE,
                intArrayOf(state.bassMono.spk.mode)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_MONO_FREQUENCY,
                intArrayOf(state.bassMono.spk.frequency + 15)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_MONO_GAIN,
                intArrayOf(state.bassMono.spk.gain * 50 + 50)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_BASS_MONO_ANTI_POP,
                intArrayOf(if (state.bassMono.spk.antiPop) 1 else 0)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CLARITY_ENABLE,
                intArrayOf(if (state.clarity.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CLARITY_MODE,
                intArrayOf(state.clarity.spk.mode)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CLARITY_GAIN,
                intArrayOf(state.clarity.spk.gain * 50)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CURE_ENABLE,
                intArrayOf(if (state.cure.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_CURE_STRENGTH,
                intArrayOf(state.cure.spk.strength)
            )
        )

        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_ANALOGX_ENABLE,
                intArrayOf(if (state.analog.spk.enabled) 1 else 0)
            )
        )
        params.add(
            ParamEntry(
                ViperParams.PARAM_SPK_ANALOGX_MODE,
                intArrayOf(state.analog.spk.mode)
            )
        )
    }

    private fun collectEqBandParams(
        params: MutableList<ParamEntry>,
        param: Int,
        bandsString: String
    ) {
        val bands = bandsString.split(";").filter { it.isNotBlank() }
        for ((index, bandStr) in bands.withIndex()) {
            val level = (bandStr.toFloatOrNull() ?: 0f) * 100
            params.add(ParamEntry(param, intArrayOf(index, level.toInt())))
        }
    }

    private fun collectDynamicSystemParams(
        params: MutableList<ParamEntry>,
        enabled: Boolean,
        strength: Int,
        xLow: Int, xHigh: Int,
        yLow: Int, yHigh: Int,
        sideGainLow: Int, sideGainHigh: Int,
        paramEnable: Int,
        paramStrength: Int,
        paramXCoeffs: Int,
        paramYCoeffs: Int,
        paramSideGain: Int
    ) {
        params.add(ParamEntry(paramEnable, intArrayOf(if (enabled) 1 else 0)))
        params.add(ParamEntry(paramStrength, intArrayOf(strength * 20 + 100)))
        params.add(ParamEntry(paramXCoeffs, intArrayOf(xLow, xHigh)))
        params.add(ParamEntry(paramYCoeffs, intArrayOf(yLow, yHigh)))
        params.add(ParamEntry(paramSideGain, intArrayOf(sideGainLow, sideGainHigh)))
    }

    fun setEffectEnabled(enabled: Boolean) {
        globalEffect?.enabled = enabled
        for (i in 0 until sessions.size) {
            sessions.valueAt(i).enabled = enabled
        }
    }

    fun getGlobalEffect(): ViperEffect? = globalEffect

    fun recreateGlobalEffect(aidlType: Boolean) {
        globalEffect?.let { it.enabled = false; it.release() }
        globalEffect = null
        useAidlTypeUuid = aidlType
        if (globalMode) {
            initGlobalEffect()
        }
    }

    fun setGlobalMode(enabled: Boolean) {
        globalMode = enabled
        if (enabled) {
            stopSessionMonitor()
            releaseAllSessions()
            if (globalEffect == null) {
                initGlobalEffect()
            }
        } else {
            globalEffect?.let { it.enabled = false; it.release() }
            globalEffect = null
            startSessionMonitor()
        }
    }

    private fun createNotificationChannel() {
        val channelName = getString(R.string.notification_channel_name)
        val channelDescription = getString(R.string.notification_channel_description)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            channelName,
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = channelDescription
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val pendingIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }
}
