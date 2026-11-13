package com.spendly.tracker.connector.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.spendly.tracker.connector.domain.AiProvider
import com.spendly.tracker.connector.domain.AnalysisWindow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.connectorDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "connector_preferences"
)

/**
 * Configuration for the AI connector: the master toggle, the selected provider and
 * model, and the analysis window the user last used.
 */
@Singleton
class ConnectorPreferencesRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val ENABLED = booleanPreferencesKey("connector_enabled")
        val PROVIDER = stringPreferencesKey("connector_provider")
        val MODEL = stringPreferencesKey("connector_model")
        val WINDOW_UNIT = stringPreferencesKey("connector_window_unit")
        val WINDOW_COUNT = intPreferencesKey("connector_window_count")
    }

    val isEnabled: Flow<Boolean> = context.connectorDataStore.data
        .map { it[Keys.ENABLED] ?: false }

    suspend fun isEnabledOnce(): Boolean = isEnabled.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.connectorDataStore.edit { it[Keys.ENABLED] = enabled }
    }

    val provider: Flow<AiProvider> = context.connectorDataStore.data
        .map { AiProvider.fromId(it[Keys.PROVIDER]) }

    suspend fun setProvider(provider: AiProvider) {
        context.connectorDataStore.edit {
            it[Keys.PROVIDER] = provider.id
            // Switching provider invalidates the previously picked model.
            it[Keys.MODEL] = provider.defaultModel
        }
    }

    /** The configured model, falling back to the provider default. */
    val model: Flow<String> = context.connectorDataStore.data
        .map { prefs ->
            val provider = AiProvider.fromId(prefs[Keys.PROVIDER])
            prefs[Keys.MODEL]?.takeIf { it.isNotBlank() } ?: provider.defaultModel
        }

    suspend fun setModel(model: String) {
        context.connectorDataStore.edit { it[Keys.MODEL] = model.trim() }
    }

    val window: Flow<AnalysisWindow> = context.connectorDataStore.data
        .map { prefs ->
            AnalysisWindow.fromStored(prefs[Keys.WINDOW_UNIT], prefs[Keys.WINDOW_COUNT])
        }

    suspend fun setWindow(window: AnalysisWindow) {
        context.connectorDataStore.edit {
            it[Keys.WINDOW_UNIT] = window.unit.name
            it[Keys.WINDOW_COUNT] = window.count
        }
    }
}
