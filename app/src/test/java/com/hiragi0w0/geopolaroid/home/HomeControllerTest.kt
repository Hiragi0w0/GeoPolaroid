package com.hiragi0w0.geopolaroid.home

import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator
import com.hiragi0w0.geopolaroid.domain.LocationResult
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.MediaCheck
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.domain.TimeSource
import com.hiragi0w0.geopolaroid.location.PlaceNameService
import com.hiragi0w0.geopolaroid.location.PlaceNameUpdater
import com.hiragi0w0.geopolaroid.testing.FakeLocationProvider
import com.hiragi0w0.geopolaroid.testing.FakeMediaGateway
import com.hiragi0w0.geopolaroid.testing.FakePhotoRepository
import com.hiragi0w0.geopolaroid.testing.FakePlaceNameResolver
import com.hiragi0w0.geopolaroid.testing.photo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** SC-01 ホームの撮影状態遷移（多重起動防止・プロセス再生成後の復元・復旧との順序・04:00 切替）。 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeControllerTest {
    private val zone = ZoneId.of("Asia/Tokyo")
    private val repository = FakePhotoRepository()
    private val media = FakeMediaGateway()
    private val location = FakeLocationProvider()
    private val resolver = FakePlaceNameResolver()
    private var nextId = 0

    /** テストの仮想時間に連動する時計。 */
    private fun TestScope.clockFrom(local: String) = object : TimeSource {
        private val base = LocalDateTime.parse(local).atZone(zone).toInstant()
        override fun now(): Instant = base.plusMillis(testScheduler.currentTime)
        override fun zone(): ZoneId = zone
    }

    private fun TestScope.controller(
        store: ActiveCaptureStore = InMemoryActiveCaptureStore(),
        time: TimeSource = clockFrom("2026-10-22T10:30"),
    ): HomeController {
        val coordinator = CaptureCoordinator(repository, location, media, time) { "id-${++nextId}" }
        val placeNames = PlaceNameService(PlaceNameUpdater(repository, resolver), backgroundScope)
        val c = HomeController(backgroundScope, repository, coordinator, media, placeNames, time, store)
        backgroundScope.launch { c.uiState.collect {} }
        runCurrent()
        return c
    }

    private fun HomeController.state() = uiState.value

    private fun savedPhoto(id: String, date: String = "2026-10-22") =
        photo(id, date = date, uri = "u-$id").also {
            repository.put(it)
            media.write(it.imageUri)
        }

    // T01
    @Test
    fun initialStateIsEmptyAndCapturable() = runTest {
        val c = controller()
        val state = c.state()
        assertFalse(state.isLoading)
        assertEquals(LocalDate.parse("2026-10-22"), state.recordDate)
        assertEquals(0, state.savedCount)
        assertTrue(state.photos.isEmpty())
        assertTrue(state.canCapture)
        assertTrue(repository.all.isEmpty()) // 空の日次レコードは作らない
    }

    // T02
    @Test
    fun captureFlowSavesPhotoAndResolvesPlaceName() = runTest {
        val store = InMemoryActiveCaptureStore()
        val c = controller(store)
        c.startCapture()
        runCurrent()

        val request = checkNotNull(c.cameraRequest.value)
        assertEquals(CaptureStatus.CAMERA_OPEN, c.state().captureStatus)
        assertEquals(request.photoId, store.activePhotoId)
        assertFalse(c.state().canCapture)
        assertTrue(c.consumeCameraRequest(request))
        assertFalse(c.consumeCameraRequest(request)) // 同じ要求で2回起動しない

        media.write(request.imageUri)
        c.onCameraResult(true)
        runCurrent()

        val state = c.state()
        assertEquals(CaptureStatus.IDLE, state.captureStatus)
        assertNull(store.activePhotoId)
        assertEquals(1, state.savedCount)
        assertEquals("福岡市中央区", state.photos.single().placeName)
        assertTrue(state.resolvingPlaceIds.isEmpty())
        assertNull(state.message)
    }

    @Test
    fun repeatedTapsPrepareOnlyOnce() = runTest {
        val c = controller()
        c.startCapture()
        c.startCapture()
        runCurrent()
        c.startCapture() // カメラ起動中
        runCurrent()
        assertEquals(1, location.calls)
        assertEquals(1, repository.pending().size)
    }

    // T03
    @Test
    fun cancelShowsMessageAndFreesSlot() = runTest {
        val c = controller()
        c.startCapture()
        runCurrent()
        c.onCameraResult(false)
        runCurrent()
        assertEquals(HomeMessage.CAPTURE_CANCELLED, c.state().message)
        assertEquals(0, c.state().activeCount)
        assertTrue(media.items.isEmpty())
        assertTrue(c.state().canCapture)
    }

    @Test
    fun leavingAppDuringLocationFixAbortsInsteadOfLaunchingLater() = runTest {
        val c = controller()
        c.startCapture()
        c.onStop(isChangingConfigurations = false) // 現在地取得中にホームボタン
        runCurrent()
        assertNull(c.cameraRequest.value)
        assertEquals(HomeMessage.CAPTURE_ABORTED, c.state().message)
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())

        c.onResume() // 戻ってもカメラは自動で開かず、改めて撮影できる
        runCurrent()
        assertNull(c.cameraRequest.value)
        assertTrue(c.state().canCapture)
    }

    @Test
    fun unconsumedCameraRequestIsAbortedOnStop() = runTest {
        val c = controller()
        c.startCapture()
        runCurrent()
        val request = checkNotNull(c.cameraRequest.value)
        c.onStop(isChangingConfigurations = false)
        runCurrent()
        assertFalse(c.consumeCameraRequest(request))
        assertNull(repository.findById(request.photoId))
        assertEquals(CaptureStatus.IDLE, c.state().captureStatus)
    }

    @Test
    fun configurationChangeDuringPreparationDoesNotAbort() = runTest {
        val c = controller()
        c.startCapture()
        c.onStop(isChangingConfigurations = true) // 現在地取得中に画面回転
        runCurrent()
        val request = checkNotNull(c.cameraRequest.value)
        c.onStop(isChangingConfigurations = true) // 要求の消費前にもう一度回転
        runCurrent()
        assertNull(c.state().message)
        assertTrue(c.consumeCameraRequest(request)) // 新しい画面が引き継いで起動できる
        assertEquals(SaveState.PENDING, repository.findById(request.photoId)!!.saveState)
    }

    @Test
    fun stopAfterCameraLaunchDoesNotAbort() = runTest {
        val store = InMemoryActiveCaptureStore()
        val c = controller(store)
        c.startCapture()
        runCurrent()
        val request = checkNotNull(c.cameraRequest.value)
        assertTrue(c.consumeCameraRequest(request))
        c.onStop(isChangingConfigurations = false) // カメラアプリが前面に来た
        runCurrent()
        assertEquals(request.photoId, store.activePhotoId)
        media.write(request.imageUri)
        c.onResume()
        c.onCameraResult(true)
        runCurrent()
        assertEquals(1, c.state().savedCount)
    }

    @Test
    fun cameraLaunchFailureCleansUp() = runTest {
        val c = controller()
        c.startCapture()
        runCurrent()
        c.onCameraLaunchFailed(c.cameraRequest.value!!.photoId)
        runCurrent()
        assertEquals(HomeMessage.CAMERA_NOT_FOUND, c.state().message)
        assertTrue(repository.all.isEmpty())
        assertTrue(c.state().canCapture)
    }

    @Test
    fun resultWithoutActiveCaptureIsIgnored() = runTest {
        val c = controller()
        c.onCameraResult(true)
        runCurrent()
        assertNull(c.state().message)
        assertTrue(repository.all.isEmpty())
    }

    // T05
    @Test
    fun locationFailuresShowRetryableMessages() = runTest {
        val c = controller()
        location.result = LocationResult.PermissionDenied
        c.startCapture()
        runCurrent()
        assertEquals(HomeMessage.LOCATION_PERMISSION_DENIED, c.state().message)
        assertTrue(c.state().message!!.retryable)
        assertEquals(CaptureStatus.IDLE, c.state().captureStatus)

        location.result = LocationResult.Unavailable
        c.startCapture()
        runCurrent()
        assertEquals(HomeMessage.LOCATION_UNAVAILABLE, c.state().message)
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())
    }

    // T04
    @Test
    fun limitDisablesCaptureUntilExclusion() = runTest {
        repeat(MAX_PHOTOS_PER_RECORD_DATE) { savedPhoto("s$it") }
        val c = controller()
        assertTrue(c.state().isLimitReached)
        assertFalse(c.state().canCapture)

        c.exclude("s0")
        runCurrent()
        assertEquals(MAX_PHOTOS_PER_RECORD_DATE - 1, c.state().savedCount)
        assertTrue(c.state().canCapture)
        assertTrue("u-s0" in media.items) // 原本は残る
    }

    // T11
    @Test
    fun restoredActivePhotoIsNotRecoveredAndCompletesOnResult() = runTest {
        // プロセス再生成：SavedState に撮影中の id が残っており、カメラは書き込み済み
        repository.put(photo("p1", state = SaveState.PENDING, uri = "u-p1"))
        media.write("u-p1", MediaCheck.VALID)
        val store = InMemoryActiveCaptureStore("p1")
        val c = controller(store)
        assertEquals(CaptureStatus.CAMERA_OPEN, c.state().captureStatus)
        assertEquals(SaveState.PENDING, repository.findById("p1")!!.saveState)

        c.onCameraResult(true)
        runCurrent()
        assertEquals(SaveState.SAVED, repository.findById("p1")!!.saveState)
        assertNull(store.activePhotoId)
        assertEquals(1, c.state().savedCount)
    }

    // T11
    @Test
    fun recoveryFinishesBeforeNewCaptureIsPrepared() = runTest {
        repository.put(photo("stale", state = SaveState.PENDING, uri = "u-stale")) // 空のまま残った PENDING
        media.write("u-stale", MediaCheck.EMPTY_OR_BROKEN)
        repository.put(photo("lost", state = SaveState.PENDING, uri = "u-lost"))
        media.write("u-lost", MediaCheck.VALID)
        repository.findPendingDelayMs = 500 // 復旧の一覧取得が撮影準備より遅れる
        val time = clockFrom("2026-10-22T10:30")
        val coordinator = CaptureCoordinator(repository, location, media, time) { "new" }
        val placeNames = PlaceNameService(PlaceNameUpdater(repository, resolver), backgroundScope)
        val c = HomeController(backgroundScope, repository, coordinator, media, placeNames, time, InMemoryActiveCaptureStore())
        c.startCapture() // 復旧より先にタップされても待つ
        advanceTimeBy(1_000)
        runCurrent()

        assertNull(repository.findById("stale"))
        assertEquals(SaveState.SAVED, repository.findById("lost")!!.saveState)
        assertEquals("福岡市中央区", repository.findById("lost")!!.placeName)
        val created = repository.findById("new")!!
        assertEquals(SaveState.PENDING, created.saveState)
        assertTrue(created.imageUri in media.items) // 復旧に消されていない
    }

    // T06
    @Test
    fun missingPlaceNamesAreRetriedOnStart() = runTest {
        // 前回は地名取得前にプロセスが終了した／オフラインだった
        val photo = savedPhoto("a")
        repository.put(photo.copy(placeName = null))
        savedPhoto("b").also { repository.put(it.copy(placeName = "糸島市")) }
        var calls = 0
        resolver.answer = { calls++; "福岡市中央区" }
        val c = controller()
        runCurrent()
        assertEquals("福岡市中央区", repository.findById("a")!!.placeName)
        assertEquals("糸島市", repository.findById("b")!!.placeName)
        assertEquals(1, calls)
        assertTrue(c.state().resolvingPlaceIds.isEmpty())
    }

    // T07
    @Test
    fun recordDateSwitchesAt0400WhileOpen() = runTest {
        savedPhoto("yesterday", date = "2026-10-22")
        val c = controller(time = clockFrom("2026-10-23T03:59:30"))
        assertEquals(LocalDate.parse("2026-10-22"), c.state().recordDate)
        assertEquals(1, c.state().savedCount)
        assertTrue(c.state().pastDates.isEmpty())

        advanceTimeBy(29_000)
        runCurrent()
        assertEquals(LocalDate.parse("2026-10-22"), c.currentRecordDate.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertEquals(LocalDate.parse("2026-10-23"), c.currentRecordDate.value)
        assertEquals(0, c.state().savedCount)
        assertEquals(listOf(LocalDate.parse("2026-10-22")), c.state().pastDates.map { it.recordDate })
    }

    @Test
    fun fullPreviousDayDoesNotBlockNewDayAfterBoundary() = runTest {
        repeat(MAX_PHOTOS_PER_RECORD_DATE) { savedPhoto("s$it", date = "2026-10-22") }
        val c = controller(time = clockFrom("2026-10-23T03:59:59"))
        assertFalse(c.state().canCapture)
        advanceTimeBy(2_000)
        runCurrent()
        assertTrue(c.state().canCapture)
    }

    // T12
    @Test
    fun externallyDeletedOriginalIsDetectedOnResume() = runTest {
        savedPhoto("a")
        val c = controller()
        assertTrue(c.state().missingPhotoIds.isEmpty())

        media.items.remove("u-a") // 写真アプリで削除
        c.onResume()
        runCurrent()
        assertEquals(setOf("a"), c.state().missingPhotoIds)
        assertEquals(1, c.state().savedCount) // 落ちずに一覧に残り、除外できる

        c.exclude("a")
        runCurrent()
        assertTrue(c.state().photos.isEmpty())
        assertTrue(c.state().missingPhotoIds.isEmpty())
    }
}
