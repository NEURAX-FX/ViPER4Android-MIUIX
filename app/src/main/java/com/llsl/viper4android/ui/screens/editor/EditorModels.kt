package com.llsl.viper4android.ui.screens.editor

enum class EditorKind {
    FIR_EQUALIZER,
    DYNAMIC_EQUALIZER,
    MULTIBAND_COMPRESSOR;

    val route: String
        get() = name.lowercase()
}

fun editorKindFromRoute(route: String?): EditorKind? =
    route?.let { value -> EditorKind.entries.firstOrNull { it.route == value } }
