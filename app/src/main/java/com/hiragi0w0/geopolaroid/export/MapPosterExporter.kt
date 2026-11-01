package com.hiragi0w0.geopolaroid.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withTranslation
import androidx.core.net.toUri
import com.hiragi0w0.geopolaroid.R
import com.hiragi0w0.geopolaroid.domain.CropRect
import com.hiragi0w0.geopolaroid.domain.DisplayFormat
import com.hiragi0w0.geopolaroid.domain.GeoPoint
import com.hiragi0w0.geopolaroid.domain.MapCrop
import com.hiragi0w0.geopolaroid.domain.Photo
import com.hiragi0w0.geopolaroid.domain.RouteMapModel
import com.hiragi0w0.geopolaroid.domain.RouteSketch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 完成地図を1枚のポラロイド風の画像にして、端末の写真ライブラリ（Pictures/GeoPolaroid Maps）へ保存する。
 *
 * 写真が主役になるよう、上の見出しには小さめの地図と記録日・枚数・訪れた場所を並べ、その下に写真を
 * 撮影順の番号つきのポラロイドとして大きく並べる（番号は地図のピンと同じ）。
 * 地図は画面の地図を写したもの（[MapCapture]）からピンを含む範囲だけを切り抜き、地図が使えないときは撮影地点と線の略図を描く。
 * 元写真とは別のフォルダに保存するので、記録の原本と混ざらない。
 */
class MapPosterExporter(private val context: Context) {

    /** 保存できたら画像の URI、できなければ null。 */
    suspend fun export(recordDate: LocalDate, photos: List<Photo>, map: MapCapture?): Uri? =
        withContext(Dispatchers.IO) {
            val poster = try {
                render(recordDate, photos, map)
            } catch (e: OutOfMemoryError) {
                null
            } ?: return@withContext null
            try {
                save(poster, "GeoPolaroid_Map_${recordDate.toString().replace("-", "")}.jpg")
            } finally {
                poster.recycle()
            }
        }

    private fun render(recordDate: LocalDate, photos: List<Photo>, map: MapCapture?): Bitmap {
        val font = ResourcesCompat.getFont(context, R.font.zen_maru_gothic_medium) ?: Typeface.DEFAULT
        val inner = WIDTH - MARGIN * 2

        // 見出し：左に地図、右に記録日・枚数・訪れた場所
        val crop = map?.let {
            MapCrop.region(
                pins = it.pins,
                visible = it.visible,
                margin = it.bitmap.width * CROP_MARGIN_RATIO,
                minSide = it.bitmap.width * CROP_MIN_SIDE_RATIO,
                minAspect = MIN_MAP_ASPECT,
                maxAspect = MAX_MAP_ASPECT,
            )
        }
        val mapHeight = (MAP_WIDTH * (crop?.aspect?.coerceIn(MIN_MAP_ASPECT, MAX_MAP_ASPECT) ?: 1f)).toInt()
        val headerHeight = mapHeight + MAP_FRAME * 2

        // 写真：枚数が少ないほど大きく並べる
        val columns = when {
            photos.size <= 1 -> 1
            photos.size <= 6 -> 2
            else -> 3
        }
        val rows = (photos.size + columns - 1) / columns
        val tile = TileMetrics((inner - COLUMN_GAP * (columns - 1)) / columns.toFloat())
        val gridHeight = if (rows == 0) 0f else rows * tile.height + (rows - 1) * ROW_GAP

        val height = (MARGIN + headerHeight + SECTION_GAP + gridHeight + FOOTER_HEIGHT).toInt()
        val poster = createBitmap(WIDTH, height)
        val canvas = Canvas(poster)
        canvas.drawColor(DESK)

        val frameRect = RectF(
            MARGIN.toFloat(),
            MARGIN.toFloat(),
            (MARGIN + MAP_WIDTH + MAP_FRAME * 2).toFloat(),
            (MARGIN + headerHeight).toFloat(),
        )
        canvas.drawRect(frameRect, tilePaint())
        val mapRect = RectF(
            frameRect.left + MAP_FRAME,
            frameRect.top + MAP_FRAME,
            frameRect.right - MAP_FRAME,
            frameRect.bottom - MAP_FRAME,
        )
        if (map != null && crop != null) {
            drawRegion(canvas, map.bitmap, crop, mapRect)
        } else {
            drawSketch(canvas, font, photos, mapRect)
        }

        drawHeaderText(
            canvas = canvas,
            font = font,
            recordDate = recordDate,
            photos = photos,
            area = RectF(frameRect.right + HEADER_GAP, frameRect.top, (WIDTH - MARGIN).toFloat(), frameRect.bottom),
        )

        val gridTop = frameRect.bottom + SECTION_GAP
        photos.forEachIndexed { index, photo ->
            val row = index / columns
            val col = index % columns
            // 最後の行が埋まらないときは中央に寄せる
            val inRow = if (row == rows - 1) photos.size - row * columns else columns
            val rowOffset = (columns - inRow) * (tile.width + COLUMN_GAP) / 2f
            val left = MARGIN + rowOffset + col * (tile.width + COLUMN_GAP)
            val top = gridTop + row * (tile.height + ROW_GAP)
            drawPhotoTile(canvas, font, photo, number = index + 1, tile, left, top, tilt = tileTilt(index))
        }

        val brand = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK_VARIANT
            textSize = 30f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(context.getString(R.string.app_name), (WIDTH - MARGIN).toFloat(), height - 48f, brand)
        return poster
    }

