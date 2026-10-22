package com.hiragi0w0.geopolaroid.map

import com.hiragi0w0.geopolaroid.domain.PhotoRepository
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.testing.FakeMediaGateway
import com.hiragi0w0.geopolaroid.testing.FakePhotoRepository
import com.hiragi0w0.geopolaroid.testing.photo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** SC-03 完成地図の状態（T08, T10, T12、地名取得中の表示）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class MapControllerTest {
    private val date = LocalDate.parse("2026-10-21")
    private val repository = FakePhotoRepository()
    private val media = FakeMediaGateway()
    private val resolving = MutableStateFlow<Set<String>>(emptySet())

    private fun TestScope.controller(repo: PhotoRepository = repository): MapController {
        val c = MapController(backgroundScope, date, repo, media, resolving)
        backgroundScope.launch { c.uiState.collect {} }
        runCurrent()
        return c
    }

    private fun saved(id: String, at: Long, lat: Double, recordDate: String = "2026-10-21") =
        photo(id, date = recordDate, at = at, lat = lat, uri = "u-$id").also {
            repository.put(it)
            media.write(it.imageUri)
        }

    // T08
    @Test
    fun showsOnlySavedPhotosOfThatDateInCaptureOrder() = runTest {
        saved("b", at = 2, lat = 2.0)
        saved("a", at = 1, lat = 1.0)
        saved("other", at = 3, lat = 3.0, recordDate = "2026-10-22")
        repository.put(photo("p", date = "2026-10-21", at = 4, state = SaveState.PENDING))
        val state = controller().uiState.value
        assertFalse(state.isLoading)
        assertEquals(listOf("a", "b"), state.model.pins.map { it.photoId })
        assertEquals(listOf(1.0, 2.0), state.model.polyline.map { it.latitude })
    }

    // T10
    @Test
    fun excludeRemovesPinAndLineButKeepsOriginal() = runTest {
        saved("a", at = 1, lat = 1.0)
        saved("b", at = 2, lat = 2.0)
        val c = controller()
        c.exclude("b")
        runCurrent()
        val state = c.uiState.value
        assertEquals(listOf("a"), state.model.pins.map { it.photoId })
        assertTrue(state.model.polyline.isEmpty())
        assertTrue("u-b" in media.items)
        assertTrue(media.deleted.isEmpty())
    }

    @Test
    fun excludeFailureIsReportedAndDismissable() = runTest {
        saved("a", at = 1, lat = 1.0)
        val failing = object : PhotoRepository by repository {
            override suspend fun excludeSaved(photoId: String): Boolean = throw IllegalStateException("db")
        }
        val c = controller(failing)
        c.exclude("a")
        runCurrent()
        assertTrue(c.excludeFailed.value)
        c.dismissExcludeError()
        assertFalse(c.excludeFailed.value)
        assertEquals(1, c.uiState.value.model.pins.size)
    }

    // T12
    @Test
    fun externallyDeletedOriginalIsDetectedOnResume() = runTest {
        saved("a", at = 1, lat = 1.0)
        val c = controller()
        assertTrue(c.uiState.value.missingPhotoIds.isEmpty())
        media.items.remove("u-a")
        c.onResume()
        runCurrent()
        assertEquals(setOf("a"), c.uiState.value.missingPhotoIds)
        assertEquals(1, c.uiState.value.model.pins.size) // ピンも一覧も消えず、除外できる
    }

    @Test
    fun placeNameResolvingStateIsPropagated() = runTest {
        saved("a", at = 1, lat = 1.0)
        val c = controller()
        resolving.value = setOf("a")
        runCurrent()
        assertEquals(setOf("a"), c.uiState.value.resolvingPlaceIds)
        resolving.value = emptySet()
        runCurrent()
        assertTrue(c.uiState.value.resolvingPlaceIds.isEmpty())
    }
}
