package com.peyrotz.workpay

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * ייצוא דוחות ל-PDF — ללא ספריות חיצוניות.
 * תומך בייצוא חודשי וייצוא שנתי.
 */
object PdfExport {

    private const val W = 595  // A4 @ 72dpi
    private const val H = 842
    private const val M = 40f

    /**
     * ייצוא דוח חודשי.
     */
    fun create(
        ctx: Context,
        jobName: String,
        month: YearMonth,
        rows: List<DayRow>
    ): File {
        val he = Locale.forLanguageTag("he")
        val title = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", he))
        return generatePdf(ctx, jobName, "$jobName - $title", rows, isYearly = false)
    }

    /**
     * ייצוא דוח שנתי (כל 12 החודשים).
     */
    fun createYearly(
        ctx: Context,
        jobName: String,
        year: Int,
        rows: List<DayRow>
    ): File {
        return generatePdf(ctx, jobName, "$jobName - שנה $year", rows, isYearly = true)
    }

    // ============================================================
    // לוגיקת יצירת ה-PDF המרכזית
    // ============================================================
    private fun generatePdf(
        ctx: Context,
        jobName: String,
        fullTitle: String,
        rows: List<DayRow>,
        isYearly: Boolean
    ): File {
        val he = Locale.forLanguageTag("he")

        // ---------- Paints ----------
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val bold = Paint(normal).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = 12f
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9f
            color = Color.parseColor("#7A7A7A")
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(bold).apply {
            textSize = 20f
            textAlign = Paint.Align.RIGHT
            color = Color.parseColor("#2644FF")
        }
        val subTitlePaint = Paint(normal).apply {
            textSize = 13f
            color = Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }
        val monthTitlePaint = Paint(bold).apply {
            textSize = 14f
            color = Color.parseColor("#2644FF")
            textAlign = Paint.Align.RIGHT
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }
        val headerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2644FF")
            style = Paint.Style.FILL
        }
        val headerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3F2FD")
            style = Paint.Style.FILL
        }
        val totalTextPaint = Paint(bold).apply {
            textSize = 12f
            color = Color.parseColor("#2644FF")
            textAlign = Paint.Align.CENTER
        }

        // ---------- 6 עמודות (RTL): תאריך, כניסה, יציאה, שעות, מיירס, שכר ----------
        val weights = floatArrayOf(1.1f, 1.0f, 1.0f, 1.0f, 0.8f, 1.2f)
        val contentW = W - 2 * M
        val right = W - M
        val centers = FloatArray(6)
        var x = right
        for (i in 0..5) {
            val w = contentW * weights[i] / weights.sum()
            centers[i] = x - w / 2
            x -= w
        }
        val labels = arrayOf(
            ctx.getString(R.string.col_date),
            ctx.getString(R.string.col_start),
            ctx.getString(R.string.col_end),
            ctx.getString(R.string.col_hours),
            "מיירס",
            ctx.getString(R.string.pdf_pay_header)
        )

        // ---------- בניית ה-PDF ----------
        val rowH = 20f
        val subRowH = 16f
        val headerH = 24f
        val monthHeaderH = 30f
        
        val doc = PdfDocument()
        var pageNo = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
        var canvas = page.canvas
        var y: Float

        // ציור כותרת ראשית (בעמוד הראשון)
        canvas.drawText(fullTitle, right, M + 20, titlePaint)
        y = M + 40

        fun checkPageBreak(neededHeight: Float): Boolean {
            if (y + neededHeight > H - M) {
                doc.finishPage(page)
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
                canvas = page.canvas
                y = M // מתחילים מהקצה העליון בעמוד חדש
                return true
            }
            return false
        }

        fun drawTableHeader() {
            checkPageBreak(headerH + 10f)
            canvas.drawRect(M, y, right, y + headerH, headerBgPaint)
            for (i in 0..5) {
                canvas.drawText(labels[i], centers[i], y + 16f, headerTextPaint)
            }
            y += headerH
        }

        fun drawDayRow(r: DayRow) {
            val segs = r.segments
            val totalRowHeight = if (segs.size <= 1) rowH else rowH + (segs.size * subRowH)
            
            // בדיקת שבירת עמוד לפני ציור היום
            if (checkPageBreak(totalRowHeight + 5f)) {
                drawTableHeader() // ציור כותרת מחדש בעמוד החדש
            }

            if (segs.size <= 1) {
                val mires = segs.firstOrNull()?.mirros?.toString() ?: ""
                val cells = arrayOf(
                    "${r.date.dayOfMonth}/${r.date.monthValue}",
                    Calc.fmtTime(r.startMin),
                    Calc.fmtTime(r.endMin),
                    Calc.fmtHours(r.minutes),
                    mires,
                    Calc.fmtPay(r.pay)
                )
                for (i in 0..5) canvas.drawText(cells[i], centers[i], y + 14f, bold)
                y += rowH
                canvas.drawLine(M, y, right, y, linePaint)
            } else {
                // שורה ראשית (סה"כ יום)
                val cells = arrayOf(
                    "${r.date.dayOfMonth}/${r.date.monthValue}",
                    Calc.fmtTime(r.startMin),
                    Calc.fmtTime(r.endMin),
                    Calc.fmtHours(r.minutes),
                    "",
                    Calc.fmtPay(r.pay)
                )
                for (i in 0..5) canvas.drawText(cells[i], centers[i], y + 14f, bold)
                y += rowH
                
                // תתי-שורות לפרקים
                segs.sortedBy { it.startMin }.forEach { s ->
                    val subCells = arrayOf(
                        "",
                        Calc.fmtTime(s.startMin),
                        Calc.fmtTime(s.endMin),
                        Calc.fmtHours(Calc.minutes(s)),
                        s.mirros.toString(),
                        Calc.fmtPay(Calc.pay(s, r.rate))
                    )
                    for (i in 0..5) canvas.drawText(subCells[i], centers[i], y + 11f, subPaint)
                    y += subRowH
                }
                canvas.drawLine(M, y, right, y, linePaint)
            }
        }

        fun drawMonthTotal(rows: List<DayRow>) {
            checkPageBreak(headerH + 10f)
            canvas.drawRect(M, y, right, y + headerH, totalBgPaint)
            val totalMin = rows.sumOf { it.minutes }
            val totalPay = rows.sumOf { it.pay }
            val cells = arrayOf(
                "סה\"כ חודש",
                "",
                "",
                Calc.fmtHours(totalMin),
                "",
                Calc.fmtPay(totalPay)
            )
            for (i in 0..5) canvas.drawText(cells[i], centers[i], y + 16f, totalTextPaint)
            y += headerH + 10f
        }

        if (isYearly) {
            // קיבוץ לפי חודשים
            val byMonth = rows.groupBy { it.date.monthValue }
            
            for (m in 1..12) {
                val monthRows = byMonth[m]?.sortedBy { it.date } ?: emptyList()
                if (monthRows.isEmpty()) continue // מדלגים על חודשים ריקים

                checkPageBreak(monthHeaderH + headerH + 20f)
                // כותרת חודש
                canvas.drawText(Calc.hebrewMonth(m), right, y + 20f, monthTitlePaint)
                y += monthHeaderH

                drawTableHeader()
                monthRows.forEach { r -> drawDayRow(r) }
                drawMonthTotal(monthRows)
            }
            
            // סיכום שנתי כולל בסוף
            checkPageBreak(headerH + 20f)
            val yearMin = rows.sumOf { it.minutes }
            val yearPay = rows.sumOf { it.pay }
            canvas.drawRect(M, y, right, y + headerH + 10f, totalBgPaint)
            val cells = arrayOf("סה\"כ שנתי", "", "", Calc.fmtHours(yearMin), "", Calc.fmtPay(yearPay))
            for (i in 0..5) canvas.drawText(cells[i], centers[i], y + 22f, totalTextPaint)
            y += headerH + 10f

        } else {
            // ייצוא חודשי רגיל
            drawTableHeader()
            rows.forEach { r -> drawDayRow(r) }
            
            // סיכום חודשי
            checkPageBreak(headerH + 10f)
            val totalMin = rows.sumOf { it.minutes }
            val totalPay = rows.sumOf { it.pay }
            canvas.drawRect(M, y, right, y + headerH, totalBgPaint)
            val cells = arrayOf(ctx.getString(R.string.pdf_total), "", "", Calc.fmtHours(totalMin), "", Calc.fmtPay(totalPay))
            for (i in 0..5) canvas.drawText(cells[i], centers[i], y + 16f, totalTextPaint)
        }

        doc.finishPage(page)

        // ---------- שמירה ----------
        val dir = File(ctx.cacheDir, "pdf")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val safeJob = jobName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "job" }
        val fileName = if (isYearly) "${safeJob}_${rows.firstOrNull()?.date?.year ?: "Year"}.pdf" else "${safeJob}_${rows.firstOrNull()?.date?.let { YearMonth.from(it) } ?: "Month"}.pdf"
        
        val file = File(dir, fileName)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, ctx.getString(R.string.share_title)))
    }
}
