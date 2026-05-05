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

class BonbastSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.BONBAST

    private val sellSelectors = mapOf(
        Currency.USD to listOf("#usd1", "[id=\"usd1\"]"),
        Currency.EUR to listOf("#eur1", "[id=\"eur1\"]"),
        Currency.OMR to listOf("#omr1", "[id=\"omr1\"]"),
    )

    private val buySelectors = mapOf(
        Currency.USD to listOf("#usd2", "[id=\"usd2\"]"),
        Currency.EUR to listOf("#eur2", "[id=\"eur2\"]"),
        Currency.OMR to listOf("#omr2", "[id=\"omr2\"]"),
    )

    private val rowKeywords = mapOf(
        Currency.USD to listOf("us dollar", "دلار آمریکا", "دلار", "usd"),
        Currency.EUR to listOf("euro", "یورو", "eur"),
        Currency.OMR to listOf("omani rial", "ریال عمان", "عمان", "omr"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        val html = runCatching {
            scraper.fetchRenderedHtml(
                url = "https://bonbast.com/",
                settleDelayMs = 4000L,
            )
        }.getOrElse { error ->
            return currencies.associateWith { Result.failure(error) }
        }

        val doc = Jsoup.parse(html)

        return currencies.associateWith { currency ->
            runCatching {
                val sell = findFirstNumber(doc, sellSelectors[currency].orEmpty())
                    ?: findInTableRow(doc, currency, columnIndex = 1)
                    ?: error("sell not found")
                val buy = findFirstNumber(doc, buySelectors[currency].orEmpty())
                    ?: findInTableRow(doc, currency, columnIndex = 2)

                Rate(
                    source = source,
                    currency = currency,
                    priceRial = sell * 10,
                    highRial = buy?.let { it * 10 },
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

    private fun findFirstNumber(doc: Document, selectors: List<String>): Long? {
        for (sel in selectors) {
            val text = doc.selectFirst(sel)?.text() ?: continue
            val parsed = PriceParser.parseLong(text)
            if (parsed != null && parsed > 0) return parsed
        }
        return null
    }

    private fun findInTableRow(doc: Document, currency: Currency, columnIndex: Int): Long? {
        val keywords = rowKeywords[currency] ?: return null
        for (row in doc.select("tr")) {
            val rowText = row.text().lowercase()
            if (keywords.any { rowText.contains(it.lowercase()) }) {
                val cells = row.select("td, th")
                if (cells.size > columnIndex) {
                    val parsed = PriceParser.parseLong(cells[columnIndex].text())
                    if (parsed != null && parsed > 0) return parsed
                }
                for (cell in cells) {
                    val parsed = PriceParser.parseLong(cell.text())
                    if (parsed != null && parsed > 1000) return parsed
                }
            }
        }
        return null
    }
}
