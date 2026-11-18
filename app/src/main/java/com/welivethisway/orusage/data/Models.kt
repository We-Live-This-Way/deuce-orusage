package com.welivethisway.orusage.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsing + aggregation for the OpenRouter management API.
 *
 * Deliberately free of Android imports so the exact same code runs under `./gradlew test` on a
 * plain JVM. Every field is read defensively: the API is versioned but fields do get added, and a
 * missing or null field must never crash a dashboard.
 *
 * Shape notes, verified against https://openrouter.ai/openapi.json:
 *  - Keys carry usage / usage_daily / usage_weekly / usage_monthly / limit / limit_remaining.
 *  - Activity rows carry date, model, provider_name, endpoint_id, requests, usage and token counts.
 *    They do NOT carry a key hash; per-key history is only available by *filtering*
 *    `GET /activity?api_key_hash=...` one key at a time.
 */

// ---------- models ----------

data class ApiKeyInfo(
    val name: String,
    val hash: String,
    val label: String?,
    val usage: Double,
    val usageDaily: Double,
    val usageWeekly: Double,
    val usageMonthly: Double,
    val byokUsage: Double,
    val limit: Double?,
    val limitRemaining: Double?,
    val limitReset: String?,
    val disabled: Boolean,
    val createdAt: String?,
    val expiresAt: String?,
    val lastUsedAt: String?,
) {
    /** Keys are commonly created with only a label; never show an empty row. */
    val displayName: String
        get() = name.ifBlank { label?.takeIf { it.isNotBlank() } ?: ("key " + shortHash) }

    val shortHash: String get() = hash.take(10)

    /** Fraction of the key's spending limit consumed, or null when the key has no limit. */
    val limitFraction: Double?
        get() = limit?.takeIf { it > 0.0 }?.let { l -> (usage / l).coerceIn(0.0, 1.0) }

    val hasLimit: Boolean get() = limit != null && limit > 0.0
}

data class CreatedKey(val info: ApiKeyInfo, val plaintext: String)

data class Credits(val totalCredits: Double, val totalUsage: Double) {
    val balance: Double get() = totalCredits - totalUsage
}

data class ActivityRow(
    val date: String,
    val model: String,
    val providerName: String,
    val endpointId: String?,
    val requests: Long,
    val usage: Double,
    val promptTokens: Long,
    val completionTokens: Long,
    val cachedTokens: Long,
    val reasoningTokens: Long,
    val byokUsage: Double,
) {
    val totalTokens: Long get() = promptTokens + completionTokens
}

data class Bucket(val label: String, val requests: Long, val usage: Double, val tokens: Long) {
    val isEmpty: Boolean get() = usage == 0.0 && requests == 0L
}

/** Dimensions the activity endpoint can be grouped by. Keys are not one of them - see opts. */
enum class Dimension(val title: String) {
    PROVIDER("Server / provider"),
    MODEL("Model"),
    DAY("Day"),
}

data class Totals(val requests: Long, val usage: Double, val tokens: Long) {
    companion object {
        val EMPTY = Totals(0L, 0.0, 0L)
    }
}

// ---------- parsing helpers ----------

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (isNull(name) || !has(name)) null else optDouble(name, Double.NaN).takeUnless { it.isNaN() }

private fun JSONObject.optLongSafe(name: String): Long =
    if (!has(name) || isNull(name)) 0L else optDouble(name, 0.0).toLong()

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

/** Pulls a human-readable message out of an OpenRouter error body. Never throws. */
fun parseErrorMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        val root = JSONObject(body)
        when (val err = root.opt("error")) {
            is JSONObject -> err.optString("message").takeIf { it.isNotBlank() }
                ?: err.optString("error").takeIf { it.isNotBlank() }
            is String -> err.takeIf { it.isNotBlank() }
            else -> root.optString("message").takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        body.take(200)
    }
}

// ---------- parsers ----------

fun parseKey(json: JSONObject): ApiKeyInfo = ApiKeyInfo(
    name = json.optStringOrNull("name") ?: "",
    hash = json.optStringOrNull("hash") ?: "",
    label = json.optStringOrNull("label"),
    usage = json.optDoubleOrNull("usage") ?: 0.0,
    usageDaily = json.optDoubleOrNull("usage_daily") ?: 0.0,
    usageWeekly = json.optDoubleOrNull("usage_weekly") ?: 0.0,
    usageMonthly = json.optDoubleOrNull("usage_monthly") ?: 0.0,
    byokUsage = json.optDoubleOrNull("byok_usage") ?: 0.0,
    limit = json.optDoubleOrNull("limit"),
    limitRemaining = json.optDoubleOrNull("limit_remaining"),
    limitReset = json.optStringOrNull("limit_reset"),
    disabled = json.optBoolean("disabled", false),
    createdAt = json.optStringOrNull("created_at"),
    expiresAt = json.optStringOrNull("expires_at"),
    lastUsedAt = json.optStringOrNull("last_used_at"),
)

fun parseKeys(body: String): List<ApiKeyInfo> {
    val root = JSONObject(body)
    val arr: JSONArray = root.optJSONArray("data") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        arr.optJSONObject(i)?.let { parseKey(it) }
    }
}

fun parseCreatedKey(body: String): CreatedKey {
    val root = JSONObject(body)
    val infoJson = root.optJSONObject("data") ?: JSONObject()
    val plaintext = root.optStringOrNull("key") ?: ""
    return CreatedKey(parseKey(infoJson), plaintext)
}

