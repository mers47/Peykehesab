package ir.peykhesab.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.MoneyHolderSelector

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(vm: AppViewModel, orderId: String, onBack: () -> Unit, onNewOrder: () -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val item = orders.firstOrNull { it.order.id == orderId }
    val context = LocalContext.current
    var history by remember(orderId) { mutableStateOf<List<MoneyStateChange>>(emptyList()) }
    var historyLoadFailed by remember(orderId) { mutableStateOf(false) }
    var showCancelConfirm by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(orderId, item?.order?.moneyHolder) {
        vm.moneyHistory(orderId)
            .onSuccess { history = it; historyLoadFailed = false }
            .onFailure { historyLoadFailed = true }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item?.let { "سفارش شماره ${PersianNumberFormatter.integer(it.order.sequence)}" } ?: "جزئیات سفارش") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "بازگشت") } },
                actions = { IconButton(onClick = onNewOrder) { Icon(painterResource(R.drawable.ic_add_circle), "سفارش جدید") } }
            )
        }
    ) { padding ->
        if (item == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { Text("سفارش پیدا نشد") }
            return@Scaffold
        }
        val order = item.order
        val isCanceled = order.status == OrderStatus.CANCELED
        val activeDriver = drivers.firstOrNull { it.id == order.driverId }
        val isReadOnly = isCanceled || activeDriver == null
        val canCancelSafely = !isReadOnly && order.moneyHolder in setOf(MoneyHolder.UNKNOWN, MoneyHolder.UNPAID)
        val driverPhone = activeDriver?.phone
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(painterResource(R.drawable.ic_schedule), null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text(PersianDateTimeFormatter.orderLongDateTime(order), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = .12f))
                        Text(item.customerName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${item.driverName} • ${item.neighborhoodName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(MoneyFormatter.rialToTomanText(order.amountRial), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth()) {
                            Text("کمیسیون ${PersianNumberFormatter.percentFromBasisPoints(order.commissionBasisPoints)}", modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.rialToTomanText(order.commissionRial), fontWeight = FontWeight.SemiBold)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            Text("سهم راننده", modifier = Modifier.weight(1f))
                            Text(MoneyFormatter.rialToTomanText(order.driverShareRial), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp)) {
                        MoneyHolderSelector(
                            selected = order.moneyHolder,
                            onSelected = { vm.updateMoneyHolder(order.id, it) },
                            includeUnknown = true,
                            enabled = !isReadOnly
                        )
                        Text(
                            when {
                                isCanceled -> "سفارش لغوشده قفل است و اثر مالی ندارد."
                                activeDriver == null -> "راننده بایگانی شده است؛ این سفارش تاریخی فقط خواندنی است."
                                else -> "هر تغییر وضعیت وجه ثبت می‌شود و مانده راننده بلافاصله دوباره محاسبه خواهد شد."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            item {
                Text("وضعیت سفارش", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val visibleStatuses = listOf(order.status) + OrderStatusRules.allowedNext(order.status).filter { it != OrderStatus.CANCELED }
                    visibleStatuses.distinct().forEach { status ->
                        val selected = order.status == status
                        val selectable = !isReadOnly && status != order.status
                        Surface(
                            onClick = { if (selectable) vm.updateStatus(order.id, status) },
                            enabled = !isReadOnly,
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(1.dp, if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outlineVariant)
                        ) {
                            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = selected, onClick = { if (selectable) vm.updateStatus(order.id, status) }, enabled = !isReadOnly)
                                Text(status.titleFa, modifier = Modifier.weight(1f), fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        if (!driverPhone.isNullOrBlank()) {
                            val body = buildString {
                                append("سفارش شماره ${PersianNumberFormatter.integer(order.sequence)}\n")
                                append("مشتری: ${item.customerName}\n")
                                append("محله: ${item.neighborhoodName}\n")
                                append("زمان ثبت: ${PersianDateTimeFormatter.orderDateTime(order)}\n")
                                append("مبلغ: ${MoneyFormatter.rialToTomanText(order.amountRial)}")
                                order.notes?.let { append("\nتوضیح: $it") }
                            }
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("smsto:${Uri.encode(driverPhone)}")
                                putExtra("sms_body", body)
                            }
                            try {
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "برنامه پیامک در دسترس نیست.", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    enabled = !driverPhone.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(painterResource(R.drawable.ic_sms), null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (driverPhone.isNullOrBlank()) "شماره راننده ثبت نشده" else "ارسال سفارش با پیامک")
                }
            }

            order.notes?.takeIf(String::isNotBlank)?.let { note ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("توضیحات", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(note)
                        }
                    }
                }
            }

            if (historyLoadFailed) {
                item {
                    Text(
                        "خواندن تاریخچه وجه انجام نشد؛ اطلاعات اصلی سفارش تغییری نکرده است.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            if (history.isNotEmpty()) {
                item { Text("تاریخچه وجه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                items(history, key = { it.id }) { change ->
                    ListItem(
                        headlineContent = { Text("از ${change.from.titleFa} به ${change.to.titleFa}") },
                        supportingContent = { Text(PersianDateTimeFormatter.moneyChangeDateTime(change)) },
                        leadingContent = { Icon(painterResource(R.drawable.ic_history), null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                    )
                }
            }

            if (!isReadOnly) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = { showCancelConfirm = true },
                            enabled = canCancelSafely,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(painterResource(R.drawable.ic_cancel), null)
                            Spacer(Modifier.width(6.dp))
                            Text("لغو سفارش", color = if (canCancelSafely) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!canCancelSafely) {
                            Text(
                                "برای لغو، ابتدا وضعیت وجه را «پرداخت‌نشده» یا «نامشخص» ثبت کنید؛ وجه دست راننده یا دفتر نباید با لغو از حساب حذف شود.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    if (showCancelConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelConfirm = false },
            icon = { Icon(painterResource(R.drawable.ic_warning), null) },
            title = { Text("لغو سفارش؟") },
            text = { Text("سفارش پاک نمی‌شود و با وضعیت لغوشده در سوابق باقی می‌ماند. لغو فقط وقتی مجاز است که وجه پرداخت‌نشده یا نامشخص باشد.") },
            confirmButton = { TextButton(onClick = { vm.updateStatus(orderId, OrderStatus.CANCELED); showCancelConfirm = false }) { Text("لغو سفارش", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showCancelConfirm = false }) { Text("انصراف") } }
        )
    }
}
