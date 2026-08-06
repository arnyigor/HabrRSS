package com.arny.habrrss.ui.article

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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

// ================= PREVIEWS =================

@Preview(showBackground = true, name = "Feed Thumbnail")
@Composable
private fun PreviewFeedThumbnail() {
    MaterialTheme {
        FeedThumbnail(
            imageUrl = "https://us.aws.cdn.hf.co/xet-bridge-us/6a5976e58e13b67a2fcde7a1/9afdd72d7ea57393b47aa04d668c3c8cee17614a1f8ba6b6a11eda87859f4ae2?response-content-disposition=inline%3B+filename*%3DUTF-8%27%27valhalla.webp%3B+filename%3D%22valhalla.webp%22%3B&X-Xet-Cas-Uid=672376364ed56f43dc30fc6b&response-content-type=image%2Fwebp&user_id=672376364ed56f43dc30fc6b&Expires=1785909252&Policy=eyJTdGF0ZW1lbnQiOlt7IlJlc291cmNlIjoiaHR0cHM6Ly91cy5hd3MuY2RuLmhmLmNvL3hldC1icmlkZ2UtdXMvNmE1OTc2ZTU4ZTEzYjY3YTJmY2RlN2ExLzlhZmRkNzJkN2VhNTczOTNiNDdhYTA0ZDY2OGMzYzhjZWUxNzYxNGExZjhiYTZiNmExMWVkYTg3ODU5ZjRhZTJcXD9yZXNwb25zZS1jb250ZW50LWRpc3Bvc2l0aW9uPWlubGluZSUzQitmaWxlbmFtZSUyQSUzRFVURi04JTI3JTI3dmFsaGFsbGEud2VicCUzQitmaWxlbmFtZSUzRCUyMnZhbGhhbGxhLndlYnAlMjIlM0ImWC1YZXQtQ2FzLVVpZD02NzIzNzYzNjRlZDU2ZjQzZGMzMGZjNmImcmVzcG9uc2UtY29udGVudC10eXBlPWltYWdlJTJGd2VicCZ1c2VyX2lkPTY3MjM3NjM2NGVkNTZmNDNkYzMwZmM2YiIsIkNvbmRpdGlvbiI6eyJEYXRlTGVzc1RoYW4iOnsiRXBvY2hUaW1lIjoxNzg1OTA5MjUyfX19XX0_&Signature=MEQCICZysp6fQTFbftlzwxb8dpFlAz5y1K5GNprYJhMxiKS3AiB2EyKzm0O0cbsIrHGHCFZRIA81mcBJS9HUmKMo%7Ej2a7w__&Key-Pair-Id=01KXEF4KZ1B6FV465MAWR4M21F",
            contentDescription = "Изображение статьи",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 200.dp)
        )
    }
}

@Preview(showBackground = true, name = "Feed Thumbnail (Empty)")
@Composable
private fun PreviewFeedThumbnailEmpty() {
    MaterialTheme {
        FeedThumbnail(
            imageUrl = null,
            contentDescription = "Нет изображения",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 100.dp)
        )
    }
}

@Preview(showBackground = true, name = "Article Content Image")
@Composable
private fun PreviewArticleContentImage() {
    MaterialTheme {
        ArticleContentImage(
            imageUrl = "https://habrastorage.org/getpro/habr/upload_files/...",
            contentDescription = "Контент статьи",
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Preview(showBackground = true, name = "Image Loading State")
@Composable
private fun PreviewImageLoading() {
    MaterialTheme {
        ImageLoadingPreview(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
        )
    }
}

@Preview(showBackground = true, name = "Image Error State")
@Composable
private fun PreviewImageError() {
    MaterialTheme {
        ImageErrorPreview(
            contentDescription = "Ошибка загрузки изображения",
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 100.dp)
        )
    }
}

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
    var imageState by remember(imageUrl) { mutableStateOf(ImageLoadState.Loading) }

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

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (showZoom && imageState == ImageLoadState.Success) {
                        Modifier.clickable { showFullscreen = true }
                    }
                    else Modifier
                ),
            contentScale = contentScale,
            onLoading = { imageState = ImageLoadState.Loading },
            onSuccess = { imageState = ImageLoadState.Success },
            onError = { imageState = ImageLoadState.Error },
        )

        when (imageState) {
            ImageLoadState.Loading -> ImageLoadingPreview(Modifier.matchParentSize())
            ImageLoadState.Error -> ImageErrorPreview(
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
            )
            ImageLoadState.Success -> Unit
        }

        // Fullscreen zoom via Dialog for true fullscreen
        if (showFullscreen) {
            Dialog(
                onDismissRequest = {
                    showFullscreen = false
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.96f)),
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
                                    val nextScale = (scale * zoom).coerceIn(1f, 6f)
                                    scale = nextScale
                                    if (nextScale == 1f) {
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        offsetX += pan.x * nextScale
                                        offsetY += pan.y * nextScale
                                    }
                                }
                            },
                        contentScale = ContentScale.Fit,
                    )

                    IconButton(
                        onClick = {
                            showFullscreen = false
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        },
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
}

@Composable
private fun ImageLoadingPreview(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.Filled.Image,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(28.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
        )
    }
}

@Composable
private fun ImageErrorPreview(
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .size(32.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = contentDescription.takeIf { it.isNotBlank() } ?: "Изображение не загрузилось",
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 16.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private enum class ImageLoadState {
    Loading,
    Success,
    Error,
}
