package ir.peykhesab.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import ir.peykhesab.app.ui.PeykHesabApp
import ir.peykhesab.app.ui.theme.PeykHesabTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PeykHesabTheme {
                val app = application as PeykHesabApplication
                val vm: AppViewModel = viewModel(factory = AppViewModel.Factory(app.repository, app.backupService))
                PeykHesabApp(vm)
            }
        }
    }
}
