package com.hiragi0w0.geopolaroid.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.hiragi0w0.geopolaroid.R

/**
 * アプリ全体で使う書体（Zen Maru Gothic、SIL Open Font License）。すべての文字スタイルがこれを参照する。
 * 使っている太さは標準と中太だけなので、その2つを同梱する。それより太い指定は中太から合成される。
 */
private val AppFontFamily = FontFamily(
    Font(R.font.zen_maru_gothic_regular, FontWeight.Normal),
    Font(R.font.zen_maru_gothic_medium, FontWeight.Medium),
)

private fun TextStyle.withAppFont() = copy(fontFamily = AppFontFamily)

private val Base = Typography()

val Typography = Typography(
    displayLarge = Base.displayLarge.withAppFont(),
    displayMedium = Base.displayMedium.withAppFont(),
    displaySmall = Base.displaySmall.withAppFont(),
    headlineLarge = Base.headlineLarge.withAppFont(),
    headlineMedium = Base.headlineMedium.withAppFont(),
    headlineSmall = Base.headlineSmall.withAppFont(),
    titleLarge = Base.titleLarge.withAppFont(),
    titleMedium = Base.titleMedium.withAppFont(),
    titleSmall = Base.titleSmall.withAppFont(),
    bodyLarge = Base.bodyLarge.withAppFont(),
    bodyMedium = Base.bodyMedium.withAppFont(),
    bodySmall = Base.bodySmall.withAppFont(),
    labelLarge = Base.labelLarge.withAppFont(),
    labelMedium = Base.labelMedium.withAppFont(),
    labelSmall = Base.labelSmall.withAppFont(),
)
