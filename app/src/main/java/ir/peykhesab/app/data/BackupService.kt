package ir.peykhesab.app.data

import android.content.Context
import android.net.Uri
import ir.peykhesab.app.domain.AccountingEngine
import ir.peykhesab.app.domain.JalaliDate
import ir.peykhesab.app.domain.MoneyHolder
import ir.peykhesab.app.domain.OrderStatus
import ir.peykhesab.app.domain.PersianNormalizer
import ir.peykhesab.app.domain.SettlementDirection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Complete logical database snapshot. Restores are atomic through [AppDao.replaceFromBackup]. */
data class DatabaseBackup(
    val neighborhoods: List<NeighborhoodEntity>,
    val customers: List<CustomerEntity>,
    val drivers: List<DriverEntity>,
    val orders: List<OrderEntity>,
    val moneyStateHistory: List<MoneyStateHistoryEntity>,
    val settlements: List<SettlementEntity>,
    val auditEvents: List<AuditEventEntity>
)

class BackupService(
    private val context: Context,
    private val db: AppDatabase
) {
    private val dao = db.dao()

    suspend fun exportTo(uri: Uri, passphrase: CharArray): BackupSummary = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        try {
            val snapshot = dao.createBackupSnapshot()
            validateSnapshot(snapshot)
            val payload = BackupCodec.encode(snapshot)
            require(payload.size.toLong() <= MAX_BACKUP_PAYLOAD_BYTES) { "حجم اطلاعات برای یک فایل پشتیبان بیش از حد مجاز است" }
            val encrypted = BackupCrypto.encrypt(payload, passphrase)
            require(encrypted.size.toLong() <= MAX_BACKUP_BYTES) { "حجم فایل پشتیبان بیش از حد مجاز است" }
            val resolver = context.contentResolver
            resolver.openOutputStream(uri, "w")?.use { output ->
                output.write(encrypted)
                output.flush()
            } ?: error("امکان ایجاد فایل پشتیبان وجود ندارد")
            val verifiedEncrypted = readEncrypted(uri)
            val verifiedSnapshot = BackupCodec.decode(BackupCrypto.decrypt(verifiedEncrypted, passphrase))
            validateSnapshot(verifiedSnapshot)
            require(verifiedSnapshot == snapshot) { "راستی‌آزمایی فایل ذخیره‌شده با اطلاعات اصلی تطابق ندارد" }
            BackupSummary.from(snapshot, verifiedEncrypted.size.toLong())
        } finally {
            Arrays.fill(passphrase, '\u0000')
        }
    }

    suspend fun restoreFrom(uri: Uri, passphrase: CharArray): BackupSummary = withContext(Dispatchers.IO) {
        requirePassphrase(passphrase)
        try {
            val encrypted = readEncrypted(uri)
            val payload = BackupCrypto.decrypt(encrypted, passphrase)
            val snapshot = BackupCodec.decode(payload)
            validateSnapshot(snapshot)
            dao.replaceFromBackup(snapshot)
            BackupSummary.from(snapshot, encrypted.size.toLong())
        } finally {
            Arrays.fill(passphrase, '\u0000')
        }
    }

    private fun readEncrypted(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) {
                    val single = input.read()
                    if (single < 0) break
                    total = Math.addExact(total, 1L)
                    require(total <= MAX_BACKUP_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
                    output.write(single)
                    continue
                }
                total = Math.addExact(total, count.toLong())
                require(total <= MAX_BACKUP_BYTES) { "فایل پشتیبان بیش از حد بزرگ است" }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("امکان خواندن فایل پشتیبان وجود ندارد")

    private fun requirePassphrase(passphrase: CharArray) {
        require(passphrase.size >= MIN_PASSPHRASE_LENGTH) { "رمز پشتیبان باید حداقل ۸ نویسه باشد" }
    }

    private fun validateSnapshot(snapshot: DatabaseBackup) {
        fun <T> uniqueIds(items: List<T>, id: (T) -> String, title: String) {
            require(items.map(id).all(String::isNotBlank)) { "شناسه خالی در $title وجود دارد" }
            require(items.map(id).toSet().size == items.size) { "شناسه تکراری در $title وجود دارد" }
        }
        uniqueIds(snapshot.neighborhoods, NeighborhoodEntity::id, "محله‌ها")
        uniqueIds(snapshot.customers, CustomerEntity::id, "مشتریان")
        uniqueIds(snapshot.drivers, DriverEntity::id, "رانندگان")
        uniqueIds(snapshot.orders, OrderEntity::id, "سفارش‌ها")
        uniqueIds(snapshot.moneyStateHistory, MoneyStateHistoryEntity::id, "سوابق وجه")
        uniqueIds(snapshot.settlements, SettlementEntity::id, "تسویه‌ها")
        uniqueIds(snapshot.auditEvents, AuditEventEntity::id, "رویدادهای حسابرسی")

        val neighborhoods = snapshot.neighborhoods.associateBy { it.id }
        val customers = snapshot.customers.associateBy { it.id }
        val drivers = snapshot.drivers.associateBy { it.id }
        val orders = snapshot.orders.associateBy { it.id }

        val activeNormalized = snapshot.neighborhoods.filterNot { it.isArchived }.map { it.activeNormalizedName }
        require(activeNormalized.none { it.isNullOrBlank() }) { "نام نرمال‌شده محله فعال ناقص است" }
        require(activeNormalized.filterNotNull().toSet().size == activeNormalized.size) { "محله فعال تکراری در پشتیبان وجود دارد" }
        snapshot.neighborhoods.forEach { row ->
            require(row.name.isNotBlank()) { "نام محله در پشتیبان خالی است" }
            require(row.normalizedName == PersianNormalizer.normalizeText(row.name)) { "نام نرمال‌شده محله در پشتیبان ناسازگار است" }
            require(if (row.isArchived) row.activeNormalizedName == null else row.activeNormalizedName == row.normalizedName) {
                "وضعیت بایگانی محله با کلید فعال آن سازگار نیست"
            }
        }

        snapshot.customers.forEach { row ->
            require(row.name.isNotBlank()) { "نام مشتری در پشتیبان خالی است" }
            require(row.normalizedName == PersianNormalizer.normalizeText(row.name)) { "نام نرمال‌شده مشتری در پشتیبان ناسازگار است" }
            require(row.normalizedPhone == row.phone?.let(PersianNormalizer::normalizePhone)) { "شماره نرمال‌شده مشتری در پشتیبان ناسازگار است" }
            row.neighborhoodId?.let { require(neighborhoods.containsKey(it)) { "محله مشتری در پشتیبان وجود ندارد" } }
            row.phone?.let {
                require(PersianNormalizer.normalizePhone(it).matches(Regex("^09\\d{9}$"))) { "شماره مشتری در پشتیبان نامعتبر است" }
            }
        }
        snapshot.drivers.forEach { row ->
            require(row.name.isNotBlank()) { "نام راننده در پشتیبان خالی است" }
            require(row.normalizedName == PersianNormalizer.normalizeText(row.name)) { "نام نرمال‌شده راننده در پشتیبان ناسازگار است" }
            require(row.normalizedPhone == row.phone?.let(PersianNormalizer::normalizePhone)) { "شماره نرمال‌شده راننده در پشتیبان ناسازگار است" }
            row.phone?.let {
                require(PersianNormalizer.normalizePhone(it).matches(Regex("^09\\d{9}$"))) { "شماره راننده در پشتیبان نامعتبر است" }
            }
            row.cardNumber?.let { require(it.all(Char::isDigit) && it.length == 16) { "شماره کارت راننده در پشتیبان نامعتبر است" } }
        }
        require(snapshot.orders.map { it.sequence }.toSet().size == snapshot.orders.size) { "شماره ترتیبی سفارش تکراری در پشتیبان وجود دارد" }
        snapshot.orders.forEach { row ->
            require(row.sequence > 0L) { "شماره ترتیبی سفارش در پشتیبان نامعتبر است" }
            require(customers.containsKey(row.customerId)) { "مشتری سفارش در پشتیبان وجود ندارد" }
            require(drivers.containsKey(row.driverId)) { "راننده سفارش در پشتیبان وجود ندارد" }
            require(neighborhoods.containsKey(row.neighborhoodId)) { "محله سفارش در پشتیبان وجود ندارد" }
            require(row.amountRial in 1..AccountingEngine.MAX_ORDER_AMOUNT_RIAL) { "مبلغ سفارش در پشتیبان نامعتبر است" }
            require(row.commissionBasisPoints in 0..10_000) { "کمیسیون سفارش در پشتیبان نامعتبر است" }
            val split = AccountingEngine.split(row.amountRial, row.commissionBasisPoints)
            require(split.commissionRial == row.commissionRial && split.driverShareRial == row.driverShareRial) {
                "محاسبات مالی سفارش در پشتیبان با مبلغ و کمیسیون سازگار نیست"
            }
            val holder = MoneyHolder.from(row.moneyHolder)
            val status = OrderStatus.from(row.status)
            if (status == OrderStatus.CANCELED) {
                require(holder == MoneyHolder.UNKNOWN || holder == MoneyHolder.UNPAID) { "سفارش لغوشده با وضعیت وجه مالی در پشتیبان ناسازگار است" }
            }
            validateTimestamp(row.createdAt, row.createdZoneId, row.createdOffsetSeconds, row.createdJalaliDateKey, row.createdLocalSecondOfDay)
        }
        snapshot.moneyStateHistory.forEach { row ->
            require(orders.containsKey(row.orderId)) { "سفارش سابقه وجه در پشتیبان وجود ندارد" }
            val from = MoneyHolder.from(row.fromHolder)
            val to = MoneyHolder.from(row.toHolder)
            require(from != to) { "سابقه تغییر وجه بدون تغییر واقعی در پشتیبان وجود دارد" }
            validateTimestamp(row.createdAt, row.createdZoneId, row.createdOffsetSeconds, row.createdJalaliDateKey, row.createdLocalSecondOfDay)
        }
        snapshot.settlements.forEach { row ->
            require(drivers.containsKey(row.driverId)) { "راننده تسویه در پشتیبان وجود ندارد" }
            require(row.amountRial > 0L) { "مبلغ تسویه در پشتیبان نامعتبر است" }
            SettlementDirection.from(row.direction)
            validateTimestamp(row.createdAt, row.createdZoneId, row.createdOffsetSeconds, row.createdJalaliDateKey, row.createdLocalSecondOfDay)
        }
        snapshot.auditEvents.forEach { row ->
            require(row.action.isNotBlank()) { "عملیات رویداد حسابرسی خالی است" }
            require(row.createdAt >= 0L) { "زمان رویداد حسابرسی نامعتبر است" }
            val exists = when (row.entityType) {
                "CUSTOMER" -> customers.containsKey(row.entityId)
                "DRIVER" -> drivers.containsKey(row.entityId)
                "NEIGHBORHOOD" -> neighborhoods.containsKey(row.entityId)
                "ORDER" -> orders.containsKey(row.entityId)
                "SETTLEMENT" -> snapshot.settlements.any { it.id == row.entityId }
                else -> false
            }
            require(exists) { "مرجع رویداد حسابرسی نامعتبر است" }
        }

        snapshot.drivers.filter { it.isArchived }.forEach { driver ->
            val driverOrders = snapshot.orders.filter { it.driverId == driver.id }
            require(driverOrders.none { OrderStatus.from(it.status) in ACTIVE_ORDER_STATUSES }) { "راننده بایگانی‌شده سفارش باز دارد" }
            require(driverOrders.none { OrderStatus.from(it.status) != OrderStatus.CANCELED && MoneyHolder.from(it.moneyHolder) in UNRESOLVED_MONEY_HOLDERS }) {
                "راننده بایگانی‌شده سفارش با وضعیت وجه حل‌نشده دارد"
            }
            val orderNet = AccountingEngine.sumExact(driverOrders.map { orderNetEffect(it) })
            val settlementNet = AccountingEngine.sumExact(snapshot.settlements.filter { it.driverId == driver.id }.map { settlementNetEffect(it) })
            require(AccountingEngine.addExact(orderNet, settlementNet) == 0L) { "راننده بایگانی‌شده مانده مالی غیرصفر دارد" }
        }
    }

    private fun orderNetEffect(row: OrderEntity): Long {
        if (OrderStatus.from(row.status) == OrderStatus.CANCELED) return 0L
        return when (MoneyHolder.from(row.moneyHolder)) {
            MoneyHolder.DRIVER -> row.commissionRial
            MoneyHolder.OFFICE -> -row.driverShareRial
            MoneyHolder.UNKNOWN, MoneyHolder.UNPAID -> 0L
        }
    }

    private fun settlementNetEffect(row: SettlementEntity): Long = when (SettlementDirection.from(row.direction)) {
        SettlementDirection.DRIVER_TO_OFFICE -> -row.amountRial
        SettlementDirection.OFFICE_TO_DRIVER -> row.amountRial
    }

    private fun validateTimestamp(epoch: Long, zoneId: String, offsetSeconds: Int, jalaliKey: Int, localSecond: Int) {
        require(epoch >= 0L) { "زمان ثبت در پشتیبان نامعتبر است" }
        require(offsetSeconds in -64_800..64_800) { "اختلاف منطقه زمانی در پشتیبان نامعتبر است" }
        require(localSecond in 0..86_399) { "ساعت محلی در پشتیبان نامعتبر است" }
        ZoneId.of(zoneId)
        val offset = ZoneOffset.ofTotalSeconds(offsetSeconds)
        val local = Instant.ofEpochMilli(epoch).atOffset(offset)
        require(local.toLocalTime().toSecondOfDay() == localSecond) { "ساعت snapshot پشتیبان با لحظه ثبت سازگار نیست" }
        require(JalaliDate.fromGregorian(local.toLocalDate()).key() == jalaliKey) { "تاریخ شمسی snapshot پشتیبان با لحظه ثبت سازگار نیست" }
        val jalali = JalaliDate.fromKey(jalaliKey)
        require(JalaliDate.fromGregorian(jalali.toGregorian()) == jalali) { "تاریخ شمسی در پشتیبان نامعتبر است" }
    }

    companion object {
        private const val MIN_PASSPHRASE_LENGTH = 8
        private const val MAX_BACKUP_PAYLOAD_BYTES = 120L * 1024L * 1024L
        private const val MAX_BACKUP_BYTES = 128L * 1024L * 1024L
        private val ACTIVE_ORDER_STATUSES = setOf(OrderStatus.REGISTERED, OrderStatus.SENT_TO_DRIVER, OrderStatus.IN_PROGRESS)
        private val UNRESOLVED_MONEY_HOLDERS = setOf(MoneyHolder.UNKNOWN, MoneyHolder.UNPAID)
    }
}

