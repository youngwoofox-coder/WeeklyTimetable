package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.timetable.data.*
import com.example.timetable.ui.WeeklyTimetable
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 시스템 다크 모드 설정을 따릅니다.
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) { TimetableScreen() }
        }
    }
}

@Composable
fun TimetableScreen(vm: ScheduleViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var weekEpoch by rememberSaveable { mutableLongStateOf(weekStart().toEpochDay()) }
    val week = LocalDate.ofEpochDay(weekEpoch)
    var editing by rememberSaveable { mutableStateOf(false) }
    var draftJson by rememberSaveable { mutableStateOf<String?>(null) }
    var listOpen by remember { mutableStateOf(false) }
    fun open(item: ScheduleItem) { vm.message.value = null; draftJson = Json.encodeToString(item) }
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("주간 계획", style = MaterialTheme.typography.headlineSmall)
                TextButton(enabled = !busy && !state.loading && state.error == null, onClick = {
                    open(ScheduleItem(subjectName = "", dayOfWeek = 1,
                        startHour = 9, startMinute = 0, endHour = 10, endMinute = 0, dateEpochDay = weekEpoch))
                }) { Text("+ 일정") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { weekEpoch -= 7 }) { Text("이전") }
                TextButton(onClick = { weekEpoch = weekStart().toEpochDay() }) {
                    val fmt = DateTimeFormatter.ofPattern("M.d")
                    Text("${week.format(fmt)}–${week.plusDays(6).format(fmt)} · 이번 주")
                }
                TextButton(onClick = { weekEpoch += 7 }) { Text("다음") }
            }
            Row {
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "드래그 편집 켜짐" else "드래그 편집") }
                TextButton(onClick = { listOpen = true }, enabled = !state.loading) { Text("일정 목록") }
            }
            Text(if (editing) "30분 단위 · 드래그 영역은 저장/취소 전까지 유지" else "일정을 눌러 수정 · 날짜별로 저장됩니다", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            when {
                state.loading -> CircularProgressIndicator()
                state.error != null -> Text(state.error!!)
                else -> WeeklyTimetable(state.items, week, editing && !busy, ::open, ::open, Modifier.weight(1f),
                    pendingDraft = draftJson?.let { Json.decodeFromString<ScheduleItem>(it) })
            }
        }
    }
    if (listOpen) AlertDialog(onDismissRequest = { listOpen = false }, title = { Text("이번 주 일정") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            state.items.filter { it.dateEpochDay?.let { d -> d in weekEpoch..(weekEpoch + 6) } == true }
                .sortedWith(compareBy({ it.dateEpochDay }, { it.startTotal })).forEach { item ->
                    TextButton(onClick = { listOpen = false; open(item) }) {
                        Text("${DAY_LABELS[item.dayOfWeek - 1]} ${item.subjectName} · ${displayTime(item.startTotal)}–${displayTime(item.endTotal)}")
                    }
                }
        } }, confirmButton = { TextButton(onClick = { listOpen = false }) { Text("닫기") } })
    draftJson?.let { raw ->
        val draft = Json.decodeFromString<ScheduleItem>(raw)
        val existing = state.items.any { it.id == draft.id }
        key(raw) {
            ScheduleEditor(draft, existing, state.items, busy, message,
                onDismiss = { draftJson = null },
                onSave = { vm.save(it) { draftJson = null } },
                onDelete = { all -> vm.delete(draft.id, all) { draftJson = null } })
        }
    }
}

