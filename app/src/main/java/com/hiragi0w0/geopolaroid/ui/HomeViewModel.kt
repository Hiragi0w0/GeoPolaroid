package com.hiragi0w0.geopolaroid.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hiragi0w0.geopolaroid.AppContainer
import com.hiragi0w0.geopolaroid.home.ActiveCaptureStore
import com.hiragi0w0.geopolaroid.home.HomeController

/** [HomeController] を ViewModel の寿命と SavedStateHandle に結び付けるだけの薄い層。 */
class HomeViewModel(container: AppContainer, savedState: SavedStateHandle) : ViewModel() {
    val controller = HomeController(
        scope = viewModelScope,
        repository = container.repository,
        coordinator = container.captureCoordinator,
        mediaGateway = container.mediaGateway,
        placeNames = container.placeNames,
        timeSource = container.timeSource,
        activeStore = SavedStateActiveCaptureStore(savedState),
    )
}

private class SavedStateActiveCaptureStore(private val savedState: SavedStateHandle) : ActiveCaptureStore {
    override var activePhotoId: String?
        get() = savedState[KEY_ACTIVE_PHOTO_ID]
        set(value) {
            savedState[KEY_ACTIVE_PHOTO_ID] = value
        }

    private companion object {
        const val KEY_ACTIVE_PHOTO_ID = "activePhotoId"
    }
}
