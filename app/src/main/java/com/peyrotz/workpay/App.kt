package com.peyrotz.workpay

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

// ============================================================
// MainActivity — נקודת כניסה
// ============================================================

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { App() }
    }
}

// ============================================================
// Screens — ניווט
// ============================================================

sealed interface Screen {
    data object Home : Screen
    data object Settings : Screen
    data object Payments : Screen
    data object Yearly : Screen
    data object Graph : Screen
    data class Day(val id: Long, val date: String) : Screen
}

// ============================================================
// App — הרכיב הראשי
// ============================================================

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var colorIdx by remember { mutableStateOf(Prefs.colorIndex(ctx)) }
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    
    // חודש ברירת מחדל - היום
    var month by remember { mutableStateOf(YearMonth.now()) }
    
    var jobs by remember { mutableStateOf<List<JobEntity>>(emptyList()) }
    var jobsLoaded by remember { mutableStateOf(false) }
    var jobId by remember { mutableStateOf(Prefs.selectedJob(ctx)) }
    val primary = Color(Palette[colorIdx.coerceIn(0, Palette.lastIndex)])

    fun reloadJobs(select: Long? = null) {
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                val dao = AppDb.get(ctx).dao()
                var l = dao.jobs()
                if (l.isEmpty()) {
                    dao.insertJob(
                        JobEntity(
                            name = ctx.getString(R.string.default_job_name),
                            defaultRate = Prefs.rate(ctx)
                        )
                    )
                    l = dao.jobs()
                }
                l
            }
            jobs = list
            val want = select ?: jobId
            jobId = if (list.any { it.id == want }) want else list.first().id
            Prefs.setSelectedJob(ctx, jobId)
            jobsLoaded = true
        }
    }

    fun addJob(name: String, select: Boolean) {
        scope.launch {
            val newId = withContext(Dispatchers.IO) {
                AppDb.get(ctx).dao().insertJob(
                    JobEntity(name = name, defaultRate = Prefs.rate(ctx))
                )
            }
            reloadJobs(if (select) newId else null)
        }
    }

    LaunchedEffect(Unit) { reloadJobs() }

    BackHandler(enabled = screen !is Screen.Home) { screen = Screen.Home }

    AppTheme(primary) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            if (!jobsLoaded || jobs.isEmpty()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                )
            } else {
                val job = jobs.firstOrNull { it.id == jobId } ?: jobs.first()
                when (val s = screen) {
                    is Screen.Home -> HomeScreen(
                        jobs = jobs,
                        jobId = job.id,
                        month = month,
                        onMonth = { 
                            // 🆕 עדכון החודש מה-Pager
                            month = it 
                        },
                        onSelectJob = {
                            jobId = it
                            Prefs.setSelectedJob(ctx, it)
                            // כשמחליפים עבודה - חוזרים להיום
                            month = YearMonth.now()
                        },
                        onAddJob = { addJob(it, true) },
                        onAdd = {
                            val d = if (month == YearMonth.now()) LocalDate.now()
                            else month.atDay(1)
                            screen = Screen.Day(0L, d.toString())
                        },
                        onOpen = { screen = Screen.Day(it, LocalDate.now().toString()) },
                        onSettings = { screen = Screen.Settings },
                        onPayments = { screen = Screen.Payments },
                        onYearly = { screen = Screen.Yearly },
                        onGraph = { screen = Screen.Graph }
                    )

                    is Screen.Settings -> SettingsScreen(
                        jobs = jobs,
                        colorIdx = colorIdx,
                        onColor = {
                            colorIdx = it
                            Prefs.setColorIndex(ctx, it)
                        },
                        onSaveJob = { j ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDb.get(ctx).dao().updateJob(j)
                                }
                                reloadJobs()
                            }
                        },
                        onDeleteJob = { id ->
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    AppDb.get(ctx).dao().deleteJob(id)
                                }
                                reloadJobs()
                            }
                        },
                        onAddJob = { addJob(it, false) },
                        onClose = { screen = Screen.Home },
                        onRefresh = { reloadJobs() } 
                    )

                    is Screen.Payments -> PaymentsScreen(
                        jobId = job.id,
                        jobName = job.name,
                        onClose = { screen = Screen.Home }
                    )

                    is Screen.Yearly -> YearlyScreen(
                        jobId = job.id,
                        jobName = job.name,
                        onClose = { screen = Screen.Home },
                        onOpenMonth = { ym ->
                            month = ym
                            screen = Screen.Home
                        }
                    )

                    is Screen.Graph -> GraphScreen(
                        jobId = job.id,
                        month = month,
                        onMonth = { month = it },
                        onClose = { screen = Screen.Home }
                    )

                    is Screen.Day -> DayScreen(
                        dayId = s.id,
                        jobId = job.id,
                        defaultRate = job.defaultRate,
                        initialDate = s.date,
                        onClose = { screen = Screen.Home }
                    )
                }
            }
        }
    }
}

// ============================================================
// Theme — 8 ערכות צבע דינמיות
// ============================================================

private fun Context.findActivity(): Activity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

@Composable
fun AppTheme(primary: Color, content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = primary,
        onPrimary = Color.White,
        secondary = primary,
        onSecondary = Color.White,
        primaryContainer = lerp(Color.White, primary, 0.15f),
        onPrimaryContainer = primary,
        background = Color(0xFFFAFAFA),
        surface = Color(0xFFFAFAFA),
        surfaceVariant = Color.White
    )
    val view = LocalView.current
    SideEffect {
        val window = view.context.findActivity()?.window
        if (window != null) {
            window.statusBarColor = primary.toArgb()
            window.navigationBarColor = primary.toArgb()
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = false
        }
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

// ============================================================
// רכיבי UI משותפים
// ============================================================

@Composable
fun Line() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0x22000000))
    )
}

@Composable
fun RowScope.Cell(
    text: String,
    weight: Float,
    bold: Boolean = false,
    color: Color = Color.Unspecified,
    size: TextUnit = 15.sp
) {
    Text(
        text = text,
        modifier = Modifier.weight(weight),
        textAlign = TextAlign.Center,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        color = color,
        fontSize = size,
        maxLines = 1
    )
}

@Composable
fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = Color.White
            )
        }
        Text(
            title,
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
fun NameDialog(onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_job_title)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(R.string.job_name)) },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onOk(text.trim()) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
