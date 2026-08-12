package ir.peykhesab.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.EmptyState
import ir.peykhesab.app.ui.components.MoneyHolderSelector
import ir.peykhesab.app.ui.components.SearchPicker
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onCreated: (DeliveryOrder) -> Unit,
    onManageCustomers: () -> Unit,
    onManageDrivers: () -> Unit,
    onManageNeighborhoods: () -> Unit
) {
    val customers by vm.customers.collectAsStateWithLifecycle()
    val drivers by vm.drivers.collectAsStateWithLifecycle()
    val neighborhoods by vm.neighborhoods.collectAsStateWithLifecycle()
    val creatingOrder by vm.creatingOrder.collectAsStateWithLifecycle()

    var selectedCustomerId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDriverId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNeighborhoodId by rememberSaveable { mutableStateOf<String?>(null) }
    var amountText by rememberSaveable { mutableStateOf("") }
    var notes by rememberSaveable { mutableStateOf("") }
    var holderName by rememberSaveable { mutableStateOf(MoneyHolder.UNKNOWN.name) }
    var now by remember { mutableStateOf(DeviceTime.now()) }

    val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
    val selectedDriver = drivers.firstOrNull { it.id == selectedDriverId }
    val selectedNeighborhood = neighborhoods.firstOrNull { it.id == selectedNeighborhoodId }
    val holder = MoneyHolder.entries.firstOrNull { it.name == holderName } ?: MoneyHolder.UNKNOWN

    LaunchedEffect(Unit) {
        while (isActive) {
            now = DeviceTime.now()
            delay(30_000)
        }
    }

    val amountRial = MoneyFormatter.tomanInputToRial(amountText)
    val amountValid = amountRial != null && amountRial in 1..AccountingEngine.MAX_ORDER_AMOUNT_RIAL
    val split = remember(amountRial) { amountRial?.takeIf { it in 1..AccountingEngine.MAX_ORDER_AMOUNT_RIAL }?.let { AccountingEngine.split(it) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ثبت سفارش") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "بازگشت") } }
            )
        }
    ) { padding ->
        if (customers.isEmpty() || drivers.isEmpty() || neighborhoods.isEmpty()) {
            Column(Modifier.padding(padding).fillMaxSize()) {
                EmptyState(
                    title = "اطلاعات پایه برای ثبت سفارش کافی نیست",
                    description = "برای ثبت سفارش حداقل یک مشتری، یک راننده و یک محله فعال لازم است."
                )
                if (customers.isEmpty()) Button(onClick = onManageCustomers, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text("افزودن مشتری") }
                Spacer(Modifier.height(8.dp))
                if (drivers.isEmpty()) Button(onClick = onManageDrivers, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text("افزودن راننده") }
                Spacer(Modifier.height(8.dp))
                if (neighborhoods.isEmpty()) Button(onClick = onManageNeighborhoods, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) { Text("افزودن محله") }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.padding(padding).fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_schedule), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("زمان ثبت خودکار", fontWeight = FontWeight.SemiBold)
                        Text(PersianDateTimeFormatter.deviceLongDateTime(now), style = MaterialTheme.typography.bodyMedium)
                        Text("هنگام ثبت، ساعت و منطقه زمانی همین گوشی ذخیره می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            SearchPicker(
                label = "مشتری",
                items = customers,
                selected = selectedCustomer,
                title = { it.name },
                subtitle = { it.phone?.let(PersianNormalizer::toPersianDigits) },
                onSelected = { customer ->
                    selectedCustomerId = customer.id
                    selectedNeighborhoodId = customer.neighborhoodId?.takeIf { id -> neighborhoods.any { it.id == id } }
                },
                modifier = Modifier.testTag("customer-picker")
            )
            SearchPicker(
                label = "راننده",
                items = drivers,
                selected = selectedDriver,
                title = { it.name },
                subtitle = { it.phone?.let(PersianNormalizer::toPersianDigits) },
                onSelected = { selectedDriverId = it.id },
                modifier = Modifier.testTag("driver-picker")
            )
            SearchPicker(
                label = "محله", items = neighborhoods, selected = selectedNeighborhood, title = { it.name },
                onSelected = { selectedNeighborhoodId = it.id }, modifier = Modifier.testTag("neighborhood-picker")
            )

            OutlinedTextField(
                value = amountText,
                onValueChange = { value -> amountText = PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter(Char::isDigit)) },
                modifier = Modifier.fillMaxWidth().testTag("order-amount"),
                label = { Text("مبلغ سفارش (تومان)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                isError = amountText.isNotBlank() && !amountValid,
                supportingText = {
                    if (amountText.isNotBlank() && !amountValid) {
                        Text("مبلغ واردشده معتبر نیست یا از سقف ایمن سیستم بیشتر است")
                    } else {
                        split?.let {
                            Text("کمیسیون ${PersianNumberFormatter.percentFromBasisPoints(AccountingEngine.DEFAULT_COMMISSION_BPS)}: ${MoneyFormatter.rialToTomanText(it.commissionRial)} • سهم راننده: ${MoneyFormatter.rialToTomanText(it.driverShareRial)}")
                        }
                    }
                },
                leadingIcon = { Icon(painterResource(R.drawable.ic_calculate), null) }
            )

            MoneyHolderSelector(selected = holder, onSelected = { holderName = it.name })

            OutlinedTextField(
                value = notes,
                onValueChange = { value -> if (value.length <= 1_000) notes = value },
                modifier = Modifier.fillMaxWidth().testTag("order-notes"),
                label = { Text("توضیحات (اختیاری)") },
                minLines = 2,
                maxLines = 4,
                shape = MaterialTheme.shapes.medium
            )

            val canSubmit = selectedCustomer != null && selectedDriver != null && selectedNeighborhood != null && amountValid && !creatingOrder
            Button(
                onClick = {
                    val customer = selectedCustomer ?: return@Button
                    val driver = selectedDriver ?: return@Button
                    val neighborhood = selectedNeighborhood ?: return@Button
                    val amount = amountRial ?: return@Button
                    vm.createOrder(customer.id, driver.id, neighborhood.id, amount, holder, notes.takeIf(String::isNotBlank), onCreated)
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp).testTag("order-submit"),
                shape = MaterialTheme.shapes.large
            ) {
                if (creatingOrder) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("در حال ثبت…", fontWeight = FontWeight.Bold)
                } else {
                    Icon(painterResource(R.drawable.ic_save), null)
                    Spacer(Modifier.width(8.dp))
                    Text("ثبت قطعی سفارش", fontWeight = FontWeight.Bold)
                }
            }
            FilledTonalButton(
                onClick = {
                    val customer = selectedCustomer ?: return@FilledTonalButton
                    val driver = selectedDriver ?: return@FilledTonalButton
                    val neighborhood = selectedNeighborhood ?: return@FilledTonalButton
                    val amount = amountRial ?: return@FilledTonalButton
                    vm.createOrder(customer.id, driver.id, neighborhood.id, amount, holder, notes.takeIf(String::isNotBlank)) {
                        amountText = ""
                        notes = ""
                        holderName = MoneyHolder.UNKNOWN.name
                        now = DeviceTime.now()
                    }
                },
                enabled = canSubmit,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Icon(painterResource(R.drawable.ic_playlist_add), null)
                Spacer(Modifier.width(8.dp))
                Text("ثبت و سفارش بعدی")
            }
            Text(
                "برای چند سفارش یک راننده، مشتری و راننده حفظ می‌شوند و فقط مبلغ سفارش بعدی را وارد می‌کنید.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}
