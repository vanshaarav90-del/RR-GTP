package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.util.Base64
import java.util.Locale
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.ChatMessage
import com.example.data.GeneratedImage
import com.example.ui.theme.*
import kotlin.math.sin

// --- Beautiful Markdown & Code Parser ---
@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    CodeBlock(code = block.content, language = block.language)
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = block.annotatedString,
                        color = color,
                        lineHeight = 22.sp,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

sealed class MarkdownBlock {
    data class Paragraph(val annotatedString: AnnotatedString) : MarkdownBlock()
    data class Code(val content: String, val language: String) : MarkdownBlock()
}

fun parseMarkdownBlocks(text: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val lines = text.split("\n")
    var inCodeBlock = false
    val currentCode = StringBuilder()
    var currentLanguage = "code"
    val currentParagraph = StringBuilder()

    for (line in lines) {
        if (line.trim().startsWith("```")) {
            if (inCodeBlock) {
                // End of code block
                blocks.add(MarkdownBlock.Code(currentCode.toString().trimEnd(), currentLanguage))
                currentCode.clear()
                inCodeBlock = false
            } else {
                // Start of code block
                if (currentParagraph.isNotEmpty()) {
                    blocks.add(MarkdownBlock.Paragraph(buildMarkdownAnnotatedString(currentParagraph.toString().trimEnd())))
                    currentParagraph.clear()
                }
                currentLanguage = line.trim().substringAfter("```").trim().ifEmpty { "code" }
                inCodeBlock = true
            }
        } else {
            if (inCodeBlock) {
                currentCode.append(line).append("\n")
            } else {
                currentParagraph.append(line).append("\n")
            }
        }
    }

    if (inCodeBlock) {
        blocks.add(MarkdownBlock.Code(currentCode.toString().trimEnd(), currentLanguage))
    } else if (currentParagraph.isNotEmpty()) {
        blocks.add(MarkdownBlock.Paragraph(buildMarkdownAnnotatedString(currentParagraph.toString().trimEnd())))
    }

    return blocks
}

fun buildMarkdownAnnotatedString(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val len = text.length

        while (cursor < len) {
            val boldIndex = text.indexOf("**", cursor)
            val italicIndex = text.indexOf("*", cursor)
            val codeIndex = text.indexOf("`", cursor)

            // Find closest token
            val nextTokenIndex = listOf(
                if (boldIndex != -1) boldIndex else Int.MAX_VALUE,
                if (italicIndex != -1) italicIndex else Int.MAX_VALUE,
                if (codeIndex != -1) codeIndex else Int.MAX_VALUE
            ).minOrNull() ?: Int.MAX_VALUE

            if (nextTokenIndex == Int.MAX_VALUE) {
                // No more markdown tags
                append(text.substring(cursor))
                break
            }

            // Append normal text before the token
            if (nextTokenIndex > cursor) {
                append(text.substring(cursor, nextTokenIndex))
            }

            cursor = nextTokenIndex

            if (cursor == boldIndex) {
                val endBold = text.indexOf("**", cursor + 2)
                if (endBold != -1) {
                    val content = text.substring(cursor + 2, endBold)
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = PrimaryNeonViolet))
                    append(content)
                    pop()
                    cursor = endBold + 2
                } else {
                    append("**")
                    cursor += 2
                }
            } else if (cursor == italicIndex) {
                val endItalic = text.indexOf("*", cursor + 1)
                if (endItalic != -1) {
                    val content = text.substring(cursor + 1, endItalic)
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(content)
                    pop()
                    cursor = endItalic + 1
                } else {
                    append("*")
                    cursor += 1
                }
            } else if (cursor == codeIndex) {
                val endCode = text.indexOf("`", cursor + 1)
                if (endCode != -1) {
                    val content = text.substring(cursor + 1, endCode)
                    pushStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0x308B5CF6), color = SecondaryOrchid))
                    append(content)
                    pop()
                    cursor = endCode + 1
                } else {
                    append("`")
                    cursor += 1
                }
            }
        }
    }
}

