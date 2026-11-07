package com.invictus.abhyas

import android.app.Application
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.prefs.SettingsStore
import com.invictus.abhyas.data.repo.AttemptRepository
import com.invictus.abhyas.data.repo.BankImporter
import com.invictus.abhyas.data.repo.PackageReader
import com.invictus.abhyas.data.repo.QuestionRepository
import com.invictus.abhyas.data.repo.TemplateRepository

class AbhyasApp : Application() {
    val database: AbhyasDatabase by lazy { AbhyasDatabase.build(this) }
    val bankImporter: BankImporter by lazy { BankImporter(database) }
    val questionRepository: QuestionRepository by lazy { QuestionRepository(database) }
    val attemptRepository: AttemptRepository by lazy { AttemptRepository(database, questionRepository) }
    val templateRepository: TemplateRepository by lazy { TemplateRepository(this) }
    val packageReader: PackageReader by lazy { PackageReader(this) }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
}
