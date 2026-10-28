package com.peyrotz.workpay

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * מבנה הנתונים של הגיבוי — כולל את כל הטבלאות.
 */
data class BackupData(
    val jobs: List<JobEntity> = emptyList(),
    val days: List<DayEntity> = emptyList(),
    val segments: List<SegmentEntity> = emptyList(),
    val payments: List<PaymentEntity> = emptyList()
)

object BackupManager {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * מייצא את כל הנתונים לקובץ JSON.
     * מחזיר true אם הצליח.
     */
    suspend fun export(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val dao = AppDb.get(ctx).dao()
            val jobs = dao.jobs()
            val allDays = mutableListOf<DayEntity>()
            val allPayments = mutableListOf<PaymentEntity>()
            val allSegments = mutableListOf<SegmentEntity>()

            // איסוף כל הנתונים מכל מקומות העבודה
            jobs.forEach { job ->
                val days = dao.allDaysOfJob(job.id)
                allDays.addAll(days)
                allPayments.addAll(dao.paymentsOfJob(job.id))
                allSegments.addAll(dao.segmentsFor(days.map { it.id }))
            }

            val data = BackupData(jobs, allDays, allSegments, allPayments)
            val json = gson.toJson(data)

            ctx.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray())
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * מייבא נתונים מקובץ JSON.
     * הפעולה מוחקת את הנתונים הקיימים ומחליפה אותם בקובץ.
     * מחזיר true אם הצליח.
     */
    suspend fun import(ctx: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (json.isNullOrBlank()) return@withContext false

            val data = gson.fromJson(json, BackupData::class.java) ?: return@withContext false

            val dao = AppDb.get(ctx).dao()

            // ניקוי כל הנתונים הקיימים (ה-CASCADE ימחק ימים, פרקים ותשלומים)
            dao.jobs().forEach { dao.deleteJob(it.id) }

            // ייבוא מקומות עבודה ושמירת מיפוי ID (מישן לחדש)
            val jobIdMap = mutableMapOf<Long, Long>()
            data.jobs.forEach { oldJob ->
                val newId = dao.insertJob(oldJob.copy(id = 0))
                jobIdMap[oldJob.id] = newId
            }

            // ייבוא ימים
            val dayIdMap = mutableMapOf<Long, Long>()
            data.days.forEach { oldDay ->
                val newJobId = jobIdMap[oldDay.jobId] ?: 1L
                val newId = dao.insertDay(oldDay.copy(id = 0, jobId = newJobId))
                dayIdMap[oldDay.id] = newId
            }

            // ייבוא פרקים
            val newSegments = data.segments.mapNotNull { oldSeg ->
                val newDayId = dayIdMap[oldSeg.dayId]
                if (newDayId != null) oldSeg.copy(id = 0, dayId = newDayId) else null
            }
            dao.insertSegments(newSegments)

            // ייבוא תשלומים
            data.payments.forEach { oldPay ->
                val newJobId = jobIdMap[oldPay.jobId] ?: 1L
                dao.insertPayment(oldPay.copy(id = 0, jobId = newJobId))
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
