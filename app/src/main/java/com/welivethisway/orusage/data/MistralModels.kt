package com.welivethisway.orusage.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parsing for the Mistral API, kept free of Android imports so it runs under plain JVM tests.
 *
 * What Mistral actually exposes, checked against the live API and the published OpenAPI document
 * on 2026-11-18 - this is the whole reason the Mistral tab looks different from the OpenRouter one:
 *
 *  - `GET /v1/models`            - any valid key. Model inventory and capabilities, no spend.
 *  - `GET /v1/admin/spend-limit` - Enterprise admin keys only. Real usage against the monthly
 *                                  limit, currency, limit-reached and payment-failure flags.
 *  - `GET /v1/admin/usage`       - Enterprise admin keys only. Monthly usage broken down by
 *                                  product, shipped with a *price catalogue* rather than computed
 *                                  totals, so a client would have to do the billing arithmetic
 *                                  itself. Deliberately not used: a dashboard that re-derives
 *                                  money from unit prices will eventually disagree with the invoice.
 *
 * `GET /v1/usage` and `GET /v1/billing` do not exist (404, "no Route matched"), so there is no
 * per-key spend figure for a standard non-Enterprise key. The UI says so instead of showing a
 * confident zero.
 */

data class MistralModel(
    val id: String,
    val name: String,
    val maxContextLength: Long,
    val capabilities: Set<String>,
    val deprecation: String?,
    val aliases: List<String>,
) {
    /** Something you can actually chat with, as opposed to an embedding or moderation model. */
    val isChat: Boolean get() = capabilities.contains("completion_chat")

    val isVision: Boolean get() = capabilities.contains("vision")

    /** Prefer the documented name; fall back to the id so a row is never blank. */
    val label: String get() = name.ifBlank { id }

    /** Short capability list for the UI. Order is fixed so rows don't shuffle between refreshes. */
    val badges: List<String>
        get() = listOf(
            "chat" to "completion_chat",
            "vision" to "vision",
            "tools" to "function_calling",
            "reasoning" to "reasoning",
            "ocr" to "ocr",
            "audio" to "audio",
            "embeddings" to "embedding",
            "moderation" to "moderation",
        ).filter { (_, key) -> capabilities.contains(key) }.map { it.first }
}

/**
 * Organisation limits from `GET /v1/admin/spend-limit`.
 *
 * `usage` and `total_usage` are both nullable in the schema and `total_usage` is the one the
 * console compares against the limit, so it wins when present. `usage_limit` is optional because
 * an organisation can be configured with no monthly cap at all (`no_monthly_limit`).
 */
data class MistralLimits(
    val currency: String,
    val usage: Double?,
    val totalUsage: Double?,
    val usageLimit: Double?,
    val noMonthlyLimit: Boolean,
    val limitReached: Boolean,
    val lastPaymentFailure: Boolean,
) {
    val spent: Double get() = totalUsage ?: usage ?: 0.0

    val hasLimit: Boolean get() = usageLimit != null && usageLimit > 0.0

    /** Spend as a fraction of the monthly limit, or null when the org has no cap configured. */
    val fraction: Double?
        get() = usageLimit?.takeIf { it > 0.0 }?.let { limit -> (spent / limit).coerceIn(0.0, 1.0) }

    val remaining: Double? get() = usageLimit?.let { (it - spent).coerceAtLeast(0.0) }
}

/**
 * Capability tallies for the model inventory card.
 *
 * [total] is the number of models, counted directly. It is *not* the sum of the other fields:
 * a vision-chat model is both, and adding the buckets would report a bigger inventory than the
 * key can actually reach.
 */
data class ModelMix(val total: Int, val chat: Int, val vision: Int, val ocr: Int, val other: Int)

fun modelMix(models: List<MistralModel>): ModelMix = ModelMix(
    total = models.size,
    chat = models.count { it.isChat },
    vision = models.count { it.isVision },
    ocr = models.count { "ocr" in it.capabilities },
    other = models.count { !it.isChat && !it.isVision && "ocr" !in it.capabilities },
)

// ---------- parsing ----------

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (!has(name) || isNull(name)) null else optDouble(name, Double.NaN).takeUnless { it.isNaN() }

private fun JSONObject.optStringOrNull(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

fun parseMistralModels(body: String): List<MistralModel> {
    val arr: JSONArray = try {
        JSONObject(body).optJSONArray("data") ?: return emptyList()
    } catch (e: Exception) {
        return emptyList()
    }
    val out = ArrayList<MistralModel>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue
        val id = o.optStringOrNull("id") ?: continue
        val caps = LinkedHashSet<String>()
        o.optJSONObject("capabilities")?.let { c ->
            // Capabilities are booleans keyed by name; keep only the ones switched on.
            c.keys().forEach { k -> if (c.optBoolean(k, false)) caps.add(k) }
        }
        val aliases = ArrayList<String>()
        o.optJSONArray("aliases")?.let { a ->
            for (j in 0 until a.length()) a.optString(j).takeIf { it.isNotBlank() }?.let(aliases::add)
        }
        out.add(
            MistralModel(
                id = id,
                name = o.optStringOrNull("name") ?: id,
                maxContextLength = o.optDoubleOrNull("max_context_length")?.toLong() ?: 0L,
                capabilities = caps,
                deprecation = o.optStringOrNull("deprecation"),
                aliases = aliases,
            )
        )
    }
    return out
}

/** Empty or malformed bodies mean "no limits to show", never a crash. */
fun parseMistralLimits(body: String): MistralLimits? {
    val completion = try {
        JSONObject(body).optJSONObject("limits")?.optJSONObject("completion") ?: return null
    } catch (e: Exception) {
        return null
    }
    val outer = try {
        JSONObject(body).optJSONObject("limits")
    } catch (e: Exception) {
        null
    }
    return MistralLimits(
        currency = outer?.optStringOrNull("currency") ?: "USD",
        usage = completion.optDoubleOrNull("usage"),
        totalUsage = completion.optDoubleOrNull("total_usage"),
        usageLimit = completion.optDoubleOrNull("usage_limit"),
        noMonthlyLimit = completion.optBoolean("no_monthly_limit", false),
        limitReached = completion.optBoolean("monthly_limit_reached", false),
        lastPaymentFailure = outer?.optBoolean("last_payment_failure", false) ?: false,
    )
}

/**
 * Money with the currency Mistral reports, rather than a hard-coded dollar sign.
 * USD keeps the familiar `$`; anything else is shown as a suffix so a EUR org isn't lied to.
 */
fun formatMistralMoney(value: Double, currency: String): String {
    val money = formatMoney(value)
    return if (currency.equals("USD", ignoreCase = true) || currency.isBlank()) money
    else money + " " + currency.uppercase()
}

/** Sort for the model list: chat models first, then by context size, then id. */
fun chatModelsFirst(models: List<MistralModel>): List<MistralModel> = models.sortedWith(
    compareByDescending<MistralModel> { it.isChat }
        .thenByDescending { it.maxContextLength }
        .thenBy { it.id }
)
