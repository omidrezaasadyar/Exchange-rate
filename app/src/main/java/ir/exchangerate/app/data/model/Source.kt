package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
) {
    TGJU("tgju.org", "https://www.tgju.org"),
    BONBAST("bonbast.com", "https://bonbast.com"),
    ALANCHAND("alanchand.com", "https://alanchand.com"),
}
