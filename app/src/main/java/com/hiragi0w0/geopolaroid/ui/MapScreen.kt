package com.hiragi0w0.geopolaroid.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.GeoPoint
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RouteMapModel

/** SC-03 完成地図。通常ピンと撮影順の直線のみ。地図が使えなくても一覧から写真を見られる。 */
@Composable
fun MapScreen(
    state: MapUiState,
    hasMapsApiKey: Boolean,
    excludeFailed: Boolean,
    onBack: () -> Unit,
    onExclude: (Photo) -> Unit,
    onDismissExcludeError: () -> Unit,
) {
    var viewingPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    val pins = state.model.pins

    Scaffold(
        topBar = { MapTopBar(state, onBack) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.6f)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp)),
            ) {
                when {
                    state.isLoading -> Unit
                    pins.isEmpty() -> CenterMessage(stringResource(R.string.map_empty))
                    hasMapsApiKey -> RouteMap(
                        model = state.model,
                        onPinClick = { viewingPhotoId = it.photoId },
                        modifier = Modifier.fillMaxSize(),
                    )
                    else -> CenterMessage(
                        title = stringResource(R.string.map_unavailable_title),
                        text = stringResource(R.string.map_unavailable_body),
                    )
                }
            }
            if (state.model.polyline.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.map_line_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            // 地図の代替表示を兼ね、重なったピンの写真にも必ず到達できる簡易一覧。
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.4f),
            ) {
                items(pins, key = { it.photoId }) { photo ->
                    MapPhotoRow(
                        photo = photo,
                        isMissing = photo.photoId in state.missingPhotoIds,
                        onClick = { viewingPhotoId = photo.photoId },
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }

    val viewingPhoto = pins.firstOrNull { it.photoId == viewingPhotoId }
    if (viewingPhoto != null) {
        PhotoViewerDialog(
            photo = viewingPhoto,
            isMissing = viewingPhoto.photoId in state.missingPhotoIds,
            isResolvingPlace = false,
            onDismiss = { viewingPhotoId = null },
            onExclude = {
                viewingPhotoId = null
                onExclude(it)
            },
        )
    }

    if (excludeFailed) {
        AlertDialog(
            onDismissRequest = onDismissExcludeError,
            text = { Text(stringResource(R.string.photo_exclude_failed)) },
            confirmButton = {
                TextButton(onClick = onDismissExcludeError) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun MapTopBar(state: MapUiState, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.action_back)) }
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(
                text = DisplayFormat.date(state.recordDate),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.map_photo_count, state.model.pins.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RouteMap(
    model: RouteMapModel,
    onPinClick: (Photo) -> Unit,
    modifier: Modifier = Modifier,
) {
    val bounds = model.bounds ?: return
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds.center.toLatLng(), DEFAULT_ZOOM)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    val boundsPaddingPx = with(LocalDensity.current) { BOUNDS_PADDING.roundToPx() }
    val lineColor = MaterialTheme.colorScheme.secondary

    // 記録範囲が見えるように初期カメラを合わせる（地図のレイアウト完了後でないと範囲指定できない）。
    LaunchedEffect(mapLoaded, bounds) {
        if (!mapLoaded) return@LaunchedEffect
        val single = CameraUpdateFactory.newLatLngZoom(bounds.center.toLatLng(), DEFAULT_ZOOM)
        val update = if (bounds.isPointLike) {
            single
        } else {
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(bounds.southWest.toLatLng(), bounds.northEast.toLatLng()),
                boundsPaddingPx,
            )
        }
        try {
            cameraPositionState.move(update)
        } catch (e: IllegalStateException) {
            // 地図領域が小さすぎて余白を取れない場合など
            cameraPositionState.move(single)
        }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        onMapLoaded = { mapLoaded = true },
    ) {
        model.pins.forEach { photo ->
            key(photo.photoId) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(photo.latitude, photo.longitude)),
                    title = DisplayFormat.time(photo),
                    onClick = {
                        onPinClick(photo)
                        true
                    },
                )
            }
        }
        if (model.polyline.size >= 2) {
            Polyline(points = model.polyline.map { it.toLatLng() }, color = lineColor, width = LINE_WIDTH)
        }
    }
}

@Composable
private fun MapPhotoRow(photo: Photo, isMissing: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoImage(
            photo = photo,
            isMissing = isMissing,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(timeCaption(photo), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = placeLabel(photo, isResolving = false),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenterMessage(text: String, title: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

private const val DEFAULT_ZOOM = 15f
private const val LINE_WIDTH = 8f
private val BOUNDS_PADDING = 48.dp