// --- Luxury Code Block ---
@Composable
fun CodeBlock(code: String, language: String) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F111A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF07080E))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    color = TextSecondarySlate,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                IconButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = TextSecondarySlate,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            // Code Content
            Box(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = Color(0xFFE2E8F0),
                    lineHeight = 18.sp
                )
            }
        }
    }
}

// --- Dynamic Voice Waveform ---
@Composable
fun VoiceWaveform(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "Voice Wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
    ) {
        val width = size.width
        val height = size.height
        val midY = height / 2f
        val strokeWidth = 3.dp.toPx()

        val gradient = Brush.linearGradient(
            colors = listOf(PrimaryNeonViolet, SecondaryOrchid, TertiaryCyan),
            start = Offset(0f, 0f),
            end = Offset(width, 0f)
        )

        // Draw multiple overlapping sine waves
        for (waveIndex in 0..2) {
            val amplitude = (15.dp.toPx() / (waveIndex + 1))
            val frequency = 0.015f * (waveIndex + 1)
            val speedFactor = if (waveIndex == 1) -1f else 1f

            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, midY)
                for (x in 0..width.toInt() step 5) {
                    val xFloat = x.toFloat()
                    val y = midY + amplitude * sin(frequency * xFloat + phase * speedFactor + waveIndex)
                    lineTo(xFloat, y)
                }
            }
            drawPath(
                path = path,
                brush = gradient,
                style = Stroke(width = strokeWidth / (waveIndex + 1)),
                alpha = 0.8f / (waveIndex + 1)
            )
        }
    }
}

// --- Luxury Image Display Card with Progress Status & History Options ---
@Composable
fun GeneratedImageCard(
    image: GeneratedImage,
    onDownload: (GeneratedImage) -> Unit,
    onDelete: (GeneratedImage) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = SurfaceObsidian)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        when (image.aspectRatio) {
                            "16:9" -> 1.77f
                            "9:16" -> 0.56f
                            else -> 1f
                        }
                    )
                    .background(Color(0xFF0F111A))
            ) {
                if (image.status == "loading") {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = PrimaryNeonViolet)
                    }
                } else if (image.status == "error") {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = AccentErrorRed,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Failed to render image", color = TextSecondarySlate, fontSize = 14.sp)
                    }
                } else {
                    // Success Base64 loading
                    val imageBytes = remember(image.imageUrl) {
                        try {
                            Base64.decode(image.imageUrl, Base64.DEFAULT)
                        } catch (e: Exception) {
                            null
                        }
                    }

                    if (imageBytes != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(imageBytes)
                                .crossfade(true)
                                .build(),
                            contentDescription = image.prompt,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Hover Quick Actions
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color(0xB0090A0F), RoundedCornerShape(8.dp))
                            .padding(2.dp)
                    ) {
                        IconButton(
                            onClick = { onDownload(image) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Download, "Download", tint = TextPrimaryWhite, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = {
                                clipboard.setText(AnnotatedString(image.prompt))
                                Toast.makeText(context, "Prompt copied!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Outlined.ContentCopy, "Copy Prompt", tint = TextPrimaryWhite, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDelete(image) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, "Delete", tint = AccentErrorRed, modifier = Modifier.size(18.dp))
                        }
                    }

                    // Metadata overlay
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0xE0090A0F))
                                )
                            )
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = image.modelUsed,
                                fontFamily = FontFamily.Monospace,
                                color = TertiaryCyan,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = image.aspectRatio,
                                color = AccentGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Description block
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = image.prompt,
                    color = TextPrimaryWhite,
                    fontSize = 14.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(image.timestamp),
                    color = TextSecondarySlate,
                    fontSize = 11.sp
                )
            }
        }
    }
}

// --- Luxury Glassmorphic Card Container ---
@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Box(
        modifier = modifier
            .background(SurfaceGlass, RoundedCornerShape(16.dp))
            .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Column {
            content()
        }
    }
}
