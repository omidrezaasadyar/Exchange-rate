package ir.exchangerate.app.data.model

enum class Source(
    val displayName: String,
    val websiteUrl: String,
    val defaultIntervalSeconds: Int,
    val accentColorHex: Long,
    val requiresVpn: Boolean = false,
) {
    TGJU("tgju.org", "https://www.tgju.org", 10, 0xFF1F6FEB),
    ECOIRAN("اقتصاد ایران", "https://ecoiran.com", 60, 0xFF0EA5A4),
    DONYA_EQTESAD("دنیای اقتصاد", "https://donya-e-eqtesad.com", 60, 0xFFEA580C),
    ALANCHAND("alanchand.com", "https://alanchand.com", 30, 0xFF9333EA, requiresVpn = true),
    BONBAST("bonbast.com", "https://bonbast.com", 30, 0xFFE11D48, requiresVpn = true),
}
