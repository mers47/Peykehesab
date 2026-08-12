package ir.peykhesab.app.domain

import java.time.Clock
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Iranian Solar Hijri date used by the UI and reporting layer. */
data class JalaliDate(val year: Int, val month: Int, val day: Int) : Comparable<JalaliDate> {
    init {
        require(month in 1..12) { "ماه نامعتبر است" }
        val maxDay = when {
            month <= 6 -> 31
            month <= 11 -> 30
            isLeapYear(year) -> 30
            else -> 29
        }
        require(day in 1..maxDay) { "روز نامعتبر است" }
    }

    fun format(): String = PersianNumberFormatter.digits("${year.toString().padStart(4, '0')}/${month.toString().padStart(2, '0')}/${day.toString().padStart(2, '0')}")
    fun key(): Int = year * 10_000 + month * 100 + day
    fun monthName(): String = MONTH_NAMES[month - 1]
    fun longFormat(): String = "${PersianNumberFormatter.integer(day)} ${monthName()} ${PersianNumberFormatter.integer(year)}"

    override fun compareTo(other: JalaliDate): Int = key().compareTo(other.key())

    fun toGregorian(): LocalDate {
        var jy = year
        val jm = month
        val jd = day
        var gy: Int
        if (jy > 979) {
            gy = 1600
            jy -= 979
        } else {
            gy = 621
        }
        var days = 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + 78 + jd +
            if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186
        gy += 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = gy % 4 == 0 && (gy % 100 != 0 || gy % 400 == 0)
        val monthDays = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 1
        while (gm <= 12 && gd > monthDays[gm]) {
            gd -= monthDays[gm]
            gm++
        }
        return LocalDate.of(gy, gm, gd)
    }

    companion object {
        private val MONTH_NAMES = listOf("فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند")

        fun daysInMonth(year: Int, month: Int): Int {
            require(month in 1..12) { "ماه نامعتبر است" }
            if (month <= 6) return 31
            if (month <= 11) return 30
            return if (isLeapYear(year)) 30 else 29
        }

        fun isLeapYear(year: Int): Boolean {
            // New-year boundaries are valid by definition and avoid accepting a hypothetical Esfand 30.
            val start = JalaliDate(year, 1, 1).toGregorian()
            val next = JalaliDate(year + 1, 1, 1).toGregorian()
            return ChronoUnit.DAYS.between(start, next) == 366L
        }

        fun fromKey(key: Int): JalaliDate = JalaliDate(key / 10_000, (key / 100) % 100, key % 100)

        fun fromGregorian(date: LocalDate): JalaliDate {
            var gy = date.year
            val gm = date.monthValue
            val gd = date.dayOfMonth
            val gdm = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
            var jy: Int
            if (gy > 1600) {
                jy = 979
                gy -= 1600
            } else {
                jy = 0
                gy -= 621
            }
            val gy2 = if (gm > 2) gy + 1 else gy
            var days = 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 - 80 + gd + gdm[gm - 1]
            jy += 33 * (days / 12053)
            days %= 12053
            jy += 4 * (days / 1461)
            days %= 1461
            if (days > 365) {
                jy += (days - 1) / 365
                days = (days - 1) % 365
            }
            val jm: Int
            val jd: Int
            if (days < 186) {
                jm = 1 + days / 31
                jd = 1 + days % 31
            } else {
                jm = 7 + (days - 186) / 30
                jd = 1 + (days - 186) % 30
            }
            return JalaliDate(jy, jm, jd)
        }

        fun parse(text: String): JalaliDate? {
            val normalized = PersianNormalizer.toEnglishDigits(text.trim())
            val parts = if (normalized.all(Char::isDigit) && normalized.length == 8) {
                listOf(normalized.substring(0, 4), normalized.substring(4, 6), normalized.substring(6, 8))
            } else normalized.split('/', '-', '.')
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            return runCatching {
                val candidate = JalaliDate(y, m, d)
                if (fromGregorian(candidate.toGregorian()) != candidate) error("تاریخ نامعتبر")
                candidate
            }.getOrNull()
        }
    }
}

