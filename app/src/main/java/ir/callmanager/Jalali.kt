package ir.callmanager

import java.util.Calendar
import java.util.TimeZone

object Jalali {
    fun fromGregorian(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val g = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + g[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) { jy += (days - 1) / 365; days = (days - 1) % 365 }
        val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
        val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
        return Triple(jy, jm, jd)
    }

    val months = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

    /** نمایش اعداد به فارسی یا انگلیسی طبق تنظیمات */
    fun fa(s: String): String =
        if (Prefs.persian) s.map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")
        else en(s)

    fun fa(n: Int): String = fa(n.toString())

    fun en(s: String): String = s.map { if (it in '۰'..'۹') '0' + (it - '۰') else if (it in '٠'..'٩') '0' + (it - '٠') else it }.joinToString("")

    fun parts(ms: Long): Triple<Int, Int, Int> {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return fromGregorian(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun time(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return fa("%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE)))
    }

    fun dateOnly(ms: Long): String { val (y, m, d) = parts(ms); return fa("$d ${months[m - 1]} $y") }
    fun format(ms: Long): String = dateOnly(ms) + " - " + time(ms)

    /** برای خروجی اکسل: همیشه با ارقام انگلیسی */
    fun plain(ms: Long): String { val (y, m, d) = parts(ms); return "%04d/%02d/%02d".format(y, m, d) }
    fun plainTime(ms: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = ms }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    fun monthKey(ms: Long): Int { val (y, m, _) = parts(ms); return y * 12 + m - 1 }
    fun monthName(key: Int) = months[((key % 12) + 12) % 12]

    fun dayIndex(ms: Long): Long = (ms + TimeZone.getDefault().getOffset(ms)) / 86_400_000L

    fun dayLabel(ms: Long): String = when (dayIndex(System.currentTimeMillis()) - dayIndex(ms)) {
        0L -> "امروز"
        1L -> "دیروز"
        else -> dateOnly(ms)
    }
}

fun fmtDur(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return Jalali.fa(if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s))
}

fun fmtMin(sec: Long): String = if (sec < 60) Jalali.fa("$sec ث") else Jalali.fa("${sec / 60} د")
