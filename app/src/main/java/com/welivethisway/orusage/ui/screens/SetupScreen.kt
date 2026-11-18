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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.SectionCard

private const val MANAGEMENT_KEYS_DOC =
    "https://openrouter.ai/docs/guides/overview/auth/management-api-keys"
private const val MANAGEMENT_KEYS_PAGE = "https://openrouter.ai/settings/management-keys"

@Composable
fun SetupScreen(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }

    Scaffold { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Spacer(Modifier.height(24.dp))
            Text("OR Usage", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Spend and key management for your OpenRouter account.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))

            SectionCard(title = "Connect a management key") {
                Text(
                    "This app reads usage and mints API keys, so it needs a management key from your " +
                        "OpenRouter account - not an inference key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Management key") },
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
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { vm.connect(input) { } },
                        enabled = !state.loading && input.isNotBlank(),
                    ) {
                        if (state.loading) {
                            CircularProgressIndicator(
                                Modifier.height(16.dp).padding(end = 8.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(if (state.loading) "Checking…" else "Connect")
                    }
                    TextButton(
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, MANAGEMENT_KEYS_PAGE.toUri())
                            )
                        }
                    ) { Text("Open dashboard") }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "Where to get it") {
                Text(
                    "OpenRouter dashboard → Management API Keys → Create New Key, then paste it here. " +
                        "Give it read/write access if you want this app to mint keys, or read-only if " +
                        "you only want the dashboards.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, MANAGEMENT_KEYS_DOC.toUri())
                        )
                    }
                ) { Text("Read the docs") }
            }

            Spacer(Modifier.height(16.dp))

            SectionCard(title = "How it's stored") {
                Text(
                    "The key is encrypted with a hardware-backed Android Keystore key and kept on this " +
                        "device only. It is never logged and never leaves the app except in requests to " +
                        "openrouter.ai. Signing out deletes it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Note: management keys can only be created in the OpenRouter dashboard. Their API " +
                        "can mint API keys, but not management keys - so this app can create API keys " +
                        "for you, not more management keys.",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Default,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
