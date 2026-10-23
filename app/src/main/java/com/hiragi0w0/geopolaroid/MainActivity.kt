package com.hiragi0w0.geopolaroid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.hiragi0w0.geopolaroid.ui.GeoPolaroidApp
import com.hiragi0w0.geopolaroid.ui.theme.GeoPolaroidTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as GeoPolaroidApplication).container
        setContent {
            GeoPolaroidTheme {
                GeoPolaroidApp(container)
            }
        }
    }
}
