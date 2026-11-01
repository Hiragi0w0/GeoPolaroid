package com.hiragi0w0.geopolaroid.domain

/** 画像上の範囲（px）。 */
data class CropRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    /** 縦/横。 */
    val aspect: Float get() = height / width
}

/**
 * 保存画像に入れる地図の切り抜き範囲。画面の地図を写した画像から、見えている範囲 [visible] にあるピンを
 * すべて含む範囲を選ぶ。ピンの外側に [margin] の余白をとり、[minSide] より小さければ広げ（拡大しすぎてぼやけないように）、
 * 縦横比（縦/横）が [minAspect]〜[maxAspect] に収まるよう足りない向きへ広げる。[visible] より広くはしない。
 */
object MapCrop {
    fun region(
        pins: List<SketchPoint>,
        visible: CropRect,
        margin: Float,
        minSide: Float,
        minAspect: Float,
        maxAspect: Float,
    ): CropRect {
        val inside = pins.filter { it.x in visible.left..visible.right && it.y in visible.top..visible.bottom }
        // 見えているピンがなければ、見えている範囲の中央を使う。
        val anchors = inside.ifEmpty {
            listOf(SketchPoint((visible.left + visible.right) / 2, (visible.top + visible.bottom) / 2))
        }
        val side = if (inside.isEmpty()) minOf(visible.width, visible.height) else minSide
        val minX = anchors.minOf { it.x }
        val maxX = anchors.maxOf { it.x }
        val minY = anchors.minOf { it.y }
        val maxY = anchors.maxOf { it.y }

        var width = maxOf(maxX - minX + margin * 2, side)
        var height = maxOf(maxY - minY + margin * 2, side)
        if (height / width < minAspect) height = width * minAspect
        if (height / width > maxAspect) width = height / maxAspect
        width = width.coerceAtMost(visible.width)
        height = height.coerceAtMost(visible.height)

        // ピンの中心に合わせ、はみ出す分は見えている範囲の内側へ寄せる（幅はピンの広がり以上なので全ピンが入る）。
        val left = ((minX + maxX) / 2 - width / 2).coerceIn(visible.left, visible.right - width)
        val top = ((minY + maxY) / 2 - height / 2).coerceIn(visible.top, visible.bottom - height)
        return CropRect(left, top, left + width, top + height)
    }
}
