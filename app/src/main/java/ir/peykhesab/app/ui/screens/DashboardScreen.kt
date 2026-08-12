package ir.peykhesab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.BalanceText
import ir.peykhesab.app.ui.components.StatCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onNewOrder: () -> Unit,
    onOrders: () -> Unit,
    onNeighborhoods: () -> Unit,
    onDrivers: () -> Unit,
    onBackup: () -> Unit
) {
    val stats by vm.dashboard.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    var now by remember { mutableStateOf(DeviceTime.now()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            now = DeviceTime.now()
            delay(30_000)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("پیک‌حساب", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(PersianDateTimeFormatter.deviceLongDateTime(now), color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Icon(painterResource(R.drawable.ic_two_wheeler), null, tint = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            Button(onClick = onNewOrder, modifier = Modifier.fillMaxWidth().height(60.dp), shape = MaterialTheme.shapes.large) {
                Icon(painterResource(R.drawable.ic_add_circle), null)
                Spacer(Modifier.width(8.dp))
                Text("ثبت سفارش جدید", style = MaterialTheme.typography.titleMedium)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("سفارش امروز", PersianNumberFormatter.integer(stats.todayOrderCount), Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                StatCard("کمیسیون امروز", MoneyFormatter.rialToTomanText(stats.todayCommissionRial), Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("طلب دفتر از راننده‌ها", MoneyFormatter.rialToTomanText(stats.driverReceivableRial), Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
                StatCard("بدهی دفتر به راننده‌ها", MoneyFormatter.rialToTomanText(stats.officePayableRial), Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer)
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("سفارش‌ها", R.drawable.ic_receipt_long, onOrders, Modifier.weight(1f))
                QuickAction("راننده‌ها", R.drawable.ic_two_wheeler, onDrivers, Modifier.weight(1f))
                QuickAction("محله‌ها", R.drawable.ic_location_on, onNeighborhoods, Modifier.weight(1f))
            }
        }

        item {
            OutlinedButton(onClick = onBackup, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.ic_save), null)
                Spacer(Modifier.width(8.dp))
                Text("پشتیبان‌گیری و بازیابی")
            }
        }

        if (balances.isNotEmpty()) {
            item { Text("مانده راننده‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            items(balances.take(6), key = { it.driverId }) { balance ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_two_wheeler), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(balance.driverName, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text("${PersianNumberFormatter.integer(balance.activeOrderCount)} باز", style = MaterialTheme.typography.labelMedium)
                        }
                        BalanceText(balance.netRial)
                    }
                }
            }
        }

        if (orders.isNotEmpty()) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("آخرین سفارش‌ها", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOrders) { Text("همه") }
                }
            }
            items(orders.take(5), key = { it.order.id }) { item ->
                ListItem(
                    headlineContent = { Text("شماره ${PersianNumberFormatter.integer(item.order.sequence)} • ${item.customerName}") },
                    supportingContent = { Text("${item.driverName} • ${item.neighborhoodName}\n${PersianDateTimeFormatter.orderDateTime(item.order)}") },
                    trailingContent = { Text(MoneyFormatter.rialToTomanText(item.order.amountRial), fontWeight = FontWeight.Bold) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_local_shipping), null) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.clickable(onClick = onOrders)
                )
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun QuickAction(label: String, iconRes: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
