package ir.exchangerate.app.data

import ir.exchangerate.app.data.api.TgjuApi
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Rate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface RatesState {
    data object Loading : RatesState
    data class Success(val rates: Map<Currency, Rate>) : RatesState
    data class Partial(val rates: Map<Currency, Rate>, val errors: Map<Currency, Throwable>) : RatesState
    data class Error(val cause: Throwable) : RatesState
}

class ExchangeRateRepository(
    private val api: TgjuApi = TgjuApi(),
) {
    private val tracked = listOf(Currency.USD, Currency.EUR, Currency.OMR)

    private val _state = MutableStateFlow<RatesState>(RatesState.Loading)
    val state: StateFlow<RatesState> = _state.asStateFlow()

    private val cache = mutableMapOf<Currency, Rate>()

    suspend fun refresh() {
        val results = runCatching { api.fetchAll(tracked) }.getOrElse {
            _state.update { current ->
                if (cache.isNotEmpty()) RatesState.Partial(cache.toMap(), mapOf())
                else RatesState.Error(it)
            }
            return
        }

        val successful = results.mapNotNull { (k, v) -> v.getOrNull()?.let { k to it } }.toMap()
        val errors = results.filterValues { it.isFailure }
            .mapValues { it.value.exceptionOrNull() ?: RuntimeException("unknown") }

        cache.putAll(successful)

        _state.update {
            when {
                errors.isEmpty() && successful.isNotEmpty() -> RatesState.Success(cache.toMap())
                successful.isNotEmpty() -> RatesState.Partial(cache.toMap(), errors)
                else -> RatesState.Error(errors.values.first())
            }
        }
    }
}
