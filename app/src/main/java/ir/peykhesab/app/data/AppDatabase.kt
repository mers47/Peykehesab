package ir.peykhesab.app.data

import android.content.Context
import java.io.File
import androidx.room3.*
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import ir.peykhesab.app.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "neighborhoods",
    indices = [Index(value = ["active_normalized_name"], unique = true)]
)
data class NeighborhoodEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    @ColumnInfo(name = "active_normalized_name") val activeNormalizedName: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "customers",
    foreignKeys = [ForeignKey(
        entity = NeighborhoodEntity::class,
        parentColumns = ["id"], childColumns = ["neighborhood_id"],
        onDelete = ForeignKey.SET_NULL
    )],
    indices = [Index("neighborhood_id"), Index(value = ["normalized_name", "normalized_phone", "is_archived"])]
)
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val phone: String?,
    @ColumnInfo(name = "normalized_phone") val normalizedPhone: String?,
    @ColumnInfo(name = "neighborhood_id") val neighborhoodId: String?,
    val notes: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "drivers",
    indices = [Index(value = ["normalized_name", "normalized_phone", "is_archived"])]
)
data class DriverEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "normalized_name") val normalizedName: String,
    val phone: String?,
    @ColumnInfo(name = "normalized_phone") val normalizedPhone: String?,
    val plate: String?,
    @ColumnInfo(name = "card_number") val cardNumber: String?,
    val notes: String?,
    @ColumnInfo(name = "is_archived") val isArchived: Boolean,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(entity = CustomerEntity::class, parentColumns = ["id"], childColumns = ["customer_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = DriverEntity::class, parentColumns = ["id"], childColumns = ["driver_id"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = NeighborhoodEntity::class, parentColumns = ["id"], childColumns = ["neighborhood_id"], onDelete = ForeignKey.RESTRICT)
    ],
    indices = [
        Index(value = ["id"], unique = true),
        Index(value = ["driver_id", "created_at"]),
        Index(value = ["customer_id", "created_at"]),
        Index("neighborhood_id"),
        Index("created_jalali_date_key"),
        Index(value = ["money_holder", "status"])
    ]
)
data class OrderEntity(
    @PrimaryKey(autoGenerate = true) val sequence: Long = 0,
    val id: String,
    @ColumnInfo(name = "customer_id") val customerId: String,
    @ColumnInfo(name = "driver_id") val driverId: String,
    @ColumnInfo(name = "neighborhood_id") val neighborhoodId: String,
    @ColumnInfo(name = "amount_rial") val amountRial: Long,
    @ColumnInfo(name = "commission_bps") val commissionBasisPoints: Int,
    @ColumnInfo(name = "commission_rial") val commissionRial: Long,
    @ColumnInfo(name = "driver_share_rial") val driverShareRial: Long,
    @ColumnInfo(name = "money_holder") val moneyHolder: String,
    val status: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "created_zone_id") val createdZoneId: String,
    @ColumnInfo(name = "created_offset_seconds") val createdOffsetSeconds: Int,
    @ColumnInfo(name = "created_jalali_date_key") val createdJalaliDateKey: Int,
    @ColumnInfo(name = "created_local_second_of_day") val createdLocalSecondOfDay: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long
)

@Entity(
    tableName = "money_state_history",
    foreignKeys = [ForeignKey(entity = OrderEntity::class, parentColumns = ["id"], childColumns = ["order_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["order_id", "created_at"])]
)
data class MoneyStateHistoryEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "order_id") val orderId: String,
    @ColumnInfo(name = "from_holder") val fromHolder: String,
    @ColumnInfo(name = "to_holder") val toHolder: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "created_zone_id") val createdZoneId: String,
    @ColumnInfo(name = "created_offset_seconds") val createdOffsetSeconds: Int,
    @ColumnInfo(name = "created_jalali_date_key") val createdJalaliDateKey: Int,
    @ColumnInfo(name = "created_local_second_of_day") val createdLocalSecondOfDay: Int
)

