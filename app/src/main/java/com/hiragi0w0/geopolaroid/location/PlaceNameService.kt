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

    fun resolveInBackground(photoId: String) {
        _resolvingIds.update { it + photoId }
        scope.launch {
            try {
                updater.update(photoId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // DB エラー等。地名は未取得のまま残す。
            } finally {
                _resolvingIds.update { it - photoId }
            }
        }
    }
}
