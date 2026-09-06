package com.smsforwarder.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.Composable

@Composable
fun CallMeBotWarningDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = {
            Text(text = "Third-Party Relay Privacy Notice")
        },
        text = {
            Text(
                text = "CallMeBot is a free, third-party WhatsApp gateway. Messages sent through CallMeBot transit through external servers.\n\n" +
                        "⚠️ PRIVACY RISK: Never send bank OTPs, verification codes, or personal credentials through unencrypted third-party relays.\n\n" +
                        "The built-in 'Privacy Guard' will automatically exclude OTPs and banking messages from being sent via CallMeBot while still forwarding safely to your private Telegram Bot."
            )
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = "I Understand & Accept")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Cancel")
            }
        }
    )
}
