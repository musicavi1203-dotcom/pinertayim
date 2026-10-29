package com.peyrotz.workpay

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class DialItem(val label: String, val icon: ImageVector, val onClick: () -> Unit)

// מספר דפים עצום כדי לאפשר גלילה "אינסופית" קדימה ואחורה
private const val INFINITE_PAGES = 1_000_000
private const val CENTER_PAGE = INFINITE_PAGES / 2

@Composable
fun HomeScreen(
    jobs: List<JobEntity>,
    jobId: Long,
    month: YearMonth, // המסך הראשי מחזיק את החודש (כדי לשמור עליו בין מסכים)
    onMonth: (YearMonth) -> Unit,
    onSelectJob: (Long) -> Unit,
    onAddJob: (String) -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
    onPayments: () -> Unit,
    onYearly: () -> Unit,
    onGraph: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val he = remember { Locale.forLanguageTag("he") }
    val primary = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var addJobDialog by remember { mutableStateOf(false) }

    // ===== Pager State =====
    val currentPage = remember { CENTER_PAGE }
    val pagerState = rememberPagerState(initialPage = CENTER_PAGE, pageCount = { INFINITE_PAGES })

    // כדי שהפסנתר יתחיל תמיד על החודש הנוכחי כשהמסך נפתח בפעם הראשונה
    LaunchedEffect(Unit) {
        pagerState.scrollToPage(CENTER_PAGE + month.toMonthsSinceEpoch())
    }

    // מזהה מתי ה-Pager מתחלף ומעדכן את החודש ב-State של App
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            val newMonth = YearMonth.from(
                YearMonth.of(2020, 1).plusMonths(page - CENTER_PAGE)
            )
            if (newMonth != month) {
                onMonth(newMonth)
            }
        }
    }

    val currentDisplayedMonth = YearMonth.of(2020, 1).plusMonths(pagerState.currentPage - CENTER_PAGE)
    val jobName = jobs.firstOrNull { it.id == jobId }?.name ?: ""
    val today = LocalDate.now()

    // מטמון נתונים לפי חודש (כדי לא לטעון מחדש כשמחליקים אחורה)
    val rowsCache = remember { mutableStateMapOf<YearMonth, List<DayRow>>() }

    fun exportPdf() {
        val rows = rowsCache[currentDisplayedMonth] ?: emptyList()
        if (rows.isEmpty()) {
            Toast.makeText(ctx, ctx.getString(R.string.err_no_days_export), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    PdfExport.create(ctx, jobName, currentDisplayedMonth, rows)
                }
                PdfExport.share(ctx, file)
            } catch (e: Exception) {
                Toast.makeText(ctx, ctx.getString(R.string.err_export), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            // ======= סרגל עליון =======
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(primary)
                    .padding(
                        top = statusBarPadding + 12.dp,
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 14.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    Row(
                        Modifier.clickable { menuOpen = true },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            jobName,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        jobs.forEach { j ->
                            DropdownMenuItem(
                                text = { Text(j.name) },
                                onClick = {
                                    menuOpen = false
                                    onSelectJob(j.id)
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_job)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                menuOpen = false
                                addJobDialog = true
                            }
                        )
                    }
                }
                Text(
                    "${today.dayOfMonth}/${today.monthValue}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // ======= שורת חודש =======
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(primary)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                    }
                }) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = Color.White)
                }
                Text(
                    currentDisplayedMonth.format(DateTimeFormatter.ofPattern("LLLL yyyy", he)),
                    color = Color.White,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                }) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null, tint = Color.White)
                }
            }

            // ======= כותרות טבלה =======
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.width(44.dp))
                Cell(stringResource(R.string.col_date), 1.1f, size = 16.sp)
                Cell(stringResource(R.string.col_start), 1f, size = 16.sp)
                Cell(stringResource(R.string.col_end), 1f, size = 16.sp)
                Cell(stringResource(R.string.col_hours), 1f, size = 16.sp)
                Cell(stringResource(R.string.col_pay), 1.2f, bold = true, size = 16.sp)
            }
            Line()

            // ======= ה-Pager שמכיל את כל החודשים =======
            Box(Modifier.weight(1f)) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    pageSpacing = 0.dp
                ) { page ->
                    // חישוב החודש של הדף הנוכחי
                    val pageMonth = YearMonth.of(2020, 1).plusMonths(page - CENTER_PAGE)
                    
                    // רשימה פנימית לדף הזה
                    var rows by remember(pageMonth) { mutableStateOf(rowsCache[pageMonth]) }
                    
                    // טעינת נתונים כשלא קיימים במטמון
                    LaunchedEffect(pageMonth, jobId) {
                        if (rows == null) {
                            rows = withContext(Dispatchers.IO) { loadMonth(ctx, jobId, pageMonth) }
                            rowsCache[pageMonth] = rows!!
                        }
                    }

                    val list = rows.orEmpty()
                    val totalMin = list.sumOf { it.minutes }
                    val totalPay = list.sumOf { it.pay }

                    Column(Modifier.fillMaxSize()) {
                        // רשימת הימים - החלקה חלקה
                        Box(Modifier.weight(1f)) {
                            if (rows == null) {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = primary)
                                }
                            } else if (list.isEmpty()) {
                                Text(
                                    stringResource(R.string.no_days),
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray
                                )
                            } else {
                                LazyColumn(
                                    Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 120.dp)
                                ) {
                                    items(list, key = { it.id }) { r ->
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable { onOpen(r.id) }
                                                .padding(horizontal = 8.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                                Box(
                                                    Modifier.size(34.dp).clip(CircleShape).background(primary),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(Calc.dayLetter(r.date.dayOfWeek), color = Color.White, fontSize = 14.sp)
                                                }
                                            }
                                            Cell("${r.date.dayOfMonth}/${r.date.monthValue}", 1.1f)
                                            Cell(Calc.fmtTime(r.startMin), 1f)
                                            Cell(Calc.fmtTime(r.endMin), 1f)
                                            Cell(Calc.fmtHours(r.minutes), 1f)
                                            Cell(Calc.fmtPay(r.pay), 1.2f, bold = true, size = 16.sp)
                                        }
                                        Line()
                                    }
                                }
                            }
                        }

                        // פס סיכום תחתון - ספציפי לדף הזה
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(primary)
                                .padding(horizontal = 12.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Cell(stringResource(R.string.days_count) + ": " + list.size, 1f, color = Color.White, size = 16.sp)
                            Cell(stringResource(R.string.hours_label) + ": " + Calc.fmtHours(totalMin), 1.2f, color = Color.White, size = 16.sp)
                            Cell(stringResource(R.string.pay_label) + ": ₪" + Calc.fmtPay(totalPay), 1.3f, bold = true, color = Color.White, size = 16.sp)
                        }
                    }
                }
            }
        }

        // רקע כהה לתפריט
        if (expanded) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0x66000000))
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { expanded = false }
            )
        }

        // FAB עם אנימציה
        val dialItems = listOf(
            DialItem(stringResource(R.string.add_day), Icons.Default.Add) { expanded = false; onAdd() },
            DialItem(stringResource(R.string.payments), Icons.Default.Payments) { expanded = false; onPayments() },
            DialItem(stringResource(R.string.yearly_summary), Icons.Default.CalendarMonth) { expanded = false; onYearly() },
            DialItem(stringResource(R.string.graph), Icons.Default.BarChart) { expanded = false; onGraph() },
            DialItem(stringResource(R.string.export_pdf), Icons.Default.Share) { expanded = false; exportPdf() },
            DialItem(stringResource(R.string.jump_today), Icons.Default.Home) {
                expanded = false
                val targetPage = CENTER_PAGE + YearMonth.now().toMonthsSinceEpoch()
                scope.launch { pagerState.animateScrollToPage(targetPage) }
            },
            DialItem(stringResource(R.string.settings), Icons.Default.Settings) { expanded = false; onSettings() }
        )

        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 76.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    dialItems.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(shape = RoundedCornerShape(8.dp), color = Color.White, shadowElevation = 2.dp) {
                                Text(item.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontSize = 14.sp)
                            }
                            Spacer(Modifier.width(10.dp))
                            SmallFloatingActionButton(onClick = item.onClick, containerColor = primary, contentColor = Color.White) {
                                Icon(item.icon, contentDescription = item.label)
                            }
                        }
                    }
                }
            }
            
            FloatingActionButton(onClick = { expanded = !expanded }, containerColor = primary, contentColor = Color.White) {
                Icon(if (expanded) Icons.Default.Close else Icons.Default.MoreVert, contentDescription = stringResource(R.string.more))
            }
        }
    }

    if (addJobDialog) {
        NameDialog(onDismiss = { addJobDialog = false }, onOk = { addJobDialog = false; onAddJob(it) })
    }
}

// פונקציית עזר: כמה חודשים עברו מ-2020-01
private fun YearMonth.toMonthsSinceEpoch(): Int {
    return (year - 2020) * 12 + (monthValue - 1)
}
