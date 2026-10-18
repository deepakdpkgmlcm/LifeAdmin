package com.lifeadmin.app.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lifeadmin.app.AlarmActivity
import com.lifeadmin.app.MainActivity
import com.lifeadmin.app.R
import com.lifeadmin.app.data.AlertStyle
import com.lifeadmin.app.data.Item

object Notifier {
    private const val CHANNEL_REMINDERS = "reminders_v2"
    private const val OLD_CHANNEL = "reminders"
    private const val CHANNEL_ALARMS = "alarms_v1"
    private const val CHANNEL_PAYMENTS = "payments_v1"
    private const val CHANNEL_EXPIRY = "expiry_v1"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        // v0.1-0.3 used a quieter channel; channel importance can't be raised in place.
        nm.deleteNotificationChannel(OLD_CHANNEL)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Tasks and everyday reminders"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_PAYMENTS, "Payments", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Bills, subscriptions and other payments coming due"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_EXPIRY, "Expiry & renewals", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Documents, insurance, vehicle papers and warranties expiring"
                enableVibration(true)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ALARMS, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Reminders set to ring like an alarm"
                setSound(
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 600, 400, 600, 400, 600)
                setBypassDnd(true)
            }
        )
    }

    fun canNotify(context: Context): Boolean {
        val permissionOk = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionOk && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /** Android 14+ can block full-screen alarms; earlier versions always allow them. */
    fun canUseFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 ||
            context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun openAppIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SKIP_SPLASH, true)
        }
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    /** A simple one-off notification (used by the settings test button). */
    @SuppressLint("MissingPermission")
    fun show(context: Context, id: Int, title: String, text: String) {
        if (!canNotify(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, id))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /**
     * The reminder itself. Notification style: heads-up with Done and Snooze buttons.
     * Alarm style: rings (repeating sound) and opens the full-screen alarm screen.
     */
    @SuppressLint("MissingPermission")
    fun alert(context: Context, item: Item, text: String, occurrenceMillis: Long) {
        if (!canNotify(context)) return
        val id = item.id.toInt()
        val alarm = item.alertStyle == AlertStyle.ALARM
        val channel = when {
            alarm -> CHANNEL_ALARMS
            item.kind == com.lifeadmin.app.data.Kind.PAYMENT -> CHANNEL_PAYMENTS
            item.kind == com.lifeadmin.app.data.Kind.RENEWAL -> CHANNEL_EXPIRY
            else -> CHANNEL_REMINDERS
        }
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(item.title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openAppIntent(context, id))
            .setAutoCancel(true)
            .setPriority(if (alarm) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_HIGH)
            .setCategory(if (alarm) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .addAction(0, item.kind.doneVerb, AlarmReceiver.actionIntent(context, AlarmReceiver.ACTION_DONE, item.id, occurrenceMillis))
            .addAction(0, "Snooze 10 min", AlarmReceiver.actionIntent(context, AlarmReceiver.ACTION_SNOOZE, item.id, occurrenceMillis, 10))

        if (alarm) {
            val fullScreen = PendingIntent.getActivity(
                context, id,
                AlarmActivity.intent(context, item.id, occurrenceMillis),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.setFullScreenIntent(fullScreen, true)
                .setOngoing(true)
                .setAutoCancel(false)
        }

        val notification = builder.build()
        if (alarm) notification.flags = notification.flags or Notification.FLAG_INSISTENT
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
