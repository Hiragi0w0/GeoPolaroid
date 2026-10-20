package com.hiragi0w0.geopolaroid

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

data class PhotoCard(
    val imageResID: Int,
    val time: String,
    val place: String
)

@Composable
fun HomeScreen(modifier: Modifier = Modifier){
    var isVisible by remember { mutableStateOf(true) }
    val pictures = listOf (
        PhotoCard(
            R.drawable.test1,
            "10:30",
            "天神周辺"
        ),
        PhotoCard(
            R.drawable.test2,
            "15:00",
            "大濠公園周辺"
        )
    )
    val pictureCount = if (isVisible) pictures.size else 0
    Column(modifier = modifier){
        Text(text = "今日の記録")
        Text(text = "$pictureCount / 12枚")
        Button(onClick = {}, enabled = false) {
            Text(text = "撮影する")
        }
        Button(onClick = { isVisible = !isVisible }, enabled = true) {
            Text(text = "サンプル表示 ON/OFF")
        }
        Text(text = "サンプル")
        if (isVisible) {
            pictures.forEach { picture ->
                Text(text = picture.time + " " + picture.place)
                Image(
                    painter = painterResource(id = picture.imageResID),
                    contentDescription = "お化けの写真",
                    modifier = Modifier.size(200.dp)
                )
            }
        }
    }
}