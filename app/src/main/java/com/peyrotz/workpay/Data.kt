package com.peyrotz.workpay

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Update
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

// ============================================================
// Entities
// ============================================================

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val defaultRate: Double
)

@Entity(tableName = "days", indices = [Index("jobId")])
data class DayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val rate: Double,
    @ColumnInfo(defaultValue = "1") val jobId: Long = 1
)

@Entity(
    tableName = "segments",
    foreignKeys = [
        ForeignKey(
            entity = DayEntity::class,
            parentColumns = ["id"],
            childColumns = ["dayId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("dayId")]
)
data class SegmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dayId: Long,
    val startMin: Int,
    val endMin: Int,
    val mirros: Int
)

@Entity(
    tableName = "payments",
    foreignKeys = [
        ForeignKey(
            entity = JobEntity::class,
            parentColumns = ["id"],
            childColumns = ["jobId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("jobId")]
)
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val jobId: Long,
    val date: String,
    val amount: Double,
    @ColumnInfo(defaultValue = "") val note: String = ""
)

// ============================================================
// DAO
// ============================================================

@Dao
abstract class AppDao {

    // ---------- Jobs ----------
    @Query("SELECT * FROM jobs ORDER BY id")
    abstract fun jobs(): List<JobEntity>

    @Insert
    abstract fun insertJob(j: JobEntity): Long

    @Update
    abstract fun updateJob(j: JobEntity)

    @Query("DELETE FROM jobs WHERE id = :id")
    abstract fun deleteJobRow(id: Long)

    @Query("DELETE FROM days WHERE jobId = :jobId")
    abstract fun deleteDaysOfJob(jobId: Long)

    @Transaction
    open fun deleteJob(id: Long) {
        deleteDaysOfJob(id)
        deleteJobRow(id)
    }

    // ---------- Days & Segments ----------
    @Insert
    abstract fun insertDay(d: DayEntity): Long

    @Update
    abstract fun updateDay(d: DayEntity)

    @Insert
    abstract fun insertSegments(s: List<SegmentEntity>)

    @Query("DELETE FROM segments WHERE dayId = :dayId")
    abstract fun deleteSegments(dayId: Long)

    @Query("DELETE FROM days WHERE id = :id")
    abstract fun deleteDay(id: Long)

    @Query("SELECT * FROM days WHERE jobId = :jobId AND date >= :from AND date < :to ORDER BY date")
    abstract fun daysInRange(jobId: Long, from: String, to: String): List<DayEntity>

    @Query("SELECT * FROM days WHERE jobId = :jobId ORDER BY date")
    abstract fun allDaysOfJob(jobId: Long): List<DayEntity>

    @Query("SELECT * FROM segments WHERE dayId IN (:ids)")
    abstract fun segmentsFor(ids: List<Long>): List<SegmentEntity>

    @Query("SELECT * FROM days WHERE id = :id")
    abstract fun getDay(id: Long): DayEntity?

    @Query("SELECT * FROM segments WHERE dayId = :id ORDER BY startMin")
    abstract fun segmentsOf(id: Long): List<SegmentEntity>

    @Transaction
    open fun saveDay(day: DayEntity, segs: List<SegmentEntity>) {
        val id: Long = if (day.id == 0L) {
            insertDay(day)
        } else {
            updateDay(day)
            day.id
        }
        deleteSegments(id)
        insertSegments(segs.map { it.copy(id = 0, dayId = id) })
    }

    // ---------- Payments ----------
    @Query("SELECT * FROM payments WHERE jobId = :jobId ORDER BY date DESC")
    abstract fun paymentsOfJob(jobId: Long): List<PaymentEntity>

    @Insert
    abstract fun insertPayment(p: PaymentEntity): Long

    @Update
    abstract fun updatePayment(p: PaymentEntity)

    @Query("DELETE FROM payments WHERE id = :id")
    abstract fun deletePayment(id: Long)
}

// ============================================================
// Database
// ============================================================

@Database(
    entities = [JobEntity::class, DayEntity::class, SegmentEntity::class, PaymentEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        @Volatile
        private var inst: AppDb? = null

        fun get(ctx: Context): AppDb =
            inst ?: synchronized(this) {
                inst ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    AppDb::class.java,
                    "workpay.db"
                )
                    .build()
                    .also { inst = it }
            }
    }
}

// ============================================================
// Helper: DayRow + loadMonth
// ============================================================

/**
 * Represents one row in the main table.
 * Includes rate + segments (with mires) so the PDF can show a per-segment breakdown.
 */
data class DayRow(
    val id: Long,
    val date: LocalDate,
    val startMin: Int,
    val endMin: Int,
    val minutes: Int,
    val pay: Double,
    val rate: Double,
    val segments: List<SegmentEntity> = emptyList()
)

fun loadMonth(ctx: Context, jobId: Long, m: YearMonth): List<DayRow> {
    val dao = AppDb.get(ctx).dao()
    val days = dao.daysInRange(
        jobId,
        m.atDay(1).toString(),
        m.plusMonths(1).atDay(1).toString()
    )
    if (days.isEmpty()) return emptyList()
    val byDay = dao.segmentsFor(days.map { it.id }).groupBy { it.dayId }
    return days.map { d ->
        val s = byDay[d.id].orEmpty().sortedBy { it.startMin }
        DayRow(
            id = d.id,
            date = LocalDate.parse(d.date),
            startMin = s.firstOrNull()?.startMin ?: 0,
            endMin = s.lastOrNull()?.endMin ?: 0,
            minutes = s.sumOf { Calc.minutes(it) },
            pay = s.sumOf { Calc.pay(it, d.rate) },
            rate = d.rate,
            segments = s
        )
    }
}

// ============================================================
// Calc — כל החישובים
// ============================================================

object Calc {
    fun minutesOf(start: Int, end: Int): Int {
        val d = end - start
        return if (d < 0) d + 1440 else d
    }

    fun minutes(s: SegmentEntity): Int = minutesOf(s.startMin, s.endMin)

    fun pay(s: SegmentEntity, rate: Double): Double =
        minutes(s) / 60.0 * s.mirros * rate

    fun fmtHours(totalMin: Int): String =
        String.format(Locale.US, "%d:%02d", totalMin / 60, totalMin % 60)

    fun fmtMoney(v: Double): String =
        String.format(Locale.US, "%,.2f", v) + " ₪"

    fun fmtPay(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString()
        else String.format(Locale.US, "%.2f", v)

    fun fmtTime(min: Int): String =
        String.format(Locale.US, "%02d:%02d", min / 60, min % 60)

    fun fmtRateInput(v: Double): String =
        if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun dayLetter(d: DayOfWeek): String = when (d) {
        DayOfWeek.SUNDAY -> "א׳"
        DayOfWeek.MONDAY -> "ב׳"
        DayOfWeek.TUESDAY -> "ג׳"
        DayOfWeek.WEDNESDAY -> "ד׳"
        DayOfWeek.THURSDAY -> "ה׳"
        DayOfWeek.FRIDAY -> "ו׳"
        DayOfWeek.SATURDAY -> "ש׳"
    }
}

// ============================================================
// Palette — 8 ערכות צבע
// ============================================================

val Palette: List<Long> = listOf(
    0xFF2644FFL, // כחול
    0xFF00897BL, // טורקיז
    0xFF2E7D32L, // ירוק
    0xFFC8741BL, // כתום
    0xFFD32F2FL, // אדום
    0xFF7B1FA2L, // סגול
    0xFFE91E63L, // ורוד
    0xFF37474FL  // אפור כהה
)

// ============================================================
// Prefs — העדפות
// ============================================================

object Prefs {
    private const val FILE = "workpay_prefs"
    private const val KEY_RATE = "default_rate"
    private const val KEY_COLOR = "color_index"
    private const val KEY_JOB = "selected_job"
    private const val KEY_REMINDER_ON = "reminder_on"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MIN = "reminder_min"

    private fun p(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun rate(ctx: Context): Double = p(ctx).getFloat(KEY_RATE, 40f).toDouble()

    fun colorIndex(ctx: Context): Int = p(ctx).getInt(KEY_COLOR, 0)
    fun setColorIndex(ctx: Context, i: Int) {
        p(ctx).edit().putInt(KEY_COLOR, i).apply()
    }

    fun selectedJob(ctx: Context): Long = p(ctx).getLong(KEY_JOB, 0L)
    fun setSelectedJob(ctx: Context, id: Long) {
        p(ctx).edit().putLong(KEY_JOB, id).apply()
    }

    fun reminderOn(ctx: Context): Boolean = p(ctx).getBoolean(KEY_REMINDER_ON, false)
    fun setReminderOn(ctx: Context, on: Boolean) {
        p(ctx).edit().putBoolean(KEY_REMINDER_ON, on).apply()
    }

    fun reminderHour(ctx: Context): Int = p(ctx).getInt(KEY_REMINDER_HOUR, 20)
    fun setReminderHour(ctx: Context, h: Int) {
        p(ctx).edit().putInt(KEY_REMINDER_HOUR, h).apply()
    }

    fun reminderMinute(ctx: Context): Int = p(ctx).getInt(KEY_REMINDER_MIN, 0)
    fun setReminderMinute(ctx: Context, m: Int) {
        p(ctx).edit().putInt(KEY_REMINDER_MIN, m).apply()
    }
}
