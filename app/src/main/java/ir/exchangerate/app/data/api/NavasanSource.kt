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

    private val candidateUrls = listOf(
        "https://navasan.tech/",
        "https://www.navasan.tech/",
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
                        "document.body && (document.body.innerText.includes('دلار') || document.body.innerText.includes('یورو'))",
                    minDelayMs = 2500L,
                    maxWaitAfterLoadMs = 15_000L,
                    timeoutMs = 30_000L,
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

        for (row in doc.select("tr, li, .row, [class*=\"row\"], [class*=\"item\"], [class*=\"currency\"], [class*=\"card\"], [class*=\"price\"]")) {
            val rowText = row.text().lowercase()
            if (terms.none { rowText.contains(it.lowercase()) }) continue
            val price = extractLargeNumber(row)
            if (price != null) return price
        }

        for (label in doc.select("h1, h2, h3, h4, h5, span, div, td, p, a, strong, b")) {
            val txt = label.text().lowercase()
            if (terms.none { txt.contains(it.lowercase()) }) continue
            var ancestor: Element? = label.parent()
            var depth = 0
            while (ancestor != null && depth < 3) {
                val price = extractLargeNumber(ancestor)
                if (price != null) return price
                ancestor = ancestor.parent()
                depth++
            }
        }
        return null
    }

    private fun extractLargeNumber(scope: Element): Long? {
        var best: Long? = null
        for (node in scope.select("*")) {
            val text = node.ownText()
            if (text.isBlank()) continue
            val parsed = PriceParser.parseLong(text) ?: continue
            if (parsed > 5000 && parsed < 100_000_000_000L) {
                if (best == null || parsed > best!!) best = parsed
            }
        }
        if (best != null) return best
        return PriceParser.parseLong(scope.text())?.takeIf { it in 5000..100_000_000_000L }
    }

    private fun preview(html: String): String {
        val text = Jsoup.parse(html).body()?.text().orEmpty()
        return text.take(120).replace("\n", " ")
    }
}
