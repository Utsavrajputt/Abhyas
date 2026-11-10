package com.invictus.abhyas.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.invictus.abhyas.data.local.BookEntity
import com.invictus.abhyas.data.pdf.BookCovers
import com.invictus.abhyas.domain.ncert.BookNaming

/** A book's cover: the picture found on the internet, or a designed cover (subject, part, class) when there is none. */
@Composable
fun BookCoverArt(book: BookEntity, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val cover by produceState<Bitmap?>(null, book.id, book.status) { value = BookCovers.load(context, book) }
    val bmp = cover
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = book.title,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        GeneratedCover(book, modifier)
    }
}

@Composable
private fun GeneratedCover(book: BookEntity, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val (top, bottom, on) = when (book.subject) {
        "Physics" -> Triple(cs.primary, cs.primaryContainer, cs.onPrimary)
        "Chemistry" -> Triple(cs.tertiary, cs.tertiaryContainer, cs.onTertiary)
        "Biology" -> Triple(cs.secondary, cs.secondaryContainer, cs.onSecondary)
        else -> Triple(cs.outline, cs.surfaceContainerHighest, cs.surface)
    }
    val part = BookNaming.part(book.title)
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("NCERT", style = MaterialTheme.typography.labelSmall, color = on.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.subject, style = MaterialTheme.typography.titleLarge, color = on, fontWeight = FontWeight.ExtraBold)
                if (part != null) Text("Part $part", style = MaterialTheme.typography.titleSmall, color = on.copy(alpha = 0.9f))
            }
            Text(
                book.classLevel?.let { "Class $it" } ?: "",
                style = MaterialTheme.typography.labelLarge,
                color = on.copy(alpha = 0.9f),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
