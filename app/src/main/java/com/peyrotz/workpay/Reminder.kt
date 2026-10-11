package com.peyrotz.workpay

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

// ============================================================
// קבועים ברמת הקובץ — נגישים גם ל-Reminder וגם ל-ReminderWorker
// ============================================================
private const val CHANNEL_ID = "daily_reminder"
private const val NOTIF_ID = 1001
private const val WORK_NAME = "daily_reminder_work"

/**
 * תזכורת יומית — שולח התראה בערב להזכיר למלא את היום.
 * משתמש ב-WorkManager כדי לשרוד הפעלה מחדש של הטלפון.
 */
object Reminder {

    /**
     * מתזמן תזכורת יומית לפי השעה שנשמרה ב-Prefs.
     * אם המשתמש כיבה — לא עושה כלום.
     */
    fun schedule(ctx: Context) {
        if (!Prefs.reminderOn(ctx)) return

        val hour = Prefs.reminderHour(ctx)
        val minute = Prefs.reminderMinute(ctx)

        // חישוב זמן עד התזכורת הבאה (delay ראשוני)
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(hour, minute))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        val delayMinutes = Duration.between(now, target).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(WORK_NAME)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    /**
     * מבטל את התזכורת.
     */
    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(WORK_NAME)
    }
}

/**
 * Worker ששולח את ההתראה בפועל.
 */
class ReminderWorker(
    ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        // אם המשתמש כיבה — לא שולחים
        if (!Prefs.reminderOn(ctx)) return Result.success()

        showNotification(ctx)
        return Result.success()
    }

    private fun showNotification(ctx: Context) {
        // ---------- בדיקת הרשאה (אנדרואיד 13+) ----------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // ---------- יצירת ערוץ (אנדרואיד 8+) ----------
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val existing = nm.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    ctx.getString(R.string.reminder_title),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = ctx.getString(R.string.reminder_desc)
                }
                nm.createNotificationChannel(channel)
            }
        }

        // ---------- פתיחת האפליקציה בלחיצה ----------
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            ctx,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // ---------- ההתראה ----------
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(ctx.getString(R.string.reminder_title))
            .setContentText(ctx.getString(R.string.reminder_text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID, notif)
        } catch (e: SecurityException) {
            // הרשאה בוטלה — מתעלמים בשקט
        }
    }
}
