package ir.peykhesab.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
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
import ir.peykhesab.app.ui.components.EmptyState
import ir.peykhesab.app.ui.components.SearchPicker

@Composable
fun CustomersScreen(vm: AppViewModel) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val neighborhoods by vm.neighborhoods.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val editing = customers.firstOrNull { it.id == editingId }
    val filtered = remember(customers, query) {
        val q = PersianNormalizer.normalizeText(query)
        val phoneQuery = PersianNormalizer.normalizePhone(query).takeIf { value -> value.any(Char::isDigit) }
        customers.filter { customer ->
            query.isBlank() ||
                PersianNormalizer.normalizeText(customer.name).contains(q) ||
                (phoneQuery != null && customer.phone?.contains(phoneQuery) == true)
        }
    }

    EntityListScaffold(
        title = "مشتریان",
        query = query,
        onQuery = { query = it },
        onAdd = { showAdd = true },
        empty = customers.isEmpty(),
        emptyTitle = "مشتری ثبت نشده",
        emptyDescription = "مشتریان را یک‌بار ثبت کن تا موقع سفارش با جست‌وجوی سریع انتخاب شوند."
    ) {
        items(filtered, key = { it.id }) { customer ->
            Card(modifier = Modifier.fillMaxWidth().clickable { editingId = customer.id }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) { Icon(painterResource(R.drawable.ic_person), null, modifier = Modifier.padding(10.dp)) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium)
                        customer.phone?.let { Text(PersianNormalizer.toPersianDigits(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        customer.neighborhoodId?.let { id -> neighborhoods.firstOrNull { it.id == id }?.let { Text(it.name, style = MaterialTheme.typography.bodySmall) } }
                    }
                    Icon(painterResource(R.drawable.ic_edit), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (showAdd || editing != null) {
        CustomerDialog(
            initial = editing,
            neighborhoods = neighborhoods,
            onDismiss = { showAdd = false; editingId = null },
            onSave = { vm.saveCustomer(it) { showAdd = false; editingId = null } },
            onArchive = editing?.let { item -> { vm.archiveCustomer(item.id) { editingId = null } } }
        )
    }
}

@Composable
fun DriversScreen(vm: AppViewModel, onDriver: ((String) -> Unit)? = null) {
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val balances by vm.balances.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val editing = drivers.firstOrNull { it.id == editingId }
    val filtered = remember(drivers, query) {
        val q = PersianNormalizer.normalizeText(query)
        val phoneQuery = PersianNormalizer.normalizePhone(query).takeIf { value -> value.any(Char::isDigit) }
        drivers.filter { driver ->
            query.isBlank() ||
                PersianNormalizer.normalizeText(driver.name).contains(q) ||
                (phoneQuery != null && driver.phone?.contains(phoneQuery) == true)
        }
    }

    EntityListScaffold(
        title = "راننده‌ها",
        query = query,
        onQuery = { query = it },
        onAdd = { showAdd = true },
        empty = drivers.isEmpty(),
        emptyTitle = "راننده ثبت نشده",
        emptyDescription = "هر راننده می‌تواند هم‌زمان هر تعداد سفارش مستقل داشته باشد."
    ) {
        items(filtered, key = { it.id }) { driver ->
            val balance = balances.firstOrNull { it.driverId == driver.id }
            Card(
                modifier = Modifier.fillMaxWidth().clickable { if (onDriver != null) onDriver(driver.id) else editingId = driver.id },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer) { Icon(painterResource(R.drawable.ic_two_wheeler), null, modifier = Modifier.padding(10.dp)) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(driver.name, style = MaterialTheme.typography.titleMedium)
                            driver.phone?.let { Text(PersianNormalizer.toPersianDigits(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        IconButton(onClick = { editingId = driver.id }) { Icon(painterResource(R.drawable.ic_edit), "ویرایش") }
                    }
                    balance?.let {
                        val text = when {
                            it.netRial > 0 -> "بدهی به دفتر: ${MoneyFormatter.rialToTomanText(it.netRial)}"
                            it.netRial < 0 -> "طلب از دفتر: ${MoneyFormatter.rialToTomanText(AccountingEngine.safeAbsolute(it.netRial))}"
                            else -> "مانده: تسویه"
                        }
                        Text(text, fontWeight = FontWeight.SemiBold, color = if (it.netRial > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
                        Text("${PersianNumberFormatter.integer(it.activeOrderCount)} سفارش باز", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (showAdd || editing != null) {
        DriverDialog(
            initial = editing,
            onDismiss = { showAdd = false; editingId = null },
            onSave = { vm.saveDriver(it) { showAdd = false; editingId = null } },
            onArchive = editing?.let { item -> { vm.archiveDriver(item.id) { editingId = null } } }
        )
    }
}

@Composable
fun NeighborhoodsScreen(vm: AppViewModel, onBack: (() -> Unit)? = null) {
    val items by vm.neighborhoods.collectAsStateWithLifecycle()
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val editing = items.firstOrNull { it.id == editingId }
    val filtered = remember(items, query) {
        val q = PersianNormalizer.normalizeText(query)
        items.filter { query.isBlank() || PersianNormalizer.normalizeText(it.name).contains(q) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "بازگشت") }
            Text("محله‌ها", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = { showAdd = true }) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(4.dp)); Text("جدید") }
        }
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("جست‌وجوی محله") }, leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) }, singleLine = true, shape = MaterialTheme.shapes.large
        )
        if (items.isEmpty()) EmptyState("محله ثبت نشده", "محله‌ها را از قبل بساز تا ثبت سفارش بسیار سریع باشد.", "افزودن محله") { showAdd = true }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { item ->
                ListItem(
                    headlineContent = { Text(item.name, fontWeight = FontWeight.SemiBold) },
                    leadingContent = { Icon(painterResource(R.drawable.ic_location_on), null, tint = MaterialTheme.colorScheme.tertiary) },
                    trailingContent = { Icon(painterResource(R.drawable.ic_edit), null) },
                    modifier = Modifier.clickable { editingId = item.id },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface)
                )
            }
        }
    }

    if (showAdd || editing != null) {
        NeighborhoodDialog(
            initial = editing,
            onDismiss = { showAdd = false; editingId = null },
            onSave = { vm.saveNeighborhood(it) { showAdd = false; editingId = null } },
            onArchive = editing?.let { item -> { vm.archiveNeighborhood(item.id) { editingId = null } } }
        )
    }
}

@Composable
private fun EntityListScaffold(
    title: String,
    query: String,
    onQuery: (String) -> Unit,
    onAdd: () -> Unit,
    empty: Boolean,
    emptyTitle: String,
    emptyDescription: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            FilledTonalButton(onClick = onAdd) { Icon(painterResource(R.drawable.ic_add), null); Spacer(Modifier.width(5.dp)); Text("جدید") }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("جست‌وجو") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
            singleLine = true,
            shape = MaterialTheme.shapes.large
        )
        if (empty) EmptyState(emptyTitle, emptyDescription, "افزودن") { onAdd() }
        else LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomerDialog(
    initial: Customer?,
    neighborhoods: List<Neighborhood>,
    onDismiss: () -> Unit,
    onSave: (Customer) -> Unit,
    onArchive: (() -> Unit)?
) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var phone by rememberSaveable(initial?.id) { mutableStateOf(initial?.phone?.let(PersianNormalizer::toPersianDigits) ?: "") }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    var neighborhoodId by rememberSaveable(initial?.id) { mutableStateOf(initial?.neighborhoodId) }
    val neighborhood = neighborhoods.firstOrNull { it.id == neighborhoodId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "مشتری جدید" else "ویرایش مشتری") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { value -> name = value.take(120) }, label = { Text("نام *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { value -> phone = PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter { it.isDigit() || it == '+' }.take(14)) }, label = { Text("موبایل (اختیاری)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                SearchPicker("محله پیش‌فرض (اختیاری)", neighborhoods, neighborhood, { it.name }, onSelected = { neighborhoodId = it.id })
                if (neighborhood != null) {
                    TextButton(onClick = { neighborhoodId = null }) { Text("حذف محله پیش‌فرض") }
                }
                OutlinedTextField(notes, { value -> notes = value.take(1_000) }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                if (onArchive != null) TextButton(onClick = onArchive) { Text("بایگانی مشتری", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val base = initial ?: Customer(name = name.trim())
                onSave(base.copy(name = name.trim(), phone = phone.trim().takeIf { it.isNotBlank() }, neighborhoodId = neighborhoodId, notes = notes.trim().takeIf { it.isNotBlank() }))
            }, enabled = name.isNotBlank()) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun DriverDialog(initial: Driver?, onDismiss: () -> Unit, onSave: (Driver) -> Unit, onArchive: (() -> Unit)?) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var phone by rememberSaveable(initial?.id) { mutableStateOf(initial?.phone?.let(PersianNormalizer::toPersianDigits) ?: "") }
    var plate by rememberSaveable(initial?.id) { mutableStateOf(initial?.plate ?: "") }
    var card by rememberSaveable(initial?.id) { mutableStateOf(initial?.cardNumber?.let(PersianNormalizer::toPersianDigits) ?: "") }
    var notes by rememberSaveable(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "راننده جدید" else "ویرایش راننده") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                OutlinedTextField(name, { value -> name = value.take(120) }, label = { Text("نام *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { value -> phone = PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter { it.isDigit() || it == '+' }.take(14)) }, label = { Text("موبایل (اختیاری)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(plate, { value -> plate = value.take(40) }, label = { Text("پلاک (اختیاری)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(card, { value -> card = PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter(Char::isDigit).take(16)) }, label = { Text("شماره کارت (اختیاری)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { value -> notes = value.take(1_000) }, label = { Text("توضیحات (اختیاری)") }, modifier = Modifier.fillMaxWidth())
                if (onArchive != null) TextButton(onClick = onArchive) { Text("بایگانی راننده", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val base = initial ?: Driver(name = name.trim())
                onSave(base.copy(name = name.trim(), phone = phone.trim().takeIf { it.isNotBlank() }, plate = plate.trim().takeIf { it.isNotBlank() }, cardNumber = card.trim().takeIf { it.isNotBlank() }, notes = notes.trim().takeIf { it.isNotBlank() }))
            }, enabled = name.isNotBlank()) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}

@Composable
private fun NeighborhoodDialog(initial: Neighborhood?, onDismiss: () -> Unit, onSave: (Neighborhood) -> Unit, onArchive: (() -> Unit)?) {
    var name by rememberSaveable(initial?.id) { mutableStateOf(initial?.name ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "محله جدید" else "ویرایش محله") },
        text = {
            Column {
                OutlinedTextField(name, { value -> name = value.take(100) }, label = { Text("نام محله") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (onArchive != null) TextButton(onClick = onArchive) { Text("بایگانی محله", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(onClick = {
                val base = initial ?: Neighborhood(name = name.trim())
                onSave(base.copy(name = name.trim()))
            }, enabled = name.isNotBlank()) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
