package com.llsl.viper4android.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ViperTypography(
    val display: TextStyle,
    val title: TextStyle,
    val section: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val value: TextStyle,
    val micro: TextStyle,
    val mono: TextStyle,
)

val ViperType = ViperTypography(
    display = TextStyle(
        fontSize = 40.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
    ),
    title = TextStyle(
        fontSize = 20.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Medium,
    ),
    section = TextStyle(
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    body = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Normal,
    ),
    caption = TextStyle(
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    value = TextStyle(
        fontSize = 15.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    micro = TextStyle(
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
)
