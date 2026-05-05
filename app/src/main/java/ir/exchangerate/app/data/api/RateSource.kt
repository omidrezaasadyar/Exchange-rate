package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source

interface RateSource {
    val source: Source
    suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>>
}