fun parseCredits(body: String): Credits {
    val data = JSONObject(body).optJSONObject("data") ?: JSONObject()
    return Credits(
        totalCredits = data.optDoubleOrNull("total_credits") ?: 0.0,
        totalUsage = data.optDoubleOrNull("total_usage") ?: 0.0,
    )
}

fun parseActivity(body: String): List<ActivityRow> {
    val root = JSONObject(body)
    val arr = root.optJSONArray("data") ?: return emptyList()
    return (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        ActivityRow(
            date = o.optStringOrNull("date") ?: "",
            model = o.optStringOrNull("model") ?: "unknown",
            providerName = o.optStringOrNull("provider_name") ?: "unknown",
            endpointId = o.optStringOrNull("endpoint_id"),
            requests = o.optLongSafe("requests"),
            usage = o.optDoubleOrNull("usage") ?: 0.0,
            promptTokens = o.optLongSafe("prompt_tokens"),
            completionTokens = o.optLongSafe("completion_tokens"),
            cachedTokens = o.optLongSafe("cached_tokens"),
            reasoningTokens = o.optLongSafe("reasoning_tokens"),
            byokUsage = o.optDoubleOrNull("byok_usage_inference") ?: 0.0,
        )
    }
}

// ---------- aggregation ----------

fun totals(rows: List<ActivityRow>): Totals = Totals(
    requests = rows.sumOf { it.requests },
    usage = rows.sumOf { it.usage },
    tokens = rows.sumOf { it.totalTokens },
)

/**
 * Groups activity rows by one dimension, descending by spend.
 * Rows merge on the exact label, so a provider appearing across many models collapses to one line.
 */
fun groupBy(rows: List<ActivityRow>, dimension: Dimension): List<Bucket> {
    val grouped = LinkedHashMap<String, MutableList<ActivityRow>>()
    for (row in rows) {
        val key = when (dimension) {
            Dimension.PROVIDER -> row.providerName.ifBlank { "unknown" }
            Dimension.MODEL -> row.model.ifBlank { "unknown" }
            Dimension.DAY -> row.date.ifBlank { "unknown" }
        }
        grouped.getOrPut(key) { mutableListOf() }.add(row)
    }
    return grouped.map { (label, group) ->
        val t = totals(group)
        Bucket(label = label, requests = t.requests, usage = t.usage, tokens = t.tokens)
    }.sortedWith(
        // Most expensive first; ties broken by volume so the list is stable rather than random.
        compareByDescending<Bucket> { it.usage }.thenByDescending { it.requests }.thenBy { it.label }
    )
}

/**
 * Per-key buckets. Built from the keys endpoint because activity rows do not carry a key hash:
 * the documented `api_key_hash` filter narrows a request to one key, it never reports which key
 * a row belongs to.
 */
fun keyBuckets(keys: List<ApiKeyInfo>): List<Bucket> = keys.map { k ->
    Bucket(label = k.displayName, requests = 0L, usage = k.usage, tokens = 0L)
}.sortedWith(compareByDescending<Bucket> { it.usage }.thenBy { it.label })

/** Rows for a single UTC day, i.e. the "today" tile. */
fun rowsForDate(rows: List<ActivityRow>, date: String): List<ActivityRow> =
    rows.filter { it.date == date }

fun usageBetweenDates(rows: List<ActivityRow>, fromInclusive: String, toInclusive: String): Double =
    rows.filter { it.date.isNotBlank() && it.date >= fromInclusive && it.date <= toInclusive }
        .sumOf { it.usage }

/**
 * Rolling window spend. [days] counts back from [todayInclusive], so 7 means today plus the
 * previous 6 days - matching how a dashboard reader counts a week.
 */
fun usageLastDays(rows: List<ActivityRow>, todayInclusive: java.time.LocalDate, days: Int): Double {
    if (days <= 0) return 0.0
    val from = todayInclusive.minusDays((days - 1).toLong()).toString()
    return usageBetweenDates(rows, from, todayInclusive.toString())
}

/** Sums key usage as reported by the keys endpoint (authoritative for limits). */
fun totalKeyUsage(keys: List<ApiKeyInfo>): Double = keys.sumOf { it.usage }

/** Keys past their expiry, using ISO-8601 string comparison which is safe for this format. */
fun expiredKeys(keys: List<ApiKeyInfo>, nowIso: String): List<ApiKeyInfo> =
    keys.filter { k -> k.expiresAt?.let { it < nowIso } == true }

/** Top N buckets, dropping empty ones so a nearly-idle account shows a clean list. */
fun topBuckets(buckets: List<Bucket>, n: Int): List<Bucket> =
    buckets.filterNot { it.isEmpty }.take(n.coerceAtLeast(0))

// ---------- formatting ----------

fun formatMoney(value: Double): String {
    val abs = kotlin.math.abs(value)
    val sign = if (value < 0) "-" else ""
    return when {
        // Grouped thousands but never rounded away: a spend dashboard that shows $1,235 instead
        // of $1,234.56 is lying about 44 cents.
        abs >= 1 -> sign + "$" + String.format("%,.2f", abs)
        abs == 0.0 -> "$0.00"
        abs >= 0.01 -> sign + "$" + String.format("%.2f", abs)
        // Sub-cent spend is real on OpenRouter; showing "$0.00" would hide it.
        else -> sign + "$" + String.format("%.4f", abs)
    }
}

fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000_000 -> String.format("%.2fB", tokens / 1_000_000_000.0)
    tokens >= 1_000_000 -> String.format("%.2fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format("%.1fK", tokens / 1_000.0)
    else -> tokens.toString()
}

fun formatCount(n: Long): String = String.format("%,d", n)

/** Convenience for List.size and other Int sources. */
fun formatCount(n: Int): String = formatCount(n.toLong())
