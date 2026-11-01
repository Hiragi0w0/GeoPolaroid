package com.hiragi0w0.geopolaroid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.hiragi0w0.geopolaroid.AppContainer
import com.hiragi0w0.geopolaroid.BuildConfig
import com.hiragi0w0.geopolaroid.domain.RecordDay
import com.hiragi0w0.geopolaroid.home.CameraRequest
import com.hiragi0w0.geopolaroid.home.CaptureStatus
import com.hiragi0w0.geopolaroid.location.FusedLocationProvider
import java.time.LocalDate

/**
 * 画面遷移：ホーム → 撮影 → アプリ内カメラ → ホーム／ホーム → 過去の記録 → 完成地図 → 戻る（過去の記録へ）。
 * 04:00 前の記録日は一覧、04:00 を越えた記録日だけ地図で開く。
 */
@Composable
fun GeoPolaroidApp(container: AppContainer) {
    val context = LocalContext.current
    val homeViewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container, createSavedStateHandle()) }
        },
    )
    val home = homeViewModel.controller
    val currentRecordDate by home.currentRecordDate.collectAsStateWithLifecycle()

    // アプリ再開時に必ず完成判定をやり直し、アプリ外で削除された原本も再検査する。
    // 開いたまま 04:00 を跨いだ場合は HomeController のタイマーが切り替える。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { home.onResume() }
    val activity = LocalActivity.current
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        home.onStop(isChangingConfigurations = activity?.isChangingConfigurations == true)
    }

    // 開いているアプリ内カメラ。プロセスが作り直されても同じ撮影の続きから開く。
    var cameraSession by rememberSaveable(stateSaver = CameraRequestSaver) { mutableStateOf<CameraRequest?>(null) }
    val cameraRequest by home.cameraRequest.collectAsStateWithLifecycle()
    LaunchedEffect(cameraRequest) {
        val request = cameraRequest ?: return@LaunchedEffect
        if (home.consumeCameraRequest(request)) cameraSession = request
    }
    val captureStatus = home.uiState.collectAsStateWithLifecycle().value.captureStatus
    // 撮影が終わった（復旧処理で片付いた等）のにカメラが開いたままにならないようにする。
    LaunchedEffect(captureStatus) {
        if (captureStatus == CaptureStatus.IDLE) cameraSession = null
    }

    val hasCameraPermission = {
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    val startCaptureIfPermitted = {
        when {
            !FusedLocationProvider.hasLocationPermission(context) -> home.onLocationPermissionDenied()
            !hasCameraPermission() -> home.onCameraPermissionDenied()
            else -> home.startCapture()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { startCaptureIfPermitted() }
    val requestCapture = {
        val missing = buildList {
            if (!FusedLocationProvider.hasLocationPermission(context)) addAll(FusedLocationProvider.PERMISSIONS)
            if (!hasCameraPermission()) add(Manifest.permission.CAMERA)
        }
        if (missing.isEmpty()) home.startCapture() else permissionLauncher.launch(missing.toTypedArray())
    }

    var showPastRecords by rememberSaveable { mutableStateOf(false) }
    var openedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val mapDate = openedDate?.let(LocalDate::parse)

    val homeState by home.uiState.collectAsStateWithLifecycle()
    val screen: Screen = cameraSession?.let(Screen::Camera)
        ?: mapDate?.let(Screen::Map)
        ?: if (showPastRecords) Screen.PastRecords else Screen.Home

    // 戻る操作は表示中の画面だけが受ける（切り替えアニメーション中に消えていく側の画面が受けないように外で持つ）。
    BackHandler(enabled = screen is Screen.PastRecords) { showPastRecords = false }
    BackHandler(enabled = screen is Screen.Map) { openedDate = null }

    AnimatedContent(
        targetState = screen,
        contentKey = Screen::key,
        transitionSpec = { screenTransition(initialState, targetState) },
        label = "screen",
    ) { target ->
        when (target) {
            is Screen.Camera -> {
                val camera = target.request
                // 閉じていくカメラの操作（消える途中の戻る操作など）では撮影結果を二重に扱わない。
                val isCurrent = { cameraSession?.photoId == camera.photoId }
                CameraScreen(
                    request = camera,
                    photoNumber = homeState.activeCount,
                    onClose = {
                        if (isCurrent()) {
                            cameraSession = null
                            home.onCameraResult(false)
                        }
                    },
                    onPhotoTaken = { path, capturedAtUtc ->
                        if (isCurrent()) {
                            cameraSession = null
                            home.onPhotoTaken(path, capturedAtUtc)
                        }
                    },
                    onCameraUnavailable = {
                        if (isCurrent()) {
                            cameraSession = null
                            home.onCameraLaunchFailed(camera.photoId)
                        }
                    },
                )
            }
            Screen.PastRecords -> PastRecordsScreen(
                dates = homeState.pastDates,
                onBack = { showPastRecords = false },
                onSelect = { date ->
                    home.refreshRecordDate()
                    if (RecordDay.isCompleted(date, home.currentRecordDate.value)) {
                        openedDate = date.toString()
                    }
                },
            )
            Screen.Home -> HomeScreen(
                state = homeState,
                onCaptureClick = requestCapture,
                onRetry = requestCapture,
                onOpenSettings = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            "package:${context.packageName}".toUri(),
                        ),
                    )
                },
                onDismissMessage = home::dismissMessage,
                onOpenPastRecords = { showPastRecords = true },
                onExclude = { home.exclude(it.photoId) },
            )
            is Screen.Map -> {
                val date = target.date
                // 完成していない記録日は地図で開かない（時刻変更などで境界前に戻った場合）。
                LaunchedEffect(date, currentRecordDate) {
                    if (!RecordDay.isCompleted(date, currentRecordDate) && openedDate == date.toString()) {
                        openedDate = null
                    }
                }
                val mapViewModel: MapViewModel = viewModel(
                    key = "map-$date",
                    factory = viewModelFactory {
                        initializer {
                            MapViewModel(
                                recordDate = date,
                                repository = container.repository,
                                mediaGateway = container.mediaGateway,
                                resolvingPlaceIds = container.placeNames.resolvingIds,
                            )
                        }
                    },
                )
                val map = mapViewModel.controller
                LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { map.onResume() }
                val mapState by map.uiState.collectAsStateWithLifecycle()
                val excludeFailed by map.excludeFailed.collectAsStateWithLifecycle()
                MapScreen(
                    state = mapState,
                    hasMapsApiKey = BuildConfig.HAS_MAPS_API_KEY,
                    excludeFailed = excludeFailed,
                    onBack = { openedDate = null },
                    onExclude = { map.exclude(it.photoId) },
                    onDismissExcludeError = map::dismissExcludeError,
                )
            }
        }
    }
}

