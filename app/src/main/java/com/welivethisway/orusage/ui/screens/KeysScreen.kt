package com.welivethisway.orusage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.welivethisway.orusage.data.ApiKeyInfo
import com.welivethisway.orusage.data.formatMoney
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.LimitBar
import com.welivethisway.orusage.ui.components.SecureScreen
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val ISO_SECONDS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC)

@Composable
fun KeysScreen(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<ApiKeyInfo?>(null) }

    SecureScreen(enabled = state.revealedKey != null)

    Box(modifier.fillMaxSize()) {
        if (state.keys.isEmpty()) {
            Column(Modifier.padding(24.dp)) {
                Text("No API keys yet", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Mint one with the + button. The plaintext is shown once and can never be " +
                        "retrieved again - save it before closing the dialog.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.keys, key = { it.hash }) { key ->
                    KeyRow(
                        key = key,
                        onToggleDisabled = { vm.setDisabled(key, !key.disabled) },
                        onDelete = { pendingDelete = key },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreate = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Create API key")
        }
    }

    if (showCreate) {
        CreateKeyDialog(
            onDismiss = { showCreate = false },
            onCreate = { name, limit, reset, expiresAt, onDone ->
                vm.createKey(name, limit, reset, expiresAt) { ok ->
                    if (ok) showCreate = false
                    onDone(ok)
                }
            },
        )
    }

    val revealed = state.revealedKey
    if (revealed != null) {
        RevealKeyDialog(
            name = revealed.info.displayName,
            plaintext = revealed.plaintext,
            onDismiss = { vm.dismissRevealedKey() },
        )
    }

    val toDelete = pendingDelete
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete key?") },
            text = {
                Text(
                    "Delete " + toDelete.displayName + " (" + toDelete.shortHash + ")? Any app using " +
                        "it stops working immediately. This can't be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteKey(toDelete)
                    pendingDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun KeyRow(
    key: ApiKeyInfo,
    onToggleDisabled: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        key.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        key.shortHash + "…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        formatMoney(key.usage),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "total spend",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (key.disabled) AssistChip(onClick = onToggleDisabled, label = { Text("Disabled") })
                if (key.expiresAt != null) {
                    val expired = key.expiresAt < Instant.now().toString()
                    AssistChip(
                        onClick = {},
                        label = { Text((if (expired) "Expired " else "Expires ") + key.expiresAt.take(10)) },
                    )
                }
                if (key.limitReset != null) {
                    AssistChip(onClick = {}, label = { Text("Resets " + key.limitReset) })
                }
            }

            if (key.hasLimit) {
                Spacer(Modifier.height(10.dp))
                val fraction = key.limitFraction ?: 0.0
                LimitBar(fraction)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatMoney(key.usage) + " of " + formatMoney(key.limit ?: 0.0) + " limit" +
                        (key.limitRemaining?.let { " · " + formatMoney(it) + " left" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "Today " + formatMoney(key.usageDaily) +
                        " · 7d " + formatMoney(key.usageWeekly) +
                        " · 30d " + formatMoney(key.usageMonthly),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onToggleDisabled) {
                    Text(if (key.disabled) "Enable" else "Disable")
                }
                TextButton(onClick = onDelete) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun CreateKeyDialog(
    onDismiss: () -> Unit,
    onCreate: (String, Double?, String?, String?, (Boolean) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var limitText by remember { mutableStateOf("") }
    var submitError by remember { mutableStateOf<String?>(null) }
    var reset by remember { mutableStateOf<String?>(null) }
    var expiryDays by remember { mutableStateOf(0) }
    var busy by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("New API key") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = limitText,
                    onValueChange = { limitText = it },
                    label = { Text("Spend limit (USD, optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Limit resets",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(null to "Never", "daily" to "Daily", "weekly" to "Weekly", "monthly" to "Monthly")
                        .forEach { (value, label) ->
                            FilterChip(
                                selected = reset == value,
                                onClick = { reset = value },
                                label = { Text(label) },
                            )
                        }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Expires",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(0 to "Never", 30 to "30d", 90 to "90d", 365 to "1y").forEach { (days, label) ->
                        FilterChip(
                            selected = expiryDays == days,
                            onClick = { expiryDays = days },
                            label = { Text(label) },
                        )
                    }
                }
                if (submitError != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        submitError!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !busy && name.isNotBlank(),
                onClick = {
                    val limit = limitText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                    if (limitText.isNotBlank() && (limit == null || limit < 0)) {
                        submitError = "Limit must be a positive number, or blank for none."
                        return@Button
                    }
                    // A reset interval without a limit is meaningless; send null for both.
                    val effectiveLimit = limit?.takeIf { it > 0 }
                    val effectiveReset = if (effectiveLimit == null) null else reset
                    val expiresAt = if (expiryDays <= 0) null
                    else ISO_SECONDS.format(Instant.now().plusSeconds(expiryDays.toLong() * 86_400))
                    busy = true
                    submitError = null
                    onCreate(name, effectiveLimit, effectiveReset, expiresAt) { ok ->
                        busy = false
                        if (!ok) submitError = "Could not create the key - see the message at the bottom."
                    }
                },
            ) { Text(if (busy) "Creating…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
private fun RevealKeyDialog(name: String, plaintext: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy your key now") },
        text = {
            Column {
                Text(
                    "This is the only time OpenRouter will show the key for " + name +
                        ". Once you close this, it cannot be recovered - only replaced.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                SelectionContainer {
                    Text(
                        plaintext,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        clipboard.setText(AnnotatedString(plaintext))
                        copied = true
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(Modifier.height(0.dp))
                    Text(if (copied) "  Copied" else "  Copy key")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("I've saved it") }
        },
    )
}
