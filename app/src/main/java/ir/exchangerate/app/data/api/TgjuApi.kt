package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.Request
import org.jsoup.Jsoup

class TgjuApi(
    private val client: okhttp3.OkHttpClient = HttpClient.instance,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun fetchAll(currencies: List<Currency>): Map<Currency, Result<Rate>> =
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
            val parsed = json.parseToJsonElement(body).let { it as JsonObject }
            val current = parsed["current"] as? JsonObject ?: error("missing current")

            val now = System.currentTimeMillis()
            return currencies.associateWith { currency ->
                runCatching {
                    val item = current[currency.tgjuKey] ?: error("missing ${currency.tgjuKey}")
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
                val request = Request.Builder()
                    .url("https://www.tgju.org/profile/${currency.tgjuKey}")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val html = response.body?.string().orEmpty()
                    val doc = Jsoup.parse(html)

                    val priceText = doc.selectFirst("[data-price=\"current\"]")?.text()
                        ?: doc.selectFirst("span.price")?.text()
                        ?: doc.selectFirst(".text-muted-2")?.text()
                        ?: error("price not found")

                    val high = doc.selectFirst("[data-col=\"info.last_max\"]")?.text()
                    val low = doc.selectFirst("[data-col=\"info.last_min\"]")?.text()
                    val time = doc.selectFirst(".inline.update")?.text()

                    Rate(
                        currency = currency,
                        priceRial = parseLong(priceText) ?: error("bad price"),
                        highRial = parseLong(high),
                        lowRial = parseLong(low),
                        changeRial = null,
                        changePercent = null,
                        direction = Direction.FLAT,
                        sourceTimeText = time,
                        fetchedAt = now,
                    )
                }
            }
        }
    }

    private fun dtoToRate(currency: Currency, dto: TgjuItem, now: Long): Rate = Rate(
        currency = currency,
        priceRial = parseLong(dto.price) ?: error("price missing"),
        highRial = parseLong(dto.high),
        lowRial = parseLong(dto.low),
        changeRial = parseLong(dto.change),
        changePercent = dto.changePercent,
        direction = when (dto.direction?.lowercase()) {
            "high", "up" -> Direction.UP
            "low", "down" -> Direction.DOWN
            else -> Direction.FLAT
        },
        sourceTimeText = dto.time,
        fetchedAt = now,
    )

    private fun parseLong(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.trim()
            .replace(" ", "")
            .replace(",", "")
            .replace("،", "")
            .replace("٬", "")
            .let(::toEnglishDigits)
        return cleaned.toDoubleOrNull()?.toLong()
    }

    private fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(when (c) {
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                else -> c
            })
        }
        return sb.toString()
    }
}