    /** 見出しの文字。記録日は幅に収まるまで縮め、訪れた場所は地図の高さに収まる行数で折り返す。 */
    private fun drawHeaderText(canvas: Canvas, font: Typeface, recordDate: LocalDate, photos: List<Photo>, area: RectF) {
        val width = area.width()
        val title = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK
            textSize = 60f
        }
        val date = DisplayFormat.date(recordDate)
        while (title.measureText(date) > width && title.textSize > 32f) title.textSize -= 2f
        var baseline = area.top + 8f - title.ascent()
        canvas.drawText(date, area.left, baseline, title)

        val sub = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK_VARIANT
            textSize = 36f
        }
        baseline += title.descent() + 20f - sub.ascent()
        canvas.drawText(context.getString(R.string.poster_summary, photos.size), area.left, baseline, sub)

        val places = placesLine(photos)
        if (places.isEmpty()) return
        val placePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = INK
            textSize = 32f
        }
        val top = baseline + sub.descent() + 28f
        val lineHeight = placePaint.fontSpacing * PLACE_LINE_SPACING
        val maxLines = ((area.bottom - top) / lineHeight).toInt().coerceAtLeast(1)
        val layout = StaticLayout.Builder.obtain(places, 0, places.length, placePaint, width.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, PLACE_LINE_SPACING)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .build()
        canvas.withTranslation(area.left, top) { layout.draw(this) }
    }

    /** 写真1枚分のポラロイド。正方形に切り抜いた写真の左上に撮影順の番号、下の余白に時刻と場所を書く。 */
    private fun drawPhotoTile(
        canvas: Canvas,
        font: Typeface,
        photo: Photo,
        number: Int,
        tile: TileMetrics,
        left: Float,
        top: Float,
        tilt: Float,
    ) {
        val frame = RectF(left, top, left + tile.width, top + tile.height)
        canvas.withRotation(tilt, frame.centerX(), frame.centerY()) {
            drawRect(frame, tilePaint())
            val photoRect = RectF(left + tile.padding, top + tile.padding, left + tile.padding + tile.photo, top + tile.padding + tile.photo)
            val bitmap = loadPhoto(photo.imageUri, tile.photo.toInt())
            if (bitmap != null) {
                drawCropped(this, bitmap, photoRect)
                bitmap.recycle()
            } else {
                drawRect(photoRect, fill(PLACEHOLDER))
            }

            val badgeRadius = tile.base * BADGE_RADIUS_RATIO
            drawPinLabel(
                canvas = this,
                font = font,
                label = number.toString(),
                cx = photoRect.left + badgeRadius * 1.6f,
                cy = photoRect.top + badgeRadius * 1.6f,
                radius = badgeRadius,
            )

            val time = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font
                color = INK
                textSize = tile.base * TIME_TEXT_RATIO
            }
            val place = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = font
                color = INK_VARIANT
                textSize = tile.base * PLACE_TEXT_RATIO
            }
            val captionTop = photoRect.bottom
            drawText(DisplayFormat.time(photo), photoRect.left + 2f, captionTop + tile.caption * 0.44f, time)
            photo.placeName?.let { name ->
                val fitted = TextUtils.ellipsize(name, place, tile.photo - 4f, TextUtils.TruncateAt.END).toString()
                drawText(fitted, photoRect.left + 2f, captionTop + tile.caption * 0.80f, place)
            }
        }
    }

    /** 地名を初めて訪れた順に、重複を除いて「天神 → 博多」のようにつなぐ。 */
    private fun placesLine(photos: List<Photo>): String =
        photos.mapNotNull { it.placeName }.distinct().joinToString(" → ")

    /** 地図が使えないときの略図。撮影順の直線と、番号を書いた地点の丸を描く。 */
    private fun drawSketch(canvas: Canvas, font: Typeface, photos: List<Photo>, rect: RectF) {
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
                strokeWidth = 6f
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                color = LINE
            }
            canvas.drawPath(path, line)
        }
        // 同じ地点はピンを1つにまとめ、番号を並べる（地図のピンと同じ）
        RouteMapModel.from(photos).spots.forEach { spot ->
            val at = points.getOrNull(spot.numbers.first() - 1) ?: return@forEach
            drawPinLabel(canvas, font, spot.label, at.x, at.y, SKETCH_PIN_RADIUS)
        }
    }

    /** 番号を書いた丸（長い番号は横長の丸）。紙の色で縁取る。 */
    private fun drawPinLabel(canvas: Canvas, font: Typeface, label: String, cx: Float, cy: Float, radius: Float) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font
            color = PIN_TEXT
            textSize = radius * 1.05f
            textAlign = Paint.Align.CENTER
        }
        val halfWidth = maxOf(radius, text.measureText(label) / 2 + radius * 0.6f)
        val ring = radius * 0.16f
        canvas.drawRoundRect(cx - halfWidth, cy - radius, cx + halfWidth, cy + radius, radius, radius, fill(TILE))
        canvas.drawRoundRect(
            cx - halfWidth + ring,
            cy - radius + ring,
            cx + halfWidth - ring,
            cy + radius - ring,
            radius - ring,
            radius - ring,
            fill(PIN),
        )
        canvas.drawText(label, cx, cy - (text.descent() + text.ascent()) / 2, text)
    }

    /** [bitmap] の [region] を、縦横比を保って [rect] の中央に収める。余りは地図の地の色で埋める。 */
    private fun drawRegion(canvas: Canvas, bitmap: Bitmap, region: CropRect, rect: RectF) {
        canvas.drawRect(rect, fill(SKETCH_GROUND))
        val scale = minOf(rect.width() / region.width, rect.height() / region.height)
        val w = region.width * scale
        val h = region.height * scale
        val dst = RectF(rect.centerX() - w / 2, rect.centerY() - h / 2, rect.centerX() + w / 2, rect.centerY() + h / 2)
        val src = Rect(region.left.toInt(), region.top.toInt(), region.right.toInt(), region.bottom.toInt())
        canvas.drawBitmap(bitmap, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
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
        val src = Rect(left.toInt(), top.toInt(), (left + srcW).toInt(), (top + srcH).toInt())
        canvas.drawBitmap(bitmap, src, rect, Paint(Paint.FILTER_BITMAP_FLAG))
    }

    /**
     * 原本を、短い辺が [side] を下回らない範囲で縮小して読む（向きは EXIF に従う）。読めなければ null。
     * 1枚ずつ読んで描いたら捨てるので、枚数が多くても同時に持つのは1枚分だけ。
     */
    private fun loadPhoto(uri: String, side: Int): Bitmap? = try {
        val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val shortSide = minOf(info.size.width, info.size.height)
            var sample = 1
            while (shortSide / (sample * 2) >= side) sample *= 2
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

    /** ポラロイドの台紙。机に置いたように薄い影を落とす。 */
    private fun tilePaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TILE
        setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, SHADOW)
    }

    /** 写真1枚分の寸法。台紙の幅 [width] から、余白・写真・下の余白を決める。文字は大きくなりすぎないよう [base] を基準にする。 */
    private class TileMetrics(val width: Float) {
        val padding = width * 0.05f
        val photo = width - padding * 2
        val base = minOf(width, 640f)
        val caption = base * 0.26f
        val height = padding + photo + caption
    }

    private companion object {
        const val ALBUM = "GeoPolaroid Maps"
        const val QUALITY = 92

        const val WIDTH = 1440
        const val MARGIN = 72
        const val MAP_WIDTH = 520
        const val MAP_FRAME = 16
        const val HEADER_GAP = 48
        const val SECTION_GAP = 72f
        const val COLUMN_GAP = 40
        const val ROW_GAP = 48f
        const val FOOTER_HEIGHT = 120f
        const val PLACE_LINE_SPACING = 1.3f

        /** 地図の縦横比（縦/横）。横長 4:3 から縦長 4:5 まで。 */
        const val MIN_MAP_ASPECT = 0.75f
        const val MAX_MAP_ASPECT = 1.25f

        /** 地図の切り抜き：ピンの外側の余白と、拡大しすぎないための最小の大きさ（画面の地図の幅に対する割合）。 */
        const val CROP_MARGIN_RATIO = 0.08f
        const val CROP_MIN_SIDE_RATIO = 0.5f

        const val BADGE_RADIUS_RATIO = 0.055f
        const val TIME_TEXT_RATIO = 0.075f
        const val PLACE_TEXT_RATIO = 0.06f
        const val SKETCH_PADDING = 56f
        const val SKETCH_PIN_RADIUS = 20f

        const val SHADOW_RADIUS = 18f
        const val SHADOW_DY = 6f
        const val SHADOW = 0x33000000

        /** 画面のカードと同じく、隣の写真と向きを変えて少し傾ける。 */
        fun tileTilt(index: Int) = if (index % 2 == 0) -0.8f else 0.6f

        // 画面のテーマと同じ紙とインクの色
        const val DESK = 0xFFEFE7D9.toInt()
        const val TILE = 0xFFFBF8F1.toInt()
        const val INK = 0xFF2E261E.toInt()
        const val INK_VARIANT = 0xFF6B5D4D.toInt()
        const val PLACEHOLDER = 0xFFE7DECD.toInt()
        const val SKETCH_GROUND = 0xFFEFE6D6.toInt()
        const val LINE = 0xFF7B5B3B.toInt()
        const val PIN = 0xFF9A5A48.toInt()
        const val PIN_TEXT = 0xFFFFFFFF.toInt()
    }
}
