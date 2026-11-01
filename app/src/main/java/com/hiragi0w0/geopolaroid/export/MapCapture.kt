package com.hiragi0w0.geopolaroid.export

import android.graphics.Bitmap
import com.hiragi0w0.geopolaroid.domain.CropRect
import com.hiragi0w0.geopolaroid.domain.SketchPoint

/**
 * 画面の地図を写した画像。[pins] は撮影地点の画像上の位置、[visible] は戻るボタンやシートに隠れていない範囲。
 * 保存画像にはこの中からピンを含む範囲だけを切り抜いて使う。
 */
class MapCapture(val bitmap: Bitmap, val pins: List<SketchPoint>, val visible: CropRect)
