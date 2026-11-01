package com.hiragi0w0.geopolaroid.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.GeoPoint
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RouteSketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 完成地図を1枚のポラロイド風の画像にして、端末の写真ライブラリ（Pictures/GeoPolaroid Maps）へ保存する。
 *
 * 台紙の上に地図、その下に記録日・枚数・訪れた場所、さらに写真の小さなサムネイルを撮影順に並べる。
 * 地図は画面の地図をそのまま写したもの（[mapSnapshot]）を使い、地図が使えないときは撮影地点と線の略図を描く。
 * 元写真とは別のフォルダに保存するので、記録の原本と混ざらない。
 */
class MapPosterExporter(private val context: Context) {

    /** 保存できたら画像の URI、できなければ null。 */
    suspend fun export(recordDate: LocalDate, photos: List<Photo>, mapSnapshot: Bitmap?): Uri? =
        withContext(Dispatchers.IO) {
            val thumbnails = photos.map { loadThumbnail(it.imageUri) }
            val poster = try {
                render(recordDate, photos, mapSnapshot, thumbnails)
            } catch (e: OutOfMemoryError) {
                null
            } finally {
                thumbnails.forEach { it?.recycle() }
            } ?: return@withContext null
            try {
                save(poster, "GeoPolaroid_Map_${recordDate.toString().replace("-", "")}.jpg")
            } finally {
                poster.recycle()
            }
        }

