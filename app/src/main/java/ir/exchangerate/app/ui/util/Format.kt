package ir.exchangerate.app.ui.util

import ir.exchangerate.app.data.DisplayUnit

fun formatPrice(rial: Long, unit: DisplayUnit): String {
    val value = if (unit == DisplayUnit.TOMAN) rial / 10 else rial
    return formatGroupedPersian(value)
}

fun formatGroupedPersian(value: Long): String {
    val sign = if (value < 0) "-" else ""
    val s = kotlin.math.abs(value).toString()
    val sb = StringBuilder()
    var count = 0
    for (i in s.indices.reversed()) {
        sb.append(toPersianDigit(s[i]))
        count++
        if (count % 3 == 0 && i != 0) sb.append('،')
    }
    return sign + sb.reverse().toString()
}

fun formatPercent(value: Double): String {
    val sign = if (value > 0) "+" else ""
    val asString = "%.2f".format(value)
    return sign + asString.map { toPersianDigit(it) }.joinToString("") + "٪"
}

private fun toPersianDigit(c: Char): Char =
    if (c in '0'..'9') ('۰' + (c - '0')) else c

fun timeAgoFa(elapsedMillis: Long): String {
    val seconds = (elapsedMillis / 1000).coerceAtLeast(0)
    return when {
        seconds < 2 -> "همین الان"
        seconds < 60 -> "${formatGroupedPersian(seconds)} ثانیه پیش"
        seconds < 3600 -> "${formatGroupedPersian(seconds / 60)} دقیقه پیش"
        else -> "${formatGroupedPersian(seconds / 3600)} ساعت پیش"
    }
}
