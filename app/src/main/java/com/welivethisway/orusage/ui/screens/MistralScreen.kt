package com.welivethisway.orusage.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.welivethisway.orusage.data.MistralLimits
import com.welivethisway.orusage.data.chatModelsFirst
import com.welivethisway.orusage.data.formatCount
import com.welivethisway.orusage.data.formatMistralMoney
import com.welivethisway.orusage.data.formatTokens
import com.welivethisway.orusage.data.modelMix
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.LimitBar
import com.welivethisway.orusage.ui.components.SectionCard
import com.welivethisway.orusage.ui.components.StatTile

private const val MISTRAL_KEYS_PAGE = "https://console.mistral.ai/api-keys"
private const val MISTRAL_USAGE_PAGE = "https://console.mistral.ai/billing/usage"
private const val MISTRAL_ADMIN_DOC = "https://docs.mistral.ai/admin/admin-api/api-reference"

@Composable
fun MistralScreen(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    if (!state.isMistralConnected) {
        MistralConnect(vm, state, modifier)
        return
    }

    val context = LocalContext.current
    var confirmDisconnect by remember { mutableStateOf(false) }
    val limits = state.mistralLimits
    val mix = modelMix(state.mistralModels)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Mistral", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "api.mistral.ai · live, not estimated",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.mistralLoading) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { vm.refreshMistral() }, enabled = !state.mistralLoading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh Mistral")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Spend this month") {
            when {
                limits != null -> SpendAgainstLimit(limits)

                state.mistralHasAdmin == false -> {
                    Text(
                        "Not available on this key.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Mistral only exposes organisation spend to Enterprise admin keys, through " +
                            "GET /v1/admin/spend-limit. There is no per-key or per-month usage " +
                            "endpoint on the standard API - /v1/usage and /v1/billing both 404 - so " +
                            "this screen will not invent a number for you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, MISTRAL_USAGE_PAGE.toUri()))
                        }) { Text("Open usage console") }
                        TextButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, MISTRAL_ADMIN_DOC.toUri()))
                        }) { Text("Admin API docs") }
                    }
                }

                state.mistralLoading -> Text(
                    "Reading organisation limits…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> Text(
                    "Limits not read yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Model inventory") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("Total", formatCount(mix.total))
                StatTile("Chat", formatCount(mix.chat))
                StatTile("Vision", formatCount(mix.vision))
                StatTile("OCR", formatCount(mix.ocr))
            }
            Spacer(Modifier.height(12.dp))
            val shown = chatModelsFirst(state.mistralModels).take(8)
            if (shown.isEmpty()) {
                Text(
                    "No models returned for this key.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                shown.forEach { model ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            model.label,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                        )
                        Text(
                            if (model.maxContextLength > 0) formatTokens(model.maxContextLength) + " ctx"
                            else "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        model.badges.joinToString(" · ").ifBlank { model.id },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                if (state.mistralModels.size > shown.size) {
                    Text(
                        "+" + (state.mistralModels.size - shown.size) + " more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "This key") {
            Text(
                state.mistralKey?.let { it.take(6) + "…" + it.takeLast(4) + "  (masked)" } ?: "Not connected",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                when (state.mistralHasAdmin) {
                    true -> "Enterprise admin key - can read organisation limits."
                    false -> "Standard key - can list models, cannot read organisation limits."
                    null -> "Scope not checked yet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.mistralLastUpdated?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Last refreshed " + it.toString().replace("T", " ").take(19) + " UTC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextButton("Replace key", enabled = !state.mistralLoading) {
                    vm.disconnectMistral()
                }
                TextButton(onClick = { confirmDisconnect = true }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Why this screen is honest") {
            Text(
                "Mistral's public API answers two questions: which models your key can reach, and - " +
                    "for Enterprise admin keys - how much of the monthly budget the organisation " +
                    "has used. Per-key and per-model spend exist only in the console's web UI, " +
                    "which needs a browser session, not an API key. So this tab shows what the key " +
                    "can actually prove, and links to the console for the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmDisconnect) {
        AlertDialog(
            onDismissRequest = { confirmDisconnect = false },
            title = { Text("Disconnect Mistral?") },
            text = { Text("This deletes the encrypted Mistral key from this device. Nothing on your Mistral account changes.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.disconnectMistral()
                    confirmDisconnect = false
                }) { Text("Disconnect") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDisconnect = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SpendAgainstLimit(limits: MistralLimits) {
    if (limits.limitReached) {
        Text(
            "Monthly limit reached",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
    }
    if (limits.lastPaymentFailure) {
        Text(
            "Last payment failed - check the console.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(4.dp))
    }

    Text(
        formatMistralMoney(limits.spent, limits.currency),
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.Bold,
    )
    Text(
        when {
            limits.hasLimit -> "of " + formatMistralMoney(limits.usageLimit!!, limits.currency) + " this month"
            limits.noMonthlyLimit -> "spent this month · no monthly cap configured"
            else -> "spent this month"
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val fraction = limits.fraction
    if (fraction != null) {
        Spacer(Modifier.height(12.dp))
        LimitBar(fraction)
        Spacer(Modifier.height(6.dp))
        Text(
            String.format("%.1f", fraction * 100) + "% of limit · " +
                formatMistralMoney(limits.remaining ?: 0.0, limits.currency) + " left",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OutlinedTextButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick, enabled = enabled) { Text(label) }
}

@Composable
private fun MistralConnect(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Mistral", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Optional second provider, alongside OpenRouter.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Connect a Mistral key") {
            Text(
                "Paste an API key from console.mistral.ai. Its scope decides what appears: a " +
                    "standard key lists the models it can reach, an Enterprise admin key also " +
                    "reports organisation spend against the monthly limit.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                label = { Text("Mistral API key") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (visible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                trailingIcon = {
                    TextButton(onClick = { visible = !visible }) {
                        Text(if (visible) "Hide" else "Show")
                    }
                },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { vm.connectMistral(input) { } },
                    enabled = !state.mistralLoading && input.isNotBlank(),
                ) {
                    if (state.mistralLoading) {
                        CircularProgressIndicator(
                            Modifier.height(16.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(if (state.mistralLoading) "Checking…" else "Connect")
                }
                TextButton(onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, MISTRAL_KEYS_PAGE.toUri()))
                }) { Text("Open console") }
            }
            state.mistralError?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "What Mistral exposes") {
            Text(
                "• GET /v1/models - any valid key: model inventory and capabilities\n" +
                    "• GET /v1/admin/spend-limit - Enterprise admin keys: monthly usage against the " +
                    "limit, in the organisation's currency\n" +
                    "• No per-key spend endpoint exists. /v1/usage and /v1/billing return 404.\n\n" +
                    "This app shows the first two and links to the console for everything else. It " +
                    "does not guess at spend from model prices.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "How it's stored") {
            Text(
                "The Mistral key gets its own Keystore-encrypted slot, separate from the " +
                    "OpenRouter management key - disconnecting one never touches the other. Only " +
                    "ciphertext is written to disk, and the key is never logged.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
