package ir.peykhesab.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.domain.AccountingEngine
import ir.peykhesab.app.domain.MoneyFormatter
import ir.peykhesab.app.domain.MoneyHolder
import ir.peykhesab.app.domain.PersianNormalizer

@Composable
fun StatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    supporting: String? = null
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            supporting?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
fun MoneyHolderSelector(
    selected: MoneyHolder,
    onSelected: (MoneyHolder) -> Unit,
    modifier: Modifier = Modifier,
    includeUnknown: Boolean = true,
    enabled: Boolean = true
) {
    val values = if (includeUnknown) MoneyHolder.entries else listOf(MoneyHolder.DRIVER, MoneyHolder.OFFICE, MoneyHolder.UNPAID)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("وضعیت وجه", style = MaterialTheme.typography.titleMedium)
        values.forEach { holder ->
            val chosen = holder == selected
            Surface(
                modifier = Modifier.fillMaxWidth().clickable(enabled = enabled) { onSelected(holder) },
                shape = MaterialTheme.shapes.medium,
                color = if (chosen) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, if (chosen) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline.copy(alpha = .55f))
            ) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(selected = chosen, onClick = { onSelected(holder) }, enabled = enabled)
                    Text(holder.titleFa, modifier = Modifier.weight(1f), fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal)
                    if (chosen) Icon(painterResource(R.drawable.ic_check_circle), null, tint = MaterialTheme.colorScheme.secondary)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SearchPicker(
    label: String,
    items: List<T>,
    selected: T?,
    title: (T) -> String,
    subtitle: ((T) -> String?)? = null,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    emptyHint: String = "موردی ثبت نشده است"
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(items, query) {
        val q = PersianNormalizer.normalizeText(query)
        if (q.isBlank()) items else items.filter { PersianNormalizer.normalizeText(title(it)).contains(q) }
    }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected?.let(title) ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable, true),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    placeholder = { Text("جست‌وجو...") },
                    singleLine = true
                )
            }
            if (filtered.isEmpty()) {
                DropdownMenuItem(text = { Text(emptyHint) }, onClick = {}, enabled = false)
            } else {
                filtered.take(30).forEach { item ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(title(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                subtitle?.invoke(item)?.takeIf { !it.isNullOrBlank() }?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        onClick = { onSelected(item); expanded = false; query = "" }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, description: String, actionText: String? = null, onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (actionText != null && onAction != null) Button(onClick = onAction) { Text(actionText) }
    }
}

@Composable
fun BalanceText(netRial: Long) {
    val (text, color) = when {
        netRial > 0 -> "بدهی راننده به دفتر: ${MoneyFormatter.rialToTomanText(netRial)}" to MaterialTheme.colorScheme.error
        netRial < 0 -> "طلب راننده از دفتر: ${MoneyFormatter.rialToTomanText(AccountingEngine.safeAbsolute(netRial))}" to MaterialTheme.colorScheme.secondary
        else -> "تسویه" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(text, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}
