package com.invictus.abhyas

import android.app.Application
import com.invictus.abhyas.data.local.AbhyasDatabase
import com.invictus.abhyas.data.pdf.PdfStudioRepository
import com.invictus.abhyas.data.prefs.SettingsStore
import com.invictus.abhyas.data.repo.AttemptRepository
import com.invictus.abhyas.data.repo.BankImporter
import com.invictus.abhyas.data.repo.PackageReader
import com.invictus.abhyas.data.repo.QuestionRepository
import com.invictus.abhyas.data.repo.TemplateRepository

class AbhyasApp : Application() {
    val database: AbhyasDatabase by lazy { AbhyasDatabase.build(this) }
    val bankImporter: BankImporter by lazy { BankImporter(database, imagesRoot = java.io.File(filesDir, "banks")) }
    val questionRepository: QuestionRepository by lazy { QuestionRepository(database) }
    val attemptRepository: AttemptRepository by lazy { AttemptRepository(database, questionRepository) }
    val templateRepository: TemplateRepository by lazy { TemplateRepository(this) }
    val packageReader: PackageReader by lazy { PackageReader(this) }
    val pdfStudio: PdfStudioRepository by lazy { PdfStudioRepository(this, database, bankImporter) }
    val books: com.invictus.abhyas.data.ncert.BookRepository by lazy {
        com.invictus.abhyas.data.ncert.BookRepository(this, database, com.invictus.abhyas.data.pdf.PdfTextExtractor(this))
    }
    val settingsStore: SettingsStore by lazy { SettingsStore(this) }
    val apiKeyStore: com.invictus.abhyas.data.ai.ApiKeyStore by lazy { com.invictus.abhyas.data.ai.ApiKeyStore(this) }
    val aiGateway: com.invictus.abhyas.data.ai.AiGateway by lazy { com.invictus.abhyas.data.ai.AiGateway(settingsStore, apiKeyStore) }
}