data class BackupSummary(
    val neighborhoodCount: Int,
    val customerCount: Int,
    val driverCount: Int,
    val orderCount: Int,
    val settlementCount: Int,
    val encryptedBytes: Long
) {
    companion object {
        fun from(snapshot: DatabaseBackup, encryptedBytes: Long) = BackupSummary(
            neighborhoodCount = snapshot.neighborhoods.size,
            customerCount = snapshot.customers.size,
            driverCount = snapshot.drivers.size,
            orderCount = snapshot.orders.size,
            settlementCount = snapshot.settlements.size,
            encryptedBytes = encryptedBytes
        )
    }
}

private object BackupCrypto {
    private val MAGIC = byteArrayOf(0x50, 0x59, 0x4B, 0x48, 0x42, 0x41, 0x4B, 0x32)
    private const val FILE_VERSION = 2
    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KDF_ITERATIONS = 310_000

    fun encrypt(plain: ByteArray, passphrase: CharArray): ByteArray {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plain)
        return ByteArrayOutputStream(MAGIC.size + 4 + 4 + SALT_BYTES + IV_BYTES + encrypted.size).use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.write(MAGIC)
                output.writeInt(FILE_VERSION)
                output.writeInt(KDF_ITERATIONS)
                output.write(salt)
                output.write(iv)
                output.write(encrypted)
            }
            bytes.toByteArray()
        }
    }

    fun decrypt(container: ByteArray, passphrase: CharArray): ByteArray {
        require(container.size > MAGIC.size + 8 + SALT_BYTES + IV_BYTES + 16) { "فایل پشتیبان ناقص یا نامعتبر است" }
        return DataInputStream(ByteArrayInputStream(container)).use { input ->
            val magic = ByteArray(MAGIC.size).also { input.readFully(it) }
            require(magic.contentEquals(MAGIC)) { "این فایل متعلق به پیک‌حساب نیست" }
            require(input.readInt() == FILE_VERSION) { "نسخه فایل پشتیبان پشتیبانی نمی‌شود" }
            val iterations = input.readInt()
            require(iterations == KDF_ITERATIONS) { "پارامتر امنیتی فایل پشتیبان نامعتبر است" }
            val salt = ByteArray(SALT_BYTES).also { input.readFully(it) }
            val iv = ByteArray(IV_BYTES).also { input.readFully(it) }
            val encrypted = ByteArray(input.available()).also { input.readFully(it) }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(128, iv))
            try {
                cipher.doFinal(encrypted)
            } catch (error: Exception) {
                throw IllegalArgumentException("رمز نادرست است یا فایل پشتیبان آسیب دیده است", error)
            }
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val bytes = BackupKdf.deriveKeyBytes(passphrase, salt, KDF_ITERATIONS)
        return try {
            SecretKeySpec(bytes, "AES")
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }
}

