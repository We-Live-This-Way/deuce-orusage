package com.welivethisway.orusage.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Fixtures are trimmed copies of the documented response shapes (OpenAPI 3.1, revision
 * 7652f1307b3b, 2026-11-17). Field names matter more than prettiness here: a parser that reads
 * `total_usage` as `totalUsage` silently shows zero, and zero is the one number a spend screen
 * must never invent.
 */
class MistralModelsTest {

    private val modelsJson = """
        {
          "object": "list",
          "data": [
            {
              "id": "mistral-large-latest",
              "object": "model",
              "created": 1716768000,
              "owned_by": "mistralai",
              "capabilities": {
                "completion_chat": true,
                "function_calling": true,
                "reasoning": true,
                "vision": false,
                "ocr": false,
                "fine_tuning": true
              },
              "name": "Mistral Large",
              "description": "Flagship model",
              "max_context_length": 131072,
              "aliases": ["mistral-large-2411"],
              "deprecation": null,
              "type": "base"
            },
            {
              "id": "mistral-embed",
              "object": "model",
              "capabilities": { "completion_chat": false, "embedding": true },
              "name": null,
              "max_context_length": 8192,
              "aliases": [],
              "type": "base"
            },
            {
              "id": "pixtral-12b-2409",
              "capabilities": { "completion_chat": true, "vision": true },
              "max_context_length": 128000,
              "type": "base"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parses model list with capabilities and aliases`() {
        val models = parseMistralModels(modelsJson)
        assertEquals(3, models.size)

        val large = models.first { it.id == "mistral-large-latest" }
        assertEquals("Mistral Large", large.name)
        assertEquals(131072L, large.maxContextLength)
        assertTrue(large.isChat)
        assertTrue("reasoning" in large.capabilities)
        assertEquals(listOf("mistral-large-2411"), large.aliases)
        assertEquals(listOf("chat", "tools", "reasoning"), large.badges)

        val embed = models.first { it.id == "mistral-embed" }
        assertEquals("mistral-embed", embed.label)
        assertTrue(!embed.isChat)
        assertTrue("embedding" in embed.capabilities)

        val pixtral = models.first { it.id == "pixtral-12b-2409" }
        assertTrue(pixtral.isVision)
    }

    @Test
    fun `empty or malformed model bodies return an empty list rather than throwing`() {
        assertTrue(parseMistralModels("").isEmpty())
        assertTrue(parseMistralModels("not json").isEmpty())
        assertTrue(parseMistralModels("{}").isEmpty())
        assertTrue(parseMistralModels("""{"data": []}""").isEmpty())
    }

    @Test
    fun `model without an id is skipped, not shown blank`() {
        val body = """{"data": [{"name": "nameless"}, {"id": "ok-model"}]}"""
        val models = parseMistralModels(body)
        assertEquals(1, models.size)
        assertEquals("ok-model", models[0].id)
    }

    @Test
    fun `model mix counts the inventory`() {
        val mix = modelMix(parseMistralModels(modelsJson))
        assertEquals(3, mix.total)
        assertEquals(2, mix.chat)
        assertEquals(1, mix.vision)
        assertEquals(0, mix.ocr)
        assertEquals(1, mix.other) // the embedding model
    }

    @Test
    fun `chat models sort before non-chat, then by context size`() {
        val ids = chatModelsFirst(parseMistralModels(modelsJson)).map { it.id }
        assertEquals(listOf("mistral-large-latest", "pixtral-12b-2409", "mistral-embed"), ids)
    }

    private val limitsJson = """
        {
          "limits": {
            "completion": {
              "no_monthly_limit": false,
              "monthly_limit_reached": false,
              "usage": 41.2,
              "vibe_usage": 0,
              "total_usage": 53.75,
              "usage_limit": 250.0
            },
            "last_payment_failure": false,
            "last_payment_failure_protection": true,
            "currency": "USD"
          }
        }
    """.trimIndent()

    @Test
    fun `parses spend limits and prefers total_usage over usage`() {
        val limits = parseMistralLimits(limitsJson)!!
        assertEquals("USD", limits.currency)
        assertEquals(53.75, limits.spent, 1e-9)
        assertEquals(250.0, limits.usageLimit!!, 1e-9)
        assertTrue(limits.hasLimit)
        assertEquals(0.215, limits.fraction!!, 1e-9)
        assertEquals(196.25, limits.remaining!!, 1e-9)
    }

    @Test
    fun `limit reached and payment failure flags survive parsing`() {
        val body = """
            {"limits": {"completion": {"no_monthly_limit": false, "monthly_limit_reached": true,
             "usage": 250.0, "total_usage": 250.0, "usage_limit": 250.0},
             "last_payment_failure": true, "currency": "EUR"}}
        """.trimIndent()
        val limits = parseMistralLimits(body)!!
        assertTrue(limits.limitReached)
        assertTrue(limits.lastPaymentFailure)
        assertEquals("EUR", limits.currency)
        assertEquals(1.0, limits.fraction!!, 1e-9)
        assertEquals(0.0, limits.remaining!!, 1e-9)
    }

    @Test
    fun `organisation with no monthly cap reports no fraction`() {
        val body = """
            {"limits": {"completion": {"no_monthly_limit": true, "monthly_limit_reached": false,
             "usage": 12.0, "total_usage": 12.0, "usage_limit": null},
             "last_payment_failure": false, "currency": "USD"}}
        """.trimIndent()
        val limits = parseMistralLimits(body)!!
        assertTrue(limits.noMonthlyLimit)
        assertNull(limits.fraction)
        assertNull(limits.remaining)
        assertEquals(12.0, limits.spent, 1e-9)
    }

    @Test
    fun `missing total_usage falls back to usage`() {
        val body = """{"limits": {"completion": {"usage": 7.5}, "currency": "USD"}}"""
        val limits = parseMistralLimits(body)!!
        assertEquals(7.5, limits.spent, 1e-9)
    }

    @Test
    fun `unparseable limits return null instead of a zero spend`() {
        assertNull(parseMistralLimits(""))
        assertNull(parseMistralLimits("nope"))
        assertNull(parseMistralLimits("{}"))
        assertNull(parseMistralLimits("""{"limits": {}}"""))
    }

    @Test
    fun `money uses the reported currency`() {
        assertEquals("$53.75", formatMistralMoney(53.75, "USD"))
        assertEquals("$0.0042", formatMistralMoney(0.0042, "USD"))
        assertEquals("$53.75 EUR", formatMistralMoney(53.75, "eur"))
        assertEquals("$53.75", formatMistralMoney(53.75, ""))
    }

    @Test
    fun `error bodies are read from detail then message`() {
        assertEquals("Invalid API Key", parseMistralMessage("""{"detail":"Invalid API Key"}"""))
        assertEquals(
            "no Route matched with those values",
            parseMistralMessage("""{"message":"no Route matched with those values"}"""),
        )
        assertNull(parseMistralMessage(""))
        assertEquals("plain text", parseMistralMessage("plain text"))
    }
}
