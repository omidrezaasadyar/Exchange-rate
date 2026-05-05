package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
    val defaultIntervalSeconds: Int,
    val requiresVpn: Boolean = false,
) {
    TGJU("tgju.org", "https://www.tgju.org", 10),
    ECOIRAN("اقتصاد ایران", "https://ecoiran.com", 60),
    DONYA_EQTESAD("دنیای اقتصاد", "https://donya-e-eqtesad.com", 60),
    ALANCHAND("alanchand.com", "https://alanchand.com", 30, requiresVpn = true),
    BONBAST("bonbast.com", "https://bonbast.com", 30, requiresVpn = true),
}
