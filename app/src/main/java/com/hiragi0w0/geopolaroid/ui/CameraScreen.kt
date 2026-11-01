package com.hiragi0w0.geopolaroid.ui

import android.location.Location
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.home.CameraRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * アプリ内カメラ。カメラを開く前に位置の取得は済んでいる（取得できなければこの画面は開かない）。
 *
 * 原本は端末の写真ライブラリ向けに 3:4 の縦長で撮り、表示用には中央を正方形に切り抜く。
 * その切り抜き範囲を構図ガイドとして重ねる。シャッター後は一時ファイルの写真を見せ、
 * 「OK」なら [onPhotoTaken] で原本へ書き込み、「やり直す」なら一時ファイルを消して撮り直す。
 */
@Composable
fun CameraScreen(
    request: CameraRequest,
    photoNumber: Int,
    onClose: () -> Unit,
    onPhotoTaken: (path: String, capturedAtUtc: Long) -> Unit,
    onCameraUnavailable: () -> Unit,
) {
    val context = LocalContext.current
    val captureDir = remember { File(context.cacheDir, CAPTURE_DIR) }
    // プロセスが作り直されても、撮った写真の確認から続けられるよう保存する。
    var capturedPath by rememberSaveable(request.photoId) { mutableStateOf<String?>(null) }
    var capturedAt by rememberSaveable(request.photoId) { mutableStateOf<Long?>(null) }
    var shooting by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val reviewing = capturedPath != null

    LightOnDarkSystemBars()

    // 前回の途中終了などで残った一時ファイルを片付ける（確認中の写真は残す）。
    LaunchedEffect(captureDir) {
        withContext(Dispatchers.IO) {
            captureDir.listFiles()?.filter { it.path != capturedPath }?.forEach { it.delete() }
        }
    }

    val retake = {
        capturedPath?.let { File(it).delete() }
        capturedPath = null
        capturedAt = null
    }
    val close = {
        capturedPath?.let { File(it).delete() }
        onClose()
    }
    BackHandler { if (reviewing) retake() else close() }

    val controller = rememberCameraController(onCameraUnavailable)
    val lifecycleOwner = LocalLifecycleOwner.current
    // 確認中はカメラを止める。
    DisposableEffect(controller, lifecycleOwner, reviewing) {
        if (!reviewing) controller.bindToLifecycle(lifecycleOwner)
        onDispose { controller.unbind() }
    }

    val shoot = shoot@{
        if (shooting) return@shoot
        captureDir.mkdirs()
        val shutterAt = System.currentTimeMillis()
        // 同じ写真の撮り直しでも画像のキャッシュを取り違えないよう、撮るたびに名前を変える。
        val file = File(captureDir, "${request.photoId}_$shutterAt.jpg")
        val metadata = ImageCapture.Metadata().apply {
            location = Location(LOCATION_PROVIDER).apply {
                latitude = request.latitude
                longitude = request.longitude
            }
        }
        shooting = true
        failed = false
        controller.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).setMetadata(metadata).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    shooting = false
                    capturedPath = file.path
                    capturedAt = shutterAt
                }

                override fun onError(exception: ImageCaptureException) {
                    shooting = false
                    failed = true
                    file.delete()
                }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            CameraTopBar(photoNumber = photoNumber, onClose = close)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(FRAME_ASPECT),
                ) {
                    val path = capturedPath
                    if (path == null) {
                        AndroidView(
                            factory = { viewContext ->
                                PreviewView(viewContext).apply {
                                    scaleType = PreviewView.ScaleType.FIT_CENTER
                                    this.controller = controller
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        AsyncImage(
                            model = File(path),
                            contentDescription = stringResource(R.string.camera_preview_description),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    SquareGuide(modifier = Modifier.fillMaxSize())
                }
            }
            Text(
                text = stringResource(if (failed) R.string.camera_capture_failed else R.string.camera_guide_note),
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 12.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CONTROLS_HEIGHT),
                contentAlignment = Alignment.Center,
            ) {
                val path = capturedPath
                val at = capturedAt
                if (path != null && at != null) {
                    ReviewButtons(onRetake = retake, onUse = { onPhotoTaken(path, at) })
                } else {
                    CameraShutterButton(busy = shooting, onClick = shoot)
                }
            }
        }
    }
}

