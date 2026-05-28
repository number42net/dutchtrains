package net.number42.dutchtrains

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
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
import net.number42.dutchtrains.service.NotificationHelper
import net.number42.dutchtrains.service.TrainChange
import net.number42.dutchtrains.service.TrainEventType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class ScreenshotCaptureInstrumentedTest {

    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var notificationHelper: NotificationHelper

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun captureReadmeScreenshots() {
        hiltRule.inject()
        grantRuntimePermissionsForScreenshots()
        ensureNotificationChannelsExist()
        runBlocking {
            appPreferences.saveApiKey("mock-test-key")
            appPreferences.saveFromStation(Station(code = "ASD", uicCode = "8400058", name = "Amsterdam Centraal", lat = 52.378, lng = 4.9))
            appPreferences.saveToStation(Station(code = "ALM", uicCode = "8400059", name = "Almere Centrum", lat = 52.375, lng = 5.217))
        }

        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val screenshotDir = File("/sdcard/Pictures/dutchtrains")
        screenshotDir.mkdirs()

        val scenario = ActivityScenario.launch<MainActivity>(
            Intent(context, MainActivity::class.java).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )

        try {
            device.wait(Until.hasObject(By.desc("Settings")), TimeUnit.SECONDS.toMillis(12))
            dismissPermissionDialogsIfPresent(device)
            device.waitForIdle()
            device.takeScreenshot(File(screenshotDir, "commute-overview.png"))

            device.findObject(By.desc("Direct trip"))?.click()
            device.wait(Until.hasObject(By.text("Trip details")), TimeUnit.SECONDS.toMillis(8))
            device.wait(Until.hasObject(By.textContains("Length:")), TimeUnit.SECONDS.toMillis(5))
            Thread.sleep(1500)
            device.takeScreenshot(File(screenshotDir, "trip-detail-ic.png"))

            device.findObject(By.text("Follow this train"))?.click()
            dismissPermissionDialogsIfPresent(device)
            device.wait(Until.hasObject(By.text("Stop following")), TimeUnit.SECONDS.toMillis(8))
            notificationHelper.postChangeNotification(
                trainName = "IC 1234",
                ctxRecon = "mock-ctx-ic",
                changes = listOf(TrainChange(TrainEventType.PLATFORM_CHANGES, "Platform", "4", "6")),
            )
            device.openNotification()
            device.wait(Until.hasObject(By.textContains("Platform:")), TimeUnit.SECONDS.toMillis(5))
            Thread.sleep(500)
            device.takeScreenshot(File(screenshotDir, "follow-notification.png"))
            device.pressBack()

            repeat(5) {
                if (device.hasObject(By.desc("Settings"))) return@repeat
                device.pressBack()
                Thread.sleep(350)
            }
            dismissPermissionDialogsIfPresent(device)
            device.wait(Until.hasObject(By.desc("1× change trip")), TimeUnit.SECONDS.toMillis(8))
            device.findObject(By.desc("1× change trip"))?.click()
            device.wait(Until.hasObject(By.text("Trip details")), TimeUnit.SECONDS.toMillis(8))
            device.wait(Until.hasObject(By.textContains("SPR")), TimeUnit.SECONDS.toMillis(5))
            Thread.sleep(1200)
            device.takeScreenshot(File(screenshotDir, "trip-detail-sprinter.png"))
        } finally {
            scenario.close()
        }
    }

    private fun grantRuntimePermissionsForScreenshots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val packageName = instrumentation.targetContext.packageName

        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        runCatching {
            instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                instrumentation.uiAutomation.grantRuntimePermission(packageName, Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun ensureNotificationChannelsExist() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(DutchTrainsApp.CHANNEL_FOLLOWING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DutchTrainsApp.CHANNEL_FOLLOWING, "Following", NotificationManager.IMPORTANCE_LOW),
            )
        }
        if (manager.getNotificationChannel(DutchTrainsApp.CHANNEL_UPDATES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(DutchTrainsApp.CHANNEL_UPDATES, "Updates", NotificationManager.IMPORTANCE_HIGH),
            )
        }
    }

    private fun dismissPermissionDialogsIfPresent(device: UiDevice) {
        val allowResIds = listOf(
            "com.android.permissioncontroller:id/permission_allow_button",
            "com.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.android.permissioncontroller:id/permission_allow_one_time_button",
            "com.google.android.permissioncontroller:id/permission_allow_button",
            "com.google.android.permissioncontroller:id/permission_allow_foreground_only_button",
            "com.google.android.permissioncontroller:id/permission_allow_one_time_button",
        )
        val allowLabels = listOf(
            "Allow",
            "ALLOW",
            "While using the app",
            "Only this time",
            "Allow notifications",
        )

        repeat(3) {
            val button = allowResIds.firstNotNullOfOrNull { id -> device.findObject(By.res(id)) }
                ?: allowLabels.firstNotNullOfOrNull { label -> device.findObject(By.text(label)) }
            if (button == null) return
            button.click()
            device.waitForIdle()
            Thread.sleep(250)
        }
    }
}
