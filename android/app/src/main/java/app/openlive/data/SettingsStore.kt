package app.openlive.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "openlive")

object SettingsStore {
    private val BASE_URL = stringPreferencesKey("base_url")
    private val ACTIVATION_CODE = stringPreferencesKey("activation_code")
    private val ACTIVATED = booleanPreferencesKey("activated")
    private val FAVOURITES = stringSetPreferencesKey("favourites")

    fun baseUrl(context: Context): Flow<String> =
        context.dataStore.data.map { it[BASE_URL] ?: DEFAULT_BASE_URL }

    fun activationCode(context: Context): Flow<String> =
        context.dataStore.data.map { it[ACTIVATION_CODE] ?: "" }

    fun activated(context: Context): Flow<Boolean> =
        context.dataStore.data.map { it[ACTIVATED] ?: false }

    fun favourites(context: Context): Flow<Set<String>> =
        context.dataStore.data.map { it[FAVOURITES] ?: emptySet() }

    suspend fun saveBaseUrl(context: Context, url: String) {
        context.dataStore.edit { it[BASE_URL] = url.trim() }
    }

    suspend fun saveActivation(context: Context, code: String, ok: Boolean) {
        context.dataStore.edit {
            it[ACTIVATION_CODE] = code.trim()
            it[ACTIVATED] = ok
        }
    }

    suspend fun toggleFavourite(context: Context, id: String, fav: Boolean) {
        context.dataStore.edit {
            val current = (it[FAVOURITES] ?: emptySet()).toMutableSet()
            if (fav) current.add(id) else current.remove(id)
            it[FAVOURITES] = current
        }
    }
}
