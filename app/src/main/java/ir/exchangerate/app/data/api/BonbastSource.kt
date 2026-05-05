package ir.exchangerate.app.data.api

import ir.exchangerate.app.data.api.util.PriceParser
import ir.exchangerate.app.data.model.Currency
import ir.exchangerate.app.data.model.Direction
import ir.exchangerate.app.data.model.Rate
import ir.exchangerate.app.data.model.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class BonbastSource(
    private val client: OkHttpClient = HttpClient.instance,
) : RateSource {

    override val source: Source = Source.BONBAST

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val sellKeyOf = mapOf(
        Currency.USD to "usd1",
        Currency.EUR to "eur1",
        Currency.OMR to "omr1",
    )

    private val buyKeyOf = mapOf(
        Currency.USD to "usd2",
        Currency.EUR to "eur2",
        Currency.OMR to "omr2",
    )

    override suspend fun fetch(currencies: List<Currency>): Map<Currency, Result<Rate>> =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val priceMap = runCatching { fetchPriceMap() }.getOrElse { error ->
                return@withContext currencies.associateWith { Result.failure(error) }
            }

            currencies.associateWith { currency ->
                runCatching {
                    val sellKey = sellKeyOf[currency] ?: error("unmapped")
                    val buyKey = buyKeyOf[currency] ?: error("unmapped")
                    val sell = priceMap[sellKey]?.let(PriceParser::parseLong)
                        ?: error("missing $sellKey")
                    val buy = priceMap[buyKey]?.let(PriceParser::parseLong)

                    Rate(
                        source = source,
                        currency = currency,
                        priceRial = sell * 10,
                        highRial = buy?.let { it * 10 },
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

    private fun fetchPriceMap(): Map<String, String> {
        val viaToken = runCatching {
            val token = fetchToken()
            val postRequest = Request.Builder()
                .url("https://bonbast.com/json")
                .post(FormBody.Builder().add("param", token).build())
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Origin", "https://bonbast.com")
                .build()
            client.newCall(postRequest).execute().use { response ->
                if (!response.isSuccessful) error("HTTP ${response.code}")
                val body = response.body?.string().orEmpty()
                val parsed = json.parseToJsonElement(body) as? JsonObject ?: error("not json")
                parsed.mapValues { it.value.jsonPrimitive.content }
            }
        }.getOrNull()

        if (viaToken != null && viaToken.values.any { it.isNotBlank() }) return viaToken
        return scrapeHomepage()
    }

    private fun fetchToken(): String {
        val request = Request.Builder().url("https://bonbast.com/").build()
        client.newCall(request).execute().use { response ->
            val html = response.body?.string().orEmpty()
            val tokenRegex = Regex("""\$\.post\(\s*["']/json["']\s*,\s*\{\s*param\s*:\s*["']([^"']+)["']""")
            tokenRegex.find(html)?.groupValues?.get(1)?.let { return it }
            val csrfMeta = Jsoup.parse(html).selectFirst("meta[name=csrf-token]")?.attr("content")
            if (!csrfMeta.isNullOrBlank()) return csrfMeta
            error("token not found")
        }
    }

    private fun scrapeHomepage(): Map<String, String> {
        val request = Request.Builder().url("https://bonbast.com/").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val html = response.body?.string().orEmpty()
            val doc = Jsoup.parse(html)
            val keys = sellKeyOf.values + buyKeyOf.values
            return keys.mapNotNull { key ->
                val text = doc.selectFirst("#$key")?.text()
                    ?: doc.selectFirst("[data-currency=\"$key\"]")?.text()
                if (text.isNullOrBlank()) null else key to text
            }.toMap()
        }
    }
}
