package ir.peykhesab.app.domain

data class DriverReportSummary(
    val id: String,
    val name: String,
    val count: Int,
    val grossRial: Long,
    val commissionRial: Long
)

data class ReportSnapshot(
    val orders: List<OrderWithNames>,
    val settlements: List<Settlement>,
    val grossRial: Long,
    val commissionRial: Long,
    val heldByDriversRial: Long,
    val heldByOfficeRial: Long,
    val canceledCount: Int,
    val driverToOfficeSettledRial: Long,
    val officeToDriverSettledRial: Long,
    val drivers: List<DriverReportSummary>,
    val driverNamesById: Map<String, String>
) {
    val validOrderCount: Int get() = orders.size - canceledCount
}

/** Pure report engine. It is intentionally UI-free so it can run off the main thread and be unit-tested. */
object ReportEngine {
    fun calculate(
        allOrders: List<OrderWithNames>,
        allSettlements: List<Settlement>,
        fromJalaliKey: Int,
        toJalaliKey: Int
    ): ReportSnapshot {
        require(fromJalaliKey <= toJalaliKey) { "بازه گزارش نامعتبر است" }

        val reportOrders = allOrders.filter { it.order.createdJalaliDateKey in fromJalaliKey..toJalaliKey }
        val reportSettlements = allSettlements.filter { it.createdJalaliDateKey in fromJalaliKey..toJalaliKey }
        val financialOrders = reportOrders.filter { it.order.status != OrderStatus.CANCELED }

        val byDriver = financialOrders
            .groupBy { it.order.driverId }
            .map { (driverId, items) ->
                DriverReportSummary(
                    id = driverId,
                    name = items.first().driverName,
                    count = items.size,
                    grossRial = items.sumExact { it.order.amountRial },
                    commissionRial = items.sumExact { it.order.commissionRial }
                )
            }
            .sortedByDescending { it.grossRial }

        return ReportSnapshot(
            orders = reportOrders,
            settlements = reportSettlements,
            grossRial = financialOrders.sumExact { it.order.amountRial },
            commissionRial = financialOrders.sumExact { it.order.commissionRial },
            heldByDriversRial = financialOrders.filter { it.order.moneyHolder == MoneyHolder.DRIVER }.sumExact { it.order.amountRial },
            heldByOfficeRial = financialOrders.filter { it.order.moneyHolder == MoneyHolder.OFFICE }.sumExact { it.order.amountRial },
            canceledCount = reportOrders.count { it.order.status == OrderStatus.CANCELED },
            driverToOfficeSettledRial = reportSettlements.filter { it.direction == SettlementDirection.DRIVER_TO_OFFICE }.sumExact { it.amountRial },
            officeToDriverSettledRial = reportSettlements.filter { it.direction == SettlementDirection.OFFICE_TO_DRIVER }.sumExact { it.amountRial },
            drivers = byDriver,
            driverNamesById = allOrders.associate { it.order.driverId to it.driverName }
        )
    }

    fun buildText(title: String, rangeLabel: String, report: ReportSnapshot): String = buildString {
        appendLine("گزارش پیک‌حساب • $title")
        appendLine("بازه: $rangeLabel")
        appendLine("تعداد سفارش معتبر: ${PersianNumberFormatter.integer(report.validOrderCount)}")
        appendLine("جمع سفارش: ${MoneyFormatter.rialToTomanText(report.grossRial)}")
        appendLine("کمیسیون: ${MoneyFormatter.rialToTomanText(report.commissionRial)}")
        appendLine("دست راننده‌ها: ${MoneyFormatter.rialToTomanText(report.heldByDriversRial)}")
        appendLine("تحویل دفتر: ${MoneyFormatter.rialToTomanText(report.heldByOfficeRial)}")
        appendLine("لغوشده: ${PersianNumberFormatter.integer(report.canceledCount)}")
        appendLine("تعداد تسویه: ${PersianNumberFormatter.integer(report.settlements.size)}")
        appendLine("واریز راننده‌ها به دفتر: ${MoneyFormatter.rialToTomanText(report.driverToOfficeSettledRial)}")
        appendLine("پرداخت دفتر به راننده‌ها: ${MoneyFormatter.rialToTomanText(report.officeToDriverSettledRial)}")

        if (report.drivers.isNotEmpty()) {
            appendLine()
            appendLine("خلاصه راننده‌ها:")
            report.drivers.forEach {
                appendLine("• ${it.name}: ${PersianNumberFormatter.integer(it.count)} سفارش، ${MoneyFormatter.rialToTomanText(it.grossRial)}، کمیسیون ${MoneyFormatter.rialToTomanText(it.commissionRial)}")
            }
        }

        if (report.orders.isNotEmpty()) {
            appendLine()
            appendLine("جزئیات سفارش‌ها:")
            report.orders
                .sortedWith(compareBy<OrderWithNames> { it.order.createdJalaliDateKey }.thenBy { it.order.createdLocalSecondOfDay })
                .forEach { item ->
                    val order = item.order
                    append("• شماره ${PersianNumberFormatter.integer(order.sequence)} | ${PersianDateTimeFormatter.orderDateTime(order)}")
                    append(" | مشتری: ${item.customerName} | راننده: ${item.driverName} | محله: ${item.neighborhoodName}")
                    append(" | مبلغ: ${MoneyFormatter.rialToTomanText(order.amountRial)} | ${order.status.titleFa} | وجه: ${order.moneyHolder.titleFa}")
                    order.notes?.takeIf(String::isNotBlank)?.let { append(" | توضیح: $it") }
                    appendLine()
                }
        }

        if (report.settlements.isNotEmpty()) {
            appendLine()
            appendLine("جزئیات تسویه‌ها:")
            report.settlements
                .sortedWith(compareBy<Settlement> { it.createdJalaliDateKey }.thenBy { it.createdLocalSecondOfDay })
                .forEach { settlement ->
                    val driverName = report.driverNamesById[settlement.driverId] ?: "راننده بایگانی‌شده"
                    append("• ${PersianDateTimeFormatter.settlementLongDateTime(settlement)} | $driverName | ${settlement.direction.titleFa} | ${MoneyFormatter.rialToTomanText(settlement.amountRial)}")
                    settlement.notes?.takeIf(String::isNotBlank)?.let { append(" | توضیح: $it") }
                    appendLine()
                }
        }
    }

    private inline fun <T> Iterable<T>.sumExact(selector: (T) -> Long): Long {
        var result = 0L
        for (item in this) result = AccountingEngine.addExact(result, selector(item))
        return result
    }
}
