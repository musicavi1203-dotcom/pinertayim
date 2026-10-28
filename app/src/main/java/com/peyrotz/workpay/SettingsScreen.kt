package com.peyrotz.workpay

import android.app.TimePickerDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    jobs: List<JobEntity>,
    colorIdx: Int,
    onColor: (Int) -> Unit,
    onSaveJob: (JobEntity) -> Unit,
    onDeleteJob: (Long) -> Unit,
    onAddJob: (String) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit // 🆕 פרמטר לרענון אחרי שחזור
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<JobEntity?>(null) }
    var deleting by remember { mutableStateOf<JobEntity?>(null) }
    var addDialog by remember { mutableStateOf(false) }

    // Reminder state
    var reminderOn by remember { mutableStateOf(Prefs.reminderOn(ctx)) }
    var reminderHour by remember { mutableStateOf(Prefs.reminderHour(ctx)) }
    var reminderMinute by remember { mutableStateOf(Prefs.reminderMinute(ctx)) }

    // 🆕 Launchers לגיבוי ושחזור
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = BackupManager.export(ctx, uri)
                if (ok) Toast.makeText(ctx, "הגיבוי נשמר בהצלחה!", Toast.LENGTH_LONG).show()
                else Toast.makeText(ctx, "שגיאה בשמירת הגיבוי", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val ok = BackupManager.import(ctx, uri)
                if (ok) {
                    Toast.makeText(ctx, "הנתונים שוחזרו בהצלחה!", Toast.LENGTH_LONG).show()
                    onRefresh()
                } else {
                    Toast.makeText(ctx, "שגיאה בשחזור. ייתכן והקובץ פגום.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

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
                stringResource(R.string.settings_title),
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
            // ======= מקומות עבודה =======
            Text(
                stringResource(R.string.jobs_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            jobs.forEach { j ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { editing = j },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            j.name,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "₪" + Calc.fmtPay(j.defaultRate) + " " +
                                stringResource(R.string.per_hour),
                            color = Color.Gray
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { addDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.add_job))
            }

            // ======= 🆕 גיבוי ושחזור =======
            Text(
                "גיבוי ושחזור נתונים",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
            )

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "שמור את כל הנתונים בקובץ אחד, או שחזר מקובץ קיים.",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Button(
                        onClick = {
                            val date = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.US).format(java.util.Date())
                            exportLauncher.launch("pinertayim_backup_$date.json")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ייצוא גיבוי (שמירה)")
                    }
                    
                    Spacer(Modifier.padding(top = 8.dp))
                    
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(arrayOf("application/json"))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("ייבוא גיבוי (שחזור)")
                    }
                }
            }

            // ======= תזכורת יומית =======
            Text(
                stringResource(R.string.reminder_label),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
            )

            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.reminder_desc),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                if (reminderOn) stringResource(R.string.reminder_on)
                                else stringResource(R.string.reminder_off),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = reminderOn,
                            onCheckedChange = { checked ->
                                reminderOn = checked
                                Prefs.setReminderOn(ctx, checked)
                                if (checked) Reminder.schedule(ctx) else Reminder.cancel(ctx)
                            }
                        )
                    }

                    if (reminderOn) {
                        Spacer(Modifier.padding(top = 12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.reminder_time),
                                modifier = Modifier.weight(1f),
                                fontSize = 15.sp
                            )
                            OutlinedButton(
                                onClick = {
                                    TimePickerDialog(
                                        ctx,
                                        { _, h, m ->
                                            reminderHour = h
                                            reminderMinute = m
                                            Prefs.setReminderHour(ctx, h)
                                            Prefs.setReminderMinute(ctx, m)
                                            Reminder.schedule(ctx)
                                        },
                                        reminderHour,
                                        reminderMinute,
                                        true
                                    ).show()
                                }
                            ) {
                                Text(
                                    String.format(
                                        java.util.Locale.US,
                                        "%02d:%02d",
                                        reminderHour,
                                        reminderMinute
                                    ),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // ======= צבע האפליקציה =======
            Text(
                stringResource(R.string.theme_color),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 28.dp, bottom = 8.dp)
            )

            Palette.chunked(4).forEachIndexed { row, colors ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    colors.forEachIndexed { col, c ->
                        val index = row * 4 + col
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .then(
                                    if (index == colorIdx)
                                        Modifier.border(3.dp, Color.Black, CircleShape)
                                    else Modifier
                                )
                                .clickable { onColor(index) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (index == colorIdx) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }

    // ======= דיאלוג עריכת עבודה =======
    val ej = editing
    if (ej != null) {
        var nameText by remember(ej.id) { mutableStateOf(ej.name) }
        var rateText by remember(ej.id) {
            mutableStateOf(Calc.fmtRateInput(ej.defaultRate))
        }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(stringResource(R.string.edit_job)) },
            text = {
                Column {
                    OutlinedTextField(
                        value = nameText,
                        onValueChange = { nameText = it },
                        label = { Text(stringResource(R.string.job_name)) },
                        singleLine = true
                    )
                    Spacer(Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text(stringResource(R.string.job_rate)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        )
                    )
                    if (jobs.size > 1) {
                        TextButton(onClick = {
                            deleting = ej
                            editing = null
                        }) {
                            Text(
                                stringResource(R.string.delete),
                                color = Color(0xFFD32F2F)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val r = rateText.replace(',', '.').toDoubleOrNull()
                    if (nameText.isBlank()) {
                        Toast.makeText(ctx, ctx.getString(R.string.err_job_name), Toast.LENGTH_SHORT).show()
                    } else if (r == null || r < 0) {
                        Toast.makeText(ctx, ctx.getString(R.string.err_rate), Toast.LENGTH_SHORT).show()
                    } else {
                        onSaveJob(ej.copy(name = nameText.trim(), defaultRate = r))
                        editing = null
                    }
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ======= דיאלוג מחיקת עבודה =======
    val dj = deleting
    if (dj != null) {
        AlertDialog(
            onDismissRequest = { deleting = null },
            text = { Text(stringResource(R.string.confirm_delete_job)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteJob(dj.id)
                    deleting = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    // ======= דיאלוג הוספת עבודה =======
    if (addDialog) {
        NameDialog(
            onDismiss = { addDialog = false },
            onOk = {
                addDialog = false
                onAddJob(it)
            }
        )
    }
}
