package com.hiragi0w0.geopolaroid.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.GoogleMap as PlatformGoogleMap
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.GeoPoint
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import com.hiragi0w0.geopolaroid.export.MapPosterExporter
import com.hiragi0w0.geopolaroid.map.MapUiState
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * SC-03 完成地図。地図を全画面に広げ、写真一覧は下から引き出すシートに入れる。
 * 通常ピンと撮影順の直線のみ。地図が使えなくてもシートの一覧から写真を見られる。
 * 右上のボタンで、見えている地図と写真をポラロイド風の1枚の画像にして保存する。
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val peekHeight = SHEET_PEEK_HEIGHT + navigationBottom
    // 上の戻るボタンと下のシートに隠れない範囲へ、ピンと地図の操作部品を収める。
    val mapPadding = PaddingValues(top = statusTop + TOP_BAR_SPACE, bottom = peekHeight)

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val snapshotter = remember { MapSnapshotter() }
    val exporter = remember { MapPosterExporter(context.applicationContext) }
    var exporting by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.map_saved)
    val saveFailedMessage = stringResource(R.string.map_save_failed)
    val shareLabel = stringResource(R.string.action_share)
    val saveImage: () -> Unit = {
        exporting = true
        scope.launch {
            // 戻るボタンとシートに隠れていない、画面で見えている範囲の地図を使う。
            val snapshot = snapshotter.snapshot()?.let { full ->
                val top = with(density) { (statusTop + TOP_BAR_SPACE).roundToPx() }
                val bottom = with(density) { peekHeight.roundToPx() }
                val height = full.height - top - bottom
                if (height > 0) Bitmap.createBitmap(full, 0, top, full.width, height) else full
            }
            val uri = exporter.export(state.recordDate, pins, snapshot)
            exporting = false
            if (uri == null) {
                snackbarHostState.showSnackbar(saveFailedMessage)
            } else {
                val result = snackbarHostState.showSnackbar(savedMessage, actionLabel = shareLabel)
                if (result == SnackbarResult.ActionPerformed) shareImage(context, uri)
            }
        }
    }

    BottomSheetScaffold(
        sheetContent = {
            MapSheetContent(
                state = state,
                bottomPadding = navigationBottom,
                onPhotoClick = { viewingPhotoId = it.photoId },
            )
        },
        sheetPeekHeight = peekHeight,
        sheetContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                state.isLoading -> Unit
                pins.isEmpty() -> CenterMessage(text = stringResource(R.string.map_empty), contentPadding = mapPadding)
                hasMapsApiKey -> RouteMap(
                    model = state.model,
                    onPinClick = { viewingPhotoId = it.photoId },
                    contentPadding = mapPadding,
                    snapshotter = snapshotter,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> CenterMessage(
                    title = stringResource(R.string.map_unavailable_title),
                    text = stringResource(R.string.map_unavailable_body),
                    contentPadding = mapPadding,
                )
            }
            MapTopBar(
                state = state,
                onBack = onBack,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(12.dp),
            )
            if (!state.isLoading && pins.isNotEmpty()) {
                SaveImageButton(
                    busy = exporting,
                    onClick = saveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(12.dp),
                )
            }
        }
    }

    // 重なったピンは分離しないので、同じ地点の写真をまとめてカードの中で切り替える。
    val viewingSpot = viewingPhotoId?.let(state.model::photosAtSameSpot).orEmpty()
    if (viewingSpot.isNotEmpty()) {
        PhotoViewerDialog(
            photos = viewingSpot,
            initialPhotoId = viewingPhotoId!!,
            missingPhotoIds = state.missingPhotoIds,
            resolvingPlaceIds = state.resolvingPlaceIds,
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

/** 地図の上に浮かべる、戻るボタンと記録日。 */
@Composable
private fun MapTopBar(state: MapUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier.padding(end = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            Text(
                text = DisplayFormat.date(state.recordDate),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp, lineHeight = 26.sp),
            )
        }
    }
}

/** 地図を画像で保存するボタン。保存中は回転表示にして連打を防ぐ。 */
@Composable
private fun SaveImageButton(busy: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
    ) {
        IconButton(onClick = onClick, enabled = !busy) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    painter = painterResource(R.drawable.ic_save_image),
                    contentDescription = stringResource(R.string.map_save),
                )
            }
        }
    }
}

/** 保存した画像を他のアプリへ渡す。 */
private fun shareImage(context: android.content.Context, uri: Uri) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // 共有先がなくても、画像は写真ライブラリに保存済み
    }
}

