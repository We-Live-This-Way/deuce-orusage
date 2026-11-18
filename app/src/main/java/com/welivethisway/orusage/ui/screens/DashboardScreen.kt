package com.welivethisway.orusage.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.welivethisway.orusage.data.Dimension
import com.welivethisway.orusage.data.formatCount
import com.welivethisway.orusage.data.formatMoney
import com.welivethisway.orusage.data.groupBy
import com.welivethisway.orusage.data.keyBuckets
import com.welivethisway.orusage.data.topBuckets
import com.welivethisway.orusage.data.totals
import com.welivethisway.orusage.data.usageLastDays
import com.welivethisway.orusage.ui.AppViewModel
import com.welivethisway.orusage.ui.UiState
import com.welivethisway.orusage.ui.components.BarList
import com.welivethisway.orusage.ui.components.SectionCard
import com.welivethisway.orusage.ui.components.StatTile
import java.time.LocalDate
import java.time.ZoneOffset

@Composable
fun DashboardScreen(vm: AppViewModel, state: UiState, modifier: Modifier = Modifier) {
    val today = LocalDate.now(ZoneOffset.UTC)
    val all = state.activity
    val summary = totals(all)

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
                Text("Overview", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Last 30 days, UTC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.height(18.dp), strokeWidth = 2.dp)
                }
                IconButton(onClick = { vm.refresh() }, enabled = !state.loading) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Credits") {
            val credits = state.credits
            if (credits == null) {
                Text("Not loaded yet.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    formatMoney(credits.balance),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "remaining",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatTile("Purchased", formatMoney(credits.totalCredits))
                    StatTile("Used", formatMoney(credits.totalUsage))
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Spend") {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                StatTile("Today", formatMoney(usageLastDays(all, today, 1)))
                StatTile("7 days", formatMoney(usageLastDays(all, today, 7)))
                StatTile("30 days", formatMoney(usageLastDays(all, today, 30)))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                formatCount(summary.requests) + " requests · " +
                    formatMoney(summary.usage) + " total · " +
                    formatCount(state.keys.size) + " keys",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Top servers") {
            BarList(topBuckets(groupBy(all, Dimension.PROVIDER), 6))
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Top keys") {
            BarList(topBuckets(keyBuckets(state.keys), 6))
        }

        Spacer(Modifier.height(16.dp))

        SectionCard(title = "Top models") {
            BarList(topBuckets(groupBy(all, Dimension.MODEL), 6))
        }

        Spacer(Modifier.height(24.dp))
    }
}
