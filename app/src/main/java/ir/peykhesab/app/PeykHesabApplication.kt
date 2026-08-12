package ir.peykhesab.app

import android.app.Application
import ir.peykhesab.app.data.AppDatabase
import ir.peykhesab.app.data.AppRepository
import ir.peykhesab.app.data.BackupService

class PeykHesabApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.create(this) }
    val repository: AppRepository by lazy { AppRepository(database) }
    val backupService: BackupService by lazy { BackupService(this, database) }
}
