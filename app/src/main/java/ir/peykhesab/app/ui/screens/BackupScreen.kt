package ir.peykhesab.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import ir.peykhesab.app.AppViewModel
import ir.peykhesab.app.R
import ir.peykhesab.app.data.BackupSummary
import ir.peykhesab.app.domain.DeviceTime
import ir.peykhesab.app.domain.JalaliDate
import ir.peykhesab.app.domain.PersianNumberFormatter

@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val busy by vm.backupOperation.collectAsStateWithLifecycle()
    var passphrase by remember { mutableStateOf("") }
    var selectedRestoreUri by rememberSaveable { mutableStateOf<android.net.Uri?>(null) }
    var confirmRestore by rememberSaveable { mutableStateOf(false) }
    var lastSummary by remember { mutableStateOf<BackupSummary?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) vm.exportBackup(uri, passphrase) { lastSummary = it; passphrase = "" }
    }
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            confirmRestore = true
        }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { if (!busy) confirmRestore = false },
            title = { Text("جایگزینی کامل اطلاعات؟") },
            text = {
                Text("تمام اطلاعات فعلی فقط در صورت معتبر بودن کامل فایل پشتیبان و رمز صحیح، در یک عملیات اتمیک با اطلاعات فایل جایگزین می‌شوند. بهتر است قبل از بازیابی، یک پشتیبان تازه از وضعیت فعلی بگیرید.")
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        val uri = selectedRestoreUri ?: return@Button
                        confirmRestore = false
                        vm.restoreBackup(uri, passphrase) { lastSummary = it; passphrase = "" }
                    }
                ) { Text("بله، بازیابی شود") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { confirmRestore = false }) { Text("انصراف") } }
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, enabled = !busy) { Icon(painterResource(R.drawable.ic_arrow_back), "بازگشت") }
            Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.ic_save), null, tint = MaterialTheme.colorScheme.primary)
                Text("حفاظت از تمام اطلاعات", style = MaterialTheme.typography.titleLarge)
                Text("فایل پشتیبان رمزگذاری می‌شود و شامل سفارش‌ها، حسابداری، تسویه‌ها و سوابق تغییر وجه است. رمز فایل در برنامه ذخیره نمی‌شود.")
            }
        }

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it.take(128) },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("رمز فایل پشتیبان") },
            supportingText = { Text("حداقل ۸ نویسه؛ بدون این رمز بازیابی ممکن نیست") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )

        Button(
            enabled = !busy && passphrase.length >= 8,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = {
                val day = JalaliDate.fromKey(DeviceTime.todayKey())
                val fileName = "پیک‌حساب-${day.year}-${day.month.toString().padStart(2, '0')}-${day.day.toString().padStart(2, '0')}.phb"
                exportLauncher.launch(fileName)
            }
        ) {
            Icon(painterResource(R.drawable.ic_save), null)
            Spacer(Modifier.width(8.dp))
            Text("ساخت پشتیبان رمزگذاری‌شده")
        }

        OutlinedButton(
            enabled = !busy && passphrase.length >= 8,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            onClick = { restoreLauncher.launch(arrayOf("application/octet-stream", "application/*", "*/*")) }
        ) {
            Icon(painterResource(R.drawable.ic_history), null)
            Spacer(Modifier.width(8.dp))
            Text("بازیابی از فایل پشتیبان")
        }

        if (busy) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("در حال بررسی و اجرای عملیات؛ برنامه را نبندید", style = MaterialTheme.typography.bodyMedium)
        }

        lastSummary?.let { summary ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("آخرین عملیات موفق", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("${PersianNumberFormatter.integer(summary.customerCount)} مشتری • ${PersianNumberFormatter.integer(summary.driverCount)} راننده")
                    Text("${PersianNumberFormatter.integer(summary.orderCount)} سفارش • ${PersianNumberFormatter.integer(summary.settlementCount)} تسویه")
                    Text("${PersianNumberFormatter.integer(summary.neighborhoodCount)} محله")
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("نکته مهم", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("فایل پشتیبان را خارج از گوشی هم نگه دارید. حذف برنامه یا خرابی حافظه گوشی می‌تواند اطلاعات محلی را از بین ببرد؛ فایل رمزگذاری‌شده مسیر بازیابی مستقل شماست.")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}
