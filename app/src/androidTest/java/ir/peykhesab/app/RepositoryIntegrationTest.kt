package ir.peykhesab.app

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.peykhesab.app.domain.AccountingEngine
import ir.peykhesab.app.domain.Customer
import ir.peykhesab.app.domain.Driver
import ir.peykhesab.app.domain.MoneyHolder
import ir.peykhesab.app.domain.Neighborhood
import ir.peykhesab.app.domain.OrderStatus
import ir.peykhesab.app.domain.ReportEngine
import ir.peykhesab.app.domain.SettlementDirection
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RepositoryIntegrationTest {
    @Test
    fun realRoomAccountingFlowRemainsConsistent() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PeykHesabApplication>()
        val repository = app.repository
        val suffix = UUID.randomUUID().toString().take(8)

        val neighborhood = Neighborhood(name = "محله آزمون $suffix")
        val customer = Customer(name = "مشتری آزمون $suffix", neighborhoodId = neighborhood.id)
        val driver = Driver(name = "راننده آزمون $suffix", phone = "09121234567")

        repository.saveNeighborhood(neighborhood)
        repository.saveCustomer(customer)
        repository.saveDriver(driver)

        val first = repository.createOrder(
            customerId = customer.id,
            driverId = driver.id,
            neighborhoodId = neighborhood.id,
            amountRial = 1_000_000L,
            moneyHolder = MoneyHolder.DRIVER
        )
        val second = repository.createOrder(
            customerId = customer.id,
            driverId = driver.id,
            neighborhoodId = neighborhood.id,
            amountRial = 2_500_000L,
            moneyHolder = MoneyHolder.OFFICE
        )

        suspend fun balance() = withTimeout(5_000) {
            repository.balances.first { rows -> rows.any { it.driverId == driver.id } }
                .first { it.driverId == driver.id }
        }

        assertEquals(200_000L - 2_000_000L, balance().netRial)

        repository.updateMoneyHolder(second.id, MoneyHolder.DRIVER)
        assertEquals(700_000L, balance().netRial)

        repository.recordSettlement(
            driverId = driver.id,
            amountRial = 300_000L,
            direction = SettlementDirection.DRIVER_TO_OFFICE,
            notes = "تست یکپارچگی"
        )
        assertEquals(400_000L, balance().netRial)

        val archiveWithBalance = runCatching { repository.archiveDriver(driver.id) }
        assertTrue(archiveWithBalance.isFailure)

        val allOrders = repository.orders.first()
        val allSettlements = repository.settlements.first()
        val dayKey = first.createdJalaliDateKey
        val report = ReportEngine.calculate(allOrders, allSettlements, dayKey, dayKey)
        assertTrue(report.orders.any { it.order.id == first.id })
        assertTrue(report.orders.any { it.order.id == second.id })
        assertTrue(report.settlements.any { it.driverId == driver.id && it.amountRial == 300_000L })

        val unsafeCancel = runCatching { repository.updateStatus(first.id, OrderStatus.CANCELED) }
        assertTrue(unsafeCancel.isFailure)

        repository.updateMoneyHolder(first.id, MoneyHolder.UNPAID)
        repository.updateStatus(first.id, OrderStatus.CANCELED)
        assertEquals(200_000L, balance().netRial)

        val history = repository.moneyHistory(first.id)
        assertTrue(history.any { it.from == MoneyHolder.DRIVER && it.to == MoneyHolder.UNPAID })

        val split = AccountingEngine.split(2_500_000L)
        assertEquals(500_000L, split.commissionRial)
        assertEquals(2_000_000L, split.driverShareRial)
    }

    @Test
    fun concurrentSettlementsCannotOverpayDriverBalance() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PeykHesabApplication>()
        val repository = app.repository
        val suffix = UUID.randomUUID().toString().take(8)

        val neighborhood = Neighborhood(name = "محله همزمان $suffix")
        val customer = Customer(name = "مشتری همزمان $suffix", neighborhoodId = neighborhood.id)
        val driver = Driver(name = "راننده همزمان $suffix")
        repository.saveNeighborhood(neighborhood)
        repository.saveCustomer(customer)
        repository.saveDriver(driver)

        repository.createOrder(
            customerId = customer.id,
            driverId = driver.id,
            neighborhoodId = neighborhood.id,
            amountRial = 1_000_000L,
            moneyHolder = MoneyHolder.DRIVER
        )

        val results = coroutineScope {
            listOf(
                async {
                    runCatching {
                        repository.recordSettlement(
                            driverId = driver.id,
                            amountRial = 150_000L,
                            direction = SettlementDirection.DRIVER_TO_OFFICE,
                            notes = "تسویه همزمان ۱"
                        )
                    }
                },
                async {
                    runCatching {
                        repository.recordSettlement(
                            driverId = driver.id,
                            amountRial = 150_000L,
                            direction = SettlementDirection.DRIVER_TO_OFFICE,
                            notes = "تسویه همزمان ۲"
                        )
                    }
                }
            ).map { it.await() }
        }

        assertEquals(1, results.count { it.isSuccess })
        assertEquals(1, results.count { it.isFailure })

        val finalBalance = withTimeout(5_000) {
            repository.balances.first { rows -> rows.any { it.driverId == driver.id } }
                .first { it.driverId == driver.id }
        }
        assertEquals(50_000L, finalBalance.netRial)
    }

    @Test
    fun encryptedBackupRestoresAllDataAtomicallyAndRejectsWrongPassword() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PeykHesabApplication>()
        val repository = app.repository
        val suffix = UUID.randomUUID().toString().take(8)
        val neighborhood = Neighborhood(name = "محله پشتیبان $suffix")
        val customer = Customer(name = "مشتری پشتیبان $suffix", neighborhoodId = neighborhood.id)
        val driver = Driver(name = "راننده پشتیبان $suffix")
        repository.saveNeighborhood(neighborhood)
        repository.saveCustomer(customer)
        repository.saveDriver(driver)
        val protectedOrder = repository.createOrder(
            customerId = customer.id, driverId = driver.id, neighborhoodId = neighborhood.id,
            amountRial = 3_000_000L, moneyHolder = MoneyHolder.DRIVER
        )

        val file = java.io.File(app.cacheDir, "backup-$suffix.phb")
        val uri = Uri.fromFile(file)
        val password = "رمز-پشتیبان-$suffix"
        val exported = app.backupService.exportTo(uri, password.toCharArray())
        assertTrue(file.isFile && file.length() > 64L)
        assertTrue(exported.orderCount >= 1)

        val wrongPassword = runCatching { app.backupService.restoreFrom(uri, "رمز-اشتباه-۱۲۳۴".toCharArray()) }
        assertTrue(wrongPassword.isFailure)
        assertTrue(repository.orders.first().any { it.order.id == protectedOrder.id })

        val extraOrder = repository.createOrder(
            customerId = customer.id, driverId = driver.id, neighborhoodId = neighborhood.id,
            amountRial = 700_000L, moneyHolder = MoneyHolder.UNPAID
        )
        assertTrue(repository.orders.first().any { it.order.id == extraOrder.id })

        val restored = app.backupService.restoreFrom(uri, password.toCharArray())
        assertEquals(exported.orderCount, restored.orderCount)
        val rows = repository.orders.first()
        assertTrue(rows.any { it.order.id == protectedOrder.id })
        assertTrue(rows.none { it.order.id == extraOrder.id })
        file.delete()
    }

}
