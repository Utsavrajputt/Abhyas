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
import com.invictus.abhyas.domain.ncert.NcertRef
import com.invictus.abhyas.ui.screens.BodyMuted

/**
 * "Where this is explained in NCERT". Shown only when the book is installed and the passage is found
 * again by its hash; an unverifiable reference is simply not displayed.
 */
@Composable
fun NcertRefCard(app: AbhyasApp, ref: NcertRef?, onOpen: (bookId: String, page: Int, paragraph: Int) -> Unit) {
    if (ref == null) return
    val resolved by produceState<BookRepository.Resolved?>(initialValue = null, ref) { value = app.books.resolve(ref) }
    val r = resolved ?: return
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("In NCERT", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(r.book.title, style = MaterialTheme.typography.titleSmall)
            BodyMuted(listOfNotNull(r.chapter, "page ${r.page}").joinToString(", "))
            Text(r.text, maxLines = 3, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            FilledTonalButton(onClick = { onOpen(r.book.id, r.page, r.paragraph) }, shapes = ButtonDefaults.shapes()) { Text("Open in book") }
        }
    }
}
