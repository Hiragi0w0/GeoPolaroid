package com.hiragi0w0.geopolaroid.location

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 地名取得を画面のライフサイクルとは別の [scope] で実行し、取得中の photoId を公開する。
 * 表示を「取得中」と「取得失敗」で分けるために使う。
 */
class PlaceNameService(
    private val updater: PlaceNameUpdater,
    private val scope: CoroutineScope,
) {
    private val _resolvingIds = MutableStateFlow<Set<String>>(emptySet())
    val resolvingIds: StateFlow<Set<String>> = _resolvingIds.asStateFlow()

    /** 同じ写真をすでに取得中なら何もしない。 */
    fun resolveInBackground(photoId: String) {
        var started = false
        _resolvingIds.update { ids ->
            started = photoId !in ids
            if (started) ids + photoId else ids
        }
        if (!started) return
        scope.launch {
            try {
                updateQuietly(photoId)
            } finally {
                _resolvingIds.update { it - photoId }
            }
        }
    }

    /**
     * 複数の写真を1件ずつ順に取得する（Geocoder へ同時に大量の要求を出さないため）。
     * 件数に上限はなく、すでに取得中の写真は除く。
     */
    fun resolveAllInBackground(photoIds: List<String>) {
        var queued = emptyList<String>()
        _resolvingIds.update { ids ->
            queued = photoIds.distinct().filter { it !in ids }
            ids + queued
        }
        if (queued.isEmpty()) return
        scope.launch {
            try {
                for (photoId in queued) {
                    try {
                        updateQuietly(photoId)
                    } finally {
                        _resolvingIds.update { it - photoId }
                    }
                }
            } finally {
                // 途中で中断された場合も「取得中」のまま残さない。
                _resolvingIds.update { it - queued.toSet() }
            }
        }
    }

    private suspend fun updateQuietly(photoId: String) {
        try {
            updater.update(photoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // DB エラー等。地名は未取得のまま残す。
        }
    }
}
