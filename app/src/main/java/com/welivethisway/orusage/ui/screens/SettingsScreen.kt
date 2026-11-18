package com.welivethisway.orusage.ui.screens

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var confirmSignOut by remember { mutableStateOf(false) }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Connection") {
            val key = state.managementKey
            Text(
                if (key == null) "Not connected"
                else key.take(6) + "…" + key.takeLast(4) + "  (masked)",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                state.lastUpdated?.let { "Last refreshed " + it.toString().replace("T", " ").take(19) + " UTC" }
                    ?: "Never refreshed",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row {
                OutlinedButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                    Text("Refresh now")
                }
                Spacer(Modifier.height(0.dp))
                TextButton(onClick = { confirmSignOut = true }) {
                    Text("Sign out", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "What this key can do") {
            Text(
                "• Read usage, credits, activity and every API key on the account\n" +
                    "• Create, disable and delete API keys\n\n" +
                    "It cannot mint another management key - OpenRouter only allows that from the " +
                    "dashboard. It also can't call chat completions, so this app can't spend money " +
                    "on inference by accident.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        "https://openrouter.ai/settings/management-keys".toUri()
                    )
                )
            }) { Text("Manage keys in dashboard") }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Security") {
            Text(
                "• The management key is encrypted with an Android Keystore AES-256 key; only " +
                    "ciphertext is stored on disk\n" +
                    "• Screenshots are blocked while a new key is displayed\n" +
                    "• Newly minted keys exist only in memory until you close the dialog\n" +
                    "• All traffic goes to https://openrouter.ai over TLS\n" +
                    "• Nothing is sent to us - there is no backend, no analytics, no crash reporting",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "About") {
            Text("OR Usage 1.1", style = MaterialTheme.typography.bodyMedium)
            Text(
                "com.welivethisway.orusage · We Live This Way",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "OpenRouter figures are windowed to the last 30 completed UTC days, which is the " +
                    "most the activity endpoint returns. Mistral figures come from api.mistral.ai: " +
                    "model inventory for any key, organisation spend only for Enterprise admin keys.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        // The credit line. A name, a reason and a real address - provenance rather than a
        // signature. The mailbox is live and answered, so the link is not decoration.
        SectionCard(title = "Who built this") {
            Text("Deuce", style = MaterialTheme.typography.titleMedium)
            Text(
                "deuce@welivethisway.com",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Second half of a two-person dev team: I review and verify, then hand the numbers " +
                    "back checked. In tennis, deuce is the score where nobody is ahead and the next " +
                    "point settles it - which is roughly my job here. I am the last look before a " +
                    "release, and the one who runs the thing rather than assuming it works.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "So every figure on these screens is read from the provider's own API at refresh " +
                    "time. No backend, no cache, no placeholder standing in for a response that " +
                    "never arrived - and where an endpoint does not exist, the screen says so " +
                    "instead of showing a confident zero.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = {
                context.startActivity(
                    Intent(Intent.ACTION_SENDTO, "mailto:deuce@welivethisway.com".toUri())
                        .apply { putExtra(Intent.EXTRA_SUBJECT, "OR Usage") }
                )
            }) {
                Text("Write to me", fontFamily = FontFamily.Default)
            }
        }

        Spacer(Modifier.height(24.dp))
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = {
                Text(
                    "This deletes the encrypted management key from this device. " +
                        "Keys you minted are unaffected."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.signOut()
                    confirmSignOut = false
                }) { Text("Sign out") }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text("Cancel") }
            },
        )
    }
}
