package com.peyrotz.workpay

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

private const val AUTO_BACKUP_WORK_NAME = "auto_backup_work"

/**
 * מתזמן את הגיבוי האוטומטי לרוץ פעם ביום בשעה 03:00 לפנות בוקר.
 */
object AutoBackupScheduler {
    fun schedule(ctx: Context) {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(now.toLocalDate(), LocalTime.of(3, 0))
        if (target.isBefore(now) || target.isEqual(now)) {
            target = target.plusDays(1)
        }
        val delayMinutes = Duration.between(now, target).toMinutes().coerceAtLeast(1)

        val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .addTag(AUTO_BACKUP_WORK_NAME)
            .build()

        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            AUTO_BACKUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            request
        )
    }

    fun cancel(ctx: Context) {
        WorkManager.getInstance(ctx).cancelUniqueWork(AUTO_BACKUP_WORK_NAME)
    }
}

/**
 * Worker שמריץ את הגיבוי בפועל.
 */
class AutoBackupWorker(
    ctx: Context,
    params: WorkerParameters
) : Worker(ctx, params) {

    override fun doWork(): Result {
        val ctx = applicationContext
        // בודק אם המשתמש בחר תיקייה
        if (Prefs.autoBackupFolder(ctx).isNullOrBlank()) {
            return Result.success()
        }

        // הפעולה עצמה מתבצעת ב-AutoBackup
        // (אנחנו מריצים אותה בת'רד נפרד כי Worker לא חוסם את ה-UI)
        var success = false
        val thread = Thread {
            success = kotlinx.coroutines.runBlocking {
                AutoBackup.backup(ctx)
            }
        }
        thread.start()
        thread.join()

        return if (success) Result.success() else Result.retry()
    }
}
