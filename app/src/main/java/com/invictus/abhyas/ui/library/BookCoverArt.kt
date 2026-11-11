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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
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
fun BookCoverArt(book: BookEntity, modifier: Modifier = Modifier, showPart: Boolean = false) {
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
        GeneratedCover(book, modifier, showPart)
    }
}

@Composable
private fun GeneratedCover(book: BookEntity, modifier: Modifier = Modifier, showPart: Boolean = false) {
    // Class 11 and class 12 of a subject get their own colour, icon and topics line.
    val on = Color.White
    val style = coverStyle(book.subject, book.classLevel)
    val top = style.top
    val bottom = style.bottom
    val tagline = style.tagline
    val part = if (showPart) BookNaming.part(book.title) else null
    BoxWithConstraints(modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        // Text sizes follow the cover width so the subject never wraps (small cover on the chapter screen, bigger on the shelf).
        val unit = maxWidth.value
        val big = (unit * 0.16f * (7f / maxOf(7, book.subject.length))).sp
        val small = (unit * 0.09f).coerceAtLeast(9f).sp
        style.icon?.let {
            Icon(it, contentDescription = null, tint = on.copy(alpha = 0.22f), modifier = Modifier.align(Alignment.TopEnd).padding(top = (unit * 0.07f).dp, end = (unit * 0.07f).dp).size((unit * 0.34f).dp))
        }
        Column(Modifier.fillMaxSize().padding((unit * 0.09f).dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text("NCERT", fontSize = small, color = on.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, maxLines = 1)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(book.subject, fontSize = big, color = on, fontWeight = FontWeight.ExtraBold, maxLines = 1, softWrap = false)
                if (part != null) Text("Part $part", fontSize = small, color = on.copy(alpha = 0.9f), maxLines = 1)
                if (tagline.isNotEmpty()) Text(tagline, fontSize = (unit * 0.075f).coerceAtLeast(8f).sp, color = on.copy(alpha = 0.75f), maxLines = 2)
            }
            Text(book.classLevel?.let { "Class $it" } ?: "", fontSize = small, color = on.copy(alpha = 0.9f), fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

private class CoverStyle(val top: Color, val bottom: Color, val icon: ImageVector?, val tagline: String)

private fun coverStyle(subject: String, cls: Int?): CoverStyle = when (subject to cls) {
    "Physics" to 11 -> CoverStyle(Color(0xFF4C8DFF), Color(0xFF0B2A6B), Icons.Outlined.Speed, "Mechanics, heat and waves")
    "Physics" to 12 -> CoverStyle(Color(0xFF8B7CFF), Color(0xFF241A6B), Icons.Outlined.Bolt, "Electricity, light and atoms")
    "Chemistry" to 11 -> CoverStyle(Color(0xFFFF9F43), Color(0xFF7A3300), Icons.Outlined.Science, "Atoms, bonds and matter")
    "Chemistry" to 12 -> CoverStyle(Color(0xFFFF6B5E), Color(0xFF6B1410), Icons.Outlined.Hub, "Solutions, kinetics, organic")
    "Biology" to 11 -> CoverStyle(Color(0xFF3DBB7A), Color(0xFF0E4D2E), Icons.Outlined.Eco, "Diversity, plants, the body")
    "Biology" to 12 -> CoverStyle(Color(0xFF2DC7C0), Color(0xFF0B4A52), Icons.Outlined.Biotech, "Genetics, evolution, ecology")
    else -> when (subject) {
        "Physics" -> CoverStyle(Color(0xFF4C8DFF), Color(0xFF0B2A6B), Icons.Outlined.Bolt, "Matter, motion and energy")
        "Chemistry" -> CoverStyle(Color(0xFFFF9F43), Color(0xFF7A3300), Icons.Outlined.Science, "Atoms, bonds and reactions")
        "Biology" -> CoverStyle(Color(0xFF3DBB7A), Color(0xFF0E4D2E), Icons.Outlined.Eco, "The science of life")
        else -> CoverStyle(Color(0xFF8A94A8), Color(0xFF2C3446), null, "")
    }
}
