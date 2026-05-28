package net.number42.dutchtrains

import android.content.Intent
import android.location.Location
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import net.number42.dutchtrains.data.datastore.AppPreferences
import net.number42.dutchtrains.domain.model.Station
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class AutoSwitchInstrumentedTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var appPreferences: AppPreferences

    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    private val amsterdam = Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9)
    private val almere    = Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum",    lat = 52.375, lng = 5.217)

    @Before
    fun setup() {
        hiltRule.inject()
        FakeLocationHelper.nextResult = Result.failure(Exception("No location"))
        runBlocking { resetAppState() }
        grantLocationPermission()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    @After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }

    @Test
    fun swapsStationsWhenNearestMatchesToStation() {
        FakeLocationHelper.nextResult = Result.success(mockLocation(almere.lat, almere.lng))
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(amsterdam)
            appPreferences.saveToStation(almere)
        }
        launchApp()
        dismissPermissionsIfPresent()

        assertTrue(
            "From field should show Almere Centrum after swap",
            device.wait(Until.hasObject(By.desc("From station: Almere Centrum")), TimeUnit.SECONDS.toMillis(8)),
        )
        assertTrue(
            "To field should show Amsterdam Centraal after swap",
            device.wait(Until.hasObject(By.desc("To station: Amsterdam Centraal")), TimeUnit.SECONDS.toMillis(4)),
        )
        assertTrue(
            "Trip list should refresh and show Almere→Amsterdam trips (2× change)",
            device.wait(Until.hasObject(By.desc("2× change trip")), TimeUnit.SECONDS.toMillis(8)),
        )
    }

    @Test
    fun noChangeWhenNearestMatchesFromStation() {
        FakeLocationHelper.nextResult = Result.success(mockLocation(amsterdam.lat, amsterdam.lng))
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(amsterdam)
            appPreferences.saveToStation(almere)
        }
        launchApp()
        dismissPermissionsIfPresent()

        // Wait for home screen and let auto-switch complete
        device.wait(Until.hasObject(By.desc("Settings")), TimeUnit.SECONDS.toMillis(8))
        Thread.sleep(2_000)

        assertTrue(
            "From field should remain Amsterdam Centraal",
            device.wait(Until.hasObject(By.desc("From station: Amsterdam Centraal")), TimeUnit.SECONDS.toMillis(4)),
        )
        assertTrue(
            "To field should remain Almere Centrum",
            device.wait(Until.hasObject(By.desc("To station: Almere Centrum")), TimeUnit.SECONDS.toMillis(4)),
        )
    }

    @Test
    fun setsFromStationWhenNoStationSelected() {
        FakeLocationHelper.nextResult = Result.success(mockLocation(amsterdam.lat, amsterdam.lng))
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
        }
        launchApp()
        dismissPermissionsIfPresent()

        assertTrue(
            "Home screen should be visible",
            device.wait(Until.hasObject(By.desc("Settings")), TimeUnit.SECONDS.toMillis(8)),
        )
        assertTrue(
            "From field should be set to Amsterdam Centraal",
            device.wait(Until.hasObject(By.desc("From station: Amsterdam Centraal")), TimeUnit.SECONDS.toMillis(8)),
        )
        assertTrue(
            "To field should remain empty",
            device.wait(Until.hasObject(By.desc("To station: empty")), TimeUnit.SECONDS.toMillis(4)),
        )
    }

    private fun mockLocation(lat: Double, lng: Double) = Location("test").apply {
        latitude = lat
        longitude = lng
        accuracy = 1f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    private fun launchApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        scenario = ActivityScenario.launch(
            Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        )
    }

    private fun grantLocationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName
        listOf(
            "pm grant $packageName android.permission.ACCESS_FINE_LOCATION",
            "pm grant $packageName android.permission.ACCESS_COARSE_LOCATION",
        ).forEach { cmd ->
            runCatching {
                // Drain the output stream to wait for the command to finish
                val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
                android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
            }
        }
    }

    private fun dismissPermissionsIfPresent() {
        val denyResIds = listOf(
            "com.android.permissioncontroller:id/permission_deny_button",
            "com.android.permissioncontroller:id/permission_deny_and_dont_ask_again_button",
            "com.google.android.permissioncontroller:id/permission_deny_button",
        )
        repeat(3) {
            val button = denyResIds.firstNotNullOfOrNull { id -> device.findObject(By.res(id)) }
                ?: device.findObject(By.text("Don't allow"))
                ?: device.findObject(By.text("Deny"))
            if (button == null) return
            button.click()
            device.waitForIdle()
            Thread.sleep(250)
        }
    }

    private suspend fun resetAppState() {
        MockBackendTestModule.updatedSingleTripTrack = null
        appPreferences.saveApiKey("")
        appPreferences.saveFromStation(null)
        appPreferences.saveToStation(null)
        appPreferences.saveDirectOnly(false)
        appPreferences.saveIcOnly(false)
        appPreferences.saveFollowedCtxRecon(null)
    }
}
