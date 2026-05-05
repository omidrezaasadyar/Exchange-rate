package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class TgjuSource(
    private val client: OkHttpClient = HttpClient.instance,
) : RateSource {

    override val source: Source = Source.TGJU

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val keyOf = mapOf(
        Currency.USD to "price_dollar_rl",
        Currency.EUR to "price_eur",
        Currency.OMR to "price_omr",
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> =
        withContext(Dispatchers.IO) {
            runCatching { fetchFromJson(currencies) }
                .getOrElse { fetchFromHtml(currencies) }
        }

    private fun fetchFromJson(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val request = Request.Builder()
            .url("https://call3.tgju.org/ajax.json")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val parsed = json.parseToJsonElement(body) as JsonObject
            val current = parsed["current"] as? JsonObject ?: error("missing current")

            val now = System.currentTimeMillis()
            return currencies.associateWith { currency ->
                runCatching {
                    val key = keyOf[currency] ?: error("unmapped currency")
                    val item = current[key] ?: error("missing $key")
                    val dto = json.decodeFromJsonElement(TgjuItem.serializer(), item)
                    dtoToRate(currency, dto, now)
                }
            }
        }
    }

    private fun fetchFromHtml(currencies: List<Currency>): Map<Currency, Result<Rate>> {
        val now = System.currentTimeMillis()
        return currencies.associateWith { currency ->
            runCatching {
                val key = keyOf[currency] ?: error("unmapped currency")
                val request = Request.Builder()
                    .url("https://www.tgju.org/profile/$key")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val html = response.body?.string().orEmpty()
                    val doc = Jsoup.parse(html)

                    val priceText = doc.selectFirst("[data-price=\"current\"]")?.text()
                        ?: doc.selectFirst("span.price")?.text()
                        ?: error("price not found")

                    Rate(
                        source = source,
                        currency = currency,
                        priceRial = PriceParser.parseLong(priceText) ?: error("bad price"),
                        highRial = PriceParser.parseLong(doc.selectFirst("[data-col=\"info.last_max\"]")?.text()),
                        lowRial = PriceParser.parseLong(doc.selectFirst("[data-col=\"info.last_min\"]")?.text()),
                        changeRial = null,
                        changePercent = null,
                        direction = Direction.FLAT,
                        sourceTimeText = doc.selectFirst(".inline.update")?.text(),
                        fetchedAt = now,
                    )
                }
            }
        }
    }

    private fun dtoToRate(currency: Currency, dto: TgjuItem, now: Long): Rate = Rate(
        source = source,
        currency = currency,
        priceRial = PriceParser.parseLong(dto.price) ?: error("price missing"),
        highRial = PriceParser.parseLong(dto.high),
        lowRial = PriceParser.parseLong(dto.low),
        changeRial = PriceParser.parseLong(dto.change),
        changePercent = dto.changePercent,
        direction = when (dto.direction?.lowercase()) {
            "high", "up" -> Direction.UP
            "low", "down" -> Direction.DOWN
            else -> Direction.FLAT
        },
        sourceTimeText = dto.time,
        fetchedAt = now,
    )

    @Serializable
    private data class TgjuItem(
        @SerialName("p") val price: String? = null,
        @SerialName("h") val high: String? = null,
        @SerialName("l") val low: String? = null,
        @SerialName("d") val change: String? = null,
        @SerialName("dp") val changePercent: Double? = null,
        @SerialName("dt") val direction: String? = null,
        @SerialName("t") val time: String? = null,
    )
}
