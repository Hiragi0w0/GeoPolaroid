package com.hiragi0w0.geopolaroid.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
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
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.GoogleMap as PlatformGoogleMap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.CameraPositionState
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
import com.hiragi0w0.geopolaroid.domain.CropRect
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.GeoBounds
import com.hiragi0w0.geopolaroid.domain.GeoPoint
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import com.hiragi0w0.geopolaroid.domain.SketchPoint
import com.hiragi0w0.geopolaroid.export.MapCapture
import com.hiragi0w0.geopolaroid.export.MapPosterExporter
import com.hiragi0w0.geopolaroid.map.MapUiState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.ceil

/**
 * SC-03 完成地図。地図を全画面に広げ、写真一覧は下から引き出すシートに入れる。
 * ピンは撮影順の番号を書いた丸（同じ地点はまとめて番号を並べる）と撮影順の直線のみ。
 * 一覧の行をタップすると地図がそのピンへ移動して強調し、サムネイルかピンをタップするとカードを開く。
 * 地図を動かすと「リセット」ボタンが出て、最初の表示（全ピンが見える範囲）に戻せる。
 * 地図が使えなくてもシートの一覧から写真を見られる。
 * 右上のボタンで、地図を最初の表示に戻してから、地図と写真をポラロイド風の1枚の画像にして保存する。
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
    // 地図で強調している写真。一覧の行をタップするたび [focusRequests] を進めて地図をそこへ動かす。
    var selectedPhotoId by rememberSaveable { mutableStateOf<String?>(null) }
    var focusRequests by remember { mutableIntStateOf(0) }
    val pins = state.model.pins
    val showsMap = hasMapsApiKey && !state.isLoading && pins.isNotEmpty()
    val navigationBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val peekHeight = SHEET_PEEK_HEIGHT + navigationBottom
    // 上の戻るボタンと下のシートに隠れない範囲へ、ピンと地図の操作部品を収める。
    val mapPadding = PaddingValues(top = statusTop + TOP_BAR_SPACE, bottom = peekHeight)

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val scaffoldState = rememberBottomSheetScaffoldState()
    val snackbarHostState = remember { SnackbarHostState() }
    val mapHandle = remember { RouteMapHandle() }
    val exporter = remember { MapPosterExporter(context.applicationContext) }
    var exporting by remember { mutableStateOf(false) }
    val savedMessage = stringResource(R.string.map_saved)
    val saveFailedMessage = stringResource(R.string.map_save_failed)
    val shareLabel = stringResource(R.string.action_share)
    val saveImage: () -> Unit = {
        exporting = true
        scope.launch {
            // 強調中のピンを通常に戻してから写す（地図への反映を数フレーム待つ）。
            if (selectedPhotoId != null) {
                selectedPhotoId = null
                repeat(2) { withFrameNanos { } }
                delay(MARKER_SETTLE_MS)
            }
            // 全ピンが入るよう最初の表示に戻し、地図の描画が終わってから写す。
            mapHandle.resetView(animate = true)
            mapHandle.awaitRendered()
            // 戻るボタンとシートに隠れていない、画面で見えている範囲の中から使う。
            val capture = mapHandle.capture(pins.map { LatLng(it.latitude, it.longitude) })?.let { (bitmap, points) ->
                val top = with(density) { (statusTop + TOP_BAR_SPACE).toPx() }.coerceAtMost(bitmap.height - 1f)
                val bottom = (bitmap.height - with(density) { peekHeight.toPx() }).coerceAtLeast(top + 1f)
                MapCapture(bitmap, points, CropRect(0f, top, bitmap.width.toFloat(), bottom))
            }
            val uri = exporter.export(state.recordDate, pins, capture)
            exporting = false
            if (uri == null) {
                snackbarHostState.showSnackbar(saveFailedMessage)
            } else {
                // 共有ボタン付きでも、しばらくすると自動で消えるようにする（既定では消えない）。
                val result = snackbarHostState.showSnackbar(
                    message = savedMessage,
                    actionLabel = shareLabel,
                    withDismissAction = true,
                    duration = SnackbarDuration.Long,
                )
                if (result == SnackbarResult.ActionPerformed) shareImage(context, uri)
            }
        }
    }
    val openPhoto: (Photo) -> Unit = {
        selectedPhotoId = it.photoId
        viewingPhotoId = it.photoId
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetContent = {
            MapSheetContent(
                state = state,
                selectedPhotoId = selectedPhotoId,
                showsMap = showsMap,
                bottomPadding = navigationBottom,
                onRowClick = { photo ->
                    if (showsMap) {
                        selectedPhotoId = photo.photoId
                        focusRequests++
                        // シートを下げて地図を見せる。
                        scope.launch { scaffoldState.bottomSheetState.partialExpand() }
                    } else {
                        openPhoto(photo)
                    }
                },
                onOpenPhoto = openPhoto,
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
                    selectedPhotoId = selectedPhotoId,
                    focusRequests = focusRequests,
                    onPinClick = openPhoto,
                    contentPadding = mapPadding,
                    handle = mapHandle,
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
            // シートのすぐ上、地図の右下に出す。
            AnimatedVisibility(
                visible = showsMap && mapHandle.isMoved,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = peekHeight + 12.dp),
            ) {
                ResetViewButton(onClick = { scope.launch { mapHandle.resetView(animate = true) } })
            }
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

/** 地図を最初の表示（全ピンが見える範囲）に戻すボタン。地図を動かしたときだけ出す。 */
@Composable
private fun ResetViewButton(onClick: () -> Unit) {
    val description = stringResource(R.string.map_reset_view_description)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        shadowElevation = 4.dp,
        modifier = Modifier.semantics { contentDescription = description },
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 18.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_reset_view),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = stringResource(R.string.map_reset_view),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
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