private object BackupCodec {
    private const val FORMAT_VERSION = 1

    fun encode(snapshot: DatabaseBackup): ByteArray {
        val root = JSONObject()
            .put("formatVersion", FORMAT_VERSION)
            .put("neighborhoods", JSONArray().apply { snapshot.neighborhoods.forEach { put(it.toJson()) } })
            .put("customers", JSONArray().apply { snapshot.customers.forEach { put(it.toJson()) } })
            .put("drivers", JSONArray().apply { snapshot.drivers.forEach { put(it.toJson()) } })
            .put("orders", JSONArray().apply { snapshot.orders.forEach { put(it.toJson()) } })
            .put("moneyStateHistory", JSONArray().apply { snapshot.moneyStateHistory.forEach { put(it.toJson()) } })
            .put("settlements", JSONArray().apply { snapshot.settlements.forEach { put(it.toJson()) } })
            .put("auditEvents", JSONArray().apply { snapshot.auditEvents.forEach { put(it.toJson()) } })
        return root.toString().toByteArray(Charsets.UTF_8)
    }

    fun decode(bytes: ByteArray): DatabaseBackup {
        val root = JSONObject(bytes.toString(Charsets.UTF_8))
        require(root.getInt("formatVersion") == FORMAT_VERSION) { "نسخه محتوای پشتیبان پشتیبانی نمی‌شود" }
        return DatabaseBackup(
            neighborhoods = root.getJSONArray("neighborhoods").mapObjects(::neighborhoodFromJson),
            customers = root.getJSONArray("customers").mapObjects(::customerFromJson),
            drivers = root.getJSONArray("drivers").mapObjects(::driverFromJson),
            orders = root.getJSONArray("orders").mapObjects(::orderFromJson),
            moneyStateHistory = root.getJSONArray("moneyStateHistory").mapObjects(::moneyHistoryFromJson),
            settlements = root.getJSONArray("settlements").mapObjects(::settlementFromJson),
            auditEvents = root.getJSONArray("auditEvents").mapObjects(::auditFromJson)
        )
    }

