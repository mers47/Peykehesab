package ir.peykhesab.app

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import ir.peykhesab.app.data.AppRepository
import ir.peykhesab.app.data.BackupService
import ir.peykhesab.app.data.BackupSummary
import ir.peykhesab.app.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

class AppViewModel(private val repository: AppRepository, private val backupService: BackupService) : ViewModel() {
    private val sharing = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000)

    val customers = repository.customers.stateIn(viewModelScope, sharing, emptyList())
    val drivers = repository.drivers.stateIn(viewModelScope, sharing, emptyList())
    val neighborhoods = repository.neighborhoods.stateIn(viewModelScope, sharing, emptyList())
    val orders = repository.orders.stateIn(viewModelScope, sharing, emptyList())
    val settlements = repository.settlements.stateIn(viewModelScope, sharing, emptyList())
    val dashboard = repository.dashboard.stateIn(viewModelScope, sharing, DashboardStats())
    val balances = repository.balances.stateIn(viewModelScope, sharing, emptyList())

    private val eventsChannel = Channel<UiEvent>(Channel.BUFFERED)
    private val createOrderMutex = Mutex()
    private val settlementMutex = Mutex()
    private val _creatingOrder = MutableStateFlow(false)
    private val _recordingSettlement = MutableStateFlow(false)
    private val _backupOperation = MutableStateFlow(false)
    val creatingOrder = _creatingOrder.asStateFlow()
    val recordingSettlement = _recordingSettlement.asStateFlow()
    val backupOperation = _backupOperation.asStateFlow()
    val events = eventsChannel.receiveAsFlow()

    fun saveCustomer(item: Customer, onDone: (() -> Unit)? = null) =
        launchAction("مشتری ذخیره شد", onDone) { repository.saveCustomer(item) }

    fun saveDriver(item: Driver, onDone: (() -> Unit)? = null) =
        launchAction("راننده ذخیره شد", onDone) { repository.saveDriver(item) }

    fun saveNeighborhood(item: Neighborhood, onDone: (() -> Unit)? = null) =
        launchAction("محله ذخیره شد", onDone) { repository.saveNeighborhood(item) }

    fun archiveCustomer(id: String, onDone: (() -> Unit)? = null) = launchAction("مشتری بایگانی شد", onDone) { repository.archiveCustomer(id) }
    fun archiveDriver(id: String, onDone: (() -> Unit)? = null) = launchAction("راننده بایگانی شد", onDone) { repository.archiveDriver(id) }
    fun archiveNeighborhood(id: String, onDone: (() -> Unit)? = null) = launchAction("محله بایگانی شد", onDone) { repository.archiveNeighborhood(id) }

    fun createOrder(
        customerId: String,
        driverId: String,
        neighborhoodId: String,
        amountRial: Long,
        moneyHolder: MoneyHolder,
        notes: String?,
        onCreated: (DeliveryOrder) -> Unit
    ) = viewModelScope.launch {
        if (!createOrderMutex.tryLock()) return@launch
        _creatingOrder.value = true
        var createdOrder: DeliveryOrder? = null
        try {
            val order = repository.createOrder(
                customerId = customerId,
                driverId = driverId,
                neighborhoodId = neighborhoodId,
                amountRial = amountRial,
                moneyHolder = moneyHolder,
                notes = notes
            )
            createdOrder = order
            eventsChannel.send(UiEvent.Message("سفارش شماره ${PersianNumberFormatter.integer(order.sequence)} ثبت شد"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emitError(error)
        } finally {
            _creatingOrder.value = false
            createOrderMutex.unlock()
        }
        createdOrder?.let { order ->
            try {
                onCreated(order)
            } catch (error: Exception) {
                Log.e("PeykHesab", "Order created but UI callback failed", error)
                eventsChannel.send(UiEvent.Message("سفارش ثبت شد؛ باز کردن صفحه جزئیات انجام نشد"))
            }
        }
    }

    fun updateMoneyHolder(orderId: String, holder: MoneyHolder) =
        launchAction("وضعیت وجه ثبت شد") { repository.updateMoneyHolder(orderId, holder) }

    fun updateStatus(orderId: String, status: OrderStatus) =
        launchAction("وضعیت سفارش تغییر کرد") { repository.updateStatus(orderId, status) }

    fun recordSettlement(
        driverId: String,
        amountRial: Long,
        direction: SettlementDirection,
        notes: String? = null,
        onDone: (() -> Unit)? = null
    ) = viewModelScope.launch {
        if (!settlementMutex.tryLock()) {
            eventsChannel.send(UiEvent.Message("ثبت تسویه قبلی هنوز در حال انجام است"))
            return@launch
        }
        _recordingSettlement.value = true
        var completed = false
        try {
            repository.recordSettlement(driverId, amountRial, direction, notes)
            completed = true
            eventsChannel.send(UiEvent.Message("تسویه ثبت شد"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emitError(error)
        } finally {
            _recordingSettlement.value = false
            settlementMutex.unlock()
        }
        if (completed) invokeUiCallbackSafely(onDone)
    }

    suspend fun moneyHistory(orderId: String): Result<List<MoneyStateChange>> = try {
        Result.success(repository.moneyHistory(orderId))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

    fun exportBackup(uri: Uri, passphrase: String, onDone: ((BackupSummary) -> Unit)? = null) =
        runBackupOperation("نسخه پشتیبان رمزگذاری‌شده ذخیره شد", onDone) {
            backupService.exportTo(uri, passphrase.toCharArray())
        }

    fun restoreBackup(uri: Uri, passphrase: String, onDone: ((BackupSummary) -> Unit)? = null) =
        runBackupOperation("بازیابی کامل شد؛ اطلاعات جایگزین شد", onDone) {
            backupService.restoreFrom(uri, passphrase.toCharArray())
        }

    private fun runBackupOperation(
        success: String,
        onDone: ((BackupSummary) -> Unit)?,
        block: suspend () -> BackupSummary
    ) = viewModelScope.launch {
        if (_backupOperation.value) {
            eventsChannel.send(UiEvent.Message("عملیات پشتیبان‌گیری یا بازیابی دیگری در حال انجام است"))
            return@launch
        }
        _backupOperation.value = true
        try {
            val summary = block()
            eventsChannel.send(UiEvent.Message(success))
            onDone?.let { callback ->
                try { callback(summary) } catch (error: Exception) { Log.e("PeykHesab", "Backup UI callback failed", error) }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emitError(error)
        } finally {
            _backupOperation.value = false
        }
    }

    private fun launchAction(success: String, onDone: (() -> Unit)? = null, block: suspend () -> Unit) = viewModelScope.launch {
        var completed = false
        try {
            block()
            completed = true
            eventsChannel.send(UiEvent.Message(success))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            emitError(error)
        }
        if (completed) invokeUiCallbackSafely(onDone)
    }

    private fun invokeUiCallbackSafely(callback: (() -> Unit)?) {
        if (callback == null) return
        try {
            callback()
        } catch (error: Exception) {
            Log.e("PeykHesab", "UI callback failed after a committed operation", error)
        }
    }

    private fun emitError(error: Exception) {
        Log.e("PeykHesab", "Operation failed", error)
        val safeMessage = if (error is IllegalArgumentException || error is IllegalStateException) {
            error.message?.takeIf { it.any { ch -> ch in '\u0600'..'\u06FF' } }
        } else null
        viewModelScope.launch {
            eventsChannel.send(UiEvent.Message(safeMessage ?: "عملیات انجام نشد. لطفاً دوباره تلاش کنید."))
        }
    }

    sealed interface UiEvent { data class Message(val text: String) : UiEvent }

    class Factory(
        private val repository: AppRepository,
        private val backupService: BackupService
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AppViewModel(repository, backupService) as T
    }
}
