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
 * Eco Iran (ecoiran.com) — same tag-page structure as Donya-e-Eqtesad:
 *   /fa/tags/قیمت_دلار   /fa/tags/قیمت_یورو   /fa/tags/قیمت_ریال_عمان
 * Each tag page has a price table and/or descriptive paragraphs that quote
 * the latest toman price in the article copy.
 */
class EcoIranSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.ECOIRAN

    private val keywords = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "us dollar", "usd", "دلار"),
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
                val html = loadTagPage(currency) ?: error("صفحه برای این ارز پیدا نشد")
                val priceToman = extractPrice(html, currency)
                    ?: error("قیمت پیدا نشد · ${preview(html)}")

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
                        "document.body && document.body.innerText.match(/[0-9۰-۹٠-٩][0-9۰-۹٠-٩,٬،]{4,}/) != null",
                    minDelayMs = 1500L,
                    maxWaitAfterLoadMs = 12_000L,
                    timeoutMs = 25_000L,
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
        return "https://ecoiran.com/fa/tags/$encoded"
    }

    private fun extractPrice(html: String, currency: Currency): Long? {
        val doc = Jsoup.parse(html)
        val terms = keywords[currency] ?: return null
        val otherTerms = keywords.filterKeys { it != currency }.values.flatten()

        priceFromTable(doc, terms, otherTerms)?.let { return it }
        priceFromParagraphs(doc, terms, otherTerms)?.let { return it }
        priceFromHeadings(doc, terms, otherTerms)?.let { return it }
        return null
    }

    private fun priceFromTable(
        doc: Document,
        terms: List<String>,
        otherTerms: List<String>,
    ): Long? {
        for (row in doc.select("tr")) {
            val cells = row.select("td")
            if (cells.isEmpty()) continue
            val firstText = cells.first()?.text()?.lowercase().orEmpty()
            if (terms.none { firstText.contains(it.lowercase()) }) continue
            if (otherTerms.any { firstText.contains(it.lowercase()) }) continue

            for (i in 1 until cells.size) {
                val price = firstReasonableNumber(cells[i].text())
                if (price != null) return price
            }
            val rowPrice = firstReasonableNumber(row.text())
            if (rowPrice != null) return rowPrice
        }
        return null
    }

    private fun priceFromParagraphs(
        doc: Document,
        terms: List<String>,
        otherTerms: List<String>,
    ): Long? {
        val regex = Regex("""[0-9۰-۹٠-٩]{1,3}(?:[,،٬]?[0-9۰-۹٠-٩]{3})+""")
        for (el in doc.select("p, .description, .summary, .lead")) {
            val text = el.text()
            val lower = text.lowercase()
            if (terms.none { lower.contains(it.lowercase()) }) continue
            if (otherTerms.any { lower.contains(it.lowercase()) }) continue

            val matches = regex.findAll(text).toList()
            for (match in matches.reversed()) {
                val parsed = PriceParser.parseLong(match.value) ?: continue
                if (parsed in 5_000..100_000_000_000L) return parsed
            }
        }
        return null
    }

    private fun priceFromHeadings(
        doc: Document,
        terms: List<String>,
        otherTerms: List<String>,
    ): Long? {
        for (selector in listOf("h1", "h2", "h3", "h4", "a.title", "a")) {
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
        return text.take(150).replace("\n", " ")
    }
}
