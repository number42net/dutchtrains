package net.number42.dutchtrains.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import net.number42.dutchtrains.DutchTrainsApp
import net.number42.dutchtrains.MainActivity
import net.number42.dutchtrains.R
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val FOLLOW_NOTIFICATION_ID = 1001
        private val changeIdCounter = AtomicInteger(2000)
    }

    fun buildFollowingNotification(tripTitle: String, detail: String? = null, ctxRecon: String? = null): Notification =
        followingNotificationBuilder(
            tripTitle = tripTitle,
            detail = detail ?: context.getString(R.string.notification_following_text),
            ctxRecon = ctxRecon,
        ).build()

    fun updateFollowingNotification(tripTitle: String, detail: String, ctxRecon: String?) {
        NotificationManagerCompat.from(context)
            .notify(FOLLOW_NOTIFICATION_ID, followingNotificationBuilder(tripTitle, detail, ctxRecon).build())
    }

    private fun followingNotificationBuilder(tripTitle: String, detail: String, ctxRecon: String?): NotificationCompat.Builder {
        val stopIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TrainFollowService::class.java).apply {
                action = TrainFollowService.ACTION_STOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val openIntent = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                if (!ctxRecon.isNullOrBlank()) {
                    putExtra(MainActivity.EXTRA_OPEN_CTX_RECON, ctxRecon)
                }
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, DutchTrainsApp.CHANNEL_FOLLOWING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(tripTitle)
            .setContentText(detail)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .addAction(0, context.getString(R.string.label_stop_following), stopIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
    }

    fun postChangeNotification(trainName: String, ctxRecon: String, changes: List<TrainChange>) {
        val text = changes.joinToString(" · ") { "${it.field}: ${it.from} → ${it.to}" }
        val openIntent = PendingIntent.getActivity(
            context,
            changeIdCounter.get(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_OPEN_CTX_RECON, ctxRecon)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, DutchTrainsApp.CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Update: $trainName")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openIntent)
            .build()
        NotificationManagerCompat.from(context).notify(changeIdCounter.getAndIncrement(), notification)
    }
}
