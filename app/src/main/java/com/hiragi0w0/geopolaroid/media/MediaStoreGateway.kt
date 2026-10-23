package com.hiragi0w0.geopolaroid.media

import android.content.ContentResolver
import android.content.ContentValues
import android.graphics.BitmapFactory
import android.os.Environment
import android.provider.MediaStore
import androidx.core.net.toUri
import com.hiragi0w0.geopolaroid.domain.MediaCheck
import com.hiragi0w0.geopolaroid.domain.MediaGateway
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException

/**
 * MediaStore Images に写真原本を1件作る。OSカメラはこの URI に直接書き込む。
 * アプリ独自の画像ファイル（縮小版・WebP等）は作らない。
 */
class MediaStoreGateway(private val resolver: ContentResolver) : MediaGateway {

    override suspend fun createCaptureUri(displayName: String): String? = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
        }
        try {
            resolver.insert(
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                values,
            )?.toString()
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 画像として読めるかを確かめる。サイズ情報（ヘッダ）だけでは途中で書き込みが止まったファイルも
     * 通ってしまうため、縮小デコードで画像データまで読めることを確認する。
     */
    override suspend fun inspect(uri: String): MediaCheck = withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            val opened = resolver.openInputStream(uri.toUri()) ?: return@withContext MediaCheck.UNVERIFIABLE
            opened.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext MediaCheck.EMPTY_OR_BROKEN

            val sampled = BitmapFactory.Options().apply {
                inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight)
            }
            val stream = resolver.openInputStream(uri.toUri()) ?: return@withContext MediaCheck.UNVERIFIABLE
            val bitmap = stream.use { BitmapFactory.decodeStream(it, null, sampled) }
                ?: return@withContext MediaCheck.EMPTY_OR_BROKEN
            bitmap.recycle()
            MediaCheck.VALID
        } catch (e: OutOfMemoryError) {
            // 読めないとは言い切れないので消さない
            MediaCheck.UNVERIFIABLE
        } catch (e: FileNotFoundException) {
            MediaCheck.MISSING
        } catch (e: SecurityException) {
            MediaCheck.UNVERIFIABLE
        } catch (e: IOException) {
            MediaCheck.UNVERIFIABLE
        } catch (e: IllegalArgumentException) {
            MediaCheck.UNVERIFIABLE
        }
    }

    override suspend fun delete(uri: String): Boolean = withContext(Dispatchers.IO) {
        try {
            resolver.delete(uri.toUri(), null, null) > 0
        } catch (e: Exception) {
            false
        }
    }

    private companion object {
        const val ALBUM = "GeoPolaroid"

        /** 検査用デコードの長辺の目安（px）。 */
        const val CHECK_DECODE_MAX_SIDE = 256

        fun sampleSizeFor(width: Int, height: Int): Int {
            var sample = 1
            while (maxOf(width, height) / (sample * 2) >= CHECK_DECODE_MAX_SIDE) sample *= 2
            return sample
        }
    }
}
