package ir.exchangerate.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class DisplayUnit { RIAL, TOMAN }
enum class ThemeMode { SYSTEM, LIGHT, DARK }

class PreferencesStore(private val context: Context) {

    private val keyUnit = stringPreferencesKey("display_unit")
    private val keyInterval = intPreferencesKey("refresh_interval_seconds")
    private val keyVpnMode = booleanPreferencesKey("vpn_mode_enabled")
    private val keyTheme = stringPreferencesKey("theme_mode")

    val displayUnit: Flow<DisplayUnit> = context.dataStore.data.map { prefs ->
        runCatching { DisplayUnit.valueOf(prefs[keyUnit] ?: DisplayUnit.TOMAN.name) }
            .getOrDefault(DisplayUnit.TOMAN)
    }

    val refreshIntervalSeconds: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[keyInterval] ?: 10).coerceIn(5, 120)
    }

    val vpnMode: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[keyVpnMode] ?: false
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        runCatching { ThemeMode.valueOf(prefs[keyTheme] ?: ThemeMode.SYSTEM.name) }
            .getOrDefault(ThemeMode.SYSTEM)
    }

    suspend fun setDisplayUnit(unit: DisplayUnit) {
        context.dataStore.edit { it[keyUnit] = unit.name }
    }

    suspend fun setRefreshIntervalSeconds(seconds: Int) {
        context.dataStore.edit { it[keyInterval] = seconds.coerceIn(5, 120) }
    }

    suspend fun setVpnMode(enabled: Boolean) {
        context.dataStore.edit { it[keyVpnMode] = enabled }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[keyTheme] = mode.name }
    }
}
