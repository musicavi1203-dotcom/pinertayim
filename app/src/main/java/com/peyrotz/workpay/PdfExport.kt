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
 * ייצוא דוח חודשי ל-PDF — ללא ספריות חיצוניות.
 * משתמש ב-PdfDocument המובנה של אנדרואיד.
 */
object PdfExport {

    private const val W = 595  // A4 @ 72dpi
    private const val H = 842
    private const val M = 40f

    private class Item(val cells: Array<String>, val bold: Boolean)

    fun create(
        ctx: Context,
        jobName: String,
        month: YearMonth,
        rows: List<DayRow>
    ): File {
        val he = Locale.forLanguageTag("he")
        val monthTitle = month.format(DateTimeFormatter.ofPattern("LLLL yyyy", he))

        // ---------- Paints ----------
        val normal = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f
            color = Color.BLACK
            textAlign = Paint.Align.CENTER
        }
        val bold = Paint(normal).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titlePaint = Paint(bold).apply {
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            color = Color.parseColor("#2644FF")
        }
        val subPaint = Paint(normal).apply {
            textSize = 15f
            color = Color.DKGRAY
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
            textSize = 12f
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val totalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E3F2FD")
            style = Paint.Style.FILL
        }
        val totalTextPaint = Paint(bold).apply {
            textSize = 14f
            color = Color.parseColor("#2644FF")
            textAlign = Paint.Align.CENTER
        }

        // ---------- עמודות (RTL): תאריך, כניסה, יציאה, שעות, שכר ----------
        val weights = floatArrayOf(1.1f, 1f, 1f, 1f, 1.2f)
        val contentW = W - 2 * M
        val right = W - M
        val centers = FloatArray(5)
        var x = right
        for (i in 0..4) {
            val w = contentW * weights[i] / weights.sum()
            centers[i] = x - w / 2
            x -= w
        }
        val labels = arrayOf(
            ctx.getString(R.string.col_date),
            ctx.getString(R.string.col_start),
            ctx.getString(R.string.col_end),
            ctx.getString(R.string.col_hours),
            ctx.getString(R.string.pdf_pay_header)
        )

        // ---------- שורות ----------
        val items = ArrayList<Item>()
        rows.forEach { r ->
            items.add(
                Item(
                    arrayOf(
                        "${r.date.dayOfMonth}/${r.date.monthValue}",
                        Calc.fmtTime(r.startMin),
                        Calc.fmtTime(r.endMin),
                        Calc.fmtHours(r.minutes),
                        Calc.fmtPay(r.pay)
                    ),
                    false
                )
            )
        }
        items.add(
            Item(
                arrayOf(
                    ctx.getString(R.string.pdf_total),
                    "",
                    "",
                    Calc.fmtHours(rows.sumOf { it.minutes }),
                    Calc.fmtPay(rows.sumOf { it.pay })
                ),
                true
            )
        )

        // ---------- יצירת PDF ----------
        val rowH = 26f
        val doc = PdfDocument()
        var idx = 0
        var pageNo = 1

        while (true) {
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(W, H, pageNo).create()
            )
            val c = page.canvas
            var y: Float

            if (pageNo == 1) {
                c.drawText(jobName, right, M + 22, titlePaint)
                c.drawText(monthTitle, right, M + 46, subPaint)
                y = M + 70
            } else {
                y = M
            }

            // Header row
            c.drawRect(M, y, right, y + 26f, headerBgPaint)
            for (i in 0..4) {
                c.drawText(labels[i], centers[i], y + 18f, headerTextPaint)
            }
            y += 26f

            // Rows
            while (idx < items.size && y + rowH <= H - M) {
                val item = items[idx]
                val p = if (item.bold) bold else normal
                for (i in 0..4) {
                    c.drawText(item.cells[i], centers[i], y + 18f, p)
                }
                y += rowH
                c.drawLine(M, y, right, y, linePaint)
                idx++
            }

            doc.finishPage(page)
            if (idx >= items.size) break
            pageNo++
        }

        // ---------- שמירה ----------
        val dir = File(ctx.cacheDir, "pdf")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }

        val safeJob = jobName
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .ifBlank { "job" }
        val file = File(dir, "${safeJob}_$month.pdf")
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun share(ctx: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            ctx,
            ctx.packageName + ".fileprovider",
            file
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(
            Intent.createChooser(send, ctx.getString(R.string.share_title))
        )
    }
}
