@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.invictus.abhyas.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.invictus.abhyas.BuildConfig
import com.invictus.abhyas.R
import com.invictus.abhyas.ui.icons.AppIcons
import com.invictus.abhyas.ui.theme.AbhyasShapes

/** Same support / GitHub details xmd uses (the app shares its author and repository). */
internal object AboutInfo {
    const val APP_NAME = "Abhyas"
    const val TAGLINE = "Learn. Practice. Improve."
    const val GITHUB_URL = "https://github.com/Utsavrajputt/xmd"
    const val UPI_ID = "utsavrajput@yesfam"
    const val UPI_PAYEE = "Utsav%20Rajput"
    const val SUPPORT_BODY =
        "Publishing Abhyas on the Play Store needs a developer account fee and testing costs. " +
            "Your small support helps make it happen. Every bit counts!"
    const val LICENSE_BODY =
        "Released under the GNU Affero General Public License v3.0 (AGPL-3.0). " +
            "Abhyas is free and open-source software — you are free to use, study, share, and modify it."
    const val MADE_BY = "Built with ♥️ by Invictus"
}

/**
 * App identity, version, GitHub link, support, GitHub contributors, and licence --
 * the xmd About screen without the update check.
 */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenLibraries: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    SettingsScaffold(title = "About", onBack = onBack) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            // ===== Hero identity card =====
            val cs = MaterialTheme.colorScheme
            val transition = rememberInfiniteTransition(label = "aboutHero")
            val fraction by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 5000),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "aboutHeroFraction",
            )
            val heroCornerRadius = 28.dp

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawWithCache {
                        val cx = size.width - size.width * fraction
                        val cy = size.height * fraction
                        val gradient = Brush.radialGradient(
                            colors = listOf(cs.primaryContainer, cs.tertiaryContainer),
                            center = Offset(cx, cy),
                            radius = 800f,
                        )
                        onDrawBehind {
                            drawRoundRect(
                                brush = gradient,
                                cornerRadius = CornerRadius(heroCornerRadius.toPx(), heroCornerRadius.toPx()),
                            )
                        }
                    }
                    .padding(20.dp),
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val context = LocalContext.current
                        val appIconBitmap = remember {
                            context.packageManager.getApplicationIcon(context.packageName)
                                .toBitmap()
                                .asImageBitmap()
                        }
                        Image(
                            bitmap = appIconBitmap,
                            contentDescription = AboutInfo.APP_NAME,
                            modifier = Modifier.size(64.dp),
                        )

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AboutInfo.APP_NAME,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = cs.onPrimaryContainer,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = AboutInfo.TAGLINE,
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                shape = AbhyasShapes.S,
                                color = cs.primary.copy(alpha = 0.16f),
                            ) {
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = cs.onPrimaryContainer,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        val btnColors = ButtonDefaults.buttonColors(
                            containerColor = cs.primary,
                            contentColor = cs.onPrimary,
                        )
                        Button(
                            onClick = onOpenLibraries,
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = AbhyasShapes.S,
                            colors = btnColors,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_library_cube),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Libraries",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                        Button(
                            onClick = { runCatching { uriHandler.openUri(AboutInfo.GITHUB_URL) } },
                            modifier = Modifier.weight(1f).height(56.dp),
                            shape = AbhyasShapes.S,
                            colors = btnColors,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_github),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "GitHub",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }

            // ===== Support =====
            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(title = "Support")

            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                val supportContext = LocalContext.current
                val upiId = AboutInfo.UPI_ID
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppIcons.MonetizationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Support Abhyas",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = AboutInfo.SUPPORT_BODY,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = AbhyasShapes.S,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val clipboard = supportContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("abhyas_upi_id", upiId))
                                Toast.makeText(supportContext, "UPI ID copied!", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "UPI ID",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = upiId,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Icon(
                            imageVector = AppIcons.Copy,
                            contentDescription = "Copy UPI ID",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        try {
                            val uri = Uri.parse("upi://pay?pa=$upiId&pn=${AboutInfo.UPI_PAYEE}&cu=INR")
                            supportContext.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) {
                            Toast.makeText(supportContext, "No UPI app found", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = AbhyasShapes.S,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp),
                ) {
                    Icon(AppIcons.MonetizationOn, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(text = "Send love", fontWeight = FontWeight.SemiBold)
                }
            }

            // ===== Developers (GitHub contributors, cached 72h) =====
            AboutContributorsSection(githubRepoUrl = AboutInfo.GITHUB_URL)

            // ===== License =====
            Spacer(Modifier.height(8.dp))
            SettingsSectionHeader(title = "License")

            SettingsSectionCard(contentPadding = PaddingValues(16.dp)) {
                Row {
                    Icon(
                        imageVector = AppIcons.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp).padding(top = 2.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "AGPL-3.0",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = AboutInfo.LICENSE_BODY,
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 20.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = AboutInfo.MADE_BY,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
            )
        }
    }
}
