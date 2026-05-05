package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.api.util.WebViewScraper
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class BonbastSource(
    private val scraper: WebViewScraper,
) : RateSource {

    override val source: Source = Source.BONBAST

    private val sellSelectors = mapOf(
        Currency.USD to listOf("#usd1", "[id=\"usd1\"]", "td#usd1", "span#usd1"),
        Currency.EUR to listOf("#eur1", "[id=\"eur1\"]", "td#eur1", "span#eur1"),
        Currency.OMR to listOf("#omr1", "[id=\"omr1\"]", "td#omr1", "span#omr1"),
    )

    private val rowKeywords = mapOf(
        Currency.USD to listOf("us dollar", "دلار آمریکا", "دلار امریکا", "دلار", "usd"),
        Currency.EUR to listOf("euro", "یورو", "eur"),
        Currency.OMR to listOf("omani rial", "ریال عمان", "عمان", "omr"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        val html = runCatching {
            scraper.fetchRenderedHtml(
                url = "https://bonbast.com/",
                readyJsExpression = "document.querySelector('#usd1') && document.querySelector('#usd1').textContent.replace(/[^0-9]/g,'').length > 3",
                minDelayMs = 1500L,
                maxWaitAfterLoadMs = 12_000L,
                timeoutMs = 25_000L,
            )
        }.getOrElse { error ->
            return currencies.associateWith { Result.failure(error) }
        }

        val doc = Jsoup.parse(html)
        val htmlPreview = preview(html)

        if (looksLikeChallenge(html)) {
            val err = RuntimeException("صفحه CloudFlare/چالش — ${htmlPreview}")
            return currencies.associateWith { Result.failure(err) }
        }

        return currencies.associateWith { currency ->
            runCatching {
                val sell = findFirstNumber(doc, sellSelectors[currency].orEmpty())
                    ?: findInTableRow(doc, currency)
                    ?: error("قیمت پیدا نشد · ${htmlPreview}")

                Rate(
                    source = source,
                    currency = currency,
                    priceRial = sell * 10,
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

    private fun findFirstNumber(doc: Document, selectors: List<String>): Long? {
        for (sel in selectors) {
            val text = doc.selectFirst(sel)?.text() ?: continue
            val parsed = PriceParser.parseLong(text)
            if (parsed != null && parsed > 1000) return parsed
        }
        return null
    }

    private fun findInTableRow(doc: Document, currency: Currency): Long? {
        val keywords = rowKeywords[currency] ?: return null
        for (row in doc.select("tr")) {
            val rowText = row.text().lowercase()
            if (keywords.none { rowText.contains(it.lowercase()) }) continue
            val cells = row.select("td, th")
            for (cell in cells) {
                val parsed = PriceParser.parseLong(cell.text())
                if (parsed != null && parsed > 5000) return parsed
            }
        }
        return null
    }

    private fun looksLikeChallenge(html: String): Boolean {
        val l = html.lowercase()
        return "checking your browser" in l ||
            "cf-browser-verification" in l ||
            "cf-challenge" in l ||
            (l.contains("cloudflare") && l.length < 5000)
    }

    private fun preview(html: String): String {
        val text = Jsoup.parse(html).body()?.text().orEmpty()
        return text.take(120).replace("\n", " ")
    }
}
