package ir.exchangerate.app.data

import android.content.Context
import ir.exchangerate.app.data.api.AlanchandSource
import ir.exchangerate.app.data.api.BrsApiSource
import ir.exchangerate.app.data.api.DonyaEqtesadSource
import ir.exchangerate.app.data.api.RateSource
import ir.exchangerate.app.data.api.TgjuSource
import ir.exchangerate.app.data.api.util.WebViewScraper
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class SourceRates(
    val source: Source,
    val rates: Map<Currency, Rate>,
    val errors: Map<Currency, Throwable>,
    val lastFetchedAt: Long?,
    val isFresh: Boolean,
    val isFetching: Boolean,
)

sealed interface RatesState {
    data object Loading : RatesState
    data class Loaded(val sources: List<SourceRates>) : RatesState
    data class Error(val cause: Throwable) : RatesState
}

class ExchangeRateRepository(context: Context) {

    private val scraper = WebViewScraper(context.applicationContext)

    val sources: List<RateSource> = listOf(
        TgjuSource(),
        BrsApiSource(),
        AlanchandSource(scraper),
        DonyaEqtesadSource(scraper),
    )

    private val tracked = listOf(Currency.USD, Currency.EUR, Currency.OMR)

    private val _state = MutableStateFlow<RatesState>(RatesState.Loading)
    val state: StateFlow<RatesState> = _state.asStateFlow()

    private val cache: MutableMap<Source, SourceRates> = sources.associate {
        it.source to SourceRates(
            source = it.source,
            rates = emptyMap(),
            errors = emptyMap(),
            lastFetchedAt = null,
            isFresh = false,
            isFetching = true,
        )
    }.toMutableMap()

    private val cacheMutex = Mutex()

    init {
        publishLoaded()
    }

    suspend fun refreshSource(source: RateSource) {
        markFetching(source.source, true)
        val now = System.currentTimeMillis()

        val results = runCatching { source.fetch(tracked) }.getOrElse { error ->
            updateAfterFetch(source.source) { previous ->
                previous.copy(
                    errors = tracked.associateWith { error },
                    isFresh = false,
                    isFetching = false,
                )
            }
            return
        }

        val rates = results.mapNotNull { (k, v) -> v.getOrNull()?.let { k to it } }.toMap()
        val errors = results.filterValues { it.isFailure }
            .mapValues { it.value.exceptionOrNull() ?: RuntimeException("unknown") }

        updateAfterFetch(source.source) { previous ->
            val merged = previous.rates + rates
            previous.copy(
                rates = merged,
                errors = errors,
                lastFetchedAt = if (rates.isNotEmpty()) now else previous.lastFetchedAt,
                isFresh = rates.isNotEmpty(),
                isFetching = false,
            )
        }
    }

    private suspend fun markFetching(source: Source, fetching: Boolean) {
        cacheMutex.withLock {
            cache[source] = (cache[source] ?: return).copy(isFetching = fetching)
        }
        publishLoaded()
    }

    private suspend fun updateAfterFetch(source: Source, block: (SourceRates) -> SourceRates) {
        cacheMutex.withLock {
            val previous = cache[source] ?: return
            cache[source] = block(previous)
        }
        publishLoaded()
    }

    private fun publishLoaded() {
        val ordered = sources.map { cache[it.source]!! }
        _state.update { RatesState.Loaded(ordered) }
    }
}
