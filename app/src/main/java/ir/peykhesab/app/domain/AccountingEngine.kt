package ir.peykhesab.app.domain

object AccountingEngine {
    const val DEFAULT_COMMISSION_BPS = 2000
    const val MAX_ORDER_AMOUNT_RIAL = 1_000_000_000_000L
    const val BASIS_POINTS = 10_000L

    data class Split(val commissionRial: Long, val driverShareRial: Long)

    fun split(amountRial: Long, commissionBasisPoints: Int = DEFAULT_COMMISSION_BPS): Split {
        require(amountRial >= 0) { "مبلغ نمی‌تواند منفی باشد" }
        require(amountRial <= MAX_ORDER_AMOUNT_RIAL) { "مبلغ از سقف ایمن حسابداری بیشتر است" }
        require(commissionBasisPoints in 0..10_000) { "درصد کمیسیون نامعتبر است" }
        val whole = amountRial / BASIS_POINTS
        val remainder = amountRial % BASIS_POINTS
        val commission = whole * commissionBasisPoints +
            (remainder * commissionBasisPoints + BASIS_POINTS / 2) / BASIS_POINTS
        return Split(commission, amountRial - commission)
    }

    fun driverNetEffect(order: DeliveryOrder): Long {
        if (order.status == OrderStatus.CANCELED) return 0
        return when (order.moneyHolder) {
            MoneyHolder.DRIVER -> order.commissionRial
            MoneyHolder.OFFICE -> -order.driverShareRial
            MoneyHolder.UNKNOWN, MoneyHolder.UNPAID -> 0
        }
    }

    fun settlementNetEffect(settlement: Settlement): Long = when (settlement.direction) {
        SettlementDirection.DRIVER_TO_OFFICE -> -settlement.amountRial
        SettlementDirection.OFFICE_TO_DRIVER -> settlement.amountRial
    }

    fun safeAbsolute(value: Long): Long = when {
        value >= 0L -> value
        value == Long.MIN_VALUE -> throw IllegalStateException("قدر مطلق مانده از محدوده امن حسابداری خارج شده است")
        else -> -value
    }

    fun addExact(left: Long, right: Long): Long = try {
        Math.addExact(left, right)
    } catch (error: ArithmeticException) {
        throw IllegalStateException("مجموع مبالغ از محدوده امن حسابداری خارج شده است", error)
    }

    fun sumExact(values: Iterable<Long>): Long {
        var total = 0L
        for (value in values) total = addExact(total, value)
        return total
    }
}

object PersianNormalizer {
    private const val PERSIAN_DIGITS = "۰۱۲۳۴۵۶۷۸۹"
    private const val ARABIC_DIGITS = "٠١٢٣٤٥٦٧٨٩"

    fun normalizeText(input: String): String = input
        .trim()
        .replace('ي', 'ی')
        .replace('ى', 'ی')
        .replace('ك', 'ک')
        .replace('\u200c', ' ')
        .replace(Regex("\\s+"), " ")
        .lowercase()

    fun normalizePhone(input: String): String {
        var value = toEnglishDigits(input).filter { it.isDigit() || it == '+' }
        if (value.startsWith("+98")) value = "0" + value.drop(3)
        if (value.startsWith("0098")) value = "0" + value.drop(4)
        if (value.startsWith("98") && value.length >= 12) value = "0" + value.drop(2)
        if (value.length == 10 && value.startsWith("9")) value = "0$value"
        return value
    }

    fun toPersianDigits(input: String): String = buildString(input.length) {
        input.forEach { ch -> append(if (ch in '0'..'9') PERSIAN_DIGITS[ch - '0'] else ch) }
    }

    fun toEnglishDigits(input: String): String = buildString(input.length) {
        input.forEach { ch ->
            val p = PERSIAN_DIGITS.indexOf(ch)
            val a = ARABIC_DIGITS.indexOf(ch)
            append(when {
                p >= 0 -> ('0'.code + p).toChar()
                a >= 0 -> ('0'.code + a).toChar()
                else -> ch
            })
        }
    }
}

/** Locale-independent Persian number formatting for every user-visible number. */
object PersianNumberFormatter {
    fun digits(value: String): String = PersianNormalizer.toPersianDigits(value)

    fun integer(value: Int): String = integer(value.toLong())

    fun integer(value: Long): String {
        val raw = value.toString()
        val negative = raw.startsWith('-')
        val body = if (negative) raw.drop(1) else raw
        val grouped = body.reversed().chunked(3).joinToString("٬").reversed()
        return (if (negative) "−" else "") + digits(grouped)
    }

    fun percentFromBasisPoints(bps: Int): String {
        val whole = bps / 100
        val fraction = bps % 100
        val value = if (fraction == 0) integer(whole) else "${integer(whole)}٫${digits(fraction.toString().padStart(2, '0')).trimEnd('۰')}"
        return "$value٪"
    }
}

object MoneyFormatter {
    fun rialToTomanText(rial: Long): String {
        val raw = rial.toString()
        val negative = raw.startsWith('-')
        val digits = raw.removePrefix("-")
        val wholeDigits = digits.dropLast(1).ifEmpty { "0" }
        val tenthDigit = digits.last()
        val groupedWhole = wholeDigits.reversed().chunked(3).joinToString("٬").reversed()
        val whole = PersianNormalizer.toPersianDigits(groupedWhole)
        val amount = if (tenthDigit == '0') whole else "$whole٫${PersianNormalizer.toPersianDigits(tenthDigit.toString())}"
        return "${if (negative) "−" else ""}$amount تومان"
    }

    fun tomanInputToRial(input: String): Long? {
        val cleaned = PersianNormalizer.toEnglishDigits(input)
            .replace(",", "")
            .replace("٬", "")
            .replace(" ", "")
        val toman = cleaned.toLongOrNull() ?: return null
        if (toman < 0 || toman > Long.MAX_VALUE / 10) return null
        return toman * 10
    }
}
