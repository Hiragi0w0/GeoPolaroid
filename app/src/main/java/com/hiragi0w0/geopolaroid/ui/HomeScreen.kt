package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.home.CaptureStatus
import com.hiragi0w0.geopolaroid.home.HomeMessage
import com.hiragi0w0.geopolaroid.home.HomeUiState

/** SC-01 記録一覧（ホーム）。 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onCaptureClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenPastRecords: () -> Unit,
    onExclude: (Photo) -> Unit,
) {
    var viewingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    // Surface で背景色に合う文字色（ダークモードでは明るい色）を子に渡す。
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = insets.calculateTopPadding() + 8.dp,
                    bottom = insets.calculateBottomPadding() + CAPTURE_AREA_HEIGHT,
                ),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                item(key = "header") { HomeHeader(state, onOpenPastRecords) }
                state.message?.let { message ->
                    item(key = "message") {
                        MessageCard(
                            message = message,
                            onRetry = onRetry,
                            onOpenSettings = onOpenSettings,
                            onDismiss = onDismissMessage,
                        )
                    }
                }
                if (!state.isLoading && state.photos.isEmpty()) {
                    item(key = "empty") { EmptyState() }
                }
                items(state.photos, key = { it.photoId }) { photo ->
                    PhotoCard(
                        photo = photo,
                        isMissing = photo.photoId in state.missingPhotoIds,
                        isResolvingPlace = photo.photoId in state.resolvingPlaceIds,
                        onClick = { viewingPhotoId = photo.photoId },
                    )
                }
            }

            // 端から端まで描画しているため、スクロールした写真がステータスバーの下に透けないようにする。
            Spacer(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(MaterialTheme.colorScheme.background),
            )
            CaptureArea(
                state = state,
                onCaptureClick = onCaptureClick,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    val viewingPhoto = state.photos.firstOrNull { it.photoId == viewingPhotoId }
    if (viewingPhoto != null) {
        PhotoViewerDialog(
            photo = viewingPhoto,
            isMissing = viewingPhoto.photoId in state.missingPhotoIds,
            isResolvingPlace = viewingPhoto.photoId in state.resolvingPlaceIds,
            onDismiss = { viewingPhotoId = null },
            onExclude = {
                viewingPhotoId = null
                onExclude(it)
            },
        )
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onOpenPastRecords: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_record_date_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = DisplayFormat.date(state.recordDate),
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            IconButton(onClick = onOpenPastRecords) {
                Icon(
                    painter = painterResource(R.drawable.ic_history),
                    contentDescription = stringResource(R.string.home_past_records),
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposureCounter(
                savedCount = state.savedCount,
                activeCount = state.activeCount,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.home_count, state.savedCount, MAX_PHOTOS_PER_RECORD_DATE),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.home_boundary_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * フィルムの残り枚数のように、上限12枚をドットで表す。保存済みは塗りつぶし、撮影中（PENDING）は淡く塗る。
 * 枚数は隣の文字で読み上げるので、ドット自体は読み上げ対象から外す。
 */
@Composable
private fun ExposureCounter(savedCount: Int, activeCount: Int, modifier: Modifier = Modifier) {
    val filled = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(MAX_PHOTOS_PER_RECORD_DATE) { index ->
            val dot = Modifier
                .size(10.dp)
                .clip(CircleShape)
            Box(
                modifier = when {
                    index < savedCount -> dot.background(filled)
                    index < activeCount -> dot.background(filled.copy(alpha = 0.35f))
                    else -> dot.border(1.5.dp, empty, CircleShape)
                },
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PhotoCard(photo: Photo, isMissing: Boolean, isResolvingPlace: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PhotoCaption(
            photo = photo,
            isResolvingPlace = isResolvingPlace,
            modifier = Modifier.padding(start = 2.dp, bottom = 8.dp),
        )
        PhotoImage(
            photo = photo,
            isMissing = isMissing,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .clickable(onClick = onClick),
        )
    }
}

@Composable
private fun MessageCard(
    message: HomeMessage,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(
                text = stringResource(message.textRes()),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (message == HomeMessage.LOCATION_PERMISSION_DENIED) {
                    TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.action_open_settings)) }
                }
                if (message.retryable) {
                    TextButton(onClick = onRetry) { Text(stringResource(R.string.action_retry)) }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            }
        }
    }
}

private fun HomeMessage.textRes(): Int = when (this) {
    HomeMessage.LIMIT_REACHED -> R.string.message_limit_reached
    HomeMessage.LOCATION_PERMISSION_DENIED -> R.string.message_location_permission_denied
    HomeMessage.LOCATION_UNAVAILABLE -> R.string.message_location_unavailable
    HomeMessage.STORAGE_UNAVAILABLE -> R.string.message_storage_unavailable
    HomeMessage.CAMERA_NOT_FOUND -> R.string.message_camera_not_found
    HomeMessage.CAPTURE_CANCELLED -> R.string.message_capture_cancelled
    HomeMessage.CAPTURE_FAILED -> R.string.message_capture_failed
    HomeMessage.CAPTURE_UNVERIFIED -> R.string.message_capture_unverified
    HomeMessage.CAPTURE_ABORTED -> R.string.message_capture_aborted
    HomeMessage.UNEXPECTED_ERROR -> R.string.message_unexpected_error
}

/** 画面下部のシャッターボタン。写真がボタンの下に潜るよう、背景色へのグラデーションを敷く。 */
@Composable
private fun CaptureArea(state: HomeUiState, onCaptureClick: () -> Unit, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    val status = when {
        state.captureStatus == CaptureStatus.PREPARING -> stringResource(R.string.home_capture_preparing)
        state.captureStatus == CaptureStatus.CAMERA_OPEN -> stringResource(R.string.home_capture_camera_open)
        state.isLimitReached -> stringResource(R.string.home_capture_limit)
        else -> null
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to background.copy(alpha = 0.92f),
                    1f to background,
                ),
            )
            .navigationBarsPadding()
            .padding(top = 36.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 状態の文言が出ても消えてもボタンの位置が動かないよう、行の高さを確保しておく。
        Text(
            text = status.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        ShutterButton(
            enabled = state.canCapture,
            busy = state.captureStatus == CaptureStatus.PREPARING,
            onClick = onCaptureClick,
        )
    }
}

@Composable
private fun ShutterButton(enabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    val color = MaterialTheme.colorScheme.primary
    val label = stringResource(R.string.home_capture)
    Box(
        modifier = Modifier
            .size(76.dp)
            .alpha(if (enabled || busy) 1f else 0.38f)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .border(3.dp, color, CircleShape)
            .padding(7.dp)
            .background(color, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.5.dp,
            )
        }
    }
}

/** シャッターボタン周り（状態の文言・ボタン・余白）の高さ。一覧の最後の写真が隠れないよう下に空ける。 */
private val CAPTURE_AREA_HEIGHT = 168.dp