    private fun NeighborhoodEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("normalizedName", normalizedName)
        .putNullable("activeNormalizedName", activeNormalizedName).put("isArchived", isArchived)
        .put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun CustomerEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("normalizedName", normalizedName)
        .putNullable("phone", phone).putNullable("normalizedPhone", normalizedPhone)
        .putNullable("neighborhoodId", neighborhoodId).putNullable("notes", notes)
        .put("isArchived", isArchived).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun DriverEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("normalizedName", normalizedName)
        .putNullable("phone", phone).putNullable("normalizedPhone", normalizedPhone)
        .putNullable("plate", plate).putNullable("cardNumber", cardNumber).putNullable("notes", notes)
        .put("isArchived", isArchived).put("createdAt", createdAt).put("updatedAt", updatedAt)

    private fun OrderEntity.toJson() = JSONObject()
        .put("sequence", sequence).put("id", id).put("customerId", customerId).put("driverId", driverId)
        .put("neighborhoodId", neighborhoodId).put("amountRial", amountRial)
        .put("commissionBasisPoints", commissionBasisPoints).put("commissionRial", commissionRial)
        .put("driverShareRial", driverShareRial).put("moneyHolder", moneyHolder).put("status", status)
        .putNullable("notes", notes).put("createdAt", createdAt).put("createdZoneId", createdZoneId)
        .put("createdOffsetSeconds", createdOffsetSeconds).put("createdJalaliDateKey", createdJalaliDateKey)
        .put("createdLocalSecondOfDay", createdLocalSecondOfDay).put("updatedAt", updatedAt)