/**
 * 画面の地図への操作口。地図が表示されている間だけ働き、それ以外は何もしない（写せなければ null）。
 * 最初の表示（全ピンが見える範囲）へ戻す操作は、「リセット」ボタン・画像保存・地図を開いたときの3か所で使う。
 */
private class RouteMapHandle {
    var map: PlatformGoogleMap? = null
    var cameraPositionState: CameraPositionState? = null
    var bounds: GeoBounds? = null
    var boundsPaddingPx: Int = 0

    /** 最初の表示から動かしたか（「リセット」ボタンを出すか）。 */
    var isMoved by mutableStateOf(false)

    suspend fun resetView(animate: Boolean) {
        val camera = cameraPositionState ?: return
        val target = bounds ?: return
        val single = CameraUpdateFactory.newLatLngZoom(target.center.toLatLng(), DEFAULT_ZOOM)
        val update = if (target.isPointLike) {
            single
        } else {
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(target.southWest.toLatLng(), target.northEast.toLatLng()),
                boundsPaddingPx,
            )
        }
        try {
            if (animate) camera.animate(update, RESET_ANIMATION_MS) else camera.move(update)
        } catch (e: IllegalStateException) {
            // 地図領域が小さすぎて余白を取れない場合など
            camera.move(single)
        }
        isMoved = false
    }

    /** 地図のタイルを描き終えるまで待つ（動かした直後に写すと描きかけになるため）。長くかかれば待たずに進む。 */
    suspend fun awaitRendered() {
        val target = map ?: return
        withTimeoutOrNull(RENDER_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                target.setOnMapLoadedCallback { if (continuation.isActive) continuation.resume(Unit) }
            }
        }
    }

    /** 地図の画像と、[points] の画像上の位置。 */
    suspend fun capture(points: List<LatLng>): Pair<Bitmap, List<SketchPoint>>? {
        val target = map ?: return null
        val projection = target.projection
        val onImage = points.map { point ->
            projection.toScreenLocation(point).let { SketchPoint(it.x.toFloat(), it.y.toFloat()) }
        }
        val bitmap: Bitmap? = suspendCancellableCoroutine { continuation ->
            target.snapshot { bitmap -> if (continuation.isActive) continuation.resume(bitmap) }
        }
        return bitmap?.let { it to onImage }
    }
}

