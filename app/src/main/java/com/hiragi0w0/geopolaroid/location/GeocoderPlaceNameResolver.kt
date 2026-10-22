package com.hiragi0w0.geopolaroid.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.hiragi0w0.geopolaroid.domain.PlaceNameResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

/** Android Geocoder による逆ジオコーディング。通信不可・サービスなし等はすべて null。 */
class GeocoderPlaceNameResolver(private val context: Context) : PlaceNameResolver {

    override suspend fun resolve(latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = try {
            withTimeoutOrNull(TIMEOUT_MS) { fetchAddress(geocoder, latitude, longitude) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        } ?: return null
        return PlaceNameFormatter.format(
            adminArea = address.adminArea,
            subAdminArea = address.subAdminArea,
            locality = address.locality,
            subLocality = address.subLocality,
        )
    }

    private suspend fun fetchAddress(geocoder: Geocoder, latitude: Double, longitude: Double): Address? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            suspendCancellableCoroutine { continuation ->
                geocoder.getFromLocation(latitude, longitude, 1, object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        if (continuation.isActive) continuation.resume(null)
                    }
                })
            }
        } else {
            withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
            }
        }

    private companion object {
        const val TIMEOUT_MS = 15_000L
    }
}
