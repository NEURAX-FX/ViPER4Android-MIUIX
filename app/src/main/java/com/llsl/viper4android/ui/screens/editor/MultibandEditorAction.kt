package com.llsl.viper4android.ui.screens.editor

enum class MultibandIntControl {
    THRESHOLD,
    RATIO,
    GAIN,
    KNEE,
    KNEE_MULTI,
    ATTACK,
    MAX_ATTACK,
    RELEASE,
    MAX_RELEASE,
    CREST,
    ADAPT,
}

enum class MultibandBooleanControl {
    BAND_ENABLE,
    KNEE_AUTO,
    GAIN_AUTO,
    ATTACK_AUTO,
    RELEASE_AUTO,
    NO_CLIP,
}

sealed interface MultibandEditorAction {
    data object BeginGesture : MultibandEditorAction
    data object SettleGesture : MultibandEditorAction
    data object Flush : MultibandEditorAction

    data class SetInt(
        val control: MultibandIntControl,
        val band: Int,
        val value: Int,
        val last: Boolean,
    ) : MultibandEditorAction

    data class SetBoolean(
        val control: MultibandBooleanControl,
        val band: Int,
        val value: Boolean,
        val last: Boolean,
    ) : MultibandEditorAction

    data class SetCrossoverHandle(
        val crossover: Int,
        val frequency: Int,
        val gain: Int,
        val last: Boolean,
    ) : MultibandEditorAction
}
