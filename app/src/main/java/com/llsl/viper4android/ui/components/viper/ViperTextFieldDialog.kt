package com.llsl.viper4android.ui.components.viper

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayDialog

/**
 * Viper-style text input dialog for save/rename operations.
 */
@Composable
fun ViperTextFieldDialog(
    show: Boolean,
    onDismissRequest: () -> Unit,
    title: String,
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    confirmText: String,
    onConfirm: () -> Unit,
    dismissText: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    label: String = "",
    confirmEnabled: Boolean = true,
) {
    OverlayDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        title = title,
        summary = summary,
        modifier = modifier,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            useLabelAsPlaceholder = label.isNotEmpty(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TextButton(
                text = dismissText,
                onClick = onDismiss,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                text = confirmText,
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
