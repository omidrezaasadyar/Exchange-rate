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

class AlanchandSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.ALANCHAND

    private val keywords = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "us dollar", "usd"),
        Currency.EUR to listOf("یورو", "euro", "eur"),
        Currency.OMR to listOf("ریال عمان", "عمان", "omani rial", "omr"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        val html = runCatching {
            scraper.fetchRenderedHtml(
                url = "https://alanchand.com/",
                readyJsExpression =
                    "document.body && (document.body.innerText.includes('دلار') && document.body.innerText.match(/[0-9][0-9,]{4,}/))",
                minDelayMs = 2500L,
                maxWaitAfterLoadMs = 15_000L,
                timeoutMs = 30_000L,
            )
        }.getOrElse { error ->
            return currencies.associateWith { Result.failure(error) }
        }

        val doc = Jsoup.parse(html)
        val htmlPreview = preview(html)

        return currencies.associateWith { currency ->
            runCatching {
                val priceToman = findPrice(doc, currency)
                    ?: error("قیمت پیدا نشد · ${htmlPreview}")
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
        val otherTerms = keywords.filterKeys { it != currency }.values.flatten()

        val rowSelectors = listOf(
            "tr",
            "li",
            "[class*=\"row\"]",
            "[class*=\"item\"]",
            "[class*=\"currency\"]",
            "[class*=\"card\"]",
            "[class*=\"price\"]",
        )

        for (selector in rowSelectors) {
            for (row in doc.select(selector)) {
                val rowText = row.text().lowercase()
                if (terms.none { rowText.contains(it.lowercase()) }) continue
                if (otherTerms.any { rowText.contains(it.lowercase()) }) continue
                val price = extractFirstReasonablePrice(row)
                if (price != null) return price
            }
        }
        return null
    }

    private fun extractFirstReasonablePrice(scope: Element): Long? {
        for (node in scope.select("*")) {
            val text = node.ownText()
            if (text.isBlank()) continue
            val parsed = PriceParser.parseLong(text) ?: continue
            if (parsed in 5_000..100_000_000_000L) return parsed
        }
        val whole = PriceParser.parseLong(scope.text())
        return whole?.takeIf { it in 5_000..100_000_000_000L }
    }

    private fun preview(html: String): String {
        val text = Jsoup.parse(html).body()?.text().orEmpty()
        return text.take(120).replace("\n", " ")
    }
}
