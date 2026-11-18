package com.welivethisway.orusage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.welivethisway.orusage.data.Dimension
import com.welivethisway.orusage.data.formatCount
import com.welivethisway.orusage.data.formatMoney
import com.welivethisway.orusage.data.formatTokens
import com.welivethisway.orusage.data.groupBy
import com.welivethisway.orusage.data.keyBuckets
import com.welivethisway.orusage.data.totals
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.BarList
import com.welivethisway.orusage.ui.components.SectionCard
import com.welivethisway.orusage.ui.components.StatTile

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BreakdownScreen(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    var dimension by remember { mutableStateOf(Dimension.PROVIDER) }
    val scopedRows = state.selectedKeyHash?.let { hash -> state.keyActivity[hash] } ?: state.activity
    val summary = totals(scopedRows)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Breakdown", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Last 30 days, UTC. Tap a key to scope everything to that key.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        SectionCard(title = "Scope") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(
                    selected = state.selectedKeyHash == null,
                    onClick = { vm.selectKey(null) },
                    label = { Text("All keys") },
                )
                state.keys.forEach { key ->
                    FilterChip(
                        selected = state.selectedKeyHash == key.hash,
                        onClick = {
                            vm.selectKey(key.hash)
                            vm.loadKeyActivity(key.hash)
                        },
                        label = { Text(key.displayName) },
                    )
                }
            }
            if (state.keyActivityLoading && state.selectedKeyHash != null) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(14.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(0.dp))
                    Text(
                        "  Loading key history…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Totals") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                StatTile("Spend", formatMoney(summary.usage))
                StatTile("Requests", formatCount(summary.requests))
                StatTile("Tokens", formatTokens(summary.tokens))
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Group by") {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Dimension.entries.forEach { d ->
                    FilterChip(
                        selected = dimension == d,
                        onClick = { dimension = d },
                        label = { Text(d.title) },
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = dimension.title) {
            val buckets = groupBy(scopedRows, dimension)
            if (buckets.isEmpty()) {
                Text(
                    "No activity in the last 30 days for this scope.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                BarList(buckets, maxRows = 25)
                Spacer(Modifier.height(8.dp))
                Text(
                    buckets.size.toString() + " groups · " +
                        formatMoney(buckets.sumOf { it.usage }) + " total",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "By key (lifetime)") {
            BarList(keyBuckets(state.keys).filter { it.usage > 0 })
            Spacer(Modifier.height(8.dp))
            Text(
                "Per-key totals come from the keys endpoint. Activity rows don't carry a key id, so " +
                    "per-key history is fetched by filtering activity one key at a time.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}
