package ir.exchangerate.app.ui.util

import java.util.Calendar
import java.util.TimeZone

object JalaliDate {

    private val months = arrayOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    fun format(timestampMillis: Long): String {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Tehran"))
        cal.timeInMillis = timestampMillis
        val gy = cal.get(Calendar.YEAR)
        val gm = cal.get(Calendar.MONTH) + 1
        val gd = cal.get(Calendar.DAY_OF_MONTH)
        val (jy, jm, jd) = gregorianToJalali(gy, gm, gd)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)
        val dayPart = "${formatGroupedPersian(jd.toLong())} ${months[jm - 1]} ${formatGroupedPersian(jy.toLong()).replace("،", "")}"
        val timePart = "%02d:%02d".format(hour, minute).map { toPersianDigit(it) }.joinToString("")
        return "$dayPart — ساعت $timePart"
    }

    private fun toPersianDigit(c: Char): Char =
        if (c in '0'..'9') ('۰' + (c - '0')) else c

    private fun gregorianToJalali(gyIn: Int, gmIn: Int, gdIn: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        val gy = gyIn - 1600
        val gm = gmIn - 1
        val gd = gdIn - 1

        var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
        for (i in 0 until gm) gDayNo += gDaysInMonth[i]
        if (gm > 1 && ((gy + 1600) % 4 == 0 && (gy + 1600) % 100 != 0 || (gy + 1600) % 400 == 0)) {
            gDayNo++
        }
        gDayNo += gd

        var jDayNo = gDayNo - 79

        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var jm = 0
        var jd = jDayNo
        for (i in jDaysInMonth.indices) {
            if (jd < jDaysInMonth[i]) {
                jm = i + 1
                jd += 1
                break
            }
            jd -= jDaysInMonth[i]
        }
        return Triple(jy, jm, jd)
    }
}
