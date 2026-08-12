package ir.peykhesab.app.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.EmptyState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun OrdersScreen(vm: AppViewModel, onNewOrder: () -> Unit, onOrder: (String) -> Unit) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var holderFilterName by rememberSaveable { mutableStateOf<String?>(null) }
    val holderFilter = holderFilterName?.let { name -> MoneyHolder.entries.firstOrNull { it.name == name } }
    val filtered by produceState(
        initialValue = orders,
        orders,
        query,
        holderFilter
    ) {
        if (query.isNotBlank()) delay(120)
        value = withContext(Dispatchers.Default) {
            val normalized = PersianNormalizer.normalizeText(query)
            val normalizedSequenceQuery = PersianNormalizer.toEnglishDigits(query).filter(Char::isDigit)
            orders.filter { item ->
                val matchesText = query.isBlank() ||
                    PersianNormalizer.normalizeText(item.customerName).contains(normalized) ||
                    PersianNormalizer.normalizeText(item.driverName).contains(normalized) ||
                    PersianNormalizer.normalizeText(item.neighborhoodName).contains(normalized) ||
                    (normalizedSequenceQuery.isNotBlank() && item.order.sequence.toString().contains(normalizedSequenceQuery))
                matchesText && (holderFilter == null || item.order.moneyHolder == holderFilter)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("سفارش‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${PersianNumberFormatter.integer(filtered.size)} سفارش", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FilledTonalButton(onClick = onNewOrder) {
                Icon(painterResource(R.drawable.ic_add), null)
                Spacer(Modifier.width(6.dp))
                Text("جدید")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("مشتری، راننده، محله یا شماره سفارش") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(selected = holderFilter == null, onClick = { holderFilterName = null }, label = { Text("همه") })
            FilterChip(selected = holderFilter == MoneyHolder.DRIVER, onClick = { holderFilterName = MoneyHolder.DRIVER.name }, label = { Text("دست راننده") })
            FilterChip(selected = holderFilter == MoneyHolder.OFFICE, onClick = { holderFilterName = MoneyHolder.OFFICE.name }, label = { Text("تحویل دفتر") })
        }

        if (filtered.isEmpty()) {
            EmptyState(
                title = if (orders.isEmpty()) "هنوز سفارشی ثبت نشده" else "نتیجه‌ای پیدا نشد",
                description = if (orders.isEmpty()) "اولین سفارش را ثبت کنید؛ حساب هر راننده از روی سفارش‌های واقعی ساخته می‌شود." else "فیلتر یا عبارت جست‌وجو را تغییر دهید.",
                actionText = if (orders.isEmpty()) "ثبت اولین سفارش" else null,
                onAction = if (orders.isEmpty()) onNewOrder else null
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.order.id }) { item ->
                    OrderCard(item = item, onClick = { onOrder(item.order.id) })
                }
                item { Spacer(Modifier.height(92.dp)) }
            }
        }
    }
}

@Composable
private fun OrderCard(item: OrderWithNames, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer) {
                    Text(
                        "شماره ${PersianNumberFormatter.integer(item.order.sequence)}",
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(item.customerName, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.order.status.titleFa, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text("${item.driverName} • ${item.neighborhoodName}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(PersianDateTimeFormatter.orderDateTime(item.order), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(MoneyFormatter.rialToTomanText(item.order.amountRial), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Surface(
                shape = MaterialTheme.shapes.small,
                color = when (item.order.moneyHolder) {
                    MoneyHolder.DRIVER -> MaterialTheme.colorScheme.errorContainer
                    MoneyHolder.OFFICE -> MaterialTheme.colorScheme.secondaryContainer
                    MoneyHolder.UNPAID -> MaterialTheme.colorScheme.tertiaryContainer
                    MoneyHolder.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text("وضعیت وجه: ${item.order.moneyHolder.titleFa}", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
