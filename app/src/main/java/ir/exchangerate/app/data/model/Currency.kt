package ir.exchangerate.app.data.model

enum class Currency(
    val symbol: String,
    val displayNameFa: String,
    val flagEmoji: String,
) {
    USD("USD", "دلار آمریکا", "🇺🇸"),
    EUR("EUR", "یورو", "🇪🇺"),
    OMR("OMR", "ریال عمان", "🇴🇲"),
}
