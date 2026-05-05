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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TickState(
    val nowMillis: Long = System.currentTimeMillis(),
    val isRefreshing: Boolean = false,
)

class RatesViewModel(
    private val repository: ExchangeRateRepository,
    private val preferences: PreferencesStore,
) : ViewModel() {

    val state: StateFlow<RatesState> = repository.state

    val displayUnit: StateFlow<DisplayUnit> = preferences.displayUnit.stateIn(
        viewModelScope, SharingStarted.Eagerly, DisplayUnit.TOMAN,
    )

    val refreshIntervalSeconds: StateFlow<Int> = preferences.refreshIntervalSeconds.stateIn(
        viewModelScope, SharingStarted.Eagerly, 10,
    )

    private val _tick = MutableStateFlow(TickState())
    val tick: StateFlow<TickState> = _tick.asStateFlow()

    private var pollingJob: Job? = null
    private var clockJob: Job? = null

    init {
        startClock()
    }

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (true) {
                _tick.value = _tick.value.copy(isRefreshing = true)
                repository.refresh()
                _tick.value = _tick.value.copy(
                    isRefreshing = false,
                    nowMillis = System.currentTimeMillis(),
                )
                val seconds = refreshIntervalSeconds.value.coerceAtLeast(5)
                delay(seconds * 1000L)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun manualRefresh() {
        viewModelScope.launch {
            _tick.value = _tick.value.copy(isRefreshing = true)
            repository.refresh()
            _tick.value = _tick.value.copy(
                isRefreshing = false,
                nowMillis = System.currentTimeMillis(),
            )
        }
    }

    fun setDisplayUnit(unit: DisplayUnit) {
        viewModelScope.launch { preferences.setDisplayUnit(unit) }
    }

    fun setRefreshInterval(seconds: Int) {
        viewModelScope.launch { preferences.setRefreshIntervalSeconds(seconds) }
    }

    private fun startClock() {
        clockJob = viewModelScope.launch {
            while (true) {
                _tick.value = _tick.value.copy(nowMillis = System.currentTimeMillis())
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        pollingJob?.cancel()
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
