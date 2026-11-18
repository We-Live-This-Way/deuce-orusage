package com.welivethisway.orusage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Everything the management API can fail with, mapped to something worth showing a human. */
class OpenRouterException(val status: Int?, override val message: String) : Exception(message)

class OpenRouterApi(
    private val baseUrl: String = "https://openrouter.ai/api/v1",
    private val client: OkHttpClient = defaultClient(),
) {

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ---------- keys ----------

    suspend fun listKeys(managementKey: String): List<ApiKeyInfo> {
        val url = base().addPathSegments("keys")
            .addQueryParameter("include_disabled", "true")
            .addQueryParameter("include_expired", "true")
            .build()
        return parseKeys(execute(Request.Builder().url(url).get(), managementKey))
    }

    suspend fun createKey(
        managementKey: String,
        name: String,
        limit: Double? = null,
        limitReset: String? = null,
        expiresAt: String? = null,
    ): CreatedKey {
        val body = JSONObject().apply {
            put("name", name)
            if (limit != null && limit > 0) {
                put("limit", limit)
                // A limit without a reset is a lifetime cap; only send a reset when one was chosen.
                limitReset?.let { put("limit_reset", it) }
            }
            expiresAt?.let { put("expires_at", it) }
        }
        val url = base().addPathSegments("keys").build()
        val req = Request.Builder().url(url).post(body.toString().toRequestBody(JSON))
        return parseCreatedKey(execute(req, managementKey))
    }

    suspend fun setKeyDisabled(managementKey: String, hash: String, disabled: Boolean): ApiKeyInfo {
        val body = JSONObject().put("disabled", disabled)
        val url = base().addPathSegments("keys").addPathSegment(hash).build()
        val req = Request.Builder().url(url).patch(body.toString().toRequestBody(JSON))
        val raw = execute(req, managementKey)
        val data = JSONObject(raw).optJSONObject("data") ?: JSONObject()
        return parseKey(data)
    }

    suspend fun deleteKey(managementKey: String, hash: String) {
        val url = base().addPathSegments("keys").addPathSegment(hash).build()
        execute(Request.Builder().url(url).delete(), managementKey)
    }

    // ---------- usage ----------

    suspend fun credits(managementKey: String): Credits =
        parseCredits(execute(Request.Builder().url(base().addPathSegments("credits").build()).get(), managementKey))

    /**
     * Activity over the last 30 completed UTC days, one row per date/model/provider/endpoint.
     * Omitting `date` returns the whole window; passing it narrows to a single day.
     */
    suspend fun activity(
        managementKey: String,
        apiKeyHash: String? = null,
        date: String? = null,
    ): List<ActivityRow> {
        val builder = base().addPathSegments("activity")
        apiKeyHash?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("api_key_hash", it) }
        date?.takeIf { it.isNotBlank() }?.let { builder.addQueryParameter("date", it) }
        return parseActivity(execute(Request.Builder().url(builder.build()).get(), managementKey))
    }

    // ---------- plumbing ----------

    private fun base(): HttpUrl.Builder = baseUrl.toHttpUrl().newBuilder()

    private suspend fun execute(builder: Request.Builder, managementKey: String): String =
        withContext(Dispatchers.IO) {
            val request = builder
                .header("Authorization", "Bearer " + managementKey.trim())
                .header("Accept", "application/json")
                .header("X-OpenRouter-Title", "OR Usage")
                .build()

            val response = try {
                client.newCall(request).execute()
            } catch (e: IOException) {
                throw OpenRouterException(null, "Can't reach openrouter.ai - check the connection.")
            }

            response.use { r ->
                val text = r.body?.string().orEmpty()
                if (r.isSuccessful) return@withContext text
                throw OpenRouterException(r.code, friendlyError(r.code, parseErrorMessage(text)))
            }
        }

    private fun friendlyError(code: Int, apiMessage: String?): String = when (code) {
        401 -> "Management key rejected or expired. Paste a fresh one."
        403 -> "This key can't do that: it's read-only, or scoped to another workspace."
        404 -> "Not found on this account" + (apiMessage?.let { ": $it" } ?: ".")
        429 -> "Rate limited by OpenRouter. Wait a moment and retry."
        in 500..599 -> "OpenRouter is having trouble (HTTP $code). Try again shortly."
        else -> apiMessage ?: "Request failed (HTTP $code)."
    }

}