@Composable
private fun ScheduleEditor(
    draft: ScheduleItem, existing: Boolean, items: List<ScheduleItem>, busy: Boolean, serverError: String?,
    onDismiss: () -> Unit, onSave: (List<ScheduleItem>) -> Unit, onDelete: (Boolean) -> Unit
) {
    var subject by rememberSaveable { mutableStateOf(draft.subjectName) }
    var date by rememberSaveable { mutableStateOf(LocalDate.ofEpochDay(draft.dateEpochDay!!).toString()) }
    var start by rememberSaveable { mutableStateOf("%02d:%02d".format(draft.startHour, draft.startMinute)) }
    var end by rememberSaveable { mutableStateOf("%02d:%02d".format(draft.endHour, draft.endMinute)) }
    var color by rememberSaveable { mutableStateOf(draft.colorHex) }
    var repeat by rememberSaveable { mutableStateOf(false) }
    var through by rememberSaveable { mutableStateOf(LocalDate.ofEpochDay(draft.dateEpochDay!!).plusMonths(3).toString()) }
    var dayMask by rememberSaveable { mutableIntStateOf(1 shl (draft.dayOfWeek - 1)) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteSeries by remember { mutableStateOf(false) }
    AlertDialog(properties = DialogProperties(dismissOnClickOutside = !busy, dismissOnBackPress = !busy),
        onDismissRequest = { if (!busy) onDismiss() }, title = { Text(if (existing) "일정 수정 · 이 날짜만" else "일정 추가") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(subject, { subject = it }, label = { Text("일정명") }, singleLine = true, enabled = !busy)
            OutlinedTextField(date, { date = it }, label = { Text(if (repeat) "시작일 YYYY-MM-DD" else "날짜 YYYY-MM-DD") }, singleLine = true, enabled = !busy)
            OutlinedTextField(start, { start = it }, label = { Text("시작 HH:mm (24시간제) · 00분/30분") }, singleLine = true, enabled = !busy)
            OutlinedTextField(end, { end = it }, label = { Text("종료 HH:mm (24시간제) · 00분/30분") }, singleLine = true, enabled = !busy)
            SCHEDULE_COLORS.chunked(5).forEachIndexed { row, hexes -> Row { hexes.forEachIndexed { col, hex ->
                TextButton(onClick = { color = hex }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)),
                        contentColor = androidx.compose.ui.graphics.Color(inkOn(hex)))) {
                    Text(if (color == hex) "✓" else "${row * 5 + col + 1}")
                }
            } } }
            if (!existing) {
                Row { Checkbox(checked = repeat, onCheckedChange = { repeat = it }, enabled = !busy); Text("기간 동안 반복") }
                if (repeat) {
                    OutlinedTextField(through, { through = it }, label = { Text("종료일 YYYY-MM-DD (포함)") }, singleLine = true, enabled = !busy)
                    Row { DAY_LABELS.forEachIndexed { i, label ->
                        TextButton(onClick = { dayMask = dayMask xor (1 shl i) }, enabled = !busy,
                            modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp)) {
                            Text(if (dayMask and (1 shl i) != 0) "[$label]" else label)
                        }
                    } }
                    val count = runCatching { expandRepeat(draft, LocalDate.parse(date), LocalDate.parse(through), (1..7).filter { dayMask and (1 shl (it - 1)) != 0 }.toSet()).size }.getOrNull()
                    Text(count?.let { "${it}개 날짜에 같은 시간으로 등록" } ?: "기간과 반복 요일을 확인하세요.")
                }
            } else if (draft.seriesId != null) Text("반복 일정 중 이 날짜만 변경합니다.")
            (error ?: serverError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (existing) TextButton(onClick = { confirmDelete = true }, enabled = !busy) { Text("일정 삭제") }
        } },
        confirmButton = { TextButton(enabled = !busy, onClick = {
            try {
                fun parse(value: String): Pair<Int, Int> {
                    require(Regex("""\d{2}:\d{2}""").matches(value)) { "시간을 HH:mm 형식으로 입력하세요." }
                    return value.take(2).toInt() to value.takeLast(2).toInt()
                }
                val s = parse(start); val e = parse(end); val selectedDate = LocalDate.parse(date)
                val template = draft.copy(subjectName = subject.trim(), dayOfWeek = selectedDate.dayOfWeek.value,
                    dateEpochDay = selectedDate.toEpochDay(), startHour = s.first, startMinute = s.second, endHour = e.first, endMinute = e.second, colorHex = color)
                val batch = if (repeat && !existing) expandRepeat(template, selectedDate, LocalDate.parse(through),
                    (1..7).filter { dayMask and (1 shl (it - 1)) != 0 }.toSet()) else listOf(template)
                validateBatch(items, batch)
                error = null; onSave(batch)
            } catch (e: Exception) { error = e.message ?: "입력값을 확인하세요." }
        }) { Text(if (busy) "저장 중…" else "저장") } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("취소") } })
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("일정을 삭제할까요?") },
        text = { if (draft.seriesId != null) Row { Checkbox(deleteSeries, { deleteSeries = it }); Text("같은 반복 일정 전체 삭제") } },
        confirmButton = { TextButton(enabled = !busy, onClick = { confirmDelete = false; onDelete(deleteSeries) }) { Text("삭제") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소") } })
}
