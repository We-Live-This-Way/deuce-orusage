package com.welivethisway.orusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ModelsTest {

    // Fixtures mirror the shapes documented at https://openrouter.ai/openapi.json
    private val keysJson = """
    {"data":[
      {"name":"Production","hash":"aaaa1111bbbb2222cccc","label":"sk-or-v1-0e6...1c96",
       "usage":25.5,"usage_daily":1.25,"usage_weekly":7.0,"usage_monthly":25.5,
       "byok_usage":2.0,"limit":100.0,"limit_remaining":74.5,"limit_reset":"monthly",
       "disabled":false,"created_at":"2025-08-24T10:30:00Z","expires_at":"2027-12-31T23:59:59Z",
       "last_used_at":"2026-09-15T15:45:00Z"},
      {"name":"","hash":"dddd3333eeee4444ffff","label":null,
       "usage":0.004,"usage_daily":0.0,"usage_weekly":0.0,"usage_monthly":0.004,
       "byok_usage":0.0,"limit":null,"limit_remaining":null,"limit_reset":null,
       "disabled":true,"created_at":"2025-09-01T00:00:00Z","expires_at":null,"last_used_at":null}
    ]}
    """.trimIndent()

    private val activityJson = """
    {"data":[
      {"date":"2026-11-18","model":"openai/gpt-4o","provider_name":"OpenAI","endpoint_id":"ep1",
       "requests":10,"usage":1.50,"prompt_tokens":1000,"completion_tokens":500,
       "cached_tokens":0,"reasoning_tokens":0,"byok_usage_inference":0.0},
      {"date":"2026-11-18","model":"anthropic/claude-sonnet-4","provider_name":"Anthropic","endpoint_id":"ep2",
       "requests":4,"usage":0.25,"prompt_tokens":200,"completion_tokens":100,
       "cached_tokens":50,"reasoning_tokens":0,"byok_usage_inference":0.0},
      {"date":"2026-11-17","model":"openai/gpt-4o","provider_name":"OpenAI","endpoint_id":"ep1",
       "requests":6,"usage":0.75,"prompt_tokens":400,"completion_tokens":200,
       "cached_tokens":0,"reasoning_tokens":0,"byok_usage_inference":0.0}
    ]}
    """.trimIndent()

    @Test
    fun `parses documented key fields`() {
        val keys = parseKeys(keysJson)
        assertEquals(2, keys.size)
        val first = keys[0]
        assertEquals("Production", first.displayName)
        assertEquals(25.5, first.usage, 0.0001)
        assertEquals(1.25, first.usageDaily, 0.0001)
        assertEquals(7.0, first.usageWeekly, 0.0001)
        assertEquals(2.0, first.byokUsage, 0.0001)
        assertEquals(100.0, first.limit!!, 0.0001)
        assertEquals(74.5, first.limitRemaining!!, 0.0001)
        assertEquals("monthly", first.limitReset)
        assertFalse(first.disabled)
        assertTrue(first.hasLimit)
        assertEquals(0.255, first.limitFraction!!, 0.0001)
    }

    @Test
    fun `null limit means unlimited rather than zero`() {
        val second = parseKeys(keysJson)[1]
        assertNull(second.limit)
        assertNull(second.limitRemaining)
        assertFalse(second.hasLimit)
        assertNull(second.limitFraction)
        assertTrue(second.disabled)
    }

    @Test
    fun `key with blank name falls back to label then hash`() {
        assertEquals("key dddd3333ee", parseKeys(keysJson)[1].displayName)
        val bare = """{"data":[{"name":"","label":null,"hash":"deadbeefcafe1234"}]}"""
        assertEquals("key deadbeefca", parseKeys(bare)[0].displayName)
    }

    @Test
    fun `missing fields default instead of throwing`() {
        val sparse = """{"data":[{"hash":"onlyhash"}]}"""
        val key = parseKeys(sparse)[0]
        assertEquals("onlyhash", key.hash)
        assertEquals(0.0, key.usage, 0.0)
        assertNull(key.expiresAt)
        assertFalse(key.disabled)
    }

    @Test
    fun `empty and malformed payloads yield empty lists`() {
        assertTrue(parseKeys("""{"data":[]}""").isEmpty())
        assertTrue(parseKeys("""{"data":null}""").isEmpty())
        assertTrue(parseKeys("{}").isEmpty())
        assertTrue(parseActivity("{}").isEmpty())
        assertTrue(parseKeys("""{"data":[null]}""").isEmpty())
    }

    @Test
    fun `groups activity by provider across models`() {
        val rows = parseActivity(activityJson)
        assertEquals(3, rows.size)
        val byProvider = groupBy(rows, Dimension.PROVIDER)
        assertEquals(2, byProvider.size)
        // OpenAI is the biggest spender and must sort first.
        assertEquals("OpenAI", byProvider[0].label)
        assertEquals(2.25, byProvider[0].usage, 0.0001)
        assertEquals(16L, byProvider[0].requests)
        assertEquals("Anthropic", byProvider[1].label)
        assertEquals(0.25, byProvider[1].usage, 0.0001)
    }

    @Test
    fun `groups activity by model and by day`() {
        val rows = parseActivity(activityJson)
        val byModel = groupBy(rows, Dimension.MODEL)
        assertEquals(2, byModel.size)
        assertEquals("openai/gpt-4o", byModel[0].label)
        assertEquals(2.25, byModel[0].usage, 0.0001)
        assertEquals(2100L, byModel[0].tokens)

        val byDay = groupBy(rows, Dimension.DAY)
        assertEquals(2, byDay.size)
        assertEquals("2026-11-18", byDay[0].label)
        assertEquals(1.75, byDay[0].usage, 0.0001)
        assertEquals("2026-11-17", byDay[1].label)
    }

    @Test
    fun `rolling window counts today plus previous six days`() {
        val rows = parseActivity(activityJson)
        val today = LocalDate.of(2026, 11, 18)
        // 2026-11-18 and 2026-11-17 are both inside a 7 day window ending today.
        assertEquals(2.5, usageLastDays(rows, today, 7), 0.0001)
        assertEquals(1.75, usageLastDays(rows, today, 1), 0.0001)
        assertEquals(0.0, usageLastDays(rows, today, 0), 0.0)
        // A window that ends before the data starts finds nothing.
        assertEquals(0.0, usageLastDays(rows, LocalDate.of(2026, 11, 10), 7), 0.0)
    }

    @Test
    fun `window boundary includes the sixth day back and excludes the seventh`() {
        val rows = listOf(
            row(date = "2026-11-11", usage = 3.0),  // 7 days back: outside a 7 day window
            row(date = "2026-11-12", usage = 5.0),  // 6 days back: inside
        )
        val today = LocalDate.of(2026, 11, 18)
        assertEquals(5.0, usageLastDays(rows, today, 7), 0.0001)
        assertEquals(8.0, usageLastDays(rows, today, 8), 0.0001)
        // The window must not double count rows that sit on its first day.
        assertEquals(5.0, usageLastDays(rows, today, 7), 0.0001)
    }

    @Test
    fun `totals sum requests usage and tokens`() {
        val t = totals(parseActivity(activityJson))
        assertEquals(20L, t.requests)
        assertEquals(2.5, t.usage, 0.0001)
        assertEquals(2400L, t.tokens)
    }

    @Test
    fun `key buckets sort by spend and keep unlimited keys`() {
        val buckets = keyBuckets(parseKeys(keysJson))
        assertEquals("Production", buckets[0].label)
        assertEquals(25.5, buckets[0].usage, 0.0001)
        assertEquals(0.004, buckets[1].usage, 0.0001)
    }

    @Test
    fun `expired keys compare against iso timestamps`() {
        val keys = parseKeys(keysJson)
        val expired = expiredKeys(keys, "2030-01-01T00:00:00Z")
        assertEquals(1, expired.size)
        assertEquals("Production", expired[0].displayName)
        assertTrue(expiredKeys(keys, "2020-01-01T00:00:00Z").isEmpty())
    }

    @Test
    fun `created key keeps the plaintext and the metadata`() {
        val body = """
        {"data":{"name":"CI","hash":"abc123def456","usage":0,"limit":50.0,"limit_reset":"monthly"},
         "key":"sk-or-v1-deadbeef"}
        """.trimIndent()
        val created = parseCreatedKey(body)
        assertEquals("sk-or-v1-deadbeef", created.plaintext)
        assertEquals("CI", created.info.displayName)
        assertEquals(50.0, created.info.limit!!, 0.0001)
    }

    @Test
    fun `credits expose balance`() {
        val c = parseCredits("""{"data":{"total_credits":100.5,"total_usage":25.75}}""")
        assertEquals(100.5, c.totalCredits, 0.0001)
        assertEquals(25.75, c.totalUsage, 0.0001)
        assertEquals(74.75, c.balance, 0.0001)
    }

    @Test
    fun `error messages are extracted from every documented shape`() {
        assertEquals("Invalid management key", parseErrorMessage("""{"error":{"message":"Invalid management key"}}"""))
        assertEquals("Invalid API key", parseErrorMessage("""{"error":"Invalid API key"}"""))
        assertEquals("boom", parseErrorMessage("""{"message":"boom"}"""))
        assertNull(parseErrorMessage(null))
        assertNull(parseErrorMessage(""))
        // Non-JSON bodies must degrade to text, not crash.
        assertEquals("Bad Gateway", parseErrorMessage("Bad Gateway"))
    }

    @Test
    fun `money formatting keeps sub-cent precision and groups thousands`() {
        assertEquals("$0.00", formatMoney(0.0))
        assertEquals("$25.50", formatMoney(25.5))
        assertEquals("$1,234.56", formatMoney(1234.56))
        assertEquals("$0.0040", formatMoney(0.004))
        assertEquals("$0.25", formatMoney(0.25))
        assertEquals("-$3.20", formatMoney(-3.2))
    }

    @Test
    fun `token formatting scales units`() {
        assertEquals("999", formatTokens(999))
        assertEquals("1.0K", formatTokens(1000))
        assertEquals("1.50M", formatTokens(1_500_000))
        assertEquals("2.00B", formatTokens(2_000_000_000))
    }

    @Test
    fun `topBuckets drops zero rows and honours the limit`() {
        val buckets = listOf(
            Bucket("a", 1, 1.0, 0),
            Bucket("zero", 0, 0.0, 0),
            Bucket("b", 1, 2.0, 0),
            Bucket("c", 1, 3.0, 0),
        )
        val top = topBuckets(buckets, 2)
        assertEquals(2, top.size)
        assertEquals("a", top[0].label)
        assertEquals("b", top[1].label)
        assertTrue(topBuckets(buckets, 0).isEmpty())
    }

    private fun row(date: String, usage: Double) = ActivityRow(
        date = date, model = "m", providerName = "p", endpointId = null,
        requests = 1, usage = usage, promptTokens = 0, completionTokens = 0,
        cachedTokens = 0, reasoningTokens = 0, byokUsage = 0.0,
    )
}
