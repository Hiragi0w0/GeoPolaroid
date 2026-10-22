package com.hiragi0w0.geopolaroid

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/** 専用エミュレーターで実行。FLPモックで無更新・遅延応答を再現する。 */
@RunWith(AndroidJUnit4::class)
class CurrentLocationTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val client = LocationServices.getFusedLocationProviderClient(context)

    @Before
    fun setUpLocationFixture() {
        // coarse実行前にはホスト側でfineを取り消す。実行中の取消はプロセスを終了させる。
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (InstrumentationRegistry.getArguments().getString("permissionMode") != "coarse") {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            assertEquals(
                "coarseモード実行前にホスト側で詳細権限を取り消してください",
                PackageManager.PERMISSION_DENIED,
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
            )
        }
        shell("appops set ${context.packageName} android:mock_location allow")
        shell("cmd location set-location-enabled true")
        Tasks.await(client.setMockMode(true), 10, TimeUnit.SECONDS)
    }

    @After
    fun tearDownLocationFixture() {
        try {
            Tasks.await(client.setMockMode(false), 10, TimeUnit.SECONDS)
        } finally {
            shell("appops set ${context.packageName} android:mock_location default")
        }
    }

    @Test
    fun startsWithoutRequestingLocation() {
        compose.onNodeWithText("取得状態：未取得").assertIsDisplayed()
        compose.onNodeWithText("現在地を取得").assertIsEnabled()
        compose.onNodeWithText("撮影する").assertIsNotEnabled()
    }

    @Test
    fun displaysFreshCoordinatesAndPreventsDuplicateRequests() {
        startRequest()
        injectUntilSuccess()
        compose.onNodeWithText("緯度：", substring = true).assertIsDisplayed()
        compose.onNodeWithText("経度：", substring = true).assertIsDisplayed()
        if (InstrumentationRegistry.getArguments().getString("permissionMode") != "coarse") {
            compose.onNodeWithText("緯度：35.681236").assertIsDisplayed()
            compose.onNodeWithText("経度：139.767125").assertIsDisplayed()
        }
        compose.onNodeWithText("現在地を取得").assertIsEnabled()
    }

    @Test
    fun noNewFixTimesOutWithoutReusingPreviousLocationAndCanRetry() {
        startRequest()
        injectUntilSuccess()
        // 現在位置として許容する10秒を超えた後、更新なしで再要求する。
        SystemClock.sleep(10_100)
        // FLPは即座にnullを返す場合もあるため、この要求では取得中の持続を前提にしない。
        compose.onNodeWithText("現在地を取得").performClick()
        compose.onNodeWithText("緯度：", substring = true).assertDoesNotExist()
        compose.waitUntil(25_000) { hasText("取得状態：取得できませんでした") }
        compose.onNodeWithText("現在地を取得").assertIsEnabled()
        compose.onNodeWithText("緯度：", substring = true).assertDoesNotExist()
        resetMockLocations()
        startRequest()
        injectUntilSuccess()
    }

    @Test
    fun leavingScreenCancelsRequestAndIgnoresLateLocation() {
        startRequest()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        injectLocation()
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithText("画面を離れたため取得を中断しました。再試行してください。")
            .assertIsDisplayed()
        compose.onNodeWithText("緯度：", substring = true).assertDoesNotExist()
        resetMockLocations()
        startRequest()
        injectUntilSuccess()
    }

    private fun startRequest() {
        compose.onNodeWithText("現在地を取得").performClick()
        compose.onNodeWithText("取得状態：取得中").assertIsDisplayed()
        compose.onNodeWithText("現在地を取得").assertIsNotEnabled()
    }

    private fun injectUntilSuccess() {
        compose.waitUntil(12_000) {
            injectLocation()
            hasText("取得状態：成功")
        }
    }

    private fun injectLocation() {
        val location = Location("fused").apply {
            latitude = 35.681236
            longitude = 139.767125
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        Tasks.await(client.setMockLocation(location), 10, TimeUnit.SECONDS)
    }

    private fun resetMockLocations() {
        Tasks.await(client.setMockMode(false), 10, TimeUnit.SECONDS)
        Tasks.await(client.setMockMode(true), 10, TimeUnit.SECONDS)
    }

    private fun hasText(text: String): Boolean =
        compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }
}
