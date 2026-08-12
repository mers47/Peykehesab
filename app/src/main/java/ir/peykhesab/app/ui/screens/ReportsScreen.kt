package ir.peykhesab.app.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.R
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.domain.*
import ir.peykhesab.app.ui.components.StatCard
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReportPreset(val title: String) { TODAY("امروز"), MONTH("این ماه"), CUSTOM("تاریخ تا تاریخ") }


@Composable
fun ReportsScreen(vm: AppViewModel) {
    val orders by vm.orders.collectAsStateWithLifecycle()
    val settlements by vm.settlements.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSaveText by remember { mutableStateOf("") }
    var exporting by remember { mutableStateOf(false) }
    val saveReportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null && pendingSaveText.isNotEmpty()) {
            scope.launch {
                val saved = try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8).use { writer ->
                            requireNotNull(writer) { "امکان باز کردن فایل مقصد وجود ندارد" }
                            writer.write(pendingSaveText)
                        }
                    }
                    true
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    false
                }
                Toast.makeText(context, if (saved) "گزارش ذخیره شد" else "ذخیره گزارش انجام نشد", Toast.LENGTH_SHORT).show()
            }
        }
    }
    var todayJalali by remember { mutableStateOf(JalaliDate.fromKey(DeviceTime.todayKey())) }
    LaunchedEffect(Unit) {
        while (isActive) {
            todayJalali = JalaliDate.fromKey(DeviceTime.todayKey())
            delay(30_000)
        }
    }
    var presetName by rememberSaveable { mutableStateOf(ReportPreset.TODAY.name) }
    val preset = ReportPreset.entries.firstOrNull { it.name == presetName } ?: ReportPreset.TODAY
    var fromText by rememberSaveable { mutableStateOf(todayJalali.format()) }
    var toText by rememberSaveable { mutableStateOf(todayJalali.format()) }

    val keyRange = remember(preset, fromText, toText, todayJalali) {
        runCatching {
            when (preset) {
                ReportPreset.TODAY -> todayJalali.key() to todayJalali.key()
                ReportPreset.MONTH -> {
                    val first = JalaliDate(todayJalali.year, todayJalali.month, 1).key()
                    val lastDay = JalaliDate.daysInMonth(todayJalali.year, todayJalali.month)
                    first to (todayJalali.year * 10_000 + todayJalali.month * 100 + lastDay)
                }
                ReportPreset.CUSTOM -> {
                    val from = JalaliDate.parse(fromText) ?: error("تاریخ شروع نامعتبر است")
                    val to = JalaliDate.parse(toText) ?: error("تاریخ پایان نامعتبر است")
                    require(from <= to) { "تاریخ پایان قبل از تاریخ شروع است" }
                    from.key() to to.key()
                }
            }
        }.getOrNull()
    }

    val customError = if (preset == ReportPreset.CUSTOM && keyRange == null) {
        "تاریخ شمسی معتبر را با قالب سال/ماه/روز وارد کنید"
    } else null

    val rangeLabel = keyRange?.let { (from, to) -> "${JalaliDate.fromKey(from).format()} تا ${JalaliDate.fromKey(to).format()}" }.orEmpty()
    val reportResult by key(orders, settlements, keyRange) {
        produceState<Result<ReportSnapshot>?>(
            initialValue = null,
            orders,
            settlements,
            keyRange
        ) {
            val range = keyRange ?: return@produceState
            value = try {
                withContext(Dispatchers.Default) {
                    Result.success(ReportEngine.calculate(orders, settlements, range.first, range.second))
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }
    }
    val report = reportResult?.getOrNull()
    val reportLoading = keyRange != null && reportResult == null
    val reportError = reportResult?.exceptionOrNull()
    val reportSettlements = report?.settlements.orEmpty()
    val validOrderCount = report?.validOrderCount ?: 0
    val gross = report?.grossRial ?: 0L
    val commission = report?.commissionRial ?: 0L
    val heldByDrivers = report?.heldByDriversRial ?: 0L
    val heldByOffice = report?.heldByOfficeRial ?: 0L
    val canceledCount = report?.canceledCount ?: 0
    val driverToOfficeSettled = report?.driverToOfficeSettledRial ?: 0L
    val officeToDriverSettled = report?.officeToDriverSettledRial ?: 0L
    val byDriver = report?.drivers.orEmpty()


    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("گزارش‌ها", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("گزارش مالی بر پایه تاریخ شمسی", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilledTonalIconButton(
                        onClick = {
                            val snapshot = report ?: return@FilledTonalIconButton
                            scope.launch {
                                exporting = true
                                try {
                                    pendingSaveText = withContext(Dispatchers.Default) {
                                        ReportEngine.buildText(preset.title, rangeLabel, snapshot)
                                    }
                                    val dateName = todayJalali.format().replace('/', '-')
                                    saveReportLauncher.launch("گزارش-پیک‌حساب-$dateName.txt")
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast.makeText(context, "آماده‌سازی گزارش انجام نشد", Toast.LENGTH_SHORT).show()
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                        enabled = report != null && !exporting
                    ) {
                        if (exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        else Icon(painterResource(R.drawable.ic_save), "ذخیره گزارش")
                    }
                    FilledTonalIconButton(
                        onClick = {
                            val snapshot = report ?: return@FilledTonalIconButton
                            scope.launch {
                                exporting = true
                                try {
                                    val reportText = withContext(Dispatchers.Default) {
                                        ReportEngine.buildText(preset.title, rangeLabel, snapshot)
                                    }
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "گزارش پیک‌حساب")
                                        putExtra(Intent.EXTRA_TEXT, reportText)
                                    }, "ارسال گزارش"))
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    Toast.makeText(context, "اشتراک گزارش انجام نشد", Toast.LENGTH_SHORT).show()
                                } finally {
                                    exporting = false
                                }
                            }
                        },
                        enabled = report != null && !exporting
                    ) { Icon(painterResource(R.drawable.ic_share), "اشتراک گزارش") }
                }
            }
        }

        item {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ReportPreset.entries.forEach { option ->
                    FilterChip(
                        selected = preset == option,
                        onClick = { presetName = option.name },
                        label = { Text(option.title) }
                    )
                }
            }
        }

        if (preset == ReportPreset.CUSTOM) {
            item {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DateInput("از تاریخ", fromText, { fromText = it }, Modifier.widthIn(min = 150.dp, max = 320.dp))
                    DateInput("تا تاریخ", toText, { toText = it }, Modifier.widthIn(min = 150.dp, max = 320.dp))
                }
                customError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }

        if (reportLoading) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("در حال محاسبه گزارش…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else if (reportError != null) {
            item {
                Text(
                    "محاسبه گزارش انجام نشد. داده‌ها بدون تغییر باقی مانده‌اند.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("تعداد سفارش", PersianNumberFormatter.integer(validOrderCount), Modifier.weight(1f), MaterialTheme.colorScheme.primaryContainer)
                StatCard("جمع سفارش", MoneyFormatter.rialToTomanText(gross), Modifier.weight(1f), MaterialTheme.colorScheme.secondaryContainer)
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("کمیسیون", MoneyFormatter.rialToTomanText(commission), Modifier.weight(1f), MaterialTheme.colorScheme.tertiaryContainer)
                StatCard("لغوشده", PersianNumberFormatter.integer(canceledCount), Modifier.weight(1f), MaterialTheme.colorScheme.errorContainer)
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("وضعیت گردش وجه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    ReportMoneyRow("وجه ثبت‌شده دست راننده‌ها", heldByDrivers, R.drawable.ic_two_wheeler)
                    HorizontalDivider()
                    ReportMoneyRow("وجه تحویل‌شده به دفتر", heldByOffice, R.drawable.ic_account_balance)
                }
            }
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("تسویه‌های ثبت‌شده در بازه", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text(PersianNumberFormatter.integer(reportSettlements.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ReportMoneyRow("واریز راننده‌ها به دفتر", driverToOfficeSettled, R.drawable.ic_south_west)
                    HorizontalDivider()
                    ReportMoneyRow("پرداخت دفتر به راننده‌ها", officeToDriverSettled, R.drawable.ic_north_east)
                }
            }
        }

        item { Text("عملکرد راننده‌ها", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (byDriver.isEmpty()) {
            item { Text("در این بازه سفارشی وجود ندارد.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(byDriver, key = { it.id }) { row ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row {
                            Text(row.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${PersianNumberFormatter.integer(row.count)} سفارش")
                        }
                        Row {
                            Text("مبلغ کل", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyFormatter.rialToTomanText(row.grossRial), fontWeight = FontWeight.Bold)
                        }
                        Row {
                            Text("کمیسیون", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(MoneyFormatter.rialToTomanText(row.commissionRial))
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
private fun DateInput(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { value -> onValueChange(PersianNumberFormatter.digits(PersianNormalizer.toEnglishDigits(value).filter { ch -> ch.isDigit() || ch == '/' || ch == '-' || ch == '.' })) },
        label = { Text(label) },
        placeholder = { Text("سال/ماه/روز") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun ReportMoneyRow(label: String, amountRial: Long, iconRes: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painterResource(iconRes), null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(MoneyFormatter.rialToTomanText(amountRial), fontWeight = FontWeight.Bold)
    }
}
