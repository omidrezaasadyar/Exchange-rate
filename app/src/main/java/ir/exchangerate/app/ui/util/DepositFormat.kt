package ir.exchangerate.app.ui.util

import java.util.Locale

private fun toPersianDigit(c: Char): Char = if (c in '0'..'9') ('۰' + (c - '0')) else c

fun formatDepositAmount(value: Double): String {
    val rounded = if (value % 1.0 == 0.0) {
        "%,d".format(Locale.US, value.toLong())
    } else {
        "%,.2f".format(Locale.US, value)
    }
    return rounded
        .replace(",", "،")
        .replace(".", "/")
        .map { toPersianDigit(it) }
        .joinToString("")
}
