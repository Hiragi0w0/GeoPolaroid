package com.hiragi0w0.geopolaroid.capture

import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.CompleteResult
import com.hiragi0w0.geopolaroid.capture.CaptureCoordinator.PrepareResult
import com.hiragi0w0.geopolaroid.domain.LocationResult
import com.hiragi0w0.geopolaroid.domain.MAX_PHOTOS_PER_RECORD_DATE
import com.hiragi0w0.geopolaroid.domain.MediaCheck
import com.hiragi0w0.geopolaroid.domain.SaveState
import com.hiragi0w0.geopolaroid.testing.FakeLocationProvider
import com.hiragi0w0.geopolaroid.testing.FakeMediaGateway
import com.hiragi0w0.geopolaroid.testing.FakePhotoRepository
import com.hiragi0w0.geopolaroid.testing.FakeTimeSource
import com.hiragi0w0.geopolaroid.testing.photo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

/** 仕様6章 撮影・保存シーケンス。 */
class CaptureCoordinatorTest {
    private val repository = FakePhotoRepository()
    private val media = FakeMediaGateway()
    private val location = FakeLocationProvider()
    private val time = FakeTimeSource(Instant.parse("2026-10-22T01:30:00Z")) // 東京 10:30
    private var nextId = 0
    private val coordinator = CaptureCoordinator(repository, location, media, time) { "id-${++nextId}" }

    private suspend fun ready(): PrepareResult.Ready = coordinator.prepare() as PrepareResult.Ready

    private suspend fun captureSaved(): String {
        val ready = ready()
        media.write(ready.imageUri)
        assertEquals(CompleteResult.SAVED, coordinator.complete(ready.photoId, true))
        return ready.photoId
    }

    // T02
    @Test
    fun successfulCaptureSavesOneRowAndOneMediaItem() = runTest {
        val ready = ready()
        val pending = repository.findById(ready.photoId)!!
        assertEquals(SaveState.PENDING, pending.saveState)
        assertEquals(LocalDate.parse("2026-10-22"), pending.recordDate)
        assertEquals(time.instant.toEpochMilli(), pending.requestedAtUtc)
        assertEquals("Asia/Tokyo", pending.zoneId)
        assertEquals(33.5902, pending.latitude, 0.0)
        assertEquals(130.4017, pending.longitude, 0.0)
        assertNull(pending.placeName)

        media.write(ready.imageUri)
        assertEquals(CompleteResult.SAVED, coordinator.complete(ready.photoId, true))

        assertEquals(1, repository.saved().size)
        assertEquals(0, repository.pending().size)
        assertEquals(1, media.items.size)
        assertEquals(ready.imageUri, repository.saved().single().imageUri)
    }

