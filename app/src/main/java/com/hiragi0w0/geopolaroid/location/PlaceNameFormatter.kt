package com.hiragi0w0.geopolaroid.location

/**
 * 逆ジオコーディング結果を市区町村程度の表示名にまとめる。
 * 例: locality=福岡市, subLocality=中央区 → 「福岡市中央区」。町名など細かすぎる単位は含めない。
 */
object PlaceNameFormatter {
    fun format(
        adminArea: String?,
        subAdminArea: String?,
        locality: String?,
        subLocality: String?,
    ): String? {
        val city = locality.clean() ?: subAdminArea.clean()
        val ward = subLocality.clean()?.takeIf { it.endsWith("区") && it != city }
        val name = listOfNotNull(city, ward).joinToString("")
        return name.ifEmpty { adminArea.clean() }
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
}