    private fun MoneyStateHistoryEntity.toJson() = JSONObject()
        .put("id", id).put("orderId", orderId).put("fromHolder", fromHolder).put("toHolder", toHolder)
        .put("createdAt", createdAt).put("createdZoneId", createdZoneId).put("createdOffsetSeconds", createdOffsetSeconds)
        .put("createdJalaliDateKey", createdJalaliDateKey).put("createdLocalSecondOfDay", createdLocalSecondOfDay)

    private fun SettlementEntity.toJson() = JSONObject()
        .put("id", id).put("driverId", driverId).put("amountRial", amountRial).put("direction", direction)
        .putNullable("notes", notes).put("createdAt", createdAt).put("createdZoneId", createdZoneId)
        .put("createdOffsetSeconds", createdOffsetSeconds).put("createdJalaliDateKey", createdJalaliDateKey)
        .put("createdLocalSecondOfDay", createdLocalSecondOfDay)

    private fun AuditEventEntity.toJson() = JSONObject()
        .put("id", id).put("entityType", entityType).put("entityId", entityId).put("action", action)
        .putNullable("details", details).put("createdAt", createdAt)

    private fun neighborhoodFromJson(o: JSONObject) = NeighborhoodEntity(
        o.getString("id"), o.getString("name"), o.getString("normalizedName"), o.optNullableString("activeNormalizedName"),
        o.getBoolean("isArchived"), o.getLong("createdAt"), o.getLong("updatedAt")
    )

