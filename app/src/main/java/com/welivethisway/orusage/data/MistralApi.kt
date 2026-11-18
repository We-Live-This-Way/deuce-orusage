package com.welivethisway.orusage.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException

/** A Mistral call that failed in a way worth showing a human. */
class MistralException(val status: Int?, override val message: String) : Exception(message)

/**
 * Thin client for the parts of the Mistral API that are actually documented and reachable.
 *
 * Deliberately no `mint key` / `delete key`: Mistral API keys are created in the console, and the
 * admin endpoints that could manage them are Enterprise-only. A button that always fails is worse
 * than no button.
 */
class MistralApi(
    private val baseUrl: String = "https://api.mistral.ai/v1",
    private val client: OkHttpClient = OpenRouterApi.defaultClient(),
) {

    /** Model inventory. This is also the key check: 401 here means the key itself is wrong. */
    suspend fun models(apiKey: String): List<MistralModel> =
        parseMistralModels(get("models", apiKey))

    /**
     * Organisation spend against its monthly limit.
     *
     * Returns null when the key is valid but has no admin scope - the documented behaviour for
     * every plan below Enterprise. Distinguishing "not allowed" from "broken" matters: one is a
     * normal account state, the other is an error the user should see.
     */
    suspend fun spendLimit(apiKey: String): MistralLimits? = try {
        parseMistralLimits(get("admin/spend-limit", apiKey))
    } catch (e: MistralException) {
        if (e.status == 401 || e.status == 403 || e.status == 404) null else throw e
    }

    private suspend fun get(path: String, apiKey: String): String = withContext(Dispatchers.IO) {
        val url = (baseUrl.trimEnd('/') + "/").toHttpUrl().newBuilder()
            .addPathSegments(path)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer " + apiKey.trim())
            .header("Accept", "application/json")
            .header("User-Agent", "OR-Usage/1.1 (Android; We Live This Way)")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw MistralException(null, "Can't reach api.mistral.ai - check the connection.")
        }

        response.use { r ->
            val text = r.body?.string().orEmpty()
            if (r.isSuccessful) return@withContext text
            throw MistralException(r.code, friendlyError(r.code, parseMistralMessage(text)))
        }
    }

    private fun friendlyError(code: Int, apiMessage: String?): String = when (code) {
        401 -> "Mistral rejected that key. Check it in the console and paste it again."
        403 -> "This key isn't allowed to read organisation limits."
        404 -> "Not available on this API version."
        422 -> apiMessage ?: "Mistral rejected the request as malformed."
        429 -> "Rate limited by Mistral. Wait a moment and retry."
        in 500..599 -> "Mistral is having trouble (HTTP $code). Try again shortly."
        else -> apiMessage ?: "Request failed (HTTP $code)."
    }
}

/**
 * Mistral returns errors as `{"detail": "..."}` (FastAPI) or `{"message": "..."}` (gateway).
 * Both are handled; anything else is truncated so a wall of HTML never lands in a snackbar.
 */
fun parseMistralMessage(body: String?): String? {
    if (body.isNullOrBlank()) return null
    return try {
        val root = JSONObject(body)
        root.optString("detail").takeIf { it.isNotBlank() }
            ?: root.optString("message").takeIf { it.isNotBlank() }
    } catch (e: Exception) {
        body.take(200)
    }
}
