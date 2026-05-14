package ir.exchangerate.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import ir.exchangerate.app.data.model.Deposit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.depositsDataStore by preferencesDataStore(name = "deposits")

class DepositsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val listSerializer = ListSerializer(Deposit.serializer())
    private val key = stringPreferencesKey("deposits_v1")

    val deposits: Flow<List<Deposit>> = context.depositsDataStore.data.map { prefs ->
        decode(prefs[key])
    }

    suspend fun add(deposit: Deposit) {
        context.depositsDataStore.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(listSerializer, current + deposit)
        }
    }

    suspend fun remove(id: String) {
        context.depositsDataStore.edit { prefs ->
            val current = decode(prefs[key])
            prefs[key] = json.encodeToString(listSerializer, current.filter { it.id != id })
        }
    }

    suspend fun clear() {
        context.depositsDataStore.edit { it.remove(key) }
    }

    private fun decode(raw: String?): List<Deposit> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching { json.decodeFromString(listSerializer, raw) }.getOrDefault(emptyList())
    }
}
