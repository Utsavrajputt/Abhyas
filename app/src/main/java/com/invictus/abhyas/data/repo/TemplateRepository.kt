package com.invictus.abhyas.data.repo

import android.content.Context
import com.invictus.abhyas.domain.attempt.ExamTemplate
import com.invictus.abhyas.domain.attempt.TemplateParseResult
import com.invictus.abhyas.domain.attempt.TemplateParser
import java.io.IOException

/** Built-in exam templates (the .json files under assets/templates). Invalid files are skipped. Blocking: call on IO. */
class TemplateRepository(private val context: Context) {
    fun builtIn(): List<ExamTemplate> {
        val names = try {
            context.assets.list("templates")?.filter { it.endsWith(".json") }?.sorted().orEmpty()
        } catch (e: IOException) {
            emptyList()
        }
        return names.mapNotNull { name ->
            try {
                val text = context.assets.open("templates/$name").use { it.readBytes().toString(Charsets.UTF_8) }
                (TemplateParser.parse(text) as? TemplateParseResult.Ok)?.template
            } catch (e: IOException) {
                null
            }
        }
    }
}
