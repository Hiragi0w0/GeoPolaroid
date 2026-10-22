package com.hiragi0w0.geopolaroid.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
import com.hiragi0w0.geopolaroid.location.FusedLocationProvider
import java.time.LocalDate

/**
 * 画面遷移：ホーム → 撮影 → OSカメラ → ホーム／ホーム → 過去の日付 → 完成地図 → 戻る。
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
    val currentRecordDate by homeViewModel.currentRecordDate.collectAsStateWithLifecycle()

    // アプリ再開時に必ず完成判定をやり直す（開いたまま 04:00 を跨いだ場合も含む）。
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { homeViewModel.refreshRecordDate() }

    // カメラ結果はどの画面にいても受け取れるようルートで登録する。
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        homeViewModel.onCameraResult(success)
    }
    val cameraRequest by homeViewModel.cameraRequest.collectAsStateWithLifecycle()
    LaunchedEffect(cameraRequest) {
        val request = cameraRequest ?: return@LaunchedEffect
        if (!homeViewModel.consumeCameraRequest(request)) return@LaunchedEffect
        try {
            cameraLauncher.launch(request.imageUri.toUri())
        } catch (e: ActivityNotFoundException) {
            homeViewModel.onCameraLaunchFailed(request.photoId)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.any { it }) homeViewModel.startCapture() else homeViewModel.onLocationPermissionDenied()
    }
    val requestCapture = {
        if (FusedLocationProvider.hasLocationPermission(context)) {
            homeViewModel.startCapture()
        } else {
            permissionLauncher.launch(FusedLocationProvider.PERMISSIONS)
        }
    }

    var openedDate by rememberSaveable { mutableStateOf<String?>(null) }
    val mapDate = openedDate?.let(LocalDate::parse)

    if (mapDate == null) {
        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
        HomeScreen(
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
            onDismissMessage = homeViewModel::dismissMessage,
            onOpenPastDate = { date ->
                homeViewModel.refreshRecordDate()
                if (RecordDay.isCompleted(date, homeViewModel.currentRecordDate.value)) {
                    openedDate = date.toString()
                }
            },
            onExclude = { homeViewModel.exclude(it.photoId) },
        )
    } else {
        BackHandler { openedDate = null }
        // 完成していない記録日は地図で開かない（時刻変更などで境界前に戻った場合）。
        LaunchedEffect(mapDate, currentRecordDate) {
            if (!RecordDay.isCompleted(mapDate, currentRecordDate)) openedDate = null
        }
        val mapViewModel: MapViewModel = viewModel(
            key = "map-$mapDate",
            factory = viewModelFactory {
                initializer { MapViewModel(mapDate, container.repository) }
            },
        )
        val mapState by mapViewModel.uiState.collectAsStateWithLifecycle()
        val excludeFailed by mapViewModel.excludeFailed.collectAsStateWithLifecycle()
        MapScreen(
            state = mapState,
            hasMapsApiKey = BuildConfig.HAS_MAPS_API_KEY,
            excludeFailed = excludeFailed,
            onBack = { openedDate = null },
            onExclude = { mapViewModel.exclude(it.photoId) },
            onDismissExcludeError = mapViewModel::dismissExcludeError,
        )
    }
}
