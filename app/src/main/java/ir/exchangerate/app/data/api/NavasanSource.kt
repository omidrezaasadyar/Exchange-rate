package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.api.util.WebViewScraper
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

class NavasanSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.NAVASAN

    private val keywords = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "دلار", "us dollar", "usd"),
        Currency.EUR to listOf("یورو", "euro", "eur"),
        Currency.OMR to listOf("ریال عمان", "عمان", "omani rial", "omr"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        val html = runCatching {
            scraper.fetchRenderedHtml(
                url = "https://navasan.tech/",
                settleDelayMs = 4500L,
            )
        }.getOrElse { error ->
            return currencies.associateWith { Result.failure(error) }
        }

        val doc = Jsoup.parse(html)

        return currencies.associateWith { currency ->
            runCatching {
                val priceToman = findPrice(doc, currency) ?: error("not found")
                Rate(
                    source = source,
                    currency = currency,
                    priceRial = priceToman * 10,
                    highRial = null,
                    lowRial = null,
                    changeRial = null,
                    changePercent = null,
                    direction = Direction.FLAT,
                    sourceTimeText = null,
                    fetchedAt = now,
                )
            }
        }
    }

    private fun findPrice(doc: Document, currency: Currency): Long? {
        val terms = keywords[currency] ?: return null

        for (row in doc.select("tr, .row, .item, li, [class*=\"currency\"], [class*=\"price\"]")) {
            val rowText = row.text().lowercase()
            if (terms.none { rowText.contains(it.lowercase()) }) continue
            val price = extractLargeNumber(row)
            if (price != null) return price
        }

        for (label in doc.select("h1, h2, h3, h4, h5, span, div, td, p, a, strong")) {
            val txt = label.text().lowercase()
            if (terms.none { txt.contains(it.lowercase()) }) continue
            val container = label.parent() ?: continue
            val price = extractLargeNumber(container) ?: extractLargeNumber(container.parent() ?: continue)
            if (price != null) return price
        }
        return null
    }

    private fun extractLargeNumber(scope: Element): Long? {
        var best: Long? = null
        for (node in scope.select("*")) {
            val text = node.ownText().ifBlank { node.text() }
            val parsed = PriceParser.parseLong(text) ?: continue
            if (parsed > 1000 && (best == null || parsed > best!!)) best = parsed
        }
        if (best != null) return best
        return PriceParser.parseLong(scope.text())?.takeIf { it > 1000 }
    }
}
