@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.invictus.abhyas.data.pdf.BookCovers
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
import com.invictus.abhyas.domain.ncert.BookNaming
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes

/** Horizontal slider of the books on the device; tap one to see its chapters. */
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
    Column(
        modifier = Modifier
            .width(136.dp)
            .clip(AbhyasShapes.L)
            .clickable(enabled = openable, onClick = onClick)
            .padding(4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = AbhyasShapes.M,
            color = container,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().aspectRatio(0.72f),
        ) {
            BookCoverArt(book)
        }
        Text(
            text = BookNaming.shortTitle(book.title, book.subject),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = when (book.status) {
                BookStatus.READY -> "${book.pageCount} pages"
                BookStatus.FAILED -> "Needs retry"
                BookStatus.DOWNLOADING -> book.message ?: "Downloading..."
                else -> if (openable) "${book.pageCount} pages - indexing" else "Preparing..."
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
