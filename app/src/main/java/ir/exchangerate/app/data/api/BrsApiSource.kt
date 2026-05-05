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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * BRS API — a free public Iranian aggregator that returns USD/EUR/OMR rates
 * in toman as a JSON document. The "currency" array contains an entry per
 * currency with at minimum {name, price, change}.
 */
class BrsApiSource(
    private val client: OkHttpClient = HttpClient.instance,
) : RateSource {

    override val source: Source = Source.BRSAPI

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val nameMatchers = mapOf(
        Currency.USD to listOf("دلار آمریکا", "دلار امریکا", "دلار", "us dollar", "usd"),
        Currency.EUR to listOf("یورو", "euro", "eur"),
        Currency.OMR to listOf("ریال عمان", "عمان", "omani rial", "omr"),
    )

    private val candidateUrls = listOf(
        "https://brsapi.ir/Api/Market/Gold_Currency.php",
        "https://brsapi.ir/Api/Market/Gold_Currency-v2.php",
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val items = runCatching { loadCurrencyArray() }.getOrElse { error ->
                return@withContext currencies.associateWith { Result.failure(error) }
            }

            currencies.associateWith { currency ->
                runCatching {
                    val item = findItem(items, currency)
                        ?: error("ارز در پاسخ پیدا نشد")
                    val priceToman = readToman(item)
                        ?: error("قیمت در پاسخ پیدا نشد")

                    val change = readChangeAmount(item)
                    val direction = when {
                        change == null -> Direction.FLAT
                        change > 0 -> Direction.UP
                        change < 0 -> Direction.DOWN
                        else -> Direction.FLAT
                    }

                    Rate(
                        source = source,
                        currency = currency,
                        priceRial = priceToman * 10,
                        highRial = null,
                        lowRial = null,
                        changeRial = change?.let { it * 10 },
                        changePercent = readChangePercent(item),
                        direction = direction,
                        sourceTimeText = readTime(item),
                        fetchedAt = now,
                    )
                }
            }
        }

    private fun loadCurrencyArray(): JsonArray {
        var lastError: Throwable = RuntimeException("no url tried")
        for (url in candidateUrls) {
            val attempt = runCatching {
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body?.string().orEmpty()
                    extractCurrencyArray(body)
                }
            }
            if (attempt.isSuccess) return attempt.getOrThrow()
            lastError = attempt.exceptionOrNull() ?: lastError
        }
        throw lastError
    }

    private fun extractCurrencyArray(body: String): JsonArray {
        val root = json.parseToJsonElement(body)
        if (root is JsonArray) return root
        if (root is JsonObject) {
            for (key in listOf("currency", "currencies", "arz", "data")) {
                val value = root[key]
                if (value is JsonArray) return value
            }
            for ((_, value) in root) {
                if (value is JsonArray && value.isNotEmpty()) return value
            }
        }
        error("ساختار JSON ناشناخته")
    }

    private fun findItem(items: JsonArray, currency: Currency): JsonObject? {
        val matchers = nameMatchers[currency] ?: return null
        for (el in items) {
            val obj = el as? JsonObject ?: continue
            val name = readString(obj, "name") ?: readString(obj, "title") ?: readString(obj, "fa") ?: continue
            val lower = name.lowercase()
            if (matchers.any { lower.contains(it.lowercase()) }) return obj
        }
        return null
    }

    private fun readToman(obj: JsonObject): Long? {
        val raw = readString(obj, "price")
            ?: readString(obj, "value")
            ?: readString(obj, "sell")
            ?: return null
        return PriceParser.parseLong(raw)
    }

    private fun readChangeAmount(obj: JsonObject): Long? {
        val raw = readString(obj, "change") ?: readString(obj, "diff") ?: return null
        val parsed = PriceParser.parseLong(raw.trimStart('+', '-')) ?: return null
        return if (raw.trim().startsWith("-")) -parsed else parsed
    }

    private fun readChangePercent(obj: JsonObject): Double? {
        val raw = readString(obj, "change_percent")
            ?: readString(obj, "changePercent")
            ?: readString(obj, "percent")
            ?: return null
        return raw.trim().removeSuffix("%").toDoubleOrNull()
    }

    private fun readTime(obj: JsonObject): String? =
        readString(obj, "time") ?: readString(obj, "updated") ?: readString(obj, "date")

    private fun readString(obj: JsonObject, key: String): String? {
        val el: JsonElement = obj[key] ?: return null
        return (el as? JsonPrimitive)?.contentOrNull
    }
}
