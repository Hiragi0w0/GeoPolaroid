package com.hiragi0w0.geopolaroid

import android.app.Application

class GeoPolaroidApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}
