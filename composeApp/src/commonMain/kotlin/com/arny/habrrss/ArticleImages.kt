package com.arny.habrrss

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

@Composable
internal fun FeedThumbnail(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
) {
    ReaderImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        showZoom = false,
    )
}

@Composable
internal fun ArticleContentImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
) {
    ReaderImage(
        imageUrl = imageUrl,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        showZoom = true,
    )
}

@Composable
private fun ReaderImage(
    imageUrl: String?,
    contentDescription: String,
    modifier: Modifier,
    contentScale: ContentScale,
    showZoom: Boolean,
) {
    var showFullscreen by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    if (imageUrl.isNullOrBlank()) {
        Box(
            modifier = modifier
                .heightIn(min = 100.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Article,
                contentDescription = null,
                modifier = Modifier.size(42.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Box(modifier = modifier) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showZoom) Modifier.clickable { showFullscreen = true }
                    else Modifier
                )
                .clip(RoundedCornerShape(8.dp)),
            contentScale = contentScale,
        )

        // Fullscreen zoom dialog
        if (showFullscreen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { showFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = contentDescription,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 4f)
                                offsetX += pan.x
                                offsetY += pan.y
                            }
                        },
                    contentScale = ContentScale.Fit,
                )

                IconButton(
                    onClick = { showFullscreen = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Закрыть",
                        tint = Color.White
                    )
                }
            }
        }
    }
}