    private fun customerFromJson(o: JSONObject) = CustomerEntity(
        o.getString("id"), o.getString("name"), o.getString("normalizedName"), o.optNullableString("phone"),
        o.optNullableString("normalizedPhone"), o.optNullableString("neighborhoodId"), o.optNullableString("notes"),
        o.getBoolean("isArchived"), o.getLong("createdAt"), o.getLong("updatedAt")
    )

    private fun driverFromJson(o: JSONObject) = DriverEntity(
        o.getString("id"), o.getString("name"), o.getString("normalizedName"), o.optNullableString("phone"),
        o.optNullableString("normalizedPhone"), o.optNullableString("plate"), o.optNullableString("cardNumber"),
        o.optNullableString("notes"), o.getBoolean("isArchived"), o.getLong("createdAt"), o.getLong("updatedAt")
    )

    private fun orderFromJson(o: JSONObject) = OrderEntity(
        sequence = o.getLong("sequence"), id = o.getString("id"), customerId = o.getString("customerId"),
        driverId = o.getString("driverId"), neighborhoodId = o.getString("neighborhoodId"), amountRial = o.getLong("amountRial"),
        commissionBasisPoints = o.getInt("commissionBasisPoints"), commissionRial = o.getLong("commissionRial"),
        driverShareRial = o.getLong("driverShareRial"), moneyHolder = o.getString("moneyHolder"), status = o.getString("status"),
        notes = o.optNullableString("notes"), createdAt = o.getLong("createdAt"), createdZoneId = o.getString("createdZoneId"),
        createdOffsetSeconds = o.getInt("createdOffsetSeconds"), createdJalaliDateKey = o.getInt("createdJalaliDateKey"),
        createdLocalSecondOfDay = o.getInt("createdLocalSecondOfDay"), updatedAt = o.getLong("updatedAt")
    )

    private fun moneyHistoryFromJson(o: JSONObject) = MoneyStateHistoryEntity(
        o.getString("id"), o.getString("orderId"), o.getString("fromHolder"), o.getString("toHolder"),
        o.getLong("createdAt"), o.getString("createdZoneId"), o.getInt("createdOffsetSeconds"),
        o.getInt("createdJalaliDateKey"), o.getInt("createdLocalSecondOfDay")
    )

    private fun settlementFromJson(o: JSONObject) = SettlementEntity(
        o.getString("id"), o.getString("driverId"), o.getLong("amountRial"), o.getString("direction"),
        o.optNullableString("notes"), o.getLong("createdAt"), o.getString("createdZoneId"),
        o.getInt("createdOffsetSeconds"), o.getInt("createdJalaliDateKey"), o.getInt("createdLocalSecondOfDay")
    )

    private fun auditFromJson(o: JSONObject) = AuditEventEntity(
        o.getString("id"), o.getString("entityType"), o.getString("entityId"), o.getString("action"),
        o.optNullableString("details"), o.getLong("createdAt")
    )

    private fun JSONObject.putNullable(key: String, value: String?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.optNullableString(key: String): String? = if (isNull(key)) null else getString(key)

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
        List(length()) { index -> transform(getJSONObject(index)) }
}
