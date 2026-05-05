package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class AlanchandSource(
    private val client: OkHttpClient = HttpClient.instance,
) : RateSource {

    override val source: Source = Source.ALANCHAND

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val slugOf = mapOf(
        Currency.USD to listOf("usd", "dollar", "دلار"),
        Currency.EUR to listOf("eur", "euro", "یورو"),
        Currency.OMR to listOf("omr", "ریال عمان", "عمان"),
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val priceMap = runCatching { fetchFromApi() }
                .getOrElse {
                    runCatching { scrapeHtml() }
                        .getOrElse { error ->
                            return@withContext currencies.associateWith { Result.failure(error) }
                        }
                }

            currencies.associateWith { currency ->
                runCatching {
                    val price = findForCurrency(priceMap, currency) ?: error("not found")
                    Rate(
                        source = source,
                        currency = currency,
                        priceRial = price.priceToman * 10,
                        highRial = null,
                        lowRial = null,
                        changeRial = null,
                        changePercent = price.changePercent,
                        direction = when {
                            price.changePercent == null -> Direction.FLAT
                            price.changePercent > 0 -> Direction.UP
                            price.changePercent < 0 -> Direction.DOWN
                            else -> Direction.FLAT
                        },
                        sourceTimeText = price.timeText,
                        fetchedAt = now,
                    )
                }
            }
        }

    private fun findForCurrency(map: Map<String, ParsedPrice>, currency: Currency): ParsedPrice? {
        val candidates = slugOf[currency] ?: return null
        for (key in candidates) {
            map[key]?.let { return it }
            val match = map.entries.firstOrNull { it.key.contains(key, ignoreCase = true) }
            if (match != null) return match.value
        }
        return null
    }

    private fun fetchFromApi(): Map<String, ParsedPrice> {
        val request = Request.Builder()
            .url("https://alanchand.com/api/arz")
            .header("Accept", "application/json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val root = json.parseToJsonElement(body)

            val items: JsonArray = when (root) {
                is JsonArray -> root
                is JsonObject -> {
                    val data = root["data"] ?: root["arz"] ?: error("no data array")
                    if (data is JsonArray) data else error("data is not array")
                }
                else -> error("unexpected root")
            }

            val out = mutableMapOf<String, ParsedPrice>()
            for (el in items) {
                val obj = el as? JsonObject ?: continue
                val slug = (obj["slug"] ?: obj["name"] ?: obj["title"])
                    ?.let { (it as? JsonPrimitive)?.contentOrNull }
                    ?: continue
                val priceText = listOf("price", "sell", "value", "rate")
                    .firstNotNullOfOrNull { obj[it]?.extractFirstPrice() }
                    ?: continue
                val priceLong = PriceParser.parseLong(priceText) ?: continue
                val change = (obj["change"] as? JsonPrimitive)?.contentOrNull
                    ?.let { it.toDoubleOrNull() }
                val timeText = (obj["time"] as? JsonPrimitive)?.contentOrNull
                out[slug.lowercase()] = ParsedPrice(priceLong, change, timeText)
            }
            if (out.isEmpty()) error("empty parse")
            return out
        }
    }

    private fun scrapeHtml(): Map<String, ParsedPrice> {
        val request = Request.Builder()
            .url("https://alanchand.com/currencies-price")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val out = mutableMapOf<String, ParsedPrice>()

            for (row in doc.select("tr, .currency-row, [data-slug]")) {
                val name = row.selectFirst("[data-name], .name, td:first-child")?.text() ?: continue
                val priceText = row.selectFirst("[data-price], .price, td:nth-child(2)")?.text() ?: continue
                val price = PriceParser.parseLong(priceText) ?: continue
                out[name.trim().lowercase()] = ParsedPrice(price, null, null)
            }
            if (out.isEmpty()) error("scrape empty")
            return out
        }
    }

    private fun kotlinx.serialization.json.JsonElement.extractFirstPrice(): String? = when (this) {
        is JsonPrimitive -> contentOrNull
        is JsonArray -> firstOrNull()?.let {
            when (it) {
                is JsonObject -> (it["price"] ?: it["sell"] ?: it["value"])
                    ?.let { e -> (e as? JsonPrimitive)?.contentOrNull }
                is JsonPrimitive -> it.contentOrNull
                else -> null
            }
        }
        is JsonObject -> (this["price"] ?: this["sell"] ?: this["value"])
            ?.let { (it as? JsonPrimitive)?.contentOrNull }
    }

    private data class ParsedPrice(
        val priceToman: Long,
        val changePercent: Double?,
        val timeText: String?,
    )
}