/** 背面カメラで写真だけを撮るコントローラー。カメラがない・起動できない場合は [onCameraUnavailable]。 */
@Composable
private fun rememberCameraController(onCameraUnavailable: () -> Unit): LifecycleCameraController {
    val context = LocalContext.current
    val currentOnUnavailable by rememberUpdatedState(onCameraUnavailable)
    val controller = remember {
        LifecycleCameraController(context).apply {
            setEnabledUseCases(CameraController.IMAGE_CAPTURE)
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        }
    }
    DisposableEffect(controller) {
        var active = true
        val future = controller.initializationFuture
        future.addListener(
            {
                val usable = try {
                    future.get()
                    controller.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                } catch (e: Exception) {
                    false
                }
                if (active && !usable) currentOnUnavailable()
            },
            ContextCompat.getMainExecutor(context),
        )
        onDispose { active = false }
    }
    return controller
}

@Composable
private fun CameraTopBar(photoNumber: Int, onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = stringResource(R.string.action_close),
                tint = Color.White,
            )
        }
        Text(
            text = stringResource(R.string.camera_counter, photoNumber, MAX_PHOTOS_PER_RECORD_DATE),
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier.padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(LOCATION_READY_COLOR),
            )
            Text(
                text = stringResource(R.string.camera_location_ready),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** 正方形の切り抜き範囲の外を暗くし、範囲を細い線で示す。 */
@Composable
private fun SquareGuide(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val side = minOf(size.width, size.height)
        val left = (size.width - side) / 2
        val top = (size.height - side) / 2
        val shade = Color.Black.copy(alpha = 0.45f)
        drawRect(shade, topLeft = Offset.Zero, size = Size(size.width, top))
        drawRect(shade, topLeft = Offset(0f, top + side), size = Size(size.width, size.height - top - side))
        drawRect(shade, topLeft = Offset(0f, top), size = Size(left, side))
        drawRect(shade, topLeft = Offset(left + side, top), size = Size(size.width - left - side, side))
        drawRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = Offset(left, top),
            size = Size(side, side),
            style = Stroke(width = 1.dp.toPx()),
        )
    }
}

@Composable
private fun CameraShutterButton(busy: Boolean, onClick: () -> Unit) {
    val label = stringResource(R.string.camera_shutter)
    Box(
        modifier = Modifier
            .size(76.dp)
            .alpha(if (busy) 0.6f else 1f)
            .clip(CircleShape)
            .clickable(enabled = !busy, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .border(3.dp, Color.White, CircleShape)
            .padding(7.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(28.dp), color = Color.Black, strokeWidth = 2.5.dp)
        }
    }
}

@Composable
private fun ReviewButtons(onRetake: () -> Unit, onUse: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedButton(onClick = onRetake) {
            Text(stringResource(R.string.camera_retake), color = Color.White)
        }
        FilledTonalButton(onClick = onUse) {
            Text(stringResource(R.string.camera_use))
        }
    }
}

/** 黒い画面の上でもステータスバーの文字が見えるよう、カメラを開いている間だけ明るいアイコンにする。 */
@Composable
private fun LightOnDarkSystemBars() {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window) {
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        val previousStatus = insetsController.isAppearanceLightStatusBars
        val previousNavigation = insetsController.isAppearanceLightNavigationBars
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = false
        onDispose {
            insetsController.isAppearanceLightStatusBars = previousStatus
            insetsController.isAppearanceLightNavigationBars = previousNavigation
        }
    }
}

/** カメラの一時ファイルを置く cacheDir 内のフォルダ。 */
private const val CAPTURE_DIR = "capture"
private const val LOCATION_PROVIDER = "geopolaroid"

/** 撮影する写真とファインダーの縦横比（横:縦 = 3:4）。 */
private const val FRAME_ASPECT = 3f / 4f
private val CONTROLS_HEIGHT = 128.dp
private val LOCATION_READY_COLOR = Color(0xFFA8C49A)
