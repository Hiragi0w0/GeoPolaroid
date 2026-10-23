package com.hiragi0w0.geopolaroid.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.hiragi0w0.geopolaroid.domain.LocationProvider
import com.hiragi0w0.geopolaroid.domain.LocationResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * FusedLocationProviderClient.getCurrentLocation() で現在地を1回取得する。
 * getLastLocation() は使わず、[MAX_UPDATE_AGE_MS] より古い位置は採用しない。
 */
class FusedLocationProvider(private val context: Context) : LocationProvider {
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission") // 直前で権限を確認し、SecurityException も捕捉している
    override suspend fun getCurrentLocation(): LocationResult {
        if (!hasLocationPermission(context)) return LocationResult.PermissionDenied
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(MAX_UPDATE_AGE_MS)
            .setDurationMillis(TIMEOUT_MS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            val location = withTimeoutOrNull(TIMEOUT_MS + TIMEOUT_MARGIN_MS) {
                client.getCurrentLocation(request, cancellation.token).await(cancellation)
            }
            if (location == null) {
                LocationResult.Unavailable
            } else {
                LocationResult.Success(location.latitude, location.longitude)
            }
        } catch (e: SecurityException) {
            LocationResult.PermissionDenied
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LocationResult.Unavailable
        } finally {
            cancellation.cancel()
        }
    }

    companion object {
        private const val MAX_UPDATE_AGE_MS = 15_000L
        private const val TIMEOUT_MS = 30_000L
        private const val TIMEOUT_MARGIN_MS = 5_000L

        val PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        fun hasLocationPermission(context: Context): Boolean = PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }
}
