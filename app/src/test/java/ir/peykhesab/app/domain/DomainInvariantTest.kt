package ir.peykhesab.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class DomainInvariantTest {
    @Test
    fun `کمیسیون بیست درصد و سهم راننده دقیق است`() {
        val split = AccountingEngine.split(1_000_000L)
        assertEquals(200_000L, split.commissionRial)
        assertEquals(800_000L, split.driverShareRial)
        assertEquals(1_000_000L, split.commissionRial + split.driverShareRial)
    }

    @Test
    fun `گرد کردن کمیسیون همیشه مجموع مبلغ را حفظ می‌کند`() {
        val amounts = listOf(0L, 1L, 9L, 10L, 11L, 999L, 12_345L, 1_000_001L, 9_999_999_999L)
        val rates = listOf(0, 1, 333, 2000, 3333, 9999, 10_000)
        for (amount in amounts) for (rate in rates) {
            val split = AccountingEngine.split(amount, rate)
            assertEquals(amount, split.commissionRial + split.driverShareRial)
            assertTrue(split.commissionRial in 0..amount)
        }
    }

    @Test
    fun `اثر مانده با محل وجه و تسویه سازگار است`() {
        val split = AccountingEngine.split(1_000_000L)
        fun order(holder: MoneyHolder) = DeliveryOrder(
            customerId = "c", driverId = "d", neighborhoodId = "n",
            amountRial = 1_000_000L,
            commissionRial = split.commissionRial,
            driverShareRial = split.driverShareRial,
            moneyHolder = holder,
            createdAt = 0L,
            createdZoneId = "Asia/Tehran",
            createdOffsetSeconds = 12_600,
            createdJalaliDateKey = 14050521,
            createdLocalSecondOfDay = 0
        )
        assertEquals(200_000L, AccountingEngine.driverNetEffect(order(MoneyHolder.DRIVER)))
        assertEquals(-800_000L, AccountingEngine.driverNetEffect(order(MoneyHolder.OFFICE)))
        assertEquals(0L, AccountingEngine.driverNetEffect(order(MoneyHolder.UNPAID)))
        val paidToOffice = Settlement(
            driverId = "d", amountRial = 200_000L, direction = SettlementDirection.DRIVER_TO_OFFICE,
            createdAt = 0L, createdZoneId = "Asia/Tehran", createdOffsetSeconds = 12_600,
            createdJalaliDateKey = 14050521, createdLocalSecondOfDay = 0
        )
        assertEquals(-200_000L, AccountingEngine.settlementNetEffect(paidToOffice))
    }

    @Test
    fun `تاریخ شناخته شده به شمسی درست تبدیل می‌شود`() {
        assertEquals(JalaliDate(1405, 5, 21), JalaliDate.fromGregorian(LocalDate.of(2026, 8, 12)))
        assertEquals(LocalDate.of(2026, 8, 12), JalaliDate(1405, 5, 21).toGregorian())
    }

    @Test
    fun `تمام روزهای بیست سال رفت و برگشت دقیق دارند`() {
        var date = LocalDate.of(2018, 1, 1)
        val end = LocalDate.of(2037, 12, 31)
        while (!date.isAfter(end)) {
            val jalali = JalaliDate.fromGregorian(date)
            assertEquals(date, jalali.toGregorian())
            date = date.plusDays(1)
        }
    }

    @Test
    fun `تاریخ نامعتبر اسفند حتی با constructor مستقیم پذیرفته نمی‌شود`() {
        assertNull(JalaliDate.parse("۱۴۰۵/۱۲/۳۰"))
        assertNull(JalaliDate.parse("۱۴۰۵/۱۲/۳۱"))
        assertThrows(IllegalArgumentException::class.java) { JalaliDate(1405, 12, 30) }
        assertEquals(JalaliDate(1405, 5, 21), JalaliDate.parse("۱۴۰۵۰۵۲۱"))
    }

    @Test
    fun `زمان سفارش از ساعت و منطقه زمانی موبایل snapshot می‌شود`() {
        val zone = ZoneId.of("Asia/Tehran")
        val fixed = Clock.fixed(Instant.parse("2026-08-12T10:26:25Z"), zone)
        val captured = DeviceTime.now(fixed)
        assertEquals(14050521, captured.jalaliDateKey)
        assertEquals("Asia/Tehran", captured.zoneId)
        assertEquals(12_600, captured.offsetSeconds)
        assertEquals(13 * 3600 + 56 * 60 + 25, captured.localSecondOfDay)
    }

    @Test
    fun `نرمال سازی شماره ایران قالب‌های متداول را یکسان می‌کند`() {
        assertEquals("09121234567", PersianNormalizer.normalizePhone("+98 912 123 4567"))
        assertEquals("09121234567", PersianNormalizer.normalizePhone("00989121234567"))
        assertEquals("09121234567", PersianNormalizer.normalizePhone("9121234567"))
    }

    @Test
    fun `مقدار خراب enum بی صدا fallback نمی‌شود`() {
        assertThrows(IllegalStateException::class.java) { MoneyHolder.from("BROKEN") }
        assertThrows(IllegalStateException::class.java) { OrderStatus.from("BROKEN") }
        assertThrows(IllegalStateException::class.java) { SettlementDirection.from("BROKEN") }
    }

    @Test
    fun `قانون وضعیت سفارش اجازه بازگشت نمی‌دهد`() {
        assertTrue(OrderStatusRules.canTransition(OrderStatus.REGISTERED, OrderStatus.IN_PROGRESS))
        assertFalse(OrderStatusRules.canTransition(OrderStatus.COMPLETED, OrderStatus.IN_PROGRESS))
    }
    @Test
    fun `مرزهای نوروز شناخته شده درست هستند`() {
        assertEquals(JalaliDate(1400, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2021, 3, 21)))
        assertEquals(JalaliDate(1403, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1404, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1405, 1, 1), JalaliDate.fromGregorian(LocalDate.of(2026, 3, 21)))
    }

    @Test
    fun `لغو اثر مالی را صفر و نمایش کسری تومان را حفظ می‌کند`() {
        val split = AccountingEngine.split(1_000_000L)
        val canceled = DeliveryOrder(
            customerId = "c", driverId = "d", neighborhoodId = "n", amountRial = 1_000_000L,
            commissionRial = split.commissionRial, driverShareRial = split.driverShareRial,
            moneyHolder = MoneyHolder.DRIVER, status = OrderStatus.CANCELED, createdAt = 0L,
            createdZoneId = "Asia/Tehran", createdOffsetSeconds = 12_600,
            createdJalaliDateKey = 14050521, createdLocalSecondOfDay = 0
        )
        assertEquals(0L, AccountingEngine.driverNetEffect(canceled))
        assertEquals("۲۰۰٫۲ تومان", MoneyFormatter.rialToTomanText(2_002L))
        assertEquals("۱٬۲۳۴٬۵۶۷", PersianNumberFormatter.integer(1_234_567))
    }

    @Test
    fun `مقادیر مرزی Long هیچ مبلغ جعلی تولید نمی‌کنند`() {
        assertThrows(IllegalStateException::class.java) { AccountingEngine.safeAbsolute(Long.MIN_VALUE) }
        assertEquals("−۹۲۲٬۳۳۷٬۲۰۳٬۶۸۵٬۴۷۷٬۵۸۰٫۸ تومان", MoneyFormatter.rialToTomanText(Long.MIN_VALUE))
        assertEquals("۹۲۲٬۳۳۷٬۲۰۳٬۶۸۵٬۴۷۷٬۵۸۰٫۷ تومان", MoneyFormatter.rialToTomanText(Long.MAX_VALUE))
    }

    @Test
    fun `گزارش مالی جمع سفارش کمیسیون وضعیت وجه و تسویه را دقیق محاسبه می‌کند`() {
        fun order(
            id: String,
            amount: Long,
            holder: MoneyHolder,
            status: OrderStatus = OrderStatus.REGISTERED,
            day: Int = 14050521
        ): OrderWithNames {
            val split = AccountingEngine.split(amount)
            return OrderWithNames(
                DeliveryOrder(
                    id = id,
                    sequence = id.filter(Char::isDigit).toLongOrNull() ?: 1L,
                    customerId = "c",
                    driverId = "d",
                    neighborhoodId = "n",
                    amountRial = amount,
                    commissionRial = split.commissionRial,
                    driverShareRial = split.driverShareRial,
                    moneyHolder = holder,
                    status = status,
                    createdAt = 0L,
                    createdZoneId = "Asia/Tehran",
                    createdOffsetSeconds = 12_600,
                    createdJalaliDateKey = day,
                    createdLocalSecondOfDay = 1
                ),
                customerName = "مشتری",
                driverName = "راننده",
                neighborhoodName = "محله"
            )
        }

        val orders = listOf(
            order("1", 1_000_000L, MoneyHolder.DRIVER),
            order("2", 2_500_000L, MoneyHolder.OFFICE),
            order("3", 900_000L, MoneyHolder.DRIVER, OrderStatus.CANCELED),
            order("4", 8_000_000L, MoneyHolder.DRIVER, day = 14050520)
        )
        val settlements = listOf(
            Settlement(
                id = "s1", driverId = "d", amountRial = 100_000L,
                direction = SettlementDirection.DRIVER_TO_OFFICE,
                createdAt = 0L, createdZoneId = "Asia/Tehran", createdOffsetSeconds = 12_600,
                createdJalaliDateKey = 14050521, createdLocalSecondOfDay = 2
            )
        )

        val report = ReportEngine.calculate(orders, settlements, 14050521, 14050521)
        assertEquals(2, report.validOrderCount)
        assertEquals(1, report.canceledCount)
        assertEquals(3_500_000L, report.grossRial)
        assertEquals(700_000L, report.commissionRial)
        assertEquals(1_000_000L, report.heldByDriversRial)
        assertEquals(2_500_000L, report.heldByOfficeRial)
        assertEquals(100_000L, report.driverToOfficeSettledRial)
        assertEquals(0L, report.officeToDriverSettledRial)
        assertEquals(1, report.drivers.size)
        assertEquals(2, report.drivers.single().count)
        assertTrue(ReportEngine.buildText("امروز", "۱۴۰۵/۰۵/۲۱", report).contains("جمع سفارش: ۳۵۰٬۰۰۰ تومان"))
    }

    @Test
    fun `جمع حسابداری روی overflow بی صدا نمی‌چرخد`() {
        assertEquals(6L, AccountingEngine.sumExact(listOf(1L, 2L, 3L)))
        assertThrows(IllegalStateException::class.java) { AccountingEngine.addExact(Long.MAX_VALUE, 1L) }
        assertThrows(IllegalStateException::class.java) { AccountingEngine.sumExact(listOf(Long.MAX_VALUE, 1L)) }
        assertThrows(IllegalArgumentException::class.java) { AccountingEngine.split(Long.MAX_VALUE) }
    }

}
