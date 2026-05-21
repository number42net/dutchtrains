package net.number42.dutchtrains

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import net.number42.dutchtrains.data.datastore.AppPreferences
import javax.inject.Inject

@HiltAndroidApp
class DutchTrainsApp : Application() {

    @Inject lateinit var appPreferences: AppPreferences

    // Volatile field read synchronously by NsApiKeyInterceptor on OkHttp threads.
    // Updated by the Flow collector below — avoids runBlocking in the interceptor.
    @Volatile var cachedApiKey: String = ""

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    companion object {
        const val CHANNEL_FOLLOWING = "train_following"
        const val CHANNEL_UPDATES = "train_updates"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        applicationScope.launch {
            appPreferences.saveFollowedCtxRecon(null)
        }
        applicationScope.launch {
            appPreferences.apiKeyFlow.collect { key -> cachedApiKey = key }
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        NotificationChannel(
            CHANNEL_FOLLOWING,
            getString(R.string.notification_channel_following_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notification_channel_following_desc)
            manager.createNotificationChannel(this)
        }

        NotificationChannel(
            CHANNEL_UPDATES,
            getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = getString(R.string.notification_channel_updates_desc)
            manager.createNotificationChannel(this)
        }
    }
}
