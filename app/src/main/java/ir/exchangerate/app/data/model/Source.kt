package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
    val defaultIntervalSeconds: Int,
) {
    TGJU("tgju.org", "https://www.tgju.org", 10),
    ALANCHAND("alanchand.com", "https://alanchand.com", 30),
    DONYA_EQTESAD("دنیای اقتصاد", "https://donya-e-eqtesad.com", 60),
}
