package com.hiragi0w0.geopolaroid.location

import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.PlaceNameResolver
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.testing.FakePhotoRepository
import com.hiragi0w0.geopolaroid.testing.FakePlaceNameResolver
import com.hiragi0w0.geopolaroid.testing.photo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仕様6章-5 と T06：地名取得失敗でも写真・座標は残る。 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    fun serviceTracksResolvingIdsAndSurvivesRepositoryErrors() = runTest {
        repository.put(photo("a"))
        val gate = CompletableDeferred<String?>()
        val service = PlaceNameService(
            PlaceNameUpdater(repository, PlaceNameResolver { _, _ -> gate.await() }),
            backgroundScope,
        )
        service.resolveInBackground("a")
        assertEquals(setOf("a"), service.resolvingIds.value)
        gate.complete("糸島市")
        runCurrent()
        assertTrue(service.resolvingIds.value.isEmpty())
        assertEquals("糸島市", repository.findById("a")!!.placeName)

        val failing = object : PhotoRepository by repository {
            override suspend fun findById(photoId: String) = throw IllegalStateException("db")
        }
        val broken = PlaceNameService(PlaceNameUpdater(failing, FakePlaceNameResolver()), backgroundScope)
        broken.resolveInBackground("a")
        runCurrent()
        assertTrue(broken.resolvingIds.value.isEmpty())
    }

    @Test
    fun duplicateRequestsWhileResolvingAreIgnored() = runTest {
        repository.put(photo("a"))
        val gate = CompletableDeferred<String?>()
        var calls = 0
        val service = PlaceNameService(
            PlaceNameUpdater(repository, PlaceNameResolver { _, _ -> calls++; gate.await() }),
            backgroundScope,
        )
        service.resolveInBackground("a")
        service.resolveInBackground("a")
        runCurrent()
        gate.complete("糸島市")
        runCurrent()
        assertEquals(1, calls)
        assertTrue(service.resolvingIds.value.isEmpty())
    }

    @Test
    fun bulkResolutionRunsOneAtATimeAndSkipsInProgressIds() = runTest {
        repeat(3) { repository.put(photo("p$it")) }
        val gate = CompletableDeferred<String?>()
        var calls = 0
        var active = 0
        var maxActive = 0
        val service = PlaceNameService(
            PlaceNameUpdater(repository, PlaceNameResolver { _, _ ->
                calls++
                active++
                maxActive = maxOf(maxActive, active)
                try { gate.await() } finally { active-- }
            }),
            backgroundScope,
        )
        service.resolveInBackground("p0") // 個別に取得中
        service.resolveAllInBackground(listOf("p0", "p1", "p2", "p1"))
        runCurrent()
        assertEquals(setOf("p0", "p1", "p2"), service.resolvingIds.value)
        assertEquals(2, maxActive) // 個別の p0 と一括の1件だけ
        gate.complete("糸島市")
        runCurrent()
        assertEquals(3, calls)
        assertTrue(service.resolvingIds.value.isEmpty())
        assertTrue(repository.saved().all { it.placeName == "糸島市" })
    }

    @Test
    fun cancelledBulkResolutionDoesNotLeaveIdsResolving() = runTest {
        repeat(2) { repository.put(photo("p$it")) }
        val scope = kotlinx.coroutines.CoroutineScope(coroutineContext + kotlinx.coroutines.Job())
        val service = PlaceNameService(
            PlaceNameUpdater(repository, PlaceNameResolver { _, _ -> CompletableDeferred<String?>().await() }),
            scope,
        )
        service.resolveAllInBackground(listOf("p0", "p1"))
        runCurrent()
        scope.coroutineContext[kotlinx.coroutines.Job]!!.cancel()
        runCurrent()
        assertTrue(service.resolvingIds.value.isEmpty())
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
