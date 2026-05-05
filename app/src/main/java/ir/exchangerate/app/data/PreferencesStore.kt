package ir.exchangerate.app.data

import android.content.Context
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class DisplayUnit { RIAL, TOMAN }

class PreferencesStore(private val context: Context) {

    private val keyUnit = stringPreferencesKey("display_unit")
    private val keyInterval = intPreferencesKey("refresh_interval_seconds")

    val displayUnit: Flow<DisplayUnit> = context.dataStore.data.map { prefs ->
        runCatching { DisplayUnit.valueOf(prefs[keyUnit] ?: DisplayUnit.TOMAN.name) }
            .getOrDefault(DisplayUnit.TOMAN)
    }

    val refreshIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[keyInterval] ?: 10).coerceIn(5, 120)
    }

    suspend fun setDisplayUnit(unit: DisplayUnit) {
        context.dataStore.edit { it[keyUnit] = unit.name }
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[keyInterval] = seconds.coerceIn(5, 120) }
    }
}