/** シートの中身。地図の代替表示を兼ね、重なったピンの写真にも必ず到達できる一覧。 */
@Composable
private fun MapSheetContent(
    state: MapUiState,
    selectedPhotoId: String?,
    showsMap: Boolean,
    bottomPadding: Dp,
    onRowClick: (Photo) -> Unit,
    onOpenPhoto: (Photo) -> Unit,
) {
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
                number = state.model.numberOf(photo.photoId),
                isSelected = photo.photoId == selectedPhotoId,
                isMissing = photo.photoId in state.missingPhotoIds,
                isResolvingPlace = photo.photoId in state.resolvingPlaceIds,
                showsMap = showsMap,
                onClick = { onRowClick(photo) },
                onOpen = { onOpenPhoto(photo) },
            )
        }
    }
}

@OptIn(MapsComposeExperimentalApi::class)
@Composable
private fun RouteMap(
    model: RouteMapModel,
    selectedPhotoId: String?,
    focusRequests: Int,
    onPinClick: (Photo) -> Unit,
    contentPadding: PaddingValues,
    handle: RouteMapHandle,
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
    val pinColors = PinColors(
        fill = MaterialTheme.colorScheme.tertiary,
        text = MaterialTheme.colorScheme.onTertiary,
        selectedFill = MaterialTheme.colorScheme.primary,
        selectedText = MaterialTheme.colorScheme.onPrimary,
        ring = MaterialTheme.colorScheme.surface,
    )
    val context = LocalContext.current
    val pinTypeface = remember { ResourcesCompat.getFont(context, R.font.zen_maru_gothic_medium) }
    val darkTheme = isSystemInDarkTheme()
    // 画面の紙の色に合わせ、地図もセピア調にする。
    val mapProperties = remember(darkTheme) {
        val style = if (darkTheme) R.raw.map_style_sepia_dark else R.raw.map_style_sepia
        MapProperties(mapStyleOptions = MapStyleOptions.loadRawResourceStyle(context, style))
    }

    SideEffect {
        handle.cameraPositionState = cameraPositionState
        handle.bounds = bounds
        handle.boundsPaddingPx = boundsPaddingPx
    }

    // 記録範囲が見えるように初期カメラを合わせる（地図のレイアウト完了後でないと範囲指定できない）。
    LaunchedEffect(mapLoaded, bounds) {
        if (mapLoaded) handle.resetView(animate = false)
    }

    // 指で動かしたら「リセット」ボタンを出す。
    LaunchedEffect(cameraPositionState) {
        snapshotFlow {
            cameraPositionState.isMoving &&
                cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
        }.collect { gesture -> if (gesture) handle.isMoved = true }
    }

    // 一覧で選んだ写真のピンへ移動する。引きすぎていれば寄るが、すでに寄っていればその縮尺のまま。
    LaunchedEffect(focusRequests, mapLoaded) {
        if (focusRequests == 0 || !mapLoaded) return@LaunchedEffect
        val target = model.pins.firstOrNull { it.photoId == selectedPhotoId } ?: return@LaunchedEffect
        val zoom = maxOf(cameraPositionState.position.zoom, FOCUS_ZOOM)
        handle.isMoved = true
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(target.latitude, target.longitude), zoom),
            FOCUS_ANIMATION_MS,
        )
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = mapProperties,
        uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        onMapLoaded = { mapLoaded = true },
    ) {
        MapEffect(Unit) { map -> handle.map = map }
        DisposableEffect(handle) {
            onDispose {
                handle.map = null
                handle.cameraPositionState = null
            }
        }
        model.spots.forEach { spot ->
            key(spot.point) {
                val selected = selectedPhotoId != null && spot.photos.any { it.photoId == selectedPhotoId }
                // 地図 SDK の初期化後でないと作れないため、地図の中で用意する。
                val icon = remember(spot.label, selected, pinColors) {
                    pinDescriptor(density, spot.label, pinColors, pinTypeface, selected)
                }
                Marker(
                    state = rememberUpdatedMarkerState(position = spot.point.toLatLng()),
                    icon = icon,
                    anchor = Offset(0.5f, 0.5f),
                    zIndex = if (selected) 1f else 0f,
                    title = spot.photos.joinToString(" / ") { DisplayFormat.time(it) },
                    onClick = {
                        onPinClick(spot.photos.first())
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

/**
 * シートの1行。行のタップで地図のピンへ移動し（地図がなければカードを開く）、サムネイルのタップでカードを開く。
 * 先頭にピンと同じ番号を出し、地図で強調中の行は背景を変える。
 */
@Composable
private fun MapPhotoRow(
    photo: Photo,
    number: Int,
    isSelected: Boolean,
    isMissing: Boolean,
    isResolvingPlace: Boolean,
    showsMap: Boolean,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    val openLabel = stringResource(R.string.map_open_photo, number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest else Color.Transparent)
            .clickable(
                onClickLabel = if (showsMap) stringResource(R.string.map_show_on_map) else openLabel,
                onClick = onClick,
            )
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NumberBadge(number = number, selected = isSelected)
        PhotoImage(
            photo = photo,
            isMissing = isMissing,
            modifier = Modifier
                .padding(start = 12.dp)
                .size(56.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClickLabel = openLabel, onClick = onOpen),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(timeCaption(photo), style = MaterialTheme.typography.labelLarge)
            Text(
                text = placeLabel(photo, isResolvingPlace),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** ピンと同じ色の、撮影順の番号。 */
@Composable
private fun NumberBadge(number: Int, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (selected) colors.primary else colors.tertiary),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = number.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) colors.onPrimary else colors.onTertiary,
        )
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

private data class PinColors(
    val fill: Color,
    val text: Color,
    val selectedFill: Color,
    val selectedText: Color,
    val ring: Color,
)

/**
 * 撮影地点のピン。差し色の丸を紙の色で縁取り、撮影順の番号を書く。同じ地点の「2,5」のように長い番号は横長の丸にする。
 * 強調中は色を変えて大きくする。
 */
private fun pinDescriptor(
    density: Density,
    label: String,
    colors: PinColors,
    typeface: Typeface?,
    selected: Boolean,
): BitmapDescriptor {
    val scale = if (selected) SELECTED_PIN_SCALE else 1f
    val heightPx = with(density) { PIN_SIZE.toPx() } * scale
    val ringPx = with(density) { PIN_RING.toPx() } * scale
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.typeface = typeface
        textSize = with(density) { PIN_TEXT.toPx() } * scale
        textAlign = Paint.Align.CENTER
        color = (if (selected) colors.selectedText else colors.text).toArgb()
    }
    val widthPx = maxOf(heightPx, text.measureText(label) + heightPx * 0.6f)
    val bitmap = createBitmap(ceil(widthPx).toInt(), ceil(heightPx).toInt())
    val canvas = android.graphics.Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val radius = heightPx / 2
    paint.color = colors.ring.toArgb()
    canvas.drawRoundRect(0f, 0f, widthPx, heightPx, radius, radius, paint)
    paint.color = (if (selected) colors.selectedFill else colors.fill).toArgb()
    canvas.drawRoundRect(ringPx, ringPx, widthPx - ringPx, heightPx - ringPx, radius - ringPx, radius - ringPx, paint)
    canvas.drawText(label, widthPx / 2, heightPx / 2 - (text.descent() + text.ascent()) / 2, text)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private const val DEFAULT_ZOOM = 15f
private const val FOCUS_ZOOM = 16f
private const val FOCUS_ANIMATION_MS = 600
private const val RESET_ANIMATION_MS = 500
private const val RENDER_TIMEOUT_MS = 3_000L
private const val MARKER_SETTLE_MS = 120L
private const val LINE_WIDTH = 8f
private const val SELECTED_PIN_SCALE = 1.3f
private val BOUNDS_PADDING = 48.dp
private val PIN_SIZE = 26.dp
private val PIN_RING = 3.dp
private val PIN_TEXT = 12.dp

/** シートを閉じた状態で見える高さ（つまみ・見出し・最初の1行）。ナビゲーションバーの分は別に足す。 */
private val SHEET_PEEK_HEIGHT = 184.dp

/** 地図の上に浮かべた戻るボタンの分の余白。 */
private val TOP_BAR_SPACE = 80.dp
