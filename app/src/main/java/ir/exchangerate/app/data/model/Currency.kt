package ir.exchangerate.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Currency(
    val symbol: String,
    val displayNameFa: String,
    val flagEmoji: String,
) {
    USD("USD", "دلار آمریکا", "🇺🇸"),
    EUR("EUR", "یورو", "🇪🇺"),
    OMR("OMR", "ریال عمان", "🇴🇲"),
}