    // T03
    @Test
    fun cancelLeavesNoSavedRowAndNoGarbageUri() = runTest {
        val ready = ready()
        assertEquals(CompleteResult.CANCELLED, coordinator.complete(ready.photoId, false))
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())
    }

    // T03
    @Test
    fun successReportedButEmptyFileIsTreatedAsFailure() = runTest {
        val ready = ready() // カメラが何も書き込まなかった
        assertEquals(CompleteResult.FAILED, coordinator.complete(ready.photoId, true))
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())
    }

    @Test
    fun successReportedButMediaMissingIsFailure() = runTest {
        val ready = ready()
        media.items.remove(ready.imageUri)
        assertEquals(CompleteResult.FAILED, coordinator.complete(ready.photoId, true))
        assertTrue(repository.all.isEmpty())
    }

    @Test
    fun interruptedCleanupKeepsRowSoRecoveryCanFinishIt() = runTest {
        // 片付けが原本削除の途中で止まっても、行が残っていれば次回の復旧で片付く
        val ready = ready()
        media.failDelete = true
        runCatching { coordinator.complete(ready.photoId, false) }
        assertEquals(SaveState.PENDING, repository.findById(ready.photoId)!!.saveState)

        media.failDelete = false
        assertEquals(listOf(ready.photoId), coordinator.recoverPending().discarded)
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())
    }

    @Test
    fun interruptedRecoveryDiscardKeepsRow() = runTest {
        repository.put(photo("empty", state = SaveState.PENDING, uri = "u-empty"))
        media.write("u-empty", MediaCheck.EMPTY_OR_BROKEN)
        media.failDelete = true
        runCatching { coordinator.recoverPending() }
        assertEquals(SaveState.PENDING, repository.findById("empty")!!.saveState)
    }

    @Test
    fun cancelKeepsRealImageButReleasesSlot() = runTest {
        val ready = ready()
        media.write(ready.imageUri) // 中身のある原本は消さない
        assertEquals(CompleteResult.CANCELLED, coordinator.complete(ready.photoId, false))
        assertTrue(repository.all.isEmpty())
        assertEquals(MediaCheck.VALID, media.items[ready.imageUri])
    }

    @Test
    fun unverifiableAfterSuccessStaysPending() = runTest {
        val ready = ready()
        media.write(ready.imageUri, MediaCheck.UNVERIFIABLE)
        assertEquals(CompleteResult.UNVERIFIED, coordinator.complete(ready.photoId, true))
        assertEquals(SaveState.PENDING, repository.findById(ready.photoId)!!.saveState)
        assertTrue(ready.imageUri in media.items)
    }

    @Test
    fun completeIsIdempotent() = runTest {
        val id = captureSaved()
        assertEquals(CompleteResult.ALREADY_HANDLED, coordinator.complete(id, false))
        assertEquals(1, repository.saved().size)
        assertEquals(CompleteResult.ALREADY_HANDLED, coordinator.complete("unknown", true))
    }

    // T04
    @Test
    fun twelvePhotosBlockCaptureAndExclusionReopensIt() = runTest {
        val ids = (1..MAX_PHOTOS_PER_RECORD_DATE).map { captureSaved() }
        val callsBefore = location.calls
        assertEquals(PrepareResult.LimitReached, coordinator.prepare())
        assertEquals(callsBefore, location.calls) // 上限時は位置を取りに行かない
        assertEquals(MAX_PHOTOS_PER_RECORD_DATE, media.items.size)

        assertTrue(repository.excludeSaved(ids.first()))
        assertEquals(MAX_PHOTOS_PER_RECORD_DATE, media.items.size) // 原本はそのまま
        captureSaved()
        assertEquals(MAX_PHOTOS_PER_RECORD_DATE, repository.saved().size)
    }

    @Test
    fun pendingCountsTowardLimit() = runTest {
        repeat(MAX_PHOTOS_PER_RECORD_DATE - 1) { captureSaved() }
        val ready = ready() // 12枚目が PENDING
        assertEquals(MAX_PHOTOS_PER_RECORD_DATE, repository.countActive(LocalDate.parse("2026-10-22")))
        assertEquals(PrepareResult.LimitReached, coordinator.prepare())
        coordinator.complete(ready.photoId, false) // キャンセルは枠を使わない
        captureSaved()
    }

    @Test
    fun limitIsPerRecordDate() = runTest {
        repeat(MAX_PHOTOS_PER_RECORD_DATE) { captureSaved() }
        time.setLocal("2026-10-23T04:00")
        captureSaved()
        assertEquals(1, repository.saved().count { it.recordDate == LocalDate.parse("2026-10-23") })
    }

    @Test
    fun limitRaceAfterLocationDeletesCreatedMedia() = runTest {
        // 位置取得中に別経路で 12 件埋まった場合（記録日が変わった等）
        location.result = LocationResult.Success(1.0, 2.0)
        val filling = object : com.hiragi0w0.geopolaroid.domain.LocationProvider {
            override suspend fun getCurrentLocation(): LocationResult {
                repeat(MAX_PHOTOS_PER_RECORD_DATE) { repository.put(photo("x$it")) }
                return LocationResult.Success(1.0, 2.0)
            }
        }
        val racing = CaptureCoordinator(repository, filling, media, time) { "race" }
        assertEquals(PrepareResult.LimitReached, racing.prepare())
        assertTrue(media.items.isEmpty())
        assertNull(repository.findById("race"))
    }

    // T05
    @Test
    fun locationPermissionDeniedCreatesNothing() = runTest {
        location.result = LocationResult.PermissionDenied
        assertEquals(PrepareResult.LocationPermissionDenied, coordinator.prepare())
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())
    }

    // T05
    @Test
    fun locationUnavailableCreatesNothingAndCanRetry() = runTest {
        location.result = LocationResult.Unavailable
        assertEquals(PrepareResult.LocationUnavailable, coordinator.prepare())
        assertTrue(repository.all.isEmpty())
        assertTrue(media.items.isEmpty())

        location.result = LocationResult.Success(35.0, 135.0)
        val ready = ready()
        assertEquals(35.0, repository.findById(ready.photoId)!!.latitude, 0.0)
    }

    @Test
    fun storageFailureCreatesNoRow() = runTest {
        media.failCreate = true
        assertEquals(PrepareResult.StorageUnavailable, coordinator.prepare())
        assertTrue(repository.all.isEmpty())
    }

    @Test
    fun concurrentPrepareIsRejected() = runTest {
        val gate = CompletableDeferred<Unit>()
        val slow = object : com.hiragi0w0.geopolaroid.domain.LocationProvider {
            override suspend fun getCurrentLocation(): LocationResult {
                gate.await()
                return LocationResult.Success(1.0, 2.0)
            }
        }
        val c = CaptureCoordinator(repository, slow, media, time) { "id-${++nextId}" }
        val first = async { c.prepare() }
        yield()
        assertEquals(PrepareResult.Busy, c.prepare())
        gate.complete(Unit)
        assertTrue(first.await() is PrepareResult.Ready)
        assertEquals(1, repository.all.size)
    }

    // T07
    @Test
    fun recordDateUsesTimeJustBeforeCameraLaunch() = runTest {
        time.setLocal("2026-10-23T03:59")
        val r1 = ready()
        assertEquals(LocalDate.parse("2026-10-22"), repository.findById(r1.photoId)!!.recordDate)
        media.write(r1.imageUri)
        coordinator.complete(r1.photoId, true)

        time.setLocal("2026-10-23T04:00")
        val r2 = ready()
        assertEquals(LocalDate.parse("2026-10-23"), repository.findById(r2.photoId)!!.recordDate)
    }

    @Test
    fun displayNameIsUniqueAndReadable() {
        val name = CaptureCoordinator.displayNameFor(
            Instant.parse("2026-10-22T01:30:05Z"),
            java.time.ZoneId.of("Asia/Tokyo"),
            "abcdef12-3456",
        )
        assertEquals("GeoPolaroid_20261022_103005_abcdef12.jpg", name)
    }

    // T11
    @Test
    fun recoveryRestoresValidDiscardsEmptyAndKeepsUnverifiable() = runTest {
        // 途中終了を模擬：PENDING 行を直接作る
        repository.put(photo("valid", state = SaveState.PENDING, uri = "u-valid"))
        repository.put(photo("empty", state = SaveState.PENDING, uri = "u-empty"))
        repository.put(photo("missing", state = SaveState.PENDING, uri = "u-missing"))
        repository.put(photo("unknown", state = SaveState.PENDING, uri = "u-unknown"))
        media.write("u-valid", MediaCheck.VALID)
        media.write("u-empty", MediaCheck.EMPTY_OR_BROKEN)
        media.write("u-unknown", MediaCheck.UNVERIFIABLE)

        val report = coordinator.recoverPending()

        assertEquals(listOf("valid"), report.recovered)
        assertEquals(listOf("empty", "missing"), report.discarded.sorted())
        assertEquals(listOf("unknown"), report.kept)
        assertEquals(SaveState.SAVED, repository.findById("valid")!!.saveState)
        assertNull(repository.findById("empty"))
        assertNull(repository.findById("missing"))
        assertEquals(SaveState.PENDING, repository.findById("unknown")!!.saveState)
        assertTrue("u-valid" in media.items)
        assertTrue("u-empty" !in media.items)
        assertTrue("u-unknown" in media.items) // 検証不能な内容は消さない
    }

    @Test
    fun recoverySkipsActiveCameraPhoto() = runTest {
        val ready = ready() // カメラ起動中（まだ空）
        val report = coordinator.recoverPending(activePhotoId = ready.photoId)
        assertEquals(listOf(ready.photoId), report.kept)
        assertEquals(SaveState.PENDING, repository.findById(ready.photoId)!!.saveState)
        media.write(ready.imageUri)
        assertEquals(CompleteResult.SAVED, coordinator.complete(ready.photoId, true))
    }

    @Test
    fun resultAfterRecoveryIsHandledOnce() = runTest {
        // プロセス再生成：復旧が先に SAVED にし、その後にカメラ結果(false)が届く
        val ready = ready()
        media.write(ready.imageUri)
        coordinator.recoverPending()
        assertEquals(CompleteResult.ALREADY_HANDLED, coordinator.complete(ready.photoId, false))
        assertEquals(1, repository.saved().size)
    }
}
