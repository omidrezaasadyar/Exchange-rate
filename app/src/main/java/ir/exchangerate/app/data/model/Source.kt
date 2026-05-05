package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
    val defaultIntervalSeconds: Int,
) {
    TGJU("tgju.org", "https://www.tgju.org", 10),
    BRSAPI("brsapi.ir", "https://brsapi.ir", 15),
    ALANCHAND("alanchand.com", "https://alanchand.com", 30),
    FARARU("fararu.com", "https://fararu.com", 45),
}