/** 表示する画面。[depth] は階層の深さで、深くなる向きなら右から入り、戻る向きなら左から入る。 */
private sealed interface Screen {
    val depth: Int
    val key: String

    data object Home : Screen {
        override val depth = 0
        override val key = "home"
    }

    data object PastRecords : Screen {
        override val depth = 1
        override val key = "past"
    }

    data class Map(val date: LocalDate) : Screen {
        override val depth = 2
        override val key get() = "map-$date"
    }

    data class Camera(val request: CameraRequest) : Screen {
        override val depth = 1
        override val key get() = "camera-${request.photoId}"
    }
}

/** カメラは下から重ねて開き、下へ閉じる。それ以外は階層の向きに横へ滑らせる。 */
private fun screenTransition(from: Screen, to: Screen): ContentTransform {
    val fade = tween<Float>(SCREEN_FADE_MS)
    return when {
        to is Screen.Camera ->
            slideInVertically(tween(SCREEN_SLIDE_MS)) { it } + fadeIn(fade) togetherWith fadeOut(tween(SCREEN_SLIDE_MS))
        from is Screen.Camera ->
            (fadeIn(fade) togetherWith slideOutVertically(tween(SCREEN_SLIDE_MS)) { it } + fadeOut(fade))
                .apply { targetContentZIndex = -1f }
        to.depth >= from.depth ->
            slideInHorizontally(tween(SCREEN_SLIDE_MS)) { it / 3 } + fadeIn(fade) togetherWith
                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 } + fadeOut(fade)
        else ->
            (slideInHorizontally(tween(SCREEN_SLIDE_MS)) { -it / 4 } + fadeIn(fade) togetherWith
                slideOutHorizontally(tween(SCREEN_SLIDE_MS)) { it / 3 } + fadeOut(fade))
                .apply { targetContentZIndex = -1f }
    }
}

private const val SCREEN_SLIDE_MS = 320
private const val SCREEN_FADE_MS = 220

/** [CameraRequest] を画面の保存状態に入れるための変換。 */
private val CameraRequestSaver = listSaver<CameraRequest?, Any>(
    save = { request ->
        if (request == null) emptyList() else listOf(request.photoId, request.imageUri, request.latitude, request.longitude)
    },
    restore = { values ->
        CameraRequest(
            photoId = values[0] as String,
            imageUri = values[1] as String,
            latitude = values[2] as Double,
            longitude = values[3] as Double,
        )
    },
)
