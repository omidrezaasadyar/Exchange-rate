package ir.exchangerate.app.data

import ir.exchangerate.app.data.api.AlanchandSource
import ir.exchangerate.app.data.api.BonbastSource
import ir.exchangerate.app.data.api.RateSource
import ir.exchangerate.app.data.api.TgjuSource
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SourceRates(
    val source: Source,
    val rates: Map<Currency, Rate>,
    val errors: Map<Currency, Throwable>,
    val lastFetchedAt: Long?,
    val isFresh: Boolean,
)

sealed interface RatesState {
    data object Loading : RatesState
    data class Loaded(val sources: List<SourceRates>) : RatesState
    data class Error(val cause: Throwable) : RatesState
}

class ExchangeRateRepository(
    private val sources: List<RateSource> = listOf(
        TgjuSource(),
        BonbastSource(),
        AlanchandSource(),
    ),
) {
    private val tracked = listOf(Currency.USD, Currency.EUR, Currency.OMR)

    private val _state = MutableStateFlow<RatesState>(RatesState.Loading)
    val state: StateFlow<RatesState> = _state.asStateFlow()

    private val cache: MutableMap<Source, SourceRates> = mutableMapOf()

    suspend fun refresh() = coroutineScope {
        val deferred = sources.map { src ->
            async {
                val now = System.currentTimeMillis()
                val results = runCatching { src.fetch(tracked) }.getOrElse { e ->
                    return@async src.source to SourceRates(
                        source = src.source,
                        rates = cache[src.source]?.rates.orEmpty(),
                        errors = tracked.associateWith { e },
                        lastFetchedAt = cache[src.source]?.lastFetchedAt,
                        isFresh = false,
                    )
                }

                val rates = results.mapNotNull { (k, v) -> v.getOrNull()?.let { k to it } }.toMap()
                val errors = results.filterValues { it.isFailure }
                    .mapValues { it.value.exceptionOrNull() ?: RuntimeException("unknown") }

                val merged = (cache[src.source]?.rates.orEmpty() + rates)
                src.source to SourceRates(
                    source = src.source,
                    rates = merged,
                    errors = errors,
                    lastFetchedAt = if (rates.isNotEmpty()) now else cache[src.source]?.lastFetchedAt,
                    isFresh = rates.isNotEmpty(),
                )
            }
        }

        deferred.awaitAll().forEach { (src, sourceRates) -> cache[src] = sourceRates }

        val ordered = sources.map { cache[it.source] ?: SourceRates(it.source, emptyMap(), emptyMap(), null, false) }
        _state.value = if (ordered.all { it.rates.isEmpty() }) {
            RatesState.Error(RuntimeException("هیچ منبعی پاسخ نداد"))
        } else {
            RatesState.Loaded(ordered)
        }
    }
}

