@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.ncert

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.AbhyasApp
import com.invictus.abhyas.data.ncert.BookRepository
import com.invictus.abhyas.domain.model.Question
import com.invictus.abhyas.ui.screens.BodyMuted

/**
 * "Where this is explained in NCERT", with the page number printed in the book and Open in book.
 * Uses the question's stored reference when the passage is found again by its hash; otherwise the
 * question text is searched in the installed books. A search match that is not certain is labelled
 * "likely"; when nothing convincing is found the card is simply not displayed.
 */
@Composable
fun NcertRefCard(app: AbhyasApp, question: Question, onOpen: (bookId: String, page: Int, paragraph: Int) -> Unit) {
    val resolved by produceState<BookRepository.Resolved?>(initialValue = null, question.id) {
        value = question.ncertRef?.let { app.books.resolve(it) } ?: app.books.findForQuestion(question)
    }
    val r = resolved ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (r.likely) "In NCERT (likely)" else "In NCERT",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Page ${r.pageLabel}", style = MaterialTheme.typography.titleMedium)
            BodyMuted(listOfNotNull(r.chapter, r.book.title).joinToString(" - "))
            Text(r.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { onOpen(r.book.id, r.page, r.paragraph) }, shapes = ButtonDefaults.shapes()) { Text("Open in book") }
        }
    }
}
