package ir.exchangerate.app.data.model

enum class Currency(
    val symbol: String,
    val tgjuKey: String,
    val displayNameFa: String,
    val flagEmoji: String,
) {
    USD("USD", "price_dollar_rl", "دلار آمریکا", "🇺🇸"),
    EUR("EUR", "price_eur", "یورو", "🇪🇺"),
    OMR("OMR", "price_omr", "ریال عمان", "🇴🇲"),
}
