package com.peyrotz.workpay

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private data class SegUi(
    val uid: String = UUID.randomUUID().toString(),
    val start: Int,
    val end: Int,
    val mirros: String
)

private fun pickTime(ctx: Context, cur: Int, onPick: (Int) -> Unit) {
    TimePickerDialog(
        ctx,
        { _, h, m -> onPick(h * 60 + m) },
        cur / 60 % 24,
        cur % 60,
        true
    ).show()
}

private fun SnapshotStateList<SegUi>.removeByUid(uid: String) {
    val idx = indexOfFirst { it.uid == uid }
    if (idx >= 0) removeAt(idx)
}

@Composable
fun DayScreen(
    dayId: Long,
    jobId: Long,
    defaultRate: Double,
    initialDate: String,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val he = remember { Locale.forLanguageTag("he") }
    val primary = MaterialTheme.colorScheme.primary

    var date by remember {
        mutableStateOf(
            if (initialDate.isNotEmpty()) LocalDate.parse(initialDate)
            else LocalDate.now()
        )
    }
    var rateText by remember { mutableStateOf(Calc.fmtRateInput(defaultRate)) }
    val segs = remember { mutableStateListOf<SegUi>() }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(dayId) {
        if (dayId == 0L) {
            if (segs.isEmpty()) {
                segs.add(SegUi(start = 8 * 60, end = 12 * 60, mirros = "1"))
            }
        } else {
            val loaded = withContext(Dispatchers.IO) {
                val dao = AppDb.get(ctx).dao()
                Pair(dao.getDay(dayId), dao.segmentsOf(dayId))
            }
            val d = loaded.first
            if (d == null) {
                onClose()
            } else {
                date = LocalDate.parse(d.date)
                rateText = Calc.fmtRateInput(d.rate)
                segs.clear()
                loaded.second.forEach {
                    segs.add(
                        SegUi(
                            start = it.startMin,
                            end = it.endMin,
                            mirros = it.mirros.toString()
                        )
                    )
                }
            }
        }
    }

    val rate = rateText.replace(',', '.').toDoubleOrNull()
    val totalMin = segs.sumOf { Calc.minutesOf(it.start, it.end) }
    val totalPay = segs.sumOf {
        Calc.minutesOf(it.start, it.end) / 60.0 *
            (it.mirros.toIntOrNull() ?: 0) *
            (rate ?: 0.0)
    }

    fun toast(id: Int) = Toast.makeText(ctx, ctx.getString(id), Toast.LENGTH_SHORT).show()

    fun save() {
        if (rate == null || rate < 0) {
            toast(R.string.err_rate); return
        }
        if (segs.isEmpty()) {
            toast(R.string.err_segments); return
        }
        if (segs.any { it.start == it.end }) {
            toast(R.string.err_time); return
        }
        if (segs.any { (it.mirros.toIntOrNull() ?: 0) < 1 }) {
            toast(R.string.err_mirros); return
        }
        val day = DayEntity(id = dayId, date = date.toString(), rate = rate, jobId = jobId)
        val list = segs.map {
            SegmentEntity(
                dayId = 0,
                startMin = it.start,
                endMin = it.end,
                mirros = it.mirros.toInt()
            )
        }
        scope.launch {
            withContext(Dispatchers.IO) { AppDb.get(ctx).dao().saveDay(day, list) }
            onClose()
        }
    }

    val statusBarPadding = WindowInsets.statusBars
        .asPaddingValues()
        .calculateTopPadding()

    Column(Modifier.fillMaxSize()) {
        // ======= סרגל עליון =======
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
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
                stringResource(
                    if (dayId == 0L) R.string.day_title_new
                    else R.string.day_title_edit
                ),
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // ======= תאריך =======
            Text(
                stringResource(R.string.date_label),
                fontSize = 14.sp,
                color = Color.Gray
            )
            OutlinedButton(
                onClick = {
                    DatePickerDialog(
                        ctx,
                        { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
                        date.year,
                        date.monthValue - 1,
                        date.dayOfMonth
                    ).show()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    date.format(DateTimeFormatter.ofPattern("EEEE d/M/yyyy", he)),
                    fontSize = 16.sp
                )
            }

            Spacer(Modifier.padding(top = 8.dp))

            // ======= תעריף =======
            OutlinedTextField(
                value = rateText,
                onValueChange = { rateText = it },
                label = { Text(stringResource(R.string.rate_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.padding(top = 12.dp))
            Text(
                stringResource(R.string.segments_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            // ======= רשימת פרקים =======
            segs.forEach { s ->
                val segPay = Calc.minutesOf(s.start, s.end) / 60.0 *
                    (s.mirros.toIntOrNull() ?: 0) * (rate ?: 0.0)
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    pickTime(ctx, s.start) { picked ->
                                        val i = segs.indexOfFirst { it.uid == s.uid }
                                        if (i >= 0) segs[i] = segs[i].copy(start = picked)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.start) + " " +
                                        Calc.fmtTime(s.start)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = {
                                    pickTime(ctx, s.end) { picked ->
                                        val i = segs.indexOfFirst { it.uid == s.uid }
                                        if (i >= 0) segs[i] = segs[i].copy(end = picked)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    stringResource(R.string.end) + " " +
                                        Calc.fmtTime(s.end)
                                )
                            }
                            IconButton(onClick = { segs.removeByUid(s.uid) }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = Color(0xFFD32F2F)
                                )
                            }
                        }
                        Spacer(Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = s.mirros,
                                onValueChange = { v ->
                                    if (v.all { c -> c.isDigit() } && v.length <= 3) {
                                        val i = segs.indexOfFirst { it.uid == s.uid }
                                        if (i >= 0) segs[i] = segs[i].copy(mirros = v)
                                    }
                                },
                                label = { Text(stringResource(R.string.mirros_per_hour)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.segment_pay) + ": ₪" +
                                    Calc.fmtPay(segPay),
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                        }
                    }
                }
            }

            // ======= כפתור הוסף פרק =======
            OutlinedButton(
                onClick = {
                    val last = segs.lastOrNull()
                    val st = last?.end ?: (8 * 60)
                    segs.add(
                        SegUi(
                            start = st,
                            end = (st + 60) % 1440,
                            mirros = last?.mirros ?: "1"
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.add_segment))
            }

            // ======= סיכום יומי =======
            Card(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    stringResource(R.string.day_total) + ": " +
                        Calc.fmtHours(totalMin) + "  ·  " + Calc.fmtMoney(totalPay),
                    modifier = Modifier.padding(16.dp),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = primary
                )
            }

            // ======= כפתור שמור =======
            Button(
                onClick = { save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.save), fontSize = 16.sp)
            }

            // ======= כפתור מחק =======
            if (dayId != 0L) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 24.dp)
                ) {
                    Text(stringResource(R.string.delete), color = Color(0xFFD32F2F))
                }
            }
        }
    }

    // ======= דיאלוג אישור מחיקה =======
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = { Text(stringResource(R.string.confirm_delete)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDb.get(ctx).dao().deleteDay(dayId)
                        }
                        onClose()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
