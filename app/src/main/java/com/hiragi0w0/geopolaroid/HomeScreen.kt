package com.hiragi0w0.geopolaroid

import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale

data class PhotoCard(
    val imageResID: Int,
    val time: String,
    val place: String
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier){
    var isVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val locationClient = remember(context) {
        LocationServices.getFusedLocationProviderClient(context)
    }
    var locationState by remember { mutableStateOf<LocationState>(LocationState.Idle) }
    var activeRequest by remember { mutableStateOf<CancellationTokenSource?>(null) }

    fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun fetchCurrentLocation() {
        val hasFineLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val hasCoarseLocation = hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (!hasFineLocation && !hasCoarseLocation) {
            locationState = LocationState.Failure(
                "位置情報が許可されていません。「現在地を取得」から再試行してください。" +
                    "許可画面が出ない場合は、端末の設定からこのアプリの位置情報を許可してください。"
            )
            return
        }
        val locationManager = context.getSystemService(LocationManager::class.java)
        if (!LocationManagerCompat.isLocationEnabled(locationManager)) {
            locationState = LocationState.Failure(
                "端末の位置情報がOFFです。設定でONにしてから再試行してください。"
            )
            return
        }
        val cancellation = CancellationTokenSource()
        activeRequest = cancellation
        locationState = LocationState.Loading
        val request = CurrentLocationRequest.Builder()
            // 概略のみ許可された場合、返される座標の精度はOS側で制限される。
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            // 直近10秒までを許容し、古い座標で無条件に代用しない。
            .setMaxUpdateAgeMillis(MAX_LOCATION_AGE_MILLIS)
            .setDurationMillis(15_000)
            .build()
        try {
            locationClient.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (activeRequest !== cancellation) return@addOnSuccessListener
                    activeRequest = null
                    val ageMillis = location?.let {
                        (SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos) / 1_000_000
                    }
                    locationState = if (location == null || ageMillis == null ||
                        ageMillis !in 0..MAX_LOCATION_AGE_MILLIS
                    ) {
                        LocationState.Failure(
                            "現在地を取得できませんでした。電波状況や位置情報設定を確認して再試行してください。"
                        )
                    } else {
                        LocationState.Success(location.latitude, location.longitude)
                    }
                }
                .addOnFailureListener { error ->
                    if (activeRequest !== cancellation) return@addOnFailureListener
                    activeRequest = null
                    locationState = LocationState.Failure(
                        if (error is SecurityException) "位置情報の権限を確認して再試行してください。"
                        else "位置情報の取得でエラーが発生しました。しばらくしてから再試行してください。"
                    )
                }
        } catch (_: SecurityException) {
            activeRequest = null
            cancellation.cancel()
            locationState = LocationState.Failure("位置情報の権限を確認して再試行してください。")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // 許可結果を再確認するため、概略のみの許可でも同じ入口を通す。
        fetchCurrentLocation()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activeRequest != null) {
                val cancellation = activeRequest
                activeRequest = null
                cancellation?.cancel()
                locationState = LocationState.Failure(
                    "画面を離れたため取得を中断しました。再試行してください。"
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val cancellation = activeRequest
            activeRequest = null
            cancellation?.cancel()
        }
    }
    val pictures = listOf (
        PhotoCard(
            R.drawable.test1,
            "10:30",
            "天神周辺"
        ),
        PhotoCard(
            R.drawable.test2,
            "15:00",
            "大濠公園周辺"
        )
    )
    val pictureCount = if (isVisible) pictures.size else 0

    Column(modifier = modifier){
        Text(text = "今日の記録")
        Text(text = "$pictureCount / 12枚")
        Button(onClick = {}, enabled = false) {
            Text(text = "撮影する")
        }
        Button(onClick = {
            if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
            ) {
                fetchCurrentLocation()
            } else {
                locationState = LocationState.Loading
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }, enabled = locationState !is LocationState.Loading) {
            Text(text = "現在地を取得")
        }
        when (val state = locationState) {
            LocationState.Idle -> Text(text = "取得状態：未取得")
            LocationState.Loading -> Text(text = "取得状態：取得中")
            is LocationState.Success -> {
                Text(text = "取得状態：成功")
                Text(text = String.format(Locale.JAPAN, "緯度：%.6f", state.latitude))
                Text(text = String.format(Locale.JAPAN, "経度：%.6f", state.longitude))
            }
            is LocationState.Failure -> {
                Text(text = "取得状態：取得できませんでした")
                Text(text = state.message)
            }
        }
        Button(onClick = { isVisible = !isVisible }, enabled = true) {
            Text(text = "サンプル表示 ON/OFF")
        }
        Text(text = "サンプル")
        if (isVisible) {
            pictures.forEach { picture ->
                Text(text = picture.time + " " + picture.place)
                Image(
                    painter = painterResource(id = picture.imageResID),
                    contentDescription = "お化けの写真",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
    }
}

private sealed interface LocationState {
    data object Idle : LocationState
    data object Loading : LocationState
    data class Success(val latitude: Double, val longitude: Double) : LocationState
    data class Failure(val message: String) : LocationState
}

private const val MAX_LOCATION_AGE_MILLIS = 10_000L
