package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RecordDateSummary
import com.hiragi0w0.geopolaroid.home.CaptureStatus
import com.hiragi0w0.geopolaroid.home.HomeMessage
import com.hiragi0w0.geopolaroid.home.HomeUiState
import java.time.LocalDate

/** SC-01 記録一覧（ホーム）。 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onCaptureClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismissMessage: () -> Unit,
    onOpenPastDate: (LocalDate) -> Unit,
    onExclude: (Photo) -> Unit,
) {
    var viewingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var showPastDates by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        bottomBar = { CaptureBar(state = state, onCaptureClick = onCaptureClick) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") { HomeHeader(state) }
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
            item(key = "past") {
                OutlinedButton(
                    onClick = { showPastDates = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.pastDates.isEmpty()) stringResource(R.string.home_past_records)
                        else stringResource(R.string.home_past_records_count, state.pastDates.size),
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

    if (showPastDates) {
        PastDatesDialog(
            dates = state.pastDates,
            onSelect = {
                showPastDates = false
                onOpenPastDate(it)
            },
            onDismiss = { showPastDates = false },
        )
    }
}

@Composable
private fun HomeHeader(state: HomeUiState) {
    Column {
        Text(
            text = stringResource(R.string.home_record_date_label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = DisplayFormat.date(state.recordDate),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = stringResource(R.string.home_count, state.savedCount, MAX_PHOTOS_PER_RECORD_DATE),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = stringResource(R.string.home_boundary_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.home_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun PhotoCard(photo: Photo, isMissing: Boolean, isResolvingPlace: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        PhotoImage(
            photo = photo,
            isMissing = isMissing,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        )
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(text = timeCaption(photo), style = MaterialTheme.typography.titleMedium)
            Text(
                text = placeLabel(photo, isResolvingPlace),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MessageCard(
    message: HomeMessage,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(message.textRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
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

@Composable
private fun CaptureBar(state: HomeUiState, onCaptureClick: () -> Unit) {
    Surface(tonalElevation = 3.dp, shadowElevation = 8.dp) {
        Button(
            onClick = onCaptureClick,
            enabled = state.canCapture,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
                .height(56.dp),
        ) {
            when {
                state.captureStatus == CaptureStatus.PREPARING -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.home_capture_preparing))
                }
                state.captureStatus == CaptureStatus.CAMERA_OPEN ->
                    Text(stringResource(R.string.home_capture_camera_open))
                state.isLimitReached -> Text(stringResource(R.string.home_capture_limit))
                else -> Text(stringResource(R.string.home_capture), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun PastDatesDialog(
    dates: List<RecordDateSummary>,
    onSelect: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_past_records)) },
        text = {
            if (dates.isEmpty()) {
                Text(stringResource(R.string.past_dialog_empty))
            } else {
                LazyColumn {
                    items(dates, key = { it.recordDate.toString() }) { summary ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(summary.recordDate) }
                                .padding(vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(DisplayFormat.date(summary.recordDate), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                stringResource(R.string.past_dialog_item_count, summary.photoCount),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}
