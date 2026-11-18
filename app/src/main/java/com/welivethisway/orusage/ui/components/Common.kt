package com.welivethisway.orusage.ui.components

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.welivethisway.orusage.data.Bucket
import com.welivethisway.orusage.data.formatMoney

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                trailing?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun StatTile(label: String, value: String, sub: String? = null, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (sub != null) {
            Text(
                sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Horizontal spend bars scaled to the largest value in the list.
 * Deliberately hand-drawn rather than pulling a charting dependency: one less thing to break,
 * and it renders correctly at any font scale.
 */
@Composable
fun BarList(
    buckets: List<Bucket>,
    modifier: Modifier = Modifier,
    valueLabel: (Bucket) -> String = { formatMoney(it.usage) },
    maxRows: Int = Int.MAX_VALUE,
) {
    if (buckets.isEmpty()) {
        Text(
            "No usage recorded yet.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    val shown = buckets.take(maxRows)
    val max = shown.maxOfOrNull { kotlin.math.abs(it.usage) }?.takeIf { it > 0.0 } ?: 1.0
    Column(modifier) {
        shown.forEach { bucket ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    bucket.label,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f, fill = true),
                    maxLines = 1,
                )
                Text(
                    valueLabel(bucket),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val fraction = (kotlin.math.abs(bucket.usage) / max).coerceIn(0.0, 1.0).toFloat()
                if (fraction > 0f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Progress bar used for a key's spend against its own limit. */
@Composable
fun LimitBar(fraction: Double, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        val f = fraction.coerceIn(0.0, 1.0).toFloat()
        if (f > 0f) {
            Box(
                Modifier
                    .fillMaxWidth(f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (fraction >= 0.9) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    ),
            )
        }
    }
}

/**
 * Blocks screenshots and the recents thumbnail while a plaintext key is on screen.
 * Cleared on dispose so the rest of the app stays screenshot-able.
 */
@Composable
fun SecureScreen(enabled: Boolean) {
    val context = LocalContext.current
    DisposableEffect(enabled) {
        val window = (context as? Activity)?.window
        if (enabled && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        onDispose {
            if (window != null) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }
}
