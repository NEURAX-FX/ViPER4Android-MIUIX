package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * Viper-style confirmation/info dialog wrapping MiuiX OverlayDialog.
 * Provides consistent dialog appearance across the app.
 */
@Composable
fun ViperDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    dismissText: String? = null,
    onDismiss: (() -> Unit)? = null,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        summary = summary,
        modifier = modifier,
    ) {
        content()
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (dismissText != null && onDismiss != null) {
                TextButton(
                    text = dismissText,
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                )
            }
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
