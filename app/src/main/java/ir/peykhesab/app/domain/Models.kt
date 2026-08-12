package ir.peykhesab.app.domain

import java.util.UUID

enum class MoneyHolder(val dbValue: String, val titleFa: String) {
    UNKNOWN("UNKNOWN", "نامشخص"),
    DRIVER("DRIVER", "دست راننده"),
    OFFICE("OFFICE", "تحویل دفتر"),
    UNPAID("UNPAID", "پرداخت‌نشده");

    companion object {
        fun from(value: String): MoneyHolder = entries.firstOrNull { it.dbValue == value } ?: error("وضعیت وجه ذخیره‌شده نامعتبر است")
    }
}

enum class OrderStatus(val dbValue: String, val titleFa: String) {
    REGISTERED("REGISTERED", "ثبت‌شده"),
    SENT_TO_DRIVER("SENT_TO_DRIVER", "ارسال به راننده"),
    IN_PROGRESS("IN_PROGRESS", "در حال انجام"),
    COMPLETED("COMPLETED", "انجام‌شده"),
    CANCELED("CANCELED", "لغوشده");

    companion object {
        fun from(value: String): OrderStatus = entries.firstOrNull { it.dbValue == value } ?: error("وضعیت سفارش ذخیره‌شده نامعتبر است")
    }
}


object OrderStatusRules {
    fun canTransition(from: OrderStatus, to: OrderStatus): Boolean =
        to == from || to in allowedNext(from)

    fun allowedNext(from: OrderStatus): Set<OrderStatus> = when (from) {
        OrderStatus.REGISTERED -> setOf(OrderStatus.SENT_TO_DRIVER, OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELED)
        OrderStatus.SENT_TO_DRIVER -> setOf(OrderStatus.IN_PROGRESS, OrderStatus.COMPLETED, OrderStatus.CANCELED)
        OrderStatus.IN_PROGRESS -> setOf(OrderStatus.COMPLETED, OrderStatus.CANCELED)
        OrderStatus.COMPLETED -> setOf(OrderStatus.CANCELED)
        OrderStatus.CANCELED -> emptySet()
    }
}

enum class SettlementDirection(val dbValue: String, val titleFa: String) {
    DRIVER_TO_OFFICE("DRIVER_TO_OFFICE", "راننده به دفتر"),
    OFFICE_TO_DRIVER("OFFICE_TO_DRIVER", "دفتر به راننده");

    companion object {
        fun from(value: String): SettlementDirection = entries.firstOrNull { it.dbValue == value } ?: error("جهت تسویه ذخیره‌شده نامعتبر است")
    }
}

data class Settlement(
    val id: String = UUID.randomUUID().toString(),
    val driverId: String,
    val amountRial: Long,
    val direction: SettlementDirection,
    val notes: String? = null,
    val createdAt: Long,
    val createdZoneId: String,
    val createdOffsetSeconds: Int,
    val createdJalaliDateKey: Int,
    val createdLocalSecondOfDay: Int
)

data class Customer(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String? = null,
    val neighborhoodId: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class Driver(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String? = null,
    val plate: String? = null,
    val cardNumber: String? = null,
    val notes: String? = null,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

data class Neighborhood(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt
)

/**
 * createdAt keeps the device instant. The remaining created* fields are an immutable snapshot
 * of the phone's local time at registration so changing the phone time-zone later never moves
 * an old order to a different Iranian calendar day.
 */
data class DeliveryOrder(
    val id: String = UUID.randomUUID().toString(),
    val sequence: Long = 0,
    val customerId: String,
    val driverId: String,
    val neighborhoodId: String,
    val amountRial: Long,
    val commissionBasisPoints: Int = 2000,
    val commissionRial: Long,
    val driverShareRial: Long,
    val moneyHolder: MoneyHolder = MoneyHolder.UNKNOWN,
    val status: OrderStatus = OrderStatus.REGISTERED,
    val notes: String? = null,
    val createdAt: Long,
    val createdZoneId: String,
    val createdOffsetSeconds: Int,
    val createdJalaliDateKey: Int,
    val createdLocalSecondOfDay: Int,
    val updatedAt: Long = createdAt
)

data class MoneyStateChange(
    val id: String = UUID.randomUUID().toString(),
    val orderId: String,
    val from: MoneyHolder,
    val to: MoneyHolder,
    val createdAt: Long,
    val createdZoneId: String,
    val createdOffsetSeconds: Int,
    val createdJalaliDateKey: Int,
    val createdLocalSecondOfDay: Int
)

data class OrderWithNames(
    val order: DeliveryOrder,
    val customerName: String,
    val driverName: String,
    val neighborhoodName: String
)

data class DriverBalance(
    val driverId: String,
    val driverName: String,
    /** positive => driver owes office; negative => office owes driver */
    val netRial: Long,
    val activeOrderCount: Int,
    val totalCommissionRial: Long,
    val totalOrderRial: Long
)

data class DashboardStats(
    val todayOrderCount: Int = 0,
    val todayGrossRial: Long = 0,
    val todayCommissionRial: Long = 0,
    val driverReceivableRial: Long = 0,
    val officePayableRial: Long = 0,
    val openOrderCount: Int = 0
)
