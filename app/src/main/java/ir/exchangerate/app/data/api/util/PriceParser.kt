package ir.exchangerate.app.data.api.util

object PriceParser {

    fun parseLong(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        val cleaned = s.trim()
            .replace(" ", "")
            .replace(",", "")
            .replace("،", "")
            .replace("٬", "")
            .let(::toEnglishDigits)
        return cleaned.toDoubleOrNull()?.toLong()
    }

    fun toEnglishDigits(input: String): String {
        val sb = StringBuilder(input.length)
        for (c in input) {
            sb.append(
                when (c) {
                    in '۰'..'۹' -> '0' + (c - '۰')
                    in '٠'..'٩' -> '0' + (c - '٠')
                    else -> c
                }
            )
        }
        return sb.toString()
    }
}
