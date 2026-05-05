package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.api.util.WebViewScraper
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLEncoder

/**
 * Donya-e-Eqtesad has dedicated tag pages per currency:
 *   /tags/قیمت_دلار   /tags/قیمت_یورو   /tags/قیمت_ریال_عمان
 * Each page lists news article cards whose titles contain the latest price
 * (e.g. "قیمت یورو امروز ۹۵,۰۰۰ تومان شد"). We render the page in WebView,
 * then walk article titles and pull the first realistic toman number out of
 * any title that mentions the target currency.
 */
class DonyaEqtesadSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.DONYA_EQTESAD

    private val keywords = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "دلار", "us dollar", "usd"),
        Currency.EUR to listOf("یورو", "euro", "eur"),
        Currency.OMR to listOf("ریال عمان", "عمان", "omani rial", "omr"),
    )

    private val tagPaths = mapOf(
        Currency.USD to listOf("قیمت_دلار", "قیمت-دلار"),
        Currency.EUR to listOf("قیمت_یورو", "قیمت-یورو"),
        Currency.OMR to listOf("قیمت_ریال_عمان", "ریال_عمان", "قیمت-ریال-عمان"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        val out = mutableMapOf<Currency, Result<Rate>>()

        for (currency in currencies) {
            out[currency] = runCatching {
                val html = loadTagPage(currency)
                    ?: error("صفحه برای این ارز پیدا نشد")
                val priceToman = extractPriceFromTitles(html, currency)
                    ?: error("قیمت در عناوین خبر پیدا نشد · ${preview(html)}")

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

        return out
    }

    private suspend fun loadTagPage(currency: Currency): String? {
        val candidates = tagPaths[currency] ?: return null
        var lastError: Throwable? = null
        for (path in candidates) {
            val url = buildTagUrl(path)
            val attempt = runCatching {
                scraper.fetchRenderedHtml(
                    url = url,
                    readyJsExpression =
                        "document.body && document.body.innerText.match(/[0-9][0-9,٬،]{4,}/) != null",
                    minDelayMs = 1500L,
                    maxWaitAfterLoadMs = 10_000L,
                    timeoutMs = 22_000L,
                )
            }
            val html = attempt.getOrNull()
            if (html != null) return html
            lastError = attempt.exceptionOrNull()
        }
        if (lastError != null) throw lastError
        return null
    }

    private fun buildTagUrl(persianTag: String): String {
        val encoded = URLEncoder.encode(persianTag, "UTF-8").replace("+", "%20")
        return "https://donya-e-eqtesad.com/tags/$encoded"
    }

    private fun extractPriceFromTitles(html: String, currency: Currency): Long? {
        val doc: Document = Jsoup.parse(html)
        val terms = keywords[currency] ?: return null
        val otherTerms = keywords.filterKeys { it != currency }.values.flatten()

        val titleSelectors = listOf(
            "h1", "h2", "h3", "h4",
            "a.title",
            "[class*=\"title\"] a",
            "article h2",
            "article h3",
            ".news-title",
            "a",
        )

        for (selector in titleSelectors) {
            for (el in doc.select(selector)) {
                val text = el.text().trim()
                if (text.isBlank()) continue
                val lower = text.lowercase()
                if (terms.none { lower.contains(it.lowercase()) }) continue
                if (otherTerms.any { lower.contains(it.lowercase()) }) continue
                val price = firstReasonableNumber(text)
                if (price != null) return price
            }
        }
        return null
    }

    private fun firstReasonableNumber(text: String): Long? {
        val regex = Regex("""[0-9۰-۹٠-٩]{1,3}(?:[,،٬]?[0-9۰-۹٠-٩]{3})+""")
        for (match in regex.findAll(text)) {
            val parsed = PriceParser.parseLong(match.value) ?: continue
            if (parsed in 5_000..100_000_000_000L) return parsed
        }
        return null
    }

    private fun preview(html: String): String {
        val text = Jsoup.parse(html).body()?.text().orEmpty()
        return text.take(120).replace("\n", " ")
    }
}
