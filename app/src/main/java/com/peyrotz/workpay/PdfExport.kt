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
 * כולל פירוט פרקים עם מיירס לכל פרק.
 */
object PdfExport {

    private const val W = 595  // A4 @ 72dpi
    private const val H = 842
    private const val M = 40f

    /**
     * שורה בטבלה. if bold=true — שורה ראשית של יום.
     */
    private class Row(
        val cells: Array<String>,
        val isMain: Boolean,
        val isTotal: Boolean = false
    )

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
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 10f
            color = Color.parseColor("#7A7A7A")
            textAlign = Paint.Align.CENTER
        }
        val titlePaint = Paint(bold).apply {
            textSize = 22f
            textAlign = Paint.Align.RIGHT
            color = Color.parseColor("#2644FF")
        }
        val subTitlePaint = Paint(normal).apply {
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
            textSize = 11f
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

        // ---------- בניית שורות ----------
        val items = ArrayList<Row>()

        rows.forEach { r ->
            val segs = r.segments
            if (segs.size <= 1) {
                // יום עם פרק אחד (או אפס) — שורה אחת עם מיירס
                val mires = segs.firstOrNull()?.mirros?.toString() ?: ""
                items.add(
                    Row(
                        arrayOf(
                            "${r.date.dayOfMonth}/${r.date.monthValue}",
                            Calc.fmtTime(r.startMin),
                            Calc.fmtTime(r.endMin),
                            Calc.fmtHours(r.minutes),
                            mires,
                            Calc.fmtPay(r.pay)
                        ),
                        isMain = true
                    )
                )
            } else {
                // יום עם 2+ פרקים — שורה ראשית (בלי מיירס) + תת-שורות
                items.add(
                    Row(
                        arrayOf(
                            "${r.date.dayOfMonth}/${r.date.monthValue}",
                            Calc.fmtTime(r.startMin),
                            Calc.fmtTime(r.endMin),
                            Calc.fmtHours(r.minutes),
                            "",
                            Calc.fmtPay(r.pay)
                        ),
                        isMain = true
                    )
                )
                segs.sortedBy { it.startMin }.forEach { s ->
                    items.add(
                        Row(
                            arrayOf(
                                "",
                                Calc.fmtTime(s.startMin),
                                Calc.fmtTime(s.endMin),
                                Calc.fmtHours(Calc.minutes(s)),
                                s.mirros.toString(),
                                Calc.fmtPay(Calc.pay(s, r.rate))
                            ),
                            isMain = false
                        )
                    )
                }
            }
        }

        // שורת סה"כ
        items.add(
            Row(
                arrayOf(
                    ctx.getString(R.string.pdf_total),
                    "",
                    "",
                    Calc.fmtHours(rows.sumOf { it.minutes }),
                    "",
                    Calc.fmtPay(rows.sumOf { it.pay })
                ),
                isMain = true,
                isTotal = true
            )
        )

        // ---------- יצירת PDF ----------
        val rowH = 22f
        val subRowH = 18f
        val headerH = 26f
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
                c.drawText(monthTitle, right, M + 46, subTitlePaint)
                y = M + 70
            } else {
                y = M
            }

            // Header
            c.drawRect(M, y, right, y + headerH, headerBgPaint)
            for (i in 0..5) {
                c.drawText(labels[i], centers[i], y + 18f, headerTextPaint)
            }
            y += headerH

            // Rows
            while (idx < items.size) {
                val row = items[idx]
                val h = if (row.isMain) rowH else subRowH

                // page break?
                if (y + h > H - M) break

                val paint = when {
                    row.isTotal -> bold
                    row.isMain -> normal
                    else -> subPaint
                }

                // רקע תכלת לשורת סה"כ
                if (row.isTotal) {
                    c.drawRect(M, y, right, y + h, totalBgPaint)
                }

                val textY = y + h / 2f + 4f
                for (i in 0..5) {
                    if (i == 5 && row.isTotal) {
                        c.drawText(row.cells[i], centers[i], textY, totalTextPaint)
                    } else {
                        c.drawText(row.cells[i], centers[i], textY, paint)
                    }
                }

                y += h
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
