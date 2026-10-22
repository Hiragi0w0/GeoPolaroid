package com.hiragi0w0.geopolaroid.location

import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.testing.FakePhotoRepository
import com.hiragi0w0.geopolaroid.testing.FakePlaceNameResolver
import com.hiragi0w0.geopolaroid.testing.photo
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仕様6章-5 と T06：地名取得失敗でも写真・座標は残る。 */
class PlaceNameTest {
    private val repository = FakePhotoRepository()

    @Test
    fun successUpdatesOnlyPlaceName() = runTest {
        repository.put(photo("a", lat = 33.5, lng = 130.4))
        repository.put(photo("b"))
        assertTrue(PlaceNameUpdater(repository, FakePlaceNameResolver()).update("a"))
        assertEquals("福岡市中央区", repository.findById("a")!!.placeName)
        assertEquals(33.5, repository.findById("a")!!.latitude, 0.0)
        assertNull(repository.findById("b")!!.placeName)
    }

    // T06
    @Test
    fun failureKeepsPhotoAndCoordinates() = runTest {
        repository.put(photo("a", lat = 33.5, lng = 130.4))
        assertFalse(PlaceNameUpdater(repository, FakePlaceNameResolver { null }).update("a"))
        val stored = repository.findById("a")!!
        assertEquals(SaveState.SAVED, stored.saveState)
        assertNull(stored.placeName)
        assertEquals(130.4, stored.longitude, 0.0)
    }

    @Test
    fun resolverExceptionIsSwallowed() = runTest {
        repository.put(photo("a"))
        val updater = PlaceNameUpdater(repository, FakePlaceNameResolver { throw java.io.IOException("offline") })
        assertFalse(updater.update("a"))
        assertNull(repository.findById("a")!!.placeName)
    }

    @Test
    fun excludedOrPendingPhotoIsNotUpdated() = runTest {
        repository.put(photo("p", state = SaveState.PENDING))
        val updater = PlaceNameUpdater(repository, FakePlaceNameResolver())
        assertFalse(updater.update("p"))
        assertFalse(updater.update("gone"))
    }

    @Test
    fun formatterBuildsCityLevelName() {
        assertEquals("福岡市中央区", PlaceNameFormatter.format("福岡県", null, "福岡市", "中央区"))
        assertEquals("渋谷区", PlaceNameFormatter.format("東京都", null, "渋谷区", "神南"))
        assertEquals("糸島市", PlaceNameFormatter.format("福岡県", "糸島市", null, null))
        assertEquals("北海道", PlaceNameFormatter.format("北海道", null, null, null))
        assertEquals("Mountain View", PlaceNameFormatter.format("California", "Santa Clara County", "Mountain View", null))
        assertNull(PlaceNameFormatter.format(null, " ", null, null))
    }
}
