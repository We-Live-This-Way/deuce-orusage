package com.welivethisway.orusage.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.welivethisway.orusage.data.ActivityRow
import com.welivethisway.orusage.data.ApiKeyInfo
import com.welivethisway.orusage.data.CreatedKey
import com.welivethisway.orusage.data.Credits
import com.welivethisway.orusage.data.MistralApi
import com.welivethisway.orusage.data.MistralException
import com.welivethisway.orusage.data.MistralLimits
import com.welivethisway.orusage.data.MistralModel
import com.welivethisway.orusage.data.OpenRouterApi
import com.welivethisway.orusage.data.OpenRouterException
import com.welivethisway.orusage.data.SecureStore
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneOffset

data class UiState(
    val managementKey: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val credits: Credits? = null,
    val keys: List<ApiKeyInfo> = emptyList(),
    val activity: List<ActivityRow> = emptyList(),
    val lastUpdated: Instant? = null,
    val banner: String? = null,
    /** Plaintext of a freshly minted key. Held in memory only - never persisted, never logged. */
    val revealedKey: CreatedKey? = null,
    val selectedKeyHash: String? = null,
    val keyActivity: Map<String, List<ActivityRow>> = emptyMap(),
    val keyActivityLoading: Boolean = false,

    // ---------- Mistral ----------
    val mistralKey: String? = null,
    val mistralLoading: Boolean = false,
    val mistralError: String? = null,
    val mistralModels: List<MistralModel> = emptyList(),
    val mistralLimits: MistralLimits? = null,
    /** True once we know whether the key can read admin limits; false while untested. */
    val mistralAdminChecked: Boolean = false,
    val mistralLastUpdated: Instant? = null,
) {
    val isConnected: Boolean get() = managementKey != null
    val isMistralConnected: Boolean get() = mistralKey != null
    val todayUtc: String get() = java.time.LocalDate.now(ZoneOffset.UTC).toString()

    /** Admin scope, as opposed to a valid-but-standard key. Null means "not determined yet". */
    val mistralHasAdmin: Boolean?
        get() = when {
            !isMistralConnected || !mistralAdminChecked -> null
            else -> mistralLimits != null
        }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)
    private val api = OpenRouterApi()
    private val mistral = MistralApi()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        val stored = store.loadManagementKey()
        val storedMistral = store.loadMistralKey()
        if (stored != null || storedMistral != null) {
            _state.update { it.copy(managementKey = stored, mistralKey = storedMistral) }
        }
        if (stored != null) refresh()
        if (storedMistral != null) refreshMistral()
    }

    /**
     * Validates the key against the API before storing it, so a typo never looks like a successful
     * login followed by three broken screens.
     */
    fun connect(candidate: String, onResult: (Boolean) -> Unit) {
        val key = candidate.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(error = "Paste your management API key first.") }
            onResult(false)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val keys = api.listKeys(key)
                store.saveManagementKey(key)
                _state.update {
                    it.copy(managementKey = key, keys = keys, loading = false, error = null)
                }
                onResult(true)
                refresh()
            } catch (e: OpenRouterException) {
                _state.update { it.copy(loading = false, error = e.message) }
                onResult(false)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Something went wrong.") }
                onResult(false)
            }
        }
    }

    /** Pulls keys, credits and 30-day activity concurrently; any failure surfaces one clear message. */
    fun refresh() {
        val key = _state.value.managementKey ?: return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val (keys, credits, activity) = coroutineScope {
                    val k = async { api.listKeys(key) }
                    val c = async { api.credits(key) }
                    val a = async { api.activity(key) }
                    Triple(k.await(), c.await(), a.await())
                }
                _state.update {
                    it.copy(
                        keys = keys,
                        credits = credits,
                        activity = activity,
                        loading = false,
                        error = null,
                        lastUpdated = Instant.now(),
                    )
                }
            } catch (e: OpenRouterException) {
                _state.update { it.copy(loading = false, error = e.message) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Refresh failed.") }
            }
        }
    }

    fun createKey(
        name: String,
        limit: Double?,
        limitReset: String?,
        expiresAt: String?,
        onResult: (Boolean) -> Unit,
    ) {
        val key = _state.value.managementKey ?: return
        val cleanName = name.trim()
        if (cleanName.isEmpty()) {
            _state.update { it.copy(error = "Give the key a name.") }
            onResult(false)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val created = api.createKey(key, cleanName, limit, limitReset, expiresAt)
                _state.update { it.copy(loading = false, revealedKey = created) }
                onResult(true)
                refresh()
            } catch (e: OpenRouterException) {
                _state.update { it.copy(loading = false, error = e.message) }
                onResult(false)
            }
        }
    }

    fun dismissRevealedKey() {
        // Drop the plaintext reference as soon as the user is done with it.
        _state.update { it.copy(revealedKey = null) }
    }

    fun setDisabled(target: ApiKeyInfo, disabled: Boolean) {
        val key = _state.value.managementKey ?: return
        viewModelScope.launch {
            try {
                api.setKeyDisabled(key, target.hash, disabled)
                refresh()
            } catch (e: OpenRouterException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun deleteKey(target: ApiKeyInfo) {
        val key = _state.value.managementKey ?: return
        viewModelScope.launch {
            try {
                api.deleteKey(key, target.hash)
                _state.update { s ->
                    s.copy(banner = "Deleted " + target.displayName)
                }
                refresh()
            } catch (e: OpenRouterException) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    /** Per-key drill-down: activity filtered to one key hash, cached per key for the session. */
    fun loadKeyActivity(hash: String) {
        val key = _state.value.managementKey ?: return
        if (_state.value.keyActivity.containsKey(hash)) return
        viewModelScope.launch {
            _state.update { it.copy(keyActivityLoading = true, selectedKeyHash = hash) }
            try {
                val rows = api.activity(key, apiKeyHash = hash)
                _state.update {
                    it.copy(
                        keyActivity = it.keyActivity + (hash to rows),
                        keyActivityLoading = false,
                    )
                }
            } catch (e: OpenRouterException) {
                _state.update { it.copy(keyActivityLoading = false, error = e.message) }
            }
        }
    }

    fun selectKey(hash: String?) {
        _state.update { it.copy(selectedKeyHash = hash) }
    }

    // ---------- Mistral ----------

    /**
     * Validates a Mistral key by asking for the model list - the cheapest documented call that
     * proves the credential and returns something worth showing.
     */
    fun connectMistral(candidate: String, onResult: (Boolean) -> Unit) {
        val key = candidate.trim()
        if (key.isEmpty()) {
            _state.update { it.copy(mistralError = "Paste your Mistral API key first.") }
            onResult(false)
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(mistralLoading = true, mistralError = null) }
            try {
                val models = mistral.models(key)
                store.saveMistralKey(key)
                _state.update {
                    it.copy(
                        mistralKey = key,
                        mistralModels = models,
                        mistralLoading = false,
                        mistralError = null,
                        mistralLastUpdated = Instant.now(),
                    )
                }
                onResult(true)
                refreshMistral()
            } catch (e: MistralException) {
                _state.update { it.copy(mistralLoading = false, mistralError = e.message) }
                onResult(false)
            } catch (e: Exception) {
                _state.update {
                    it.copy(mistralLoading = false, mistralError = e.message ?: "Something went wrong.")
                }
                onResult(false)
            }
        }
    }

    /**
     * Models and (when permitted) organisation limits, concurrently.
     * A key without admin scope is a documented state, not an error: `spendLimit` returns null and
     * the screen explains where the spend figure lives instead of claiming a failure.
     */
    fun refreshMistral() {
        val key = _state.value.mistralKey ?: return
        viewModelScope.launch {
            _state.update { it.copy(mistralLoading = true, mistralError = null) }
            try {
                val (models, limits) = coroutineScope {
                    val m = async { mistral.models(key) }
                    val l = async { mistral.spendLimit(key) }
                    m.await() to l.await()
                }
                _state.update {
                    it.copy(
                        mistralModels = models,
                        mistralLimits = limits,
                        mistralAdminChecked = true,
                        mistralLoading = false,
                        mistralError = null,
                        mistralLastUpdated = Instant.now(),
                    )
                }
            } catch (e: MistralException) {
                _state.update {
                    it.copy(
                        mistralLoading = false,
                        mistralError = e.message,
                        mistralAdminChecked = true,
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        mistralLoading = false,
                        mistralError = e.message ?: "Refresh failed.",
                        mistralAdminChecked = true,
                    )
                }
            }
        }
    }

    fun disconnectMistral() {
        store.clearMistral()
        _state.update {
            it.copy(
                mistralKey = null,
                mistralModels = emptyList(),
                mistralLimits = null,
                mistralAdminChecked = false,
                mistralError = null,
                mistralLastUpdated = null,
            )
        }
    }

    fun dismissMistralError() {
        _state.update { it.copy(mistralError = null) }
    }

    fun dismissError() {
        _state.update { it.copy(error = null) }
    }

    fun dismissBanner() {
        _state.update { it.copy(banner = null) }
    }

    fun signOut() {
        store.clear()
        _state.update {
            it.copy(
                managementKey = null,
                credits = null,
                keys = emptyList(),
                activity = emptyList(),
                lastUpdated = null,
                revealedKey = null,
                selectedKeyHash = null,
                keyActivity = emptyMap(),
            )
        }
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(app) as T
    }
}
