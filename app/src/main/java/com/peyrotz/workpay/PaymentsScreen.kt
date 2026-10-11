package com.peyrotz.workpay

import android.app.DatePickerDialog
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun PaymentsScreen(
    jobId: Long,
    jobName: String,
    onClose: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val he = remember { Locale.forLanguageTag("he") }
    val primary = MaterialTheme.colorScheme.primary

    var payments by remember { mutableStateOf<List<PaymentEntity>>(emptyList()) }
    var earnedTotal by remember { mutableStateOf(0.0) }
    var showAdd by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PaymentEntity?>(null) }

    suspend fun loadAll() {
        withContext(Dispatchers.IO) {
            val dao = AppDb.get(ctx).dao()
            val ps = dao.paymentsOfJob(jobId)
            val allDays = dao.allDaysOfJob(jobId)
            val byDay = if (allDays.isEmpty()) emptyMap()
            else dao.segmentsFor(allDays.map { it.id }).groupBy { it.dayId }
            val earned = allDays.sumOf { d ->
                val s = byDay[d.id].orEmpty()
                s.sumOf { Calc.pay(it, d.rate) }
            }
            payments = ps
            earnedTotal = earned
        }
    }

    LaunchedEffect(jobId) { loadAll() }

    val receivedTotal = payments.sumOf { it.amount }
    val balance = earnedTotal - receivedTotal

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                        stringResource(R.string.payments_title),
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

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {

                // ======= כרטיס סיכום =======
                Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(Modifier.padding(16.dp)) {
                        SummaryRow(
                            stringResource(R.string.earned_total),
                            "₪" + Calc.fmtPay(earnedTotal)
                        )
                        Spacer(Modifier.padding(top = 6.dp))
                        SummaryRow(
                            stringResource(R.string.received_total),
                            "₪" + Calc.fmtPay(receivedTotal)
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .background(Color.White.copy(alpha = 0.5f))
                                .padding(vertical = 1.dp)
                        )
                        Spacer(Modifier.padding(top = 8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.balance_total),
                                modifier = Modifier.weight(1f),
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                            Text(
                                if (balance <= 0.0)
                                    stringResource(R.string.balance_paid)
                                else "₪" + Calc.fmtPay(balance),
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (balance <= 0.0) Color(0xFF2E7D32) else primary
                            )
                        }
                    }
                }

                // ======= כפתור הוספה =======
                OutlinedButton(
                    onClick = { showAdd = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.add_payment))
                }

                // ======= רשימת תשלומים =======
                if (payments.isEmpty()) {
                    Text(
                        stringResource(R.string.no_payments),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        color = Color.Gray,
                        fontSize = 15.sp
                    )
                } else {
                    Spacer(Modifier.padding(top = 16.dp))
                    Text(
                        stringResource(R.string.payments_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    payments.forEach { p ->
                        PaymentRow(
                            payment = p,
                            onDelete = { pendingDelete = p },
                            he = he
                        )
                    }
                }

                Spacer(Modifier.padding(bottom = 80.dp))
            }
        }

        // ======= FAB להוספה =======
        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 24.dp),
            containerColor = primary,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_payment))
        }
    }

    // ======= דיאלוג הוספה =======
    if (showAdd) {
        AddPaymentDialog(
            onDismiss = { showAdd = false },
            onSave = { date, amount, note ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        AppDb.get(ctx).dao().insertPayment(
                            PaymentEntity(
                                jobId = jobId,
                                date = date,
                                amount = amount,
                                note = note
                            )
                        )
                    }
                    showAdd = false
                    loadAll()
                }
            }
        )
    }

    // ======= דיאלוג מחיקה =======
    val pd = pendingDelete
    if (pd != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            text = { Text(stringResource(R.string.confirm_delete_payment)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            AppDb.get(ctx).dao().deletePayment(pd.id)
                        }
                        pendingDelete = null
                        loadAll()
                    }
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

// ============================================================
// רכיבי משנה
// ============================================================

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontSize = 15.sp,
            color = Color.DarkGray
        )
        Text(
            value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PaymentRow(
    payment: PaymentEntity,
    onDelete: () -> Unit,
    he: Locale
) {
    Card(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        Color(0xFF2E7D32).copy(alpha = 0.15f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AttachMoney,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    "₪" + Calc.fmtPay(payment.amount),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    formatDateHe(payment.date, he) +
                        if (payment.note.isNotBlank()) "  ·  ${payment.note}" else "",
                    fontSize = 13.sp,
                    color = Color.Gray
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = Color(0xFFD32F2F)
                )
            }
        }
    }
}

private fun formatDateHe(isoDate: String, he: Locale): String {
    return try {
        LocalDate.parse(isoDate)
            .format(DateTimeFormatter.ofPattern("d בMMMM yyyy", he))
    } catch (e: Exception) {
        isoDate
    }
}

@Composable
private fun AddPaymentDialog(
    onDismiss: () -> Unit,
    onSave: (String, Double, String) -> Unit
) {
    val ctx = LocalContext.current
    val he = remember { Locale.forLanguageTag("he") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_payment)) },
        text = {
            Column {
                // תאריך
                Text(
                    stringResource(R.string.payment_date),
                    fontSize = 13.sp,
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        date.format(DateTimeFormatter.ofPattern("EEEE d/M/yyyy", he)),
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.padding(top = 8.dp))

                // סכום
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.payment_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.padding(top = 8.dp))

                // הערה
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text(stringResource(R.string.payment_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val amount = amountText.replace(',', '.').toDoubleOrNull()
                if (amount == null || amount <= 0) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.err_rate),
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    onSave(date.toString(), amount, noteText.trim())
                }
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
