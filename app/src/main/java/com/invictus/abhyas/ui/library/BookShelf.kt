@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes

/** Horizontal slider of the books on the device; tap one to read it. */
@Composable
fun BookShelf(books: List<BookEntity>, onOpenBook: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(books, key = { it.id }) { book -> BookCard(book, onClick = { onOpenBook(book.id) }) }
    }
}

@Composable
private fun BookCard(book: BookEntity, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (container, onContainer) = when (book.subject) {
        "Physics" -> cs.primaryContainer to cs.onPrimaryContainer
        "Chemistry" -> cs.tertiaryContainer to cs.onTertiaryContainer
        "Biology" -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.surfaceContainerHighest to cs.onSurface
    }
    val openable = book.pageCount > 0 && book.status != BookStatus.FAILED
    Surface(
        shape = AbhyasShapes.L,
        color = container,
        modifier = Modifier
            .width(150.dp)
            .height(200.dp)
            .clip(AbhyasShapes.L)
            .clickable(enabled = openable, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.MenuBook, contentDescription = null, tint = onContainer, modifier = Modifier.size(28.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column {
                Text(
                    text = listOfNotNull(book.classLevel?.let { "Class $it" }, book.subject).joinToString(" - "),
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.85f),
                )
                Text(
                    text = when (book.status) {
                        BookStatus.READY -> "${book.pageCount} pages"
                        BookStatus.FAILED -> "Needs retry"
                        BookStatus.DOWNLOADING -> book.message ?: "Downloading..."
                        else -> if (openable) "${book.pageCount} pages - indexing" else "Preparing..."
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = onContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
