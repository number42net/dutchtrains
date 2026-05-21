package net.number42.dutchtrains

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.os.Build
import androidx.test.core.app.ActivityScenario
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kotlinx.coroutines.runBlocking
import net.number42.dutchtrains.data.datastore.AppPreferences
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import net.number42.dutchtrains.domain.model.Station
import org.junit.Assert.assertTrue
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainFlowInstrumentedTest {

    @Inject lateinit var appPreferences: AppPreferences

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var device: UiDevice
    private val mockDepartureDisplay: String =
        DateTimeFormatter.ofPattern("HH:mm").format(MockBackendTestModule.mockDepartureTime)

    @Before
    fun setup() {
        hiltRule.inject()
        runBlocking {
            resetAppStateForTest()
        }
        ensureNotificationChannelsExist()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun ensureNotificationChannelsExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(DutchTrainsApp.CHANNEL_FOLLOWING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DutchTrainsApp.CHANNEL_FOLLOWING, "Following", NotificationManager.IMPORTANCE_LOW)
            )
        }
        if (manager.getNotificationChannel(DutchTrainsApp.CHANNEL_UPDATES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DutchTrainsApp.CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_HIGH)
            )
        }
    }

    @Test
    fun freshStartShowsSettingsScreen() {
        launchApp()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue(
            "Expected settings screen with NS API Key prompt",
            device.wait(Until.hasObject(By.text("NS API Key")), TimeUnit.SECONDS.toMillis(12)),
        )
    }

    @Test
    fun configuredStartShowsHomeAndCanOpenSettings() {
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
        }
        launchApp()

        dismissRuntimePermissionDialogsIfPresent()
        ensureHomeScreenVisible()

        dismissRuntimePermissionDialogsIfPresent()

        assertTrue("Home screen title 'Dutch' should be visible", device.wait(Until.hasObject(By.text("Dutch")), TimeUnit.SECONDS.toMillis(12)))
        assertTrue("Home screen title 'Trains' should be visible", device.wait(Until.hasObject(By.text("Trains")), TimeUnit.SECONDS.toMillis(12)))

        device.findObject(By.desc("Settings"))?.click()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue(
            "Settings should reopen and show NS API Key",
            device.wait(Until.hasObject(By.text("NS API Key")), TimeUnit.SECONDS.toMillis(8)),
        )

        dismissRuntimePermissionDialogsIfPresent()
        device.findObject(By.desc("Back"))?.click() ?: device.pressBack()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue("Back from settings should return home", device.wait(Until.hasObject(By.desc("Settings")), TimeUnit.SECONDS.toMillis(12)))
    }

    @Test
    fun tripResultsLoadWhenStationsAreConfigured() {
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9))
            appPreferences.saveToStation(Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum", lat = 52.375, lng = 5.217))
        }
        launchApp()
        dismissRuntimePermissionDialogsIfPresent()
        ensureHomeScreenVisible()
        dismissRuntimePermissionDialogsIfPresent()

        assertTrue(
            "Trip list should show departure time 19:48",
            device.wait(Until.hasObject(By.textContains(mockDepartureDisplay)), TimeUnit.SECONDS.toMillis(12)),
        )
        assertTrue(
            "Trip card should show 20 min duration",
            device.wait(Until.hasObject(By.textContains("20 min")), TimeUnit.SECONDS.toMillis(5)),
        )
    }

    @Test
    fun selectTripOpensTripDetailAndBack() {
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9))
            appPreferences.saveToStation(Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum", lat = 52.375, lng = 5.217))
        }
        launchApp()
        dismissRuntimePermissionDialogsIfPresent()
        ensureHomeScreenVisible()
        dismissRuntimePermissionDialogsIfPresent()

        assertTrue(
            "Trips should load before tapping",
            device.wait(Until.hasObject(By.textContains(mockDepartureDisplay)), TimeUnit.SECONDS.toMillis(12)),
        )
        device.findObject(By.textContains(mockDepartureDisplay))?.click()

        dismissRuntimePermissionDialogsIfPresent()
        assertTrue(
            "Trip detail screen title should be visible",
            device.wait(Until.hasObject(By.text("Trip details")), TimeUnit.SECONDS.toMillis(8)),
        )
        assertTrue(
            "Follow button should be visible on trip detail",
            device.wait(Until.hasObject(By.text("Follow this train")), TimeUnit.SECONDS.toMillis(5)),
        )

        device.findObject(By.desc("Back"))?.click() ?: device.pressBack()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue(
            "Back from trip detail should return home",
            device.wait(Until.hasObject(By.desc("Settings")), TimeUnit.SECONDS.toMillis(12)),
        )
    }

    private fun launchApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        scenario = ActivityScenario.launch(intent)
    }

    private fun ensureHomeScreenVisible() {
        repeat(20) {
            if (device.hasObject(By.desc("Settings"))) {
                return
            }

            if (device.hasObject(By.text("NS API Key"))) {
                val apiKeyField = device.findObject(By.clazz("android.widget.EditText"))
                assertTrue("API key input should be visible", apiKeyField != null)
                apiKeyField?.text = "mock-test-key"
                device.waitForIdle()

                val saveButton = device.findObject(By.text("Save"))
                assertTrue("Save button should be visible", saveButton != null)
                saveButton?.click()
                device.waitForIdle()
            }

            dismissRuntimePermissionDialogsIfPresent()
            Thread.sleep(700)
        }

        assertTrue("Expected home screen to appear", device.hasObject(By.desc("Settings")))
    }

    private fun dismissRuntimePermissionDialogsIfPresent() {
        val permissionButtonResIds = listOf(
            "com.android.permissioncontroller:id/permission_deny_button",
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_one_time_button",
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.google.android.permissioncontroller:id/permission_deny_button",
            "com.google.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.google.android.permissioncontroller:id/permission_allow_button",
        )

        val denyLabels = mutableListOf("Don’t allow", "Don't allow", "DENY", "Deny")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            denyLabels += "Don’t allow notifications"
            denyLabels += "Don't allow notifications"
        }

        repeat(3) {
            val button = permissionButtonResIds.firstNotNullOfOrNull { id -> device.findObject(By.res(id)) }
                ?: denyLabels.firstNotNullOfOrNull { label -> device.findObject(By.text(label)) }
            if (button == null) return
            button.click()
            device.waitForIdle()
            Thread.sleep(250)
        }
    }

    private fun navigateToTripDetail() {
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9))
            appPreferences.saveToStation(Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum", lat = 52.375, lng = 5.217))
        }
        launchApp()
        dismissRuntimePermissionDialogsIfPresent()
        ensureHomeScreenVisible()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue("Trips should load before tapping",
            device.wait(Until.hasObject(By.textContains(mockDepartureDisplay)), TimeUnit.SECONDS.toMillis(12)))
        device.findObject(By.textContains(mockDepartureDisplay))?.click()
        dismissRuntimePermissionDialogsIfPresent()
        assertTrue("Trip detail screen should be visible",
            device.wait(Until.hasObject(By.text("Trip details")), TimeUnit.SECONDS.toMillis(8)))
    }

    private fun allowNotificationPermissionIfPresent() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val allowResIds = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.google.android.permissioncontroller:id/permission_allow_button",
        )
        repeat(3) {
            val button = allowResIds.firstNotNullOfOrNull { id -> device.findObject(By.res(id)) }
                ?: device.findObject(By.text("Allow"))
            if (button == null) return
            button.click()
            device.waitForIdle()
            Thread.sleep(250)
        }
    }

    @Test
    fun followTripTogglesButton() {
        navigateToTripDetail()

        assertTrue("Follow button should be visible",
            device.wait(Until.hasObject(By.text("Follow this train")), TimeUnit.SECONDS.toMillis(5)))
        device.findObject(By.text("Follow this train"))?.click()

        allowNotificationPermissionIfPresent()
        dismissRuntimePermissionDialogsIfPresent()

        assertTrue("Button should change to 'Stop following'",
            device.wait(Until.hasObject(By.text("Stop following")), TimeUnit.SECONDS.toMillis(8)))

        device.findObject(By.text("Stop following"))?.click()

        assertTrue("Button should revert to 'Follow this train'",
            device.wait(Until.hasObject(By.text("Follow this train")), TimeUnit.SECONDS.toMillis(8)))
    }

    @Test
    fun followTripShowsForegroundNotification() {
        navigateToTripDetail()

        device.findObject(By.text("Follow this train"))?.click()

        allowNotificationPermissionIfPresent()
        dismissRuntimePermissionDialogsIfPresent()

        assertTrue("Button should show 'Stop following' before checking notifications",
            device.wait(Until.hasObject(By.text("Stop following")), TimeUnit.SECONDS.toMillis(8)))

        device.openNotification()

        assertTrue("Following notification should be visible in notification drawer",
            device.wait(Until.hasObject(By.textContains("Amsterdam Centraal -> Almere Centrum")), TimeUnit.SECONDS.toMillis(5)))

        device.pressBack()
    }

    @Test
    fun tripDetailRefreshUpdatesPlatform() {
        navigateToTripDetail()

        assertTrue("Initial platform 4 should be shown",
            device.wait(Until.hasObject(By.textContains("Platform 4")), TimeUnit.SECONDS.toMillis(8)))

        MockBackendTestModule.updatedSingleTripTrack = "7"

        device.swipe(
            device.displayWidth / 2,
            device.displayHeight / 5,
            device.displayWidth / 2,
            device.displayHeight / 2,
            40,
        )

        assertTrue("Platform should update to 7 after pull-to-refresh",
            device.wait(Until.hasObject(By.textContains("Platform 7")), TimeUnit.SECONDS.toMillis(10)))
    }

    private suspend fun resetAppStateForTest() {
        MockBackendTestModule.updatedSingleTripTrack = null
        appPreferences.saveApiKey("")
        appPreferences.saveFromStation(null)
        appPreferences.saveToStation(null)
        appPreferences.saveDirectOnly(false)
        appPreferences.saveIcOnly(false)
        appPreferences.saveNotifyPlatformChanges(true)
        appPreferences.saveNotifyDepartureTime(true)
        appPreferences.saveNotifyArrivalTime(true)
        appPreferences.saveNotifyPlatformArrivalChanges(false)
        appPreferences.saveNotifyMaterialChanges(true)
        appPreferences.saveDisplayWindowMinutes(120)
        appPreferences.saveFollowedCtxRecon(null)
    }

    @org.junit.After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }
}
