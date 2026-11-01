package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.ui.theme.PolaroidInk
import com.hiragi0w0.geopolaroid.ui.theme.PolaroidInkVariant
import com.hiragi0w0.geopolaroid.ui.theme.PolaroidPaper

/**
 * MediaStore の原本を表示する。読めない場合は「元写真が見つかりません」を表示し、落ちない。
 * [isMissing] は画面側の再検査結果で、表示中・キャッシュ済みの画像があってもアプリ外での削除を反映する。
 */
@Composable
fun PhotoImage(
    photo: Photo,
    isMissing: Boolean,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    if (isMissing) {
        Box(modifier = modifier) { PhotoPlaceholder(text = stringResource(R.string.photo_missing)) }
        return
    }
    SubcomposeAsyncImage(
        model = photo.imageUri.toUri(),
        contentDescription = stringResource(R.string.photo_content_description, DisplayFormat.time(photo)),
        modifier = modifier,
        contentScale = contentScale,
        loading = { PhotoPlaceholder() },
        error = { PhotoPlaceholder(text = stringResource(R.string.photo_missing)) },
    )
}

/** 一覧用の小さな写真。読めない場合は無地のプレースホルダーにする。 */
@Composable
fun ThumbnailImage(imageUri: String, modifier: Modifier = Modifier) {
    SubcomposeAsyncImage(
        model = imageUri.toUri(),
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Crop,
        loading = { PhotoPlaceholder() },
        error = { PhotoPlaceholder() },
    )
}

@Composable
private fun PhotoPlaceholder(text: String? = null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (text != null) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
fun placeLabel(photo: Photo, isResolving: Boolean): String = when {
    photo.placeName != null -> photo.placeName
    isResolving -> stringResource(R.string.photo_place_resolving)
    else -> stringResource(R.string.photo_place_failed)
}

@Composable
fun timeCaption(photo: Photo): String =
    stringResource(R.string.photo_time_caption, DisplayFormat.time(photo))

/**
 * 写真を1枚ずつポラロイド風のカードで見せるダイアログ。写真は中央を正方形に切り抜き、
 * 台紙の余白に撮影日時と地域名を書く。[photos] が複数（同じ地点で撮った写真）なら左右に切り替える。
 * 記録からの除外もここから行う（表示中の1枚が対象）。
 */
@Composable
fun PhotoViewerDialog(
    photos: List<Photo>,
    initialPhotoId: String,
    missingPhotoIds: Set<String>,
    resolvingPlaceIds: Set<String>,
    onDismiss: () -> Unit,
    onExclude: (Photo) -> Unit,
    pageLabelRes: Int = R.string.photo_page,
) {
    if (photos.isEmpty()) return
    val initialPage = photos.indexOfFirst { it.photoId == initialPhotoId }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { photos.size }
    var confirmExclude by remember { mutableStateOf<Photo?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 24.dp),
                pageSpacing = 12.dp,
                key = { photos[it].photoId },
            ) { page ->
                val photo = photos[page]
                PolaroidCard(
                    photo = photo,
                    isMissing = photo.photoId in missingPhotoIds,
                    isResolvingPlace = photo.photoId in resolvingPlaceIds,
                    tilt = polaroidTilt(page),
                )
            }
            if (photos.size > 1) {
                PageDots(
                    count = photos.size,
                    current = pagerState.currentPage,
                    labelRes = pageLabelRes,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                    TextButton(onClick = { confirmExclude = photos.getOrNull(pagerState.currentPage) }) {
                        Text(stringResource(R.string.photo_exclude), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }

    confirmExclude?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmExclude = null },
            title = { Text(stringResource(R.string.photo_exclude_confirm_title)) },
            text = { Text(stringResource(R.string.photo_exclude_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExclude = null
                    onExclude(target)
                }) {
                    Text(stringResource(R.string.photo_exclude), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExclude = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

/** 机に置いた写真のように少し傾け、隣の写真とは向きを変える（一覧とカードで同じ向き）。 */
fun polaroidTilt(index: Int): Float = if (index % 2 == 0) -1.2f else 0.9f

/**
 * ポラロイドの1枚。写真は中央を正方形に切り抜き、上と左右は細く、下は広い余白をとって日時と地域名を書く。
 * [onClick] を渡すと台紙ごとタップできる。
 */
@Composable
fun PolaroidCard(
    photo: Photo,
    isMissing: Boolean,
    isResolvingPlace: Boolean,
    tilt: Float,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt },
        shape = RoundedCornerShape(3.dp),
        color = PolaroidPaper,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = 20.dp),
        ) {
            PhotoImage(
                photo = photo,
                isMissing = isMissing,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
            Text(
                text = DisplayFormat.stamp(photo),
                style = MaterialTheme.typography.titleMedium,
                color = PolaroidInk,
                modifier = Modifier.padding(top = 18.dp, start = 2.dp),
            )
            Text(
                text = placeLabel(photo, isResolvingPlace),
                style = MaterialTheme.typography.bodyMedium,
                color = PolaroidInkVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp),
            )
        }
    }
}

/** 何枚目を見ているかを示す点。 */
@Composable
private fun PageDots(count: Int, current: Int, labelRes: Int, modifier: Modifier = Modifier) {
    val label = stringResource(labelRes, current + 1, count)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(if (index == current) PolaroidPaper else PolaroidPaper.copy(alpha = 0.4f)),
            )
        }
    }
}
