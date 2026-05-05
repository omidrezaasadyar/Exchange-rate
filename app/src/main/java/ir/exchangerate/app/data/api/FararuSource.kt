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

class FararuSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.FARARU

    private val keywords = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "us dollar", "usd"),
        Currency.EUR to listOf("یورو", "euro", "eur"),
        Currency.OMR to listOf("ریال عمان", "عمان", "omani rial", "omr"),
    )

    private val candidateUrls = listOf(
        "https://fararu.com/fa/markets/currency",
        "https://fararu.com/fa/economy",
        "https://fararu.com/fa/news/markets/currency",
        "https://fararu.com/",
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()

        var html: String? = null
        var lastError: Throwable? = null
        for (url in candidateUrls) {
            val attempt = runCatching {
                scraper.fetchRenderedHtml(
                    url = url,
                    readyJsExpression =
                        "document.body && document.body.innerText.includes('دلار') && document.body.innerText.match(/[0-9][0-9,]{4,}/)",
                    minDelayMs = 2000L,
                    maxWaitAfterLoadMs = 12_000L,
                    timeoutMs = 25_000L,
                )
            }
            if (attempt.isSuccess) {
                html = attempt.getOrNull()
                break
            } else {
                lastError = attempt.exceptionOrNull()
            }
        }

        if (html == null) {
            val err = lastError ?: RuntimeException("ناموفق")
            return currencies.associateWith { Result.failure(err) }
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
            "[class*=\"market\"]",
            "[class*=\"currency\"]",
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