    private fun render(
        recordDate: LocalDate,
        photos: List<Photo>,
        mapSnapshot: Bitmap?,
        thumbnails: List<Bitmap?>,
    ): Bitmap {
        val font = ResourcesCompat.getFont(context, R.font.zen_maru_gothic_medium) ?: Typeface.DEFAULT
        val inner = WIDTH - MARGIN * 2
        val mapHeight = mapSnapshot?.let { (inner * clampAspect(it.height.toFloat() / it.width)).toInt() } ?: inner
        val rows = (photos.size + THUMB_COLUMNS - 1) / THUMB_COLUMNS
        val thumbSize = (inner - THUMB_GAP * (THUMB_COLUMNS - 1)) / THUMB_COLUMNS
        val thumbsHeight = if (rows == 0) 0 else rows * thumbSize + (rows - 1) * THUMB_GAP
        val height = MARGIN + mapHeight + CAPTION_HEIGHT + thumbsHeight + FOOTER_HEIGHT

        val poster = createBitmap(WIDTH, height)
        val canvas = Canvas(poster)
        canvas.drawColor(PAPER)

        val mapRect = RectF(MARGIN.toFloat(), MARGIN.toFloat(), (MARGIN + inner).toFloat(), (MARGIN + mapHeight).toFloat())
        if (mapSnapshot != null) {
            drawCropped(canvas, mapSnapshot, mapRect)
        } else {
            drawSketch(canvas, photos, mapRect)
        }

        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK
            textSize = 52f
        }
        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK_VARIANT
            textSize = 32f
        }
        val captionTop = mapRect.bottom
        canvas.drawText(DisplayFormat.date(recordDate), mapRect.left, captionTop + 92f, title)
        val places = placesLine(photos)
        val summary = context.getString(R.string.poster_summary, photos.size) + if (places.isEmpty()) "" else " · $places"
        val fitted = TextUtils.ellipsize(summary, sub, inner.toFloat(), TextUtils.TruncateAt.END).toString()
        canvas.drawText(fitted, mapRect.left, captionTop + 148f, sub)

        val thumbsTop = captionTop + CAPTION_HEIGHT
        thumbnails.forEachIndexed { index, thumb ->
            val col = index % THUMB_COLUMNS
            val row = index / THUMB_COLUMNS
            val left = mapRect.left + col * (thumbSize + THUMB_GAP)
            val top = thumbsTop + row * (thumbSize + THUMB_GAP)
            val rect = RectF(left, top, left + thumbSize, top + thumbSize)
            if (thumb != null) drawCropped(canvas, thumb, rect) else canvas.drawRect(rect, fill(PLACEHOLDER))
        }

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK_VARIANT
            textSize = 28f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(context.getString(R.string.app_name), mapRect.right, height - 44f, brand)
        return poster
    }

    /** 地名を初めて訪れた順に、重複を除いて「天神 → 博多」のようにつなぐ。 */
    private fun placesLine(photos: List<Photo>): String =
        photos.mapNotNull { it.placeName }.distinct().joinToString(" → ")

    /** 地図が使えないときの略図。撮影順の直線と、地点の丸を描く。 */
    private fun drawSketch(canvas: Canvas, photos: List<Photo>, rect: RectF) {
        canvas.drawRect(rect, fill(SKETCH_GROUND))
        val points = RouteSketch.layout(
            photos.map { GeoPoint(it.latitude, it.longitude) },
            rect.width(),
            rect.height(),
            SKETCH_PADDING,
        ).map { it.copy(x = it.x + rect.left, y = it.y + rect.top) }
        if (points.size >= 2) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 8f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = LINE
            }
            canvas.drawPath(path, line)
        }
        points.forEach {
            canvas.drawCircle(it.x, it.y, PIN_RADIUS, fill(PAPER))
            canvas.drawCircle(it.x, it.y, PIN_RADIUS - 6f, fill(PIN))
        }
    }

    /** [bitmap] の中央を [rect] の縦横比で切り抜いて描く。 */
    private fun drawCropped(canvas: Canvas, bitmap: Bitmap, rect: RectF) {
        val targetAspect = rect.height() / rect.width()
        val srcW: Float
        val srcH: Float
        if (bitmap.height.toFloat() / bitmap.width > targetAspect) {
            srcW = bitmap.width.toFloat()
            srcH = srcW * targetAspect
        } else {
            srcH = bitmap.height.toFloat()
            srcW = srcH / targetAspect
        }
        val left = (bitmap.width - srcW) / 2
        val top = (bitmap.height - srcH) / 2
        val src = android.graphics.Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(bitmap, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /** 原本をサムネイルの大きさまで縮小して読む（向きは EXIF に従う）。読めなければ null。 */
    private fun loadThumbnail(uri: String): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longSide = maxOf(info.size.width, info.size.height)
            var sample = 1
            while (longSide / (sample * 2) >= THUMB_DECODE_SIDE) sample *= 2
            decoder.setTargetSampleSize(sample)
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    private fun save(poster: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$ALBUM")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = try {
            resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            val written = resolver.openOutputStream(uri)?.use { poster.compress(Bitmap.CompressFormat.JPEG, QUALITY, it) }
            if (written != true) error("write failed")
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            uri
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            null
        }
    }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }

    private companion object {
        const val ALBUM = "GeoPolaroid Maps"
        const val QUALITY = 92

        const val WIDTH = 1200
        const val MARGIN = 56
        const val CAPTION_HEIGHT = 196
        const val FOOTER_HEIGHT = 110
        const val THUMB_COLUMNS = 6
        const val THUMB_GAP = 10
        const val THUMB_DECODE_SIDE = 360
        const val SKETCH_PADDING = 72f
        const val PIN_RADIUS = 18f

        /** 地図の縦横比（縦/横）を、正方形から 3:2 の縦長までに収める。 */
        fun clampAspect(aspect: Float) = aspect.coerceIn(1f, 1.5f)

        // 画面のテーマと同じ紙とインクの色
        const val PAPER = 0xFFFBF8F1.toInt()
        const val INK = 0xFF2E261E.toInt()
        const val INK_VARIANT = 0xFF6B5D4D.toInt()
        const val PLACEHOLDER = 0xFFE7DECD.toInt()
        const val SKETCH_GROUND = 0xFFEFE6D6.toInt()
        const val LINE = 0xFF7B5B3B.toInt()
        const val PIN = 0xFF9A5A48.toInt()
    }
}