@Entity(
    tableName = "settlements",
    foreignKeys = [ForeignKey(entity = DriverEntity::class, parentColumns = ["id"], childColumns = ["driver_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index(value = ["driver_id", "created_at"]), Index("created_at"), Index("created_jalali_date_key")]
)
data class SettlementEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "driver_id") val driverId: String,
    @ColumnInfo(name = "amount_rial") val amountRial: Long,
    val direction: String,
    val notes: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "created_zone_id") val createdZoneId: String,
    @ColumnInfo(name = "created_offset_seconds") val createdOffsetSeconds: Int,
    @ColumnInfo(name = "created_jalali_date_key") val createdJalaliDateKey: Int,
    @ColumnInfo(name = "created_local_second_of_day") val createdLocalSecondOfDay: Int
)

@Entity(tableName = "audit_events", indices = [Index(value = ["entity_type", "entity_id", "created_at"])])
data class AuditEventEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "entity_type") val entityType: String,
    @ColumnInfo(name = "entity_id") val entityId: String,
    val action: String,
    val details: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long
)

data class OrderJoinedRow(
    @Embedded val order: OrderEntity,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "driver_name") val driverName: String,
    @ColumnInfo(name = "neighborhood_name") val neighborhoodName: String
)

@Dao
abstract class AppDao {
    @Query("SELECT * FROM customers WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    abstract fun observeCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM drivers WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    abstract fun observeDrivers(): Flow<List<DriverEntity>>

    @Query("SELECT * FROM neighborhoods WHERE is_archived = 0 ORDER BY name COLLATE NOCASE")
    abstract fun observeNeighborhoods(): Flow<List<NeighborhoodEntity>>

    @Query("""
        SELECT o.*, c.name AS customer_name, d.name AS driver_name, n.name AS neighborhood_name
        FROM orders o
        JOIN customers c ON c.id = o.customer_id
        JOIN drivers d ON d.id = o.driver_id
        JOIN neighborhoods n ON n.id = o.neighborhood_id
        ORDER BY o.created_at DESC, o.sequence DESC
    """)
    abstract fun observeOrders(): Flow<List<OrderJoinedRow>>

    @Query("SELECT * FROM settlements ORDER BY created_at DESC")
    abstract fun observeSettlements(): Flow<List<SettlementEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    abstract suspend fun customerById(id: String): CustomerEntity?

    @Query("SELECT * FROM drivers WHERE id = :id LIMIT 1")
    abstract suspend fun driverById(id: String): DriverEntity?

    @Query("SELECT * FROM neighborhoods WHERE id = :id LIMIT 1")
    abstract suspend fun neighborhoodById(id: String): NeighborhoodEntity?

    @Query("SELECT * FROM neighborhoods WHERE active_normalized_name = :normalizedName LIMIT 1")
    abstract suspend fun activeNeighborhoodByNormalizedName(normalizedName: String): NeighborhoodEntity?

    @Query("SELECT * FROM orders WHERE driver_id = :driverId")
    abstract suspend fun orderRowsByDriver(driverId: String): List<OrderEntity>

    @Query("SELECT * FROM settlements WHERE driver_id = :driverId")
    abstract suspend fun settlementRowsByDriver(driverId: String): List<SettlementEntity>

    @Query("SELECT * FROM orders WHERE id = :id LIMIT 1")
    abstract suspend fun orderById(id: String): OrderEntity?

    @Query("SELECT * FROM neighborhoods ORDER BY created_at, id")
    abstract suspend fun allNeighborhoodRows(): List<NeighborhoodEntity>

    @Query("SELECT * FROM customers ORDER BY created_at, id")
    abstract suspend fun allCustomerRows(): List<CustomerEntity>

    @Query("SELECT * FROM drivers ORDER BY created_at, id")
    abstract suspend fun allDriverRows(): List<DriverEntity>

    @Query("SELECT * FROM orders ORDER BY sequence, id")
    abstract suspend fun allOrderRows(): List<OrderEntity>

    @Query("SELECT * FROM money_state_history ORDER BY created_at, id")
    abstract suspend fun allMoneyStateHistoryRows(): List<MoneyStateHistoryEntity>

    @Query("SELECT * FROM settlements ORDER BY created_at, id")
    abstract suspend fun allSettlementRows(): List<SettlementEntity>

    @Query("SELECT * FROM audit_events ORDER BY created_at, id")
    abstract suspend fun allAuditRows(): List<AuditEventEntity>

    @Upsert
    abstract suspend fun upsertCustomer(entity: CustomerEntity)

    @Upsert
    abstract suspend fun upsertDriver(entity: DriverEntity)

    @Upsert
    abstract suspend fun upsertNeighborhood(entity: NeighborhoodEntity)

    @Insert
    abstract suspend fun insertOrderEntity(entity: OrderEntity): Long

    @Insert
    abstract suspend fun insertSettlementEntity(entity: SettlementEntity)

    @Insert
    abstract suspend fun insertMoneyStateHistory(entity: MoneyStateHistoryEntity)

    @Insert
    abstract suspend fun insertAudit(entity: AuditEventEntity)

    @Insert
    abstract suspend fun insertNeighborhoodRows(entities: List<NeighborhoodEntity>)

    @Insert
    abstract suspend fun insertCustomerRows(entities: List<CustomerEntity>)

    @Insert
    abstract suspend fun insertDriverRows(entities: List<DriverEntity>)

    @Insert
    abstract suspend fun insertOrderRows(entities: List<OrderEntity>)

    @Insert
    abstract suspend fun insertMoneyStateHistoryRows(entities: List<MoneyStateHistoryEntity>)

    @Insert
    abstract suspend fun insertSettlementRows(entities: List<SettlementEntity>)

    @Insert
    abstract suspend fun insertAuditRows(entities: List<AuditEventEntity>)

    @Query("DELETE FROM audit_events")
    abstract suspend fun deleteAllAuditRows()

    @Query("DELETE FROM money_state_history")
    abstract suspend fun deleteAllMoneyStateHistoryRows()

    @Query("DELETE FROM settlements")
    abstract suspend fun deleteAllSettlementRows()

    @Query("DELETE FROM orders")
    abstract suspend fun deleteAllOrderRows()

    @Query("DELETE FROM customers")
    abstract suspend fun deleteAllCustomerRows()

    @Query("DELETE FROM drivers")
    abstract suspend fun deleteAllDriverRows()

    @Query("DELETE FROM neighborhoods")
    abstract suspend fun deleteAllNeighborhoodRows()

    @Query("SELECT COUNT(*) FROM neighborhoods")
    abstract suspend fun neighborhoodCount(): Long

    @Query("SELECT COUNT(*) FROM customers")
    abstract suspend fun customerCount(): Long

    @Query("SELECT COUNT(*) FROM drivers")
    abstract suspend fun driverCount(): Long

    @Query("SELECT COUNT(*) FROM orders")
    abstract suspend fun orderCount(): Long

    @Query("SELECT COUNT(*) FROM money_state_history")
    abstract suspend fun moneyStateHistoryCount(): Long

    @Query("SELECT COUNT(*) FROM settlements")
    abstract suspend fun settlementCount(): Long

    @Query("SELECT COUNT(*) FROM audit_events")
    abstract suspend fun auditCount(): Long

    @Query("UPDATE customers SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun archiveCustomerRow(id: String, updatedAt: Long): Int

    @Query("UPDATE drivers SET is_archived = 1, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun archiveDriverRow(id: String, updatedAt: Long): Int

    @Query("UPDATE neighborhoods SET is_archived = 1, active_normalized_name = NULL, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun archiveNeighborhoodRow(id: String, updatedAt: Long): Int

    @Query("UPDATE orders SET money_holder = :holder, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun updateMoneyHolderRow(id: String, holder: String, updatedAt: Long): Int

    @Query("UPDATE orders SET status = :status, updated_at = :updatedAt WHERE id = :id")
    abstract suspend fun updateOrderStatusRow(id: String, status: String, updatedAt: Long): Int

    @Query("SELECT * FROM money_state_history WHERE order_id = :orderId ORDER BY created_at DESC")
    abstract suspend fun moneyHistoryRows(orderId: String): List<MoneyStateHistoryEntity>

    @Transaction
    open suspend fun createBackupSnapshot(): DatabaseBackup = DatabaseBackup(
        neighborhoods = allNeighborhoodRows(),
        customers = allCustomerRows(),
        drivers = allDriverRows(),
        orders = allOrderRows(),
        moneyStateHistory = allMoneyStateHistoryRows(),
        settlements = allSettlementRows(),
        auditEvents = allAuditRows()
    )

    @Transaction
    open suspend fun replaceFromBackup(snapshot: DatabaseBackup) {
        deleteAllAuditRows()
        deleteAllMoneyStateHistoryRows()
        deleteAllSettlementRows()
        deleteAllOrderRows()
        deleteAllCustomerRows()
        deleteAllDriverRows()
        deleteAllNeighborhoodRows()

        if (snapshot.neighborhoods.isNotEmpty()) insertNeighborhoodRows(snapshot.neighborhoods)
        if (snapshot.drivers.isNotEmpty()) insertDriverRows(snapshot.drivers)
        if (snapshot.customers.isNotEmpty()) insertCustomerRows(snapshot.customers)
        if (snapshot.orders.isNotEmpty()) insertOrderRows(snapshot.orders)
        if (snapshot.moneyStateHistory.isNotEmpty()) insertMoneyStateHistoryRows(snapshot.moneyStateHistory)
        if (snapshot.settlements.isNotEmpty()) insertSettlementRows(snapshot.settlements)
        if (snapshot.auditEvents.isNotEmpty()) insertAuditRows(snapshot.auditEvents)

        check(neighborhoodCount() == snapshot.neighborhoods.size.toLong()) { "تعداد محله‌های بازیابی‌شده نامعتبر است" }
        check(customerCount() == snapshot.customers.size.toLong()) { "تعداد مشتریان بازیابی‌شده نامعتبر است" }
        check(driverCount() == snapshot.drivers.size.toLong()) { "تعداد رانندگان بازیابی‌شده نامعتبر است" }
        check(orderCount() == snapshot.orders.size.toLong()) { "تعداد سفارش‌های بازیابی‌شده نامعتبر است" }
        check(moneyStateHistoryCount() == snapshot.moneyStateHistory.size.toLong()) { "تعداد سوابق وجه بازیابی‌شده نامعتبر است" }
        check(settlementCount() == snapshot.settlements.size.toLong()) { "تعداد تسویه‌های بازیابی‌شده نامعتبر است" }
        check(auditCount() == snapshot.auditEvents.size.toLong()) { "تعداد رویدادهای حسابرسی بازیابی‌شده نامعتبر است" }
    }

    @Transaction
    open suspend fun saveCustomer(entity: CustomerEntity, audit: AuditEventEntity) {
        upsertCustomer(entity)
        insertAudit(audit)
    }

    @Transaction
    open suspend fun saveDriver(entity: DriverEntity, audit: AuditEventEntity) {
        upsertDriver(entity)
        insertAudit(audit)
    }

    @Transaction
    open suspend fun saveNeighborhood(entity: NeighborhoodEntity, audit: AuditEventEntity) {
        upsertNeighborhood(entity)
        insertAudit(audit)
    }

    @Transaction
    open suspend fun archiveCustomer(id: String, now: Long, audit: AuditEventEntity) {
        check(archiveCustomerRow(id, now) == 1) { "مشتری پیدا نشد" }
        insertAudit(audit)
    }

    @Transaction
    open suspend fun archiveDriver(id: String, now: Long, audit: AuditEventEntity) {
        val driver = driverById(id)
        require(driver?.isArchived == false) { "راننده پیدا نشد یا قبلاً بایگانی شده است" }
        val orders = orderRowsByDriver(id).map { it.toDomain() }
        require(orders.none { it.status in ACTIVE_ORDER_STATUSES }) { "راننده سفارش باز دارد و فعلاً قابل بایگانی نیست" }
        require(orders.none { it.status != OrderStatus.CANCELED && it.moneyHolder in UNRESOLVED_MONEY_HOLDERS }) {
            "راننده سفارش با وضعیت وجه نامشخص یا پرداخت‌نشده دارد و قابل بایگانی نیست"
        }
        val settlements = settlementRowsByDriver(id).map { it.toDomain() }
        val net = AccountingEngine.addExact(
            AccountingEngine.sumExact(orders.map(AccountingEngine::driverNetEffect)),
            AccountingEngine.sumExact(settlements.map(AccountingEngine::settlementNetEffect))
        )
        require(net == 0L) { "مانده راننده باید قبل از بایگانی صفر شود" }
        check(archiveDriverRow(id, now) == 1) { "بایگانی راننده ناموفق بود" }
        insertAudit(audit)
    }

    @Transaction
    open suspend fun archiveNeighborhood(id: String, now: Long, audit: AuditEventEntity) {
        check(archiveNeighborhoodRow(id, now) == 1) { "محله پیدا نشد" }
        insertAudit(audit)
    }

    @Transaction
    open suspend fun createOrder(entity: OrderEntity, audit: AuditEventEntity): Long {
        require(customerById(entity.customerId)?.isArchived == false) { "مشتری انتخاب‌شده فعال نیست" }
        require(driverById(entity.driverId)?.isArchived == false) { "راننده انتخاب‌شده فعال نیست" }
        require(neighborhoodById(entity.neighborhoodId)?.isArchived == false) { "محله انتخاب‌شده فعال نیست" }
        val sequence = insertOrderEntity(entity)
        insertAudit(audit)
        return sequence
    }

    @Transaction
    open suspend fun changeMoneyHolder(
        orderId: String,
        holder: String,
        capturedAt: Long,
        historyId: String,
        capturedZoneId: String,
        capturedOffsetSeconds: Int,
        capturedJalaliDateKey: Int,
        capturedLocalSecondOfDay: Int,
        audit: AuditEventEntity
    ) {
        val existing = orderById(orderId) ?: error("سفارش پیدا نشد")
        require(driverById(existing.driverId)?.isArchived == false) { "راننده این سفارش بایگانی شده و سابقه فقط خواندنی است" }
        require(existing.status != OrderStatus.CANCELED.dbValue) { "سفارش لغوشده قابل تغییر نیست" }
        if (existing.moneyHolder == holder) return
        check(updateMoneyHolderRow(orderId, holder, capturedAt) == 1) { "ثبت وضعیت وجه ناموفق بود" }
        insertMoneyStateHistory(
            MoneyStateHistoryEntity(
                id = historyId,
                orderId = orderId,
                fromHolder = existing.moneyHolder,
                toHolder = holder,
                createdAt = capturedAt,
                createdZoneId = capturedZoneId,
                createdOffsetSeconds = capturedOffsetSeconds,
                createdJalaliDateKey = capturedJalaliDateKey,
                createdLocalSecondOfDay = capturedLocalSecondOfDay
            )
        )
        insertAudit(audit.copy(details = "${existing.moneyHolder}->$holder"))
    }

    @Transaction
    open suspend fun changeOrderStatus(orderId: String, status: String, now: Long, audit: AuditEventEntity) {
        val existing = orderById(orderId) ?: error("سفارش پیدا نشد")
        require(driverById(existing.driverId)?.isArchived == false) { "راننده این سفارش بایگانی شده و سابقه فقط خواندنی است" }
        if (existing.status == status) return
        val current = OrderStatus.from(existing.status)
        val target = OrderStatus.from(status)
        require(OrderStatusRules.canTransition(current, target)) { "بازگشت به وضعیت قبلی سفارش مجاز نیست" }
        if (target == OrderStatus.CANCELED) {
            require(MoneyHolder.from(existing.moneyHolder) in UNRESOLVED_MONEY_HOLDERS) {
                "برای لغو، ابتدا وجه سفارش باید پرداخت‌نشده یا نامشخص ثبت شود تا پول از حساب ناپدید نشود"
            }
        }
        check(updateOrderStatusRow(orderId, status, now) == 1) { "تغییر وضعیت سفارش ناموفق بود" }
        insertAudit(audit.copy(details = "${existing.status}->$status"))
    }

    @Transaction
    open suspend fun addSettlement(entity: SettlementEntity, audit: AuditEventEntity) {
        require(driverById(entity.driverId)?.isArchived == false) { "راننده پیدا نشد یا بایگانی شده است" }
        require(entity.amountRial > 0) { "مبلغ تسویه باید بیشتر از صفر باشد" }
        val orders = orderRowsByDriver(entity.driverId).map { it.toDomain() }
        val settlements = settlementRowsByDriver(entity.driverId).map { it.toDomain() }
        val currentNet = AccountingEngine.addExact(
            AccountingEngine.sumExact(orders.map(AccountingEngine::driverNetEffect)),
            AccountingEngine.sumExact(settlements.map(AccountingEngine::settlementNetEffect))
        )
        require(currentNet != 0L) { "مانده راننده صفر است و نیازی به تسویه ندارد" }
        when (SettlementDirection.from(entity.direction)) {
            SettlementDirection.DRIVER_TO_OFFICE -> require(currentNet > 0) { "در این وضعیت دفتر به راننده بدهکار است؛ جهت تسویه را اصلاح کنید" }
            SettlementDirection.OFFICE_TO_DRIVER -> require(currentNet < 0) { "در این وضعیت راننده به دفتر بدهکار است؛ جهت تسویه را اصلاح کنید" }
        }
        require(entity.amountRial <= AccountingEngine.safeAbsolute(currentNet)) { "مبلغ تسویه نمی‌تواند از مانده فعلی بیشتر باشد" }
        insertSettlementEntity(entity)
        insertAudit(audit)
    }

    companion object {
        private val ACTIVE_ORDER_STATUSES = setOf(OrderStatus.REGISTERED, OrderStatus.SENT_TO_DRIVER, OrderStatus.IN_PROGRESS)
        private val UNRESOLVED_MONEY_HOLDERS = setOf(MoneyHolder.UNKNOWN, MoneyHolder.UNPAID)
    }
}

@Database(
    entities = [
        NeighborhoodEntity::class,
        CustomerEntity::class,
        DriverEntity::class,
        OrderEntity::class,
        MoneyStateHistoryEntity::class,
        SettlementEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private const val DB_NAME = "peykhesab.db"

        fun create(context: Context): AppDatabase {
            val appContext = context.applicationContext
            val dbPath = appContext.getDatabasePath(DB_NAME)
            // Ensure the databases directory exists — on fresh installs (especially API 37)
            // the /databases/ directory may not exist yet, causing ENOENT on the .lck file.
            val dbDir = dbPath.parentFile ?: java.io.File(appContext.dataDir, "databases")
            if (!dbDir.exists()) {
                dbDir.mkdirs()
            }
            // Also ensure via getDir as a fallback (creates /app_databases/ if needed)
            appContext.getDir("databases", android.content.Context.MODE_PRIVATE)
            return Room.databaseBuilder<AppDatabase>(appContext, dbPath.absolutePath)
                .setDriver(BundledSQLiteDriver())
                .setQueryCoroutineContext(Dispatchers.IO)
                .build()
        }
    }
}

internal fun CustomerEntity.toDomain() = Customer(id, name, phone, neighborhoodId, notes, isArchived, createdAt, updatedAt)
internal fun DriverEntity.toDomain() = Driver(id, name, phone, plate, cardNumber, notes, isArchived, createdAt, updatedAt)
internal fun NeighborhoodEntity.toDomain() = Neighborhood(id, name, isArchived, createdAt, updatedAt)
internal fun SettlementEntity.toDomain() = Settlement(
    id, driverId, amountRial, SettlementDirection.from(direction), notes, createdAt,
    createdZoneId, createdOffsetSeconds, createdJalaliDateKey, createdLocalSecondOfDay
)
internal fun MoneyStateHistoryEntity.toDomain() = MoneyStateChange(
    id, orderId, MoneyHolder.from(fromHolder), MoneyHolder.from(toHolder), createdAt,
    createdZoneId, createdOffsetSeconds, createdJalaliDateKey, createdLocalSecondOfDay
)
internal fun OrderEntity.toDomain() = DeliveryOrder(
    id = id,
    sequence = sequence,
    customerId = customerId,
    driverId = driverId,
    neighborhoodId = neighborhoodId,
    amountRial = amountRial,
    commissionBasisPoints = commissionBasisPoints,
    commissionRial = commissionRial,
    driverShareRial = driverShareRial,
    moneyHolder = MoneyHolder.from(moneyHolder),
    status = OrderStatus.from(status),
    notes = notes,
    createdAt = createdAt,
    createdZoneId = createdZoneId,
    createdOffsetSeconds = createdOffsetSeconds,
    createdJalaliDateKey = createdJalaliDateKey,
    createdLocalSecondOfDay = createdLocalSecondOfDay,
    updatedAt = updatedAt
)
internal fun OrderJoinedRow.toDomain() = OrderWithNames(order.toDomain(), customerName, driverName, neighborhoodName)
