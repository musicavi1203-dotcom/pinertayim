package com.peyrotz.workpay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun GraphScreen(
    jobId: Long,
    month: YearMonth,
    onMonth: (YearMonth) -> Unit,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScopeSafe()
    val he = remember { Locale.forLanguageTag("he") }
    val primary = MaterialTheme.colorScheme.primary

    var rows by remember { mutableStateOf<List<DayRow>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(jobId, month) {
        loaded = false
        rows = withContext(Dispatchers.IO) { loadMonth(ctx, jobId, month) }
        loaded = true
    }

    val totalPay = rows.sumOf { it.pay }
    val totalMin = rows.sumOf { it.minutes }
    val daysInMonth = month.lengthOfMonth()

    // מיפוי שכר לפי יום בחודש
    val byDay = remember(rows) {
        val map = DoubleArray(daysInMonth + 1)
        rows.forEach { r ->
            val d = r.date.dayOfMonth
            if (d in 1..daysInMonth) map[d] += r.pay
        }
        map
    }

    val maxPay = remember(byDay) {
        val m = byDay.maxOrNull() ?: 0.0
        if (m <= 0.0) 1.0 else m
    }

    val statusBarPadding = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    Column(Modifier.fillMaxSize()) {

        // ======= סרגל עליון =======
        Row(
            Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(
                    top = statusBarPadding + 8.dp,
                    start = 4.dp,
                    end = 8.dp,
                    bottom = 8.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White
                )
            }
            Text(
                stringResource(R.string.graph_title),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        // ======= ניווט חודשים =======
        Row(
            Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { onMonth(month.minusMonths(1)) }) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                month.format(DateTimeFormatter.ofPattern("LLLL yyyy", he)),
                color = Color.White,
                fontSize = 18.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onMonth(month.plusMonths(1)) }) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .background(MaterialTheme.colorScheme.background)
                .padding(12.dp)
        ) {

            // ======= כרטיס סיכום =======
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    StatBox(
                        value = "₪" + Calc.fmtPay(totalPay),
                        label = stringResource(R.string.pay_label),
                        color = primary
                    )
                    StatBox(
                        value = Calc.fmtHours(totalMin),
                        label = stringResource(R.string.hours_label),
                        color = Color.DarkGray
                    )
                    StatBox(
                        value = rows.size.toString(),
                        label = stringResource(R.string.days_count),
                        color = Color.DarkGray
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                stringResource(R.string.graph_daily_pay),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // ======= הגרף =======
            if (!loaded) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("טוען...", color = Color.Gray)
                }
            } else if (rows.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(320.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.graph_no_data),
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                }
            } else {
                Card(
                    Modifier
                        .fillMaxWidth()
                        .height(340.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    BarChart(
                        values = byDay,
                        maxValue = maxPay,
                        barColor = primary,
                        daysInMonth = daysInMonth,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(12.dp)
                    )
                }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

// ============================================================
// רכיב עזר — rememberCoroutineScope
// ============================================================

@Composable
private fun rememberCoroutineScopeSafe() = androidx.compose.runtime.rememberCoroutineScope()

// ============================================================
// תיבת סטטיסטיקה
// ============================================================

@Composable
private fun StatBox(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

// ============================================================
// גרף עמודות מותאם אישית
// ============================================================

@Composable
private fun BarChart(
    values: DoubleArray,
    maxValue: Double,
    barColor: Color,
    daysInMonth: Int,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val padBottom = 30f
        val padTop = 10f
        val chartH = h - padBottom - padTop
        val chartTop = padTop
        val chartBottom = h - padBottom

        // קו בסיס
        drawLine(
            color = Color(0xFFE0E0E0),
            start = Offset(0f, chartBottom),
            end = Offset(w, chartBottom),
            strokeWidth = 2f
        )

        if (daysInMonth <= 0) return@Canvas

        val slotW = w / daysInMonth
        val barW = slotW * 0.65f

        // ציור עמודות
        for (i in 1..daysInMonth) {
            val value = values[i]
            val x = (i - 1) * slotW + (slotW - barW) / 2f

            val ratio = if (maxValue > 0) (value / maxValue).toFloat() else 0f
            val barH = chartH * ratio
            val top = chartBottom - barH

            if (value > 0.0 && barH > 2f) {
                drawRect(
                    color = barColor,
                    topLeft = Offset(x, top),
                    size = Size(barW, barH)
                )
            } else {
                // עמודה דקה אפורה ליום ריק
                drawRect(
                    color = Color(0xFFEFEFEF),
                    topLeft = Offset(x, chartBottom - 3f),
                    size = Size(barW, 3f)
                )
            }

            // תווית יום מתחת (רק כל 5 ימים כדי למנוע עומס)
            if (i == 1 || i % 5 == 0 || i == daysInMonth) {
                drawContext.canvas.nativeCanvas.drawText(
                    i.toString(),
                    x + barW / 2f,
                    chartBottom + 18f,
                    android.graphics.Paint().apply {
                        color = android.graphics.Color.parseColor("#5F6368")
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                )
            }
        }
    }
}
