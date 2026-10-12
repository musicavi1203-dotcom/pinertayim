package com.peyrotz.workpay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

@Composable
fun YearlyScreen(
    jobId: Long,
    jobName: String,
    onClose: () -> Unit,
    onOpenMonth: (YearMonth) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val primary = MaterialTheme.colorScheme.primary

    var year by remember { mutableStateOf(YearMonth.now().year) }
    var months by remember { mutableStateOf<List<MonthSummary>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    LaunchedEffect(jobId, year) {
        loaded = false
        months = withContext(Dispatchers.IO) {
            loadYear(ctx, jobId, year)
        }
        loaded = true
    }

    val totalPay = months.sumOf { it.pay }
    val totalMinutes = months.sumOf { it.minutes }
    val activeMonths = months.count { it.days > 0 }

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
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    stringResource(R.string.yearly_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    jobName,
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 13.sp
                )
            }
        }

        // ======= ניווט שנים =======
        Row(
            Modifier
                .fillMaxWidth()
                .background(primary)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { year-- }) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = null,
                    tint = Color.White
                )
            }
            Text(
                year.toString(),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { year++ }) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }

        LazyColumn(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // ======= כרטיס סיכום שנתי =======
            item {
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "סיכום $year",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = primary,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        SummaryLine(
                            stringResource(R.string.yearly_total_pay),
                            "₪" + Calc.fmtPay(totalPay),
                            primary
                        )
                        Spacer(Modifier.padding(top = 6.dp))
                        SummaryLine(
                            stringResource(R.string.yearly_total_hours),
                            Calc.fmtHours(totalMinutes),
                            Color.DarkGray
                        )
                        Spacer(Modifier.padding(top = 6.dp))
                        SummaryLine(
                            stringResource(R.string.yearly_active_months),
                            "$activeMonths / 12",
                            Color.DarkGray
                        )
                    }
                }
            }

            // ======= 12 חודשים =======
            items(months) { ms ->
                MonthCard(
                    ms = ms,
                    onClick = { onOpenMonth(ms.month) }
                )
            }

            item { Spacer(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

// ============================================================
// רכיבי משנה
// ============================================================

@Composable
private fun SummaryLine(label: String, value: String, valueColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = Color.DarkGray
        )
        Text(
            value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = valueColor
        )
    }
}

@Composable
private fun MonthCard(ms: MonthSummary, onClick: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val isEmpty = ms.days == 0

    Card(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !isEmpty) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (isEmpty) Color(0xFFF0F0F0) else Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isEmpty) 0.dp else 2.dp
        )
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // עיגול עם מספר החודש
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (isEmpty) Color.LightGray else primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    ms.month.monthValue.toString(),
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    Calc.hebrewMonth(ms.month.monthValue),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isEmpty) Color.Gray else Color.Black
                )
                if (isEmpty) {
                    Text(
                        stringResource(R.string.yearly_no_data),
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                } else {
                    Text(
                        "${ms.days} ${stringResource(R.string.yearly_days)} · " +
                            "${Calc.fmtHours(ms.minutes)} ${stringResource(R.string.hours_label)}",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }
            }

            if (!isEmpty) {
                Text(
                    "₪" + Calc.fmtPay(ms.pay),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }
        }
    }
}
