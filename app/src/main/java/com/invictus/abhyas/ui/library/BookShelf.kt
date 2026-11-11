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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.LineHeightStyle
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.local.BookStatus
import com.invictus.abhyas.domain.ncert.BookNaming
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes

/** Horizontal slider of the books on the device; parts of one subject share a card. Tap to see the chapters. */
@Composable
fun BookShelf(books: List<BookEntity>, chapterCounts: Map<String, Int>, onOpenBook: (String) -> Unit, modifier: Modifier = Modifier) {
    val groups = books.groupBy { it.subject }.values.map { g -> g.sortedBy { BookNaming.part(it.title) ?: 0 } }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(groups, key = { g -> g.first().id }) { g -> BookCard(g, chapterCounts, onClick = { onOpenBook(g.first().id) }) }
    }
}

@Composable
private fun BookCard(parts: List<BookEntity>, chapterCounts: Map<String, Int>, onClick: () -> Unit) {
    val book = parts.first()
    val cs = MaterialTheme.colorScheme
    val (container, _) = when (book.subject) {
        "Physics" -> cs.primaryContainer to cs.onPrimaryContainer
        "Chemistry" -> cs.tertiaryContainer to cs.onTertiaryContainer
        "Biology" -> cs.secondaryContainer to cs.onSecondaryContainer
        else -> cs.surfaceContainerHighest to cs.onSurface
    }
    val openable = parts.any { it.pageCount > 0 && it.status != BookStatus.FAILED }
    val chapters = parts.sumOf { chapterCounts[it.id] ?: 0 }
    val pending = parts.firstOrNull { it.status != BookStatus.READY }
    val subtitle = when {
        pending == null -> if (chapters > 0) "$chapters chapters" else "Ready"
        pending.status == BookStatus.FAILED -> "Needs retry"
        pending.status == BookStatus.DOWNLOADING -> pending.message ?: "Downloading..."
        else -> if (openable) "Indexing..." else "Preparing..."
    }
    // Explicit line height without trimming: the default tight metrics of this font clipped the top of digits.
    val label = LineHeightStyle(alignment = LineHeightStyle.Alignment.Center, trim = LineHeightStyle.Trim.None)
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
            text = BookNaming.baseTitle(book.title, book.subject),
            style = MaterialTheme.typography.labelLarge.copy(lineHeight = 20.sp, lineHeightStyle = label),
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(lineHeight = 16.sp, lineHeightStyle = label),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp),
        )
    }
}
