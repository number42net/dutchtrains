package net.number42.dutchtrains

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PermissionFlowInstrumentedTest {

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
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9))
            appPreferences.saveToStation(Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum", lat = 52.375, lng = 5.217))
        }
        grantLocationPermissionsForTests()
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    private fun grantLocationPermissionsForTests() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        runCatching {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant $packageName android.permission.ACCESS_FINE_LOCATION",
            )
        }
        runCatching {
            instrumentation.uiAutomation.executeShellCommand(
                "pm grant $packageName android.permission.ACCESS_COARSE_LOCATION",
            )
        }
    }

    private fun isNotificationPermissionGranted(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchApp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        scenario = ActivityScenario.launch(intent)
    }

    @Test
    fun followTripRequestsNotificationPermissionWhenNotGranted() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        assertTrue(
            "Notification permission should be denied before this test starts",
            !isNotificationPermissionGranted(),
        )

        launchApp()
        assertTrue(
            "Trips should load before tapping",
            device.wait(Until.hasObject(By.textContains(mockDepartureDisplay)), TimeUnit.SECONDS.toMillis(12)),
        )
        device.findObject(By.textContains(mockDepartureDisplay))?.click()

        assertTrue(
            "Trip detail screen should be visible",
            device.wait(Until.hasObject(By.text("Trip details")), TimeUnit.SECONDS.toMillis(8)),
        )
        assertTrue(
            "Follow button should be visible",
            device.wait(Until.hasObject(By.text("Follow this train")), TimeUnit.SECONDS.toMillis(5)),
        )
        device.findObject(By.text("Follow this train"))?.click()

        val hasPrompt = device.wait(
            Until.hasObject(By.textContains("notifications")),
            TimeUnit.SECONDS.toMillis(5),
        ) || device.wait(
            Until.hasObject(By.res("com.android.permissioncontroller:id/permission_allow_button")),
            TimeUnit.SECONDS.toMillis(2),
        ) || device.wait(
            Until.hasObject(By.res("com.google.android.permissioncontroller:id/permission_allow_button")),
            TimeUnit.SECONDS.toMillis(2),
        )

        assertTrue("Notification permission prompt should appear", hasPrompt)
    }

    @org.junit.After
    fun tearDown() {
        scenario?.close()
        scenario = null
    }
}
