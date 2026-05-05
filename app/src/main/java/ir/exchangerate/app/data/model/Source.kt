package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
    val defaultIntervalSeconds: Int,
) {
    TGJU("tgju.org", "https://www.tgju.org", 10),
    BONBAST("bonbast.com", "https://bonbast.com", 30),
    ALANCHAND("alanchand.com", "https://alanchand.com", 30),
    NAVASAN("navasan.tech", "https://navasan.tech", 30),
}
