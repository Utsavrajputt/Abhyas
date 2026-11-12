package com.invictus.abhyas.ui.library

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.sp
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
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        // Text sizes follow the cover width so the subject never wraps (small cover on the chapter screen, bigger on the shelf).
        val unit = maxWidth.value
        val big = (unit * 0.16f * (7f / maxOf(7, book.subject.length))).sp
        val small = (unit * 0.09f).coerceAtLeast(9f).sp
        Column(Modifier.fillMaxSize().padding((unit * 0.09f).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("NCERT", fontSize = small, color = on.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, maxLines = 1)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.subject, fontSize = big, color = on, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                if (part != null) Text("Part $part", fontSize = small, color = on.copy(alpha = 0.9f), maxLines = 1)
            }
            Text(book.classLevel?.let { "Class $it" } ?: "", fontSize = small, color = on.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}
