package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil3.compose.SubcomposeAsyncImage
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.Photo

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

/** 写真・撮影時刻・地域名だけのシンプルな閲覧ダイアログ。記録からの除外もここから行う。 */
@Composable
fun PhotoViewerDialog(
    photo: Photo,
    isMissing: Boolean,
    isResolvingPlace: Boolean,
    onDismiss: () -> Unit,
    onExclude: (Photo) -> Unit,
) {
    var confirmExclude by remember(photo.photoId) { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                PhotoImage(
                    photo = photo,
                    isMissing = isMissing,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 480.dp),
                )
                Spacer(Modifier.padding(top = 12.dp))
                Text(text = timeCaption(photo), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = placeLabel(photo, isResolvingPlace),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { confirmExclude = true }) {
                        Text(stringResource(R.string.photo_exclude), color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text(stringResource(R.string.action_close))
                    }
                }
            }
        }
    }

    if (confirmExclude) {
        AlertDialog(
            onDismissRequest = { confirmExclude = false },
            title = { Text(stringResource(R.string.photo_exclude_confirm_title)) },
            text = { Text(stringResource(R.string.photo_exclude_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmExclude = false
                    onExclude(photo)
                }) {
                    Text(stringResource(R.string.photo_exclude), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmExclude = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
