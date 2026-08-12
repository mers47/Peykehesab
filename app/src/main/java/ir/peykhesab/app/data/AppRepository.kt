package ir.peykhesab.app.data

import ir.peykhesab.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.UUID

class AppRepository(private val db: AppDatabase) {
    private val dao = db.dao()

    val customers: Flow<List<Customer>> = dao.observeCustomers().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    val drivers: Flow<List<Driver>> = dao.observeDrivers().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    val neighborhoods: Flow<List<Neighborhood>> = dao.observeNeighborhoods().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    val orders: Flow<List<OrderWithNames>> = dao.observeOrders().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)
    val settlements: Flow<List<Settlement>> = dao.observeSettlements().map { rows -> rows.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    val balances: Flow<List<DriverBalance>> = combine(orders, settlements, drivers) { orderRows, settlementRows, driverRows ->
        calculateBalances(orderRows, settlementRows, driverRows)
    }.flowOn(Dispatchers.Default)

    private val currentJalaliDay: Flow<Int> = flow {
        var previous: Int? = null
        while (currentCoroutineContext().isActive) {
            val current = DeviceTime.todayKey()
            if (current != previous) {
                emit(current)
                previous = current
            }
            delay(30_000)
        }
    }

    val dashboard: Flow<DashboardStats> = combine(orders, balances, currentJalaliDay) { orderRows, balanceRows, todayKey ->
        calculateDashboard(orderRows, balanceRows, todayKey)
    }.flowOn(Dispatchers.Default)

    suspend fun saveCustomer(item: Customer) {
        require(item.name.isNotBlank()) { "نام مشتری الزامی است" }
        require(item.name.trim().length <= MAX_NAME_LENGTH) { "نام مشتری بیش از حد طولانی است" }
        require(item.notes.orEmpty().length <= MAX_NOTES_LENGTH) { "توضیحات مشتری بیش از حد طولانی است" }
        val now = System.currentTimeMillis()
        val normalizedPhone = item.phone?.takeIf(String::isNotBlank)?.let(PersianNormalizer::normalizePhone)
        require(normalizedPhone == null || normalizedPhone.matches(Regex("^09\\d{9}$"))) { "شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود" }
        val clean = item.copy(
            name = item.name.trim(),
            phone = normalizedPhone,
            notes = item.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = now
        )
        clean.neighborhoodId?.let { require(dao.neighborhoodById(it)?.isArchived == false) { "محله انتخاب‌شده فعال نیست" } }
        dao.saveCustomer(
            CustomerEntity(clean.id, clean.name, PersianNormalizer.normalizeText(clean.name), clean.phone, clean.phone?.let(PersianNormalizer::normalizePhone), clean.neighborhoodId, clean.notes, clean.isArchived, clean.createdAt, clean.updatedAt),
            audit("CUSTOMER", clean.id, "UPSERT", clean.name, now)
        )
    }

    suspend fun saveDriver(item: Driver) {
        require(item.name.isNotBlank()) { "نام راننده الزامی است" }
        require(item.name.trim().length <= MAX_NAME_LENGTH) { "نام راننده بیش از حد طولانی است" }
        require(item.plate.orEmpty().length <= MAX_PLATE_LENGTH) { "پلاک بیش از حد طولانی است" }
        require(item.notes.orEmpty().length <= MAX_NOTES_LENGTH) { "توضیحات راننده بیش از حد طولانی است" }
        val now = System.currentTimeMillis()
        val normalizedPhone = item.phone?.takeIf(String::isNotBlank)?.let(PersianNormalizer::normalizePhone)
        require(normalizedPhone == null || normalizedPhone.matches(Regex("^09\\d{9}$"))) { "شماره موبایل باید ۱۱ رقم و با ۰۹ شروع شود" }
        val normalizedCard = item.cardNumber?.takeIf(String::isNotBlank)?.let { PersianNormalizer.toEnglishDigits(it).filter(Char::isDigit) }
        require(normalizedCard == null || normalizedCard.length == 16) { "شماره کارت باید ۱۶ رقم باشد" }
        val clean = item.copy(
            name = item.name.trim(),
            phone = normalizedPhone,
            plate = item.plate?.trim()?.takeIf(String::isNotEmpty),
            cardNumber = normalizedCard,
            notes = item.notes?.trim()?.takeIf(String::isNotEmpty),
            updatedAt = now
        )
        dao.saveDriver(
            DriverEntity(clean.id, clean.name, PersianNormalizer.normalizeText(clean.name), clean.phone, clean.phone?.let(PersianNormalizer::normalizePhone), clean.plate, clean.cardNumber, clean.notes, clean.isArchived, clean.createdAt, clean.updatedAt),
            audit("DRIVER", clean.id, "UPSERT", clean.name, now)
        )
    }

    suspend fun saveNeighborhood(item: Neighborhood) {
        require(item.name.isNotBlank()) { "نام محله الزامی است" }
        require(item.name.trim().length <= MAX_NEIGHBORHOOD_LENGTH) { "نام محله بیش از حد طولانی است" }
        val now = System.currentTimeMillis()
        val name = item.name.trim()
        val normalized = PersianNormalizer.normalizeText(name)
        val duplicate = dao.activeNeighborhoodByNormalizedName(normalized)
        require(duplicate == null || duplicate.id == item.id) { "این محله قبلاً ثبت شده است" }
        val clean = item.copy(name = name, updatedAt = now)
        dao.saveNeighborhood(
            NeighborhoodEntity(clean.id, name, normalized, if (clean.isArchived) null else normalized, clean.isArchived, clean.createdAt, clean.updatedAt),
            audit("NEIGHBORHOOD", clean.id, "UPSERT", name, now)
        )
    }

    suspend fun archiveCustomer(id: String) {
        val now = System.currentTimeMillis()
        dao.archiveCustomer(id, now, audit("CUSTOMER", id, "ARCHIVE", null, now))
    }

    suspend fun archiveDriver(id: String) {
        val now = System.currentTimeMillis()
        dao.archiveDriver(id, now, audit("DRIVER", id, "ARCHIVE", null, now))
    }

    suspend fun archiveNeighborhood(id: String) {
        val now = System.currentTimeMillis()
        dao.archiveNeighborhood(id, now, audit("NEIGHBORHOOD", id, "ARCHIVE", null, now))
    }

    suspend fun createOrder(
        customerId: String,
        driverId: String,
        neighborhoodId: String,
        amountRial: Long,
        commissionBps: Int = AccountingEngine.DEFAULT_COMMISSION_BPS,
        moneyHolder: MoneyHolder = MoneyHolder.UNKNOWN,
        notes: String? = null
    ): DeliveryOrder {
        require(amountRial > 0) { "مبلغ سفارش باید بیشتر از صفر باشد" }
        require(amountRial <= AccountingEngine.MAX_ORDER_AMOUNT_RIAL) { "مبلغ سفارش از سقف ایمن سیستم بیشتر است" }
        require(notes.orEmpty().length <= MAX_NOTES_LENGTH) { "توضیحات سفارش بیش از حد طولانی است" }
        require(commissionBps in 0..10_000) { "درصد کمیسیون نامعتبر است" }

        val split = AccountingEngine.split(amountRial, commissionBps)
        val captured = DeviceTime.now()
        val id = UUID.randomUUID().toString()
        val entity = OrderEntity(
            id = id,
            customerId = customerId,
            driverId = driverId,
            neighborhoodId = neighborhoodId,
            amountRial = amountRial,
            commissionBasisPoints = commissionBps,
            commissionRial = split.commissionRial,
            driverShareRial = split.driverShareRial,
            moneyHolder = moneyHolder.dbValue,
            status = OrderStatus.REGISTERED.dbValue,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = captured.epochMillis,
            createdZoneId = captured.zoneId,
            createdOffsetSeconds = captured.offsetSeconds,
            createdJalaliDateKey = captured.jalaliDateKey,
            createdLocalSecondOfDay = captured.localSecondOfDay,
            updatedAt = captured.epochMillis
        )
        val sequence = dao.createOrder(entity, audit("ORDER", id, "CREATE", "amount_rial=$amountRial", captured.epochMillis))
        return entity.copy(sequence = sequence).toDomain()
    }

    suspend fun updateMoneyHolder(orderId: String, holder: MoneyHolder) {
        val captured = DeviceTime.now()
        dao.changeMoneyHolder(
            orderId = orderId,
            holder = holder.dbValue,
            capturedAt = captured.epochMillis,
            historyId = UUID.randomUUID().toString(),
            capturedZoneId = captured.zoneId,
            capturedOffsetSeconds = captured.offsetSeconds,
            capturedJalaliDateKey = captured.jalaliDateKey,
            capturedLocalSecondOfDay = captured.localSecondOfDay,
            audit = audit("ORDER", orderId, "MONEY_HOLDER_CHANGE", null, captured.epochMillis)
        )
    }

    suspend fun updateStatus(orderId: String, status: OrderStatus) {
        val now = System.currentTimeMillis()
        dao.changeOrderStatus(orderId, status.dbValue, now, audit("ORDER", orderId, "STATUS_CHANGE", null, now))
    }

    suspend fun recordSettlement(
        driverId: String,
        amountRial: Long,
        direction: SettlementDirection,
        notes: String? = null
    ): Settlement {
        require(amountRial > 0) { "مبلغ تسویه باید بیشتر از صفر باشد" }
        require(notes.orEmpty().length <= MAX_NOTES_LENGTH) { "توضیحات تسویه بیش از حد طولانی است" }
        val captured = DeviceTime.now()
        val settlement = Settlement(
            driverId = driverId,
            amountRial = amountRial,
            direction = direction,
            notes = notes?.trim()?.takeIf(String::isNotEmpty),
            createdAt = captured.epochMillis,
            createdZoneId = captured.zoneId,
            createdOffsetSeconds = captured.offsetSeconds,
            createdJalaliDateKey = captured.jalaliDateKey,
            createdLocalSecondOfDay = captured.localSecondOfDay
        )
        dao.addSettlement(
            SettlementEntity(
                settlement.id, driverId, amountRial, direction.dbValue, settlement.notes,
                captured.epochMillis, captured.zoneId, captured.offsetSeconds, captured.jalaliDateKey, captured.localSecondOfDay
            ),
            audit("SETTLEMENT", settlement.id, "CREATE", "driver=$driverId;amount_rial=$amountRial;direction=${direction.dbValue}", captured.epochMillis)
        )
        return settlement
    }

    suspend fun moneyHistory(orderId: String): List<MoneyStateChange> = dao.moneyHistoryRows(orderId).map { it.toDomain() }

    private fun calculateBalances(orders: List<OrderWithNames>, settlements: List<Settlement>, drivers: List<Driver>): List<DriverBalance> {
        val ordersByDriver = orders.groupBy { it.order.driverId }
        val settlementsByDriver = settlements.groupBy { it.driverId }
        return drivers.map { driver ->
            val items = ordersByDriver[driver.id].orEmpty()
            val driverSettlements = settlementsByDriver[driver.id].orEmpty()
            val financialItems = items.filter { it.order.status != OrderStatus.CANCELED }
            DriverBalance(
                driverId = driver.id,
                driverName = driver.name,
                netRial = AccountingEngine.addExact(
                    AccountingEngine.sumExact(items.map { AccountingEngine.driverNetEffect(it.order) }),
                    AccountingEngine.sumExact(driverSettlements.map(AccountingEngine::settlementNetEffect))
                ),
                activeOrderCount = items.count { it.order.status in ACTIVE_ORDER_STATUSES },
                totalCommissionRial = AccountingEngine.sumExact(financialItems.map { it.order.commissionRial }),
                totalOrderRial = AccountingEngine.sumExact(financialItems.map { it.order.amountRial })
            )
        }.sortedByDescending { AccountingEngine.safeAbsolute(it.netRial) }
    }

    private fun calculateDashboard(orders: List<OrderWithNames>, balances: List<DriverBalance>, todayKey: Int): DashboardStats {
        val todayOrders = orders.filter { it.order.createdJalaliDateKey == todayKey && it.order.status != OrderStatus.CANCELED }
        return DashboardStats(
            todayOrderCount = todayOrders.size,
            todayGrossRial = AccountingEngine.sumExact(todayOrders.map { it.order.amountRial }),
            todayCommissionRial = AccountingEngine.sumExact(todayOrders.map { it.order.commissionRial }),
            driverReceivableRial = AccountingEngine.sumExact(balances.filter { it.netRial > 0 }.map { it.netRial }),
            officePayableRial = AccountingEngine.sumExact(balances.filter { it.netRial < 0 }.map { AccountingEngine.safeAbsolute(it.netRial) }),
            openOrderCount = orders.count { it.order.status in ACTIVE_ORDER_STATUSES }
        )
    }

    private fun audit(type: String, entityId: String, action: String, details: String?, now: Long) =
        AuditEventEntity(UUID.randomUUID().toString(), type, entityId, action, details, now)

    companion object {
        private const val MAX_NAME_LENGTH = 120
        private const val MAX_NEIGHBORHOOD_LENGTH = 100
        private const val MAX_PLATE_LENGTH = 40
        private const val MAX_NOTES_LENGTH = 1_000
        private val ACTIVE_ORDER_STATUSES = setOf(OrderStatus.REGISTERED, OrderStatus.SENT_TO_DRIVER, OrderStatus.IN_PROGRESS)
    }
}