/** 画面の地図を画像に写す。地図が表示されている間だけ使え、それ以外は null を返す。 */
private class MapSnapshotter {
    var map: PlatformGoogleMap? = null

    suspend fun snapshot(): Bitmap? {
        val target = map ?: return null
        return suspendCancellableCoroutine { continuation ->
            target.snapshot { bitmap -> if (continuation.isActive) continuation.resume(bitmap) }
        }
    }
}

/** シートの中身。地図の代替表示を兼ね、重なったピンの写真にも必ず到達できる一覧。 */
@Composable
private fun MapSheetContent(state: MapUiState, bottomPadding: Dp, onPhotoClick: (Photo) -> Unit) {
    val pins = state.model.pins
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
    ) {
        if (!state.isLoading) {
            item(key = "header") {
                Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                    Text(
                        text = stringResource(R.string.map_photo_count, pins.size),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.model.polyline.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.map_line_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
        items(pins, key = { it.photoId }) { photo ->
            MapPhotoRow(
                photo = photo,
                isMissing = photo.photoId in state.missingPhotoIds,
                isResolvingPlace = photo.photoId in state.resolvingPlaceIds,
                onClick = { onPhotoClick(photo) },
            )
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun RouteMap(
    model: RouteMapModel,
    onPinClick: (Photo) -> Unit,
    contentPadding: PaddingValues,
    snapshotter: MapSnapshotter,
    modifier: Modifier = Modifier,
) {
    val bounds = model.bounds ?: return
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(bounds.center.toLatLng(), DEFAULT_ZOOM)
    }
    var mapLoaded by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val boundsPaddingPx = with(density) { BOUNDS_PADDING.roundToPx() }
    val lineColor = MaterialTheme.colorScheme.primary
    val pinFill = MaterialTheme.colorScheme.tertiary
    val pinRing = MaterialTheme.colorScheme.surface
    val context = LocalContext.current
    val darkTheme = isSystemInDarkTheme()
    // 画面の紙の色に合わせ、地図もセピア調にする。
    val mapProperties = remember(darkTheme) {
        val style = if (darkTheme) R.raw.map_style_sepia_dark else R.raw.map_style_sepia
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, style))
    }

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
        contentPadding = contentPadding,
        properties = mapProperties,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        onMapLoaded = { mapLoaded = true },
    ) {
        MapEffect(Unit) { map -> snapshotter.map = map }
        DisposableEffect(snapshotter) { onDispose { snapshotter.map = null } }
        // 地図 SDK の初期化後でないと作れないため、地図の中で用意する。
        val pinIcon = remember(pinFill, pinRing) { pinDescriptor(density, pinFill, pinRing) }
        model.pins.forEach { photo ->
            key(photo.photoId) {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(photo.latitude, photo.longitude)),
                    icon = pinIcon,
                    anchor = Offset(0.5f, 0.5f),
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
private fun MapPhotoRow(photo: Photo, isMissing: Boolean, isResolvingPlace: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PhotoImage(
            photo = photo,
            isMissing = isMissing,
            modifier = Modifier
                .size(56.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(timeCaption(photo), style = MaterialTheme.typography.labelLarge)
            Text(
                text = placeLabel(photo, isResolvingPlace),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CenterMessage(text: String, contentPadding: PaddingValues, title: String? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(contentPadding)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

private fun GeoPoint.toLatLng() = LatLng(latitude, longitude)

/** 撮影地点のピン。差し色の丸を紙の色で縁取り、線の上でも見分けられるようにする。 */
private fun pinDescriptor(density: Density, fill: Color, ring: Color): BitmapDescriptor {
    val sizePx = with(density) { PIN_SIZE.roundToPx() }
    val ringPx = with(density) { PIN_RING.toPx() }
    val bitmap = createBitmap(sizePx, sizePx)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = sizePx / 2f
    paint.color = ring.toArgb()
    canvas.drawCircle(radius, radius, radius, paint)
    paint.color = fill.toArgb()
    canvas.drawCircle(radius, radius, radius - ringPx, paint)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private const val DEFAULT_ZOOM = 15f
private const val LINE_WIDTH = 8f
private val BOUNDS_PADDING = 48.dp
private val PIN_SIZE = 22.dp
private val PIN_RING = 4.dp

/** シートを閉じた状態で見える高さ（つまみ・見出し・最初の1行）。ナビゲーションバーの分は別に足す。 */
private val SHEET_PEEK_HEIGHT = 184.dp

/** 地図の上に浮かべた戻るボタンの分の余白。 */
private val TOP_BAR_SPACE = 80.dp
