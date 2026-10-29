package com.peyrotz.workpay

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * גיבוי אוטומטי לתיקייה שהמשתמש בחר (למשל Google Drive).
 * שומר קובץ JSON אחד מתגלגל (מקסימום 5 גיבויים אחרונים).
 */
object AutoBackup {

    private const val MAX_BACKUPS = 5
    private const val PREFIX = "auto_backup_"

    /**
     * יוצר גיבוי אוטומטי בתיקייה השמורה.
     * מחזיר true אם הצליח.
     */
    suspend fun backup(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val uriString = Prefs.autoBackupFolder(ctx)
            if (uriString.isNullOrBlank()) return@withContext false

            val folder = DocumentFile.fromTreeUri(ctx, Uri.parse(uriString))
                ?: return@withContext false
            if (!folder.exists() || !folder.canWrite()) return@withContext false

            // שם קובץ עם חותמת זמן
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "$PREFIX$timestamp.json"

            // יצירת קובץ חדש בתיקייה
            val file = folder.createFile("application/json", fileName)
                ?: return@withContext false

            // כתיבה
            ctx.contentResolver.openOutputStream(file.uri)?.use { output ->
                val dao = AppDb.get(ctx).dao()
                val jobs = dao.jobs()
                val allDays = mutableListOf<DayEntity>()
                val allPayments = mutableListOf<PaymentEntity>()
                val allSegments = mutableListOf<SegmentEntity>()

                jobs.forEach { job ->
                    val days = dao.allDaysOfJob(job.id)
                    allDays.addAll(days)
                    allPayments.addAll(dao.paymentsOfJob(job.id))
                    allSegments.addAll(dao.segmentsFor(days.map { it.id }))
                }

                val data = BackupData(jobs, allDays, allSegments, allPayments)
                val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                output.write(gson.toJson(data).toByteArray())
            }

            // ניקוי גיבויים ישנים (משאירים 5 אחרונים)
            cleanupOldBackups(folder)

            // עדכון זמן גיבוי אחרון
            Prefs.setLastAutoBackup(ctx, System.currentTimeMillis())

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * מוחק גיבויים ישנים ומשאיר רק את [MAX_BACKUPS] האחרונים.
     */
    private fun cleanupOldBackups(folder: DocumentFile) {
        val files = folder.listFiles()
            .filter { it.name?.startsWith(PREFIX) == true }
            .sortedByDescending { it.name }

        if (files.size > MAX_BACKUPS) {
            files.drop(MAX_BACKUPS).forEach { it.delete() }
        }
    }
}