data class DeviceTimestamp(
    val epochMillis: Long,
    val zoneId: String,
    val offsetSeconds: Int,
    val jalaliDateKey: Int,
    val localSecondOfDay: Int
) {
    val jalaliDate: JalaliDate get() = JalaliDate.fromKey(jalaliDateKey)
}

/** Captures the phone clock and phone time-zone together at the moment an order is committed. */
object DeviceTime {
    fun now(clock: Clock = Clock.systemDefaultZone()): DeviceTimestamp {
        val instant = clock.instant()
        val zone = clock.zone
        val zdt = instant.atZone(zone)
        val jalali = JalaliDate.fromGregorian(zdt.toLocalDate())
        return DeviceTimestamp(
            epochMillis = instant.toEpochMilli(),
            zoneId = zone.id,
            offsetSeconds = zdt.offset.totalSeconds,
            jalaliDateKey = jalali.key(),
            localSecondOfDay = zdt.toLocalTime().toSecondOfDay()
        )
    }

    fun todayKey(clock: Clock = Clock.systemDefaultZone()): Int = now(clock).jalaliDateKey
}

object PersianDateTimeFormatter {
    private val weekdayNames = mapOf(
        DayOfWeek.SATURDAY to "شنبه",
        DayOfWeek.SUNDAY to "یکشنبه",
        DayOfWeek.MONDAY to "دوشنبه",
        DayOfWeek.TUESDAY to "سه‌شنبه",
        DayOfWeek.WEDNESDAY to "چهارشنبه",
        DayOfWeek.THURSDAY to "پنجشنبه",
        DayOfWeek.FRIDAY to "جمعه"
    )


    fun snapshotDateTime(jalaliDateKey: Int, localSecondOfDay: Int): String =
        "${JalaliDate.fromKey(jalaliDateKey).format()} • ${timeFromSecondOfDay(localSecondOfDay)}"

    fun snapshotLongDateTime(
        epochMillis: Long,
        offsetSeconds: Int,
        jalaliDateKey: Int,
        localSecondOfDay: Int
    ): String {
        val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
        val dayOfWeek = Instant.ofEpochMilli(epochMillis).atOffset(offset).dayOfWeek
        val dayName = weekdayNames[dayOfWeek].orEmpty()
        return "$dayName ${JalaliDate.fromKey(jalaliDateKey).longFormat()} • ساعت ${timeFromSecondOfDay(localSecondOfDay)}"
    }

    fun orderDateTime(order: DeliveryOrder): String =
        snapshotDateTime(order.createdJalaliDateKey, order.createdLocalSecondOfDay)

    fun orderLongDateTime(order: DeliveryOrder): String = snapshotLongDateTime(
        order.createdAt, order.createdOffsetSeconds, order.createdJalaliDateKey, order.createdLocalSecondOfDay
    )

    fun settlementLongDateTime(settlement: Settlement): String = snapshotLongDateTime(
        settlement.createdAt, settlement.createdOffsetSeconds, settlement.createdJalaliDateKey, settlement.createdLocalSecondOfDay
    )

    fun moneyChangeDateTime(change: MoneyStateChange): String =
        snapshotDateTime(change.createdJalaliDateKey, change.createdLocalSecondOfDay)

    fun deviceLongDateTime(timestamp: DeviceTimestamp): String {
        val offset = ZoneOffset.ofTotalSeconds(timestamp.offsetSeconds)
        val dayOfWeek = Instant.ofEpochMilli(timestamp.epochMillis).atOffset(offset).dayOfWeek
        val dayName = weekdayNames[dayOfWeek].orEmpty()
        return "$dayName ${timestamp.jalaliDate.longFormat()} • ساعت ${timeFromSecondOfDay(timestamp.localSecondOfDay)}"
    }

    fun timeFromSecondOfDay(secondOfDay: Int): String {
        val safe = secondOfDay.coerceIn(0, 86_399)
        val hour = safe / 3600
        val minute = (safe % 3600) / 60
        return PersianNumberFormatter.digits("${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
    }
}
