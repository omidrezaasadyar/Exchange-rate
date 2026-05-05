package ir.exchangerate.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ir.exchangerate.app.ExchangeApp
import ir.exchangerate.app.data.DisplayUnit
import ir.exchangerate.app.data.ExchangeRateRepository
import ir.exchangerate.app.data.PreferencesStore
import ir.exchangerate.app.data.RatesState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TickState(
    val nowMillis: Long = System.currentTimeMillis(),
)

class RatesViewModel(
    private val repository: ExchangeRateRepository,
    private val preferences: PreferencesStore,
) : ViewModel() {

    val displayUnit: StateFlow<DisplayUnit> = preferences.displayUnit.stateIn(
        viewModelScope, SharingStarted.Eagerly, DisplayUnit.TOMAN,
    )

    val refreshIntervalSeconds: StateFlow<Int> = preferences.refreshIntervalSeconds.stateIn(
        viewModelScope, SharingStarted.Eagerly, 10,
    )

    val vpnMode: StateFlow<Boolean> = preferences.vpnMode.stateIn(
        viewModelScope, SharingStarted.Eagerly, false,
    )

    val state: StateFlow<RatesState> = combine(repository.state, vpnMode) { state, vpn ->
        when (state) {
            is RatesState.Loaded -> RatesState.Loaded(
                sources = state.sources.filter { !it.source.requiresVpn || vpn },
            )
            else -> state
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RatesState.Loading)

    private val _tick = MutableStateFlow(TickState())
    val tick: StateFlow<TickState> = _tick.asStateFlow()

    private val pollingJobs = mutableMapOf<String, Job>()
    private var clockJob: Job? = null

    init {
        startClock()
        viewModelScope.launch {
            var previousVpn = vpnMode.value
            vpnMode.collect { current ->
                if (current && !previousVpn) {
                    repository.sources
                        .filter { it.source.requiresVpn }
                        .forEach { src -> launch { repository.refreshSource(src) } }
                }
                previousVpn = current
            }
        }
    }

    fun startPolling() {
        repository.sources.forEach { source ->
            val key = source.source.name
            if (pollingJobs[key]?.isActive == true) return@forEach
            pollingJobs[key] = viewModelScope.launch {
                while (true) {
                    val active = !source.source.requiresVpn || vpnMode.value
                    if (active) {
                        repository.refreshSource(source)
                    }
                    val baseSeconds = refreshIntervalSeconds.value.coerceAtLeast(5)
                    val sourceSeconds = source.source.defaultIntervalSeconds
                    val effective = maxOf(baseSeconds, sourceSeconds)
                    delay(effective * 1000L)
                }
            }
        }
    }

    fun stopPolling() {
        pollingJobs.values.forEach { it.cancel() }
        pollingJobs.clear()
    }

    fun manualRefresh() {
        repository.sources.forEach { source ->
            val active = !source.source.requiresVpn || vpnMode.value
            if (active) {
                viewModelScope.launch { repository.refreshSource(source) }
            }
        }
    }

    fun setDisplayUnit(unit: DisplayUnit) {
        viewModelScope.launch { preferences.setDisplayUnit(unit) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { preferences.setRefreshIntervalSeconds(seconds) }
    }

    fun setVpnMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setVpnMode(enabled) }
    }

    private fun startClock() {
        clockJob = viewModelScope.launch {
            while (true) {
                _tick.value = TickState(System.currentTimeMillis())
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJobs.values.forEach { it.cancel() }
        clockJob?.cancel()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as ExchangeApp
                RatesViewModel(app.repository, app.preferences)
            }
        }
    }
}
