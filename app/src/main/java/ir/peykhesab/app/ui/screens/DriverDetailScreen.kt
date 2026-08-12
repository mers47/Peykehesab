package ir.peykhesab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.BalanceText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriverDetailScreen(
    vm: AppViewModel,
    driverId: String,
    onBack: () -> Unit,
    onOrder: (String) -> Unit,
    onNewOrder: () -> Unit
) {
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val settlements by vm.settlements.collectAsStateWithLifecycle()
    val recordingSettlement by vm.recordingSettlement.collectAsStateWithLifecycle()
    val driver = drivers.firstOrNull { it.id == driverId }
    val balance = balances.firstOrNull { it.driverId == driverId }
    val driverOrders = remember(orders, driverId) { orders.filter { it.order.driverId == driverId } }
    val driverSettlements = remember(settlements, driverId) { settlements.filter { it.driverId == driverId } }
    var settlementDirectionName by rememberSaveable { mutableStateOf<String?>(null) }
    val settlementDirection = settlementDirectionName?.let { name -> SettlementDirection.entries.firstOrNull { it.name == name } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(driver?.name ?: "راننده") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "بازگشت") } },
                actions = { IconButton(onClick = onNewOrder) { Icon(painterResource(R.drawable.ic_add_circle), "سفارش جدید") } }
            )
        }
    ) { padding ->
        if (driver == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("راننده پیدا نشد") }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_two_wheeler), null)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(driver.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                driver.phone?.let { Text(PersianNormalizer.toPersianDigits(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        }
                        balance?.let { BalanceText(it.netRial) }
                        Text("${PersianNumberFormatter.integer(driverOrders.size)} سفارش ثبت‌شده • ${PersianNumberFormatter.integer(balance?.activeOrderCount ?: 0)} سفارش باز", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { settlementDirectionName = SettlementDirection.DRIVER_TO_OFFICE.name },
                        enabled = (balance?.netRial ?: 0L) > 0L,
                        modifier = Modifier.weight(1f)
                    ) { Text("پرداخت راننده به دفتر") }
                    FilledTonalButton(
                        onClick = { settlementDirectionName = SettlementDirection.OFFICE_TO_DRIVER.name },
                        enabled = (balance?.netRial ?: 0L) < 0L,
                        modifier = Modifier.weight(1f)
                    ) { Text("پرداخت دفتر به راننده") }
                }
            }

            item {
                Text("سفارش‌های این راننده", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("هر سفارش مبلغ و وضعیت وجه مستقل دارد.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (driverOrders.isEmpty()) {
                item { Text("هنوز سفارشی برای این راننده ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(driverOrders, key = { it.order.id }) { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { onOrder(item.order.id) },
                        colors = CardDefaults.cardColors(
                            containerColor = when (item.order.moneyHolder) {
                                MoneyHolder.DRIVER -> MaterialTheme.colorScheme.errorContainer
                                MoneyHolder.OFFICE -> MaterialTheme.colorScheme.tertiaryContainer
                                else -> MaterialTheme.colorScheme.surface
                            }
                        )
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("شماره ${PersianNumberFormatter.integer(item.order.sequence)} • ${item.customerName}", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text(MoneyFormatter.rialToTomanText(item.order.amountRial), fontWeight = FontWeight.Bold)
                            }
                            Text(PersianDateTimeFormatter.orderDateTime(item.order), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.neighborhoodName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                AssistChip(onClick = { onOrder(item.order.id) }, label = { Text(item.order.moneyHolder.titleFa) })
                            }
                        }
                    }
                }
            }

            item { Text("تاریخچه تسویه", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
            if (driverSettlements.isEmpty()) {
                item { Text("سند تسویه‌ای ثبت نشده است.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(driverSettlements, key = { it.id }) { settlement ->
                    ListItem(
                        headlineContent = { Text(settlement.direction.titleFa, fontWeight = FontWeight.SemiBold) },
                        supportingContent = {
                            Column {
                                Text(PersianDateTimeFormatter.settlementLongDateTime(settlement))
                                settlement.notes?.let { Text(it) }
                            }
                        },
                        trailingContent = { Text(MoneyFormatter.rialToTomanText(settlement.amountRial), fontWeight = FontWeight.Bold) },
                        leadingContent = {
                            Icon(
                                painterResource(
                                    if (settlement.direction == SettlementDirection.DRIVER_TO_OFFICE)
                                        R.drawable.ic_south_west
                                    else
                                        R.drawable.ic_north_east
                                ),
                                null
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    settlementDirection?.let { direction ->
        SettlementDialog(
            direction = direction,
            maxAmountRial = AccountingEngine.safeAbsolute(balance?.netRial ?: 0L),
            inProgress = recordingSettlement,
            onDismiss = { if (!recordingSettlement) settlementDirectionName = null },
            onSave = { amount, notes ->
                vm.recordSettlement(driverId, amount, direction, notes) { settlementDirectionName = null }
            }
        )
    }
}

@Composable
private fun SettlementDialog(
    direction: SettlementDirection,
    maxAmountRial: Long,
    inProgress: Boolean,
    onDismiss: () -> Unit,
    onSave: (Long, String?) -> Unit
) {
    var amountText by rememberSaveable(direction.name) { mutableStateOf("") }
    var notes by rememberSaveable(direction.name) { mutableStateOf("") }
    val rial = MoneyFormatter.tomanInputToRial(amountText)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_account_balance_wallet), null) },
        title = { Text(direction.titleFa) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("این سند مستقل از سفارش‌ها ثبت می‌شود و مانده راننده را اصلاح می‌کند.")
                Text("حداکثر قابل تسویه: ${MoneyFormatter.rialToTomanText(maxAmountRial)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { value -> amountText = PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter(Char::isDigit)) },
                    label = { Text("مبلغ (تومان)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )
                OutlinedTextField(value = notes, onValueChange = { value -> notes = value.take(1_000) }, label = { Text("توضیح اختیاری") })
            }
        },
        confirmButton = {
            Button(
                onClick = { rial?.takeIf { it in 1..maxAmountRial }?.let { onSave(it, notes.takeIf(String::isNotBlank)) } },
                enabled = !inProgress && rial != null && rial in 1..maxAmountRial
            ) {
                if (inProgress) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("در حال ثبت…")
                } else {
                    Text("ثبت سند")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
