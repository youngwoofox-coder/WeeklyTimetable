# 날짜별 주간 계획 — 아이콘 적용 버전

README.md의 실행 절차를 확인하세요.

## 1. settings.gradle.kts

```kotlin
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "WeeklyTimetable"
include(":app")
```

## 2. build.gradle.kts

```kotlin
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
}
```

## 3. gradle.properties

```kotlin
android.useAndroidX=true
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
```

## 4. app/build.gradle.kts

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}
android {
    namespace = "com.example.timetable"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.example.timetable"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    testImplementation("junit:junit:4.13.2")
}
```

## 5. app/src/main/java/com/example/timetable/data/ScheduleItem.kt

```kotlin
package com.example.timetable.data

import kotlinx.serialization.Serializable
import java.util.UUID
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

const val SLOT_MINUTES = 30
const val START_MINUTE = 9 * 60
const val END_MINUTE = 18 * 60
const val TOTAL_MINUTES = END_MINUTE - START_MINUTE
val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
val PASTELS = listOf("#F8D8D8", "#D8E9FA", "#DCEEDC", "#E8DDF5", "#F8E8BE")

@Serializable
data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val subjectName: String,
    val room: String,
    val dayOfWeek: Int, // ISO: 월=1, 일=7
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val colorHex: String = PASTELS.first(),
    val dateEpochDay: Long? = null, // null은 구버전 마이그레이션 전 데이터만 허용
    val seriesId: String? = null
) {
    val startTotal: Int get() = startHour * 60 + startMinute
    val endTotal: Int get() = endHour * 60 + endMinute
    val duration: Int get() = endTotal - startTotal
}

fun validationError(item: ScheduleItem, others: List<ScheduleItem>): String? = when {
    item.subjectName.isBlank() -> "과목명을 입력하세요."
    item.dateEpochDay == null -> "날짜를 선택하세요."
    item.dayOfWeek !in 1..7 -> "요일을 확인하세요."
    LocalDate.ofEpochDay(item.dateEpochDay).dayOfWeek.value != item.dayOfWeek -> "날짜와 요일이 일치하지 않습니다."
    item.startHour !in 0..23 || item.endHour !in 0..23 ||
        item.startMinute !in 0..59 || item.endMinute !in 0..59 -> "시간 형식이 올바르지 않습니다."
    item.startMinute % SLOT_MINUTES != 0 || item.endMinute % SLOT_MINUTES != 0 -> "시간은 30분 단위(00분·30분)로 입력하세요."
    item.startTotal < START_MINUTE || item.endTotal > END_MINUTE -> "09:00~18:00 안에서 입력하세요."
    item.startTotal >= item.endTotal -> "종료 시간은 시작 시간보다 늦어야 합니다."
    !Regex("#[0-9A-Fa-f]{6}").matches(item.colorHex) -> "색상 형식은 #RRGGBB입니다."
    others.any { it.id != item.id && it.dateEpochDay == item.dateEpochDay &&
        item.startTotal < it.endTotal && it.startTotal < item.endTotal } -> "같은 날짜의 다른 일정과 시간이 겹칩니다."
    else -> null
}

fun weekStart(date: LocalDate = LocalDate.now()): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

val SAMPLE_ITEMS get() = listOf(
    ScheduleItem("sample-1", "자료구조", "공학관 301", 1, 9, 30, 11, 0, PASTELS[0]),
    ScheduleItem("sample-2", "디자인", "예술관 202", 2, 13, 0, 15, 0, PASTELS[1]),
    ScheduleItem("sample-3", "영어회화", "인문관 105", 3, 10, 0, 11, 30, PASTELS[2]),
    ScheduleItem("sample-4", "앱 개발", "공학관 401", 4, 14, 0, 16, 0, PASTELS[3]),
    ScheduleItem("sample-5", "프로젝트", "도서관 2층", 5, 16, 30, 18, 0, PASTELS[4])
).map { it.copy(dateEpochDay = weekStart().plusDays((it.dayOfWeek - 1).toLong()).toEpochDay()) }

/** 시작일·종료일 포함. 선택 요일마다 독립 occurrence를 생성하여 날짜별 예외 편집 가능. */
fun expandRepeat(template: ScheduleItem, from: LocalDate, through: LocalDate, weekdays: Set<Int>): List<ScheduleItem> {
    require(!through.isBefore(from)) { "종료일은 시작일 이후여야 합니다." }
    require(through.toEpochDay() - from.toEpochDay() <= 730) { "한 번에 최대 2년 범위로 등록하세요." }
    require(weekdays.isNotEmpty() && weekdays.all { it in 1..7 }) { "반복 요일을 선택하세요." }
    val series = UUID.randomUUID().toString()
    return generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(through) }
        .filter { it.dayOfWeek.value in weekdays }.map { date ->
            template.copy(id = UUID.randomUUID().toString(), dayOfWeek = date.dayOfWeek.value,
                dateEpochDay = date.toEpochDay(), seriesId = series)
        }.toList().also { require(it.isNotEmpty()) { "기간 안에 선택한 요일이 없습니다." } }
}

fun validateBatch(old: List<ScheduleItem>, batch: List<ScheduleItem>) {
    require(batch.map { it.id }.distinct().size == batch.size) { "중복 ID입니다." }
    val ids = batch.map { it.id }.toSet()
    val merged = old.filterNot { it.id in ids } + batch
    batch.forEach { item -> validationError(item, merged)?.let { error ->
        throw IllegalArgumentException("${item.dateEpochDay?.let { LocalDate.ofEpochDay(it) }}: $error")
    } }
}
```

## 6. app/src/main/java/com/example/timetable/ui/WeeklyTimetable.kt

```kotlin
package com.example.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timetable.data.*
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

@Composable
fun WeeklyTimetable(
    items: List<ScheduleItem>, week: LocalDate, editing: Boolean,
    onSelect: (ScheduleItem) -> Unit, onDraft: (ScheduleItem) -> Unit,
    modifier: Modifier = Modifier, pendingDraft: ScheduleItem? = null
) {
    var ghost by remember(week, editing) { mutableStateOf<ScheduleItem?>(null) }
    val shown = items.filter { it.dateEpochDay?.let { d -> d in week.toEpochDay()..week.plusDays(6).toEpochDay() } == true }
    val density = LocalDensity.current
    BoxWithConstraints(modifier.background(Color.White)) {
        val axisWidth = 40.dp
        val head = 40.dp
        val height = (maxHeight - head - 18.dp).coerceAtLeast(90.dp)
        val dayWidth = (maxWidth - axisWidth) / 7
        val gridPx = with(density) { height.toPx() }
        val axisPx = with(density) { axisWidth.toPx() }
        val dayPx = with(density) { dayWidth.toPx() }
        fun minute(y: Float) = (START_MINUTE + floor(y / gridPx * TOTAL_MINUTES / SLOT_MINUTES).toInt() * SLOT_MINUTES)
            .coerceIn(START_MINUTE, END_MINUTE - SLOT_MINUTES)
        fun date(x: Float) = week.plusDays(floor((x - axisPx) / dayPx).toLong().coerceIn(0, 6))
        Column {
            Row(Modifier.fillMaxWidth().height(head)) {
                Spacer(Modifier.width(axisWidth))
                DAY_LABELS.forEachIndexed { i, label ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(label, fontSize = 11.sp, color = Color.DarkGray)
                        Text("${week.plusDays(i.toLong()).dayOfMonth}", fontSize = 12.sp, color = Color.DarkGray)
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(height + 18.dp).pointerInput(editing, week, shown, gridPx, dayPx) {
                if (!editing) return@pointerInput
                var anchor = START_MINUTE
                var anchorDate = week
                var original: ScheduleItem? = null
                var resize = false
                var grab = 0
                detectDragGestures(
                    onDragStart = { pos ->
                        if (pos.x >= axisPx && pos.y in 0f..gridPx) {
                            anchor = minute(pos.y); anchorDate = date(pos.x)
                            original = shown.find { it.dateEpochDay == anchorDate.toEpochDay() && anchor in it.startTotal until it.endTotal }
                            grab = anchor - (original?.startTotal ?: anchor)
                            resize = original?.let { pos.y >= (it.endTotal - START_MINUTE) / TOTAL_MINUTES.toFloat() * gridPx - 12.dp.toPx() } ?: false
                            ghost = original ?: ScheduleItem(subjectName = "새 일정", room = "", dayOfWeek = anchorDate.dayOfWeek.value,
                                startHour = anchor / 60, startMinute = anchor % 60, endHour = (anchor + SLOT_MINUTES) / 60,
                                endMinute = (anchor + SLOT_MINUTES) % 60, dateEpochDay = anchorDate.toEpochDay())
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val current = ghost
                        if (current != null) {
                            val m = minute(change.position.y)
                            val old = original
                            val start: Int
                            val end: Int
                            val target: LocalDate
                            if (old == null) {
                                start = minOf(anchor, m); end = maxOf(anchor, m) + SLOT_MINUTES; target = anchorDate
                            } else if (resize) {
                                start = old.startTotal
                                end = (START_MINUTE + (change.position.y / gridPx * TOTAL_MINUTES / SLOT_MINUTES).roundToInt() * SLOT_MINUTES)
                                    .coerceIn(minOf(start + SLOT_MINUTES, END_MINUTE), END_MINUTE)
                                target = LocalDate.ofEpochDay(old.dateEpochDay!!)
                            } else {
                                start = (m - grab).coerceIn(START_MINUTE, END_MINUTE - old.duration)
                                end = start + old.duration; target = date(change.position.x)
                            }
                            ghost = current.copy(dateEpochDay = target.toEpochDay(), dayOfWeek = target.dayOfWeek.value,
                                startHour = start / 60, startMinute = start % 60, endHour = end / 60, endMinute = end % 60)
                        }
                    },
                    onDragEnd = { ghost?.let(onDraft); ghost = null },
                    onDragCancel = { ghost = null }
                )
            }) {
                Canvas(Modifier.offset(x = axisWidth).width(maxWidth - axisWidth).height(height)) {
                    repeat(19) { i ->
                        val y = size.height * i / 18
                        drawLine(if (i % 2 == 0) Color(0xFFDDE1E5) else Color(0xFFF1F2F4), Offset(0f, y), Offset(size.width, y))
                    }
                    repeat(8) { i ->
                        val x = size.width * i / 7
                        drawLine(Color(0xFFE7E9ED), Offset(x, 0f), Offset(x, size.height))
                    }
                }
                (9..18).forEach { h -> Text("%02d:00".format(h), fontSize = 10.sp, color = Color.Gray,
                    modifier = Modifier.offset(y = height * ((h - 9) / 9f)).width(axisWidth)) }
                val activeDraft = ghost ?: pendingDraft
                val render = shown.filterNot { it.id == activeDraft?.id } + listOfNotNull(activeDraft)
                render.forEach { item ->
                    Column(Modifier.offset(x = axisWidth + dayWidth * (item.dateEpochDay!! - week.toEpochDay()).toInt(),
                        y = height * ((item.startTotal - START_MINUTE) / TOTAL_MINUTES.toFloat()))
                        .width(dayWidth).height(height * (item.duration / TOTAL_MINUTES.toFloat()))
                        .padding(1.dp).background(Color(android.graphics.Color.parseColor(item.colorHex)))
                        .then(if (item.id == activeDraft?.id) Modifier.border(2.dp, Color(0xFF526854)) else Modifier)
                        .then(if (!editing) Modifier.clickable { onSelect(item) } else Modifier).padding(3.dp)) {
                        Text(item.subjectName, color = Color(0xFF263238), fontSize = 11.sp, lineHeight = 13.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(item.room, color = Color(0xFF263238), fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 700)
@Composable
private fun TimetablePreview() { WeeklyTimetable(SAMPLE_ITEMS, weekStart(), false, {}, {}, Modifier.fillMaxSize()) }
```

## 7. app/src/main/java/com/example/timetable/data/ScheduleRepository.kt

```kotlin
package com.example.timetable.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.scheduleStore by preferencesDataStore("weekly_schedule")
class ScheduleRepository(context: Context) {
    private val store = context.applicationContext.scheduleStore
    private val key = stringPreferencesKey("items_json")
    private val json = Json { ignoreUnknownKeys = true }
    private fun decode(raw: String?): List<ScheduleItem> = if (raw == null) SAMPLE_ITEMS else json.decodeFromString(raw)
    private suspend fun initialize() {
        store.edit { prefs ->
            val raw = prefs[key]
            val old = decode(raw)
            if (raw == null || old.any { it.dateEpochDay == null }) {
                // 날짜가 없던 기존 주간표는 최초 마이그레이션 시의 주에 한 번만 배치합니다.
                prefs[key] = json.encodeToString(old.map { item ->
                    if (item.dateEpochDay != null) item else item.copy(
                        dateEpochDay = weekStart().plusDays((item.dayOfWeek - 1).toLong()).toEpochDay())
                })
            }
        }
    }
    val items: Flow<List<ScheduleItem>> = flow {
        initialize()
        emitAll(store.data.map { decode(it[key]) })
    }
    suspend fun upsertBatch(batch: List<ScheduleItem>) {
        initialize()
        store.edit { prefs ->
            val old = decode(prefs[key])
            validateBatch(old, batch) // 한 건이라도 충돌하면 전체 저장을 취소합니다.
            val ids = batch.map { it.id }.toSet()
            prefs[key] = json.encodeToString(old.filterNot { it.id in ids } + batch)
        }
    }
    suspend fun delete(id: String, entireSeries: Boolean = false) {
        initialize()
        store.edit { prefs ->
            val old = decode(prefs[key]); val series = old.find { it.id == id }?.seriesId
            prefs[key] = json.encodeToString(old.filterNot {
                it.id == id || (entireSeries && series != null && it.seriesId == series)
            })
        }
    }
}
```

## 8. app/src/main/java/com/example/timetable/ScheduleViewModel.kt

```kotlin
package com.example.timetable

import android.app.Application
import androidx.glance.appwidget.updateAll
import com.example.timetable.widget.TimetableWidget
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.timetable.data.*
import com.example.timetable.widget.WidgetRefreshWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ScheduleState(val items: List<ScheduleItem> = emptyList(), val loading: Boolean = true, val error: String? = null)
class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val repository = ScheduleRepository(app)
    val state = repository.items.map { ScheduleState(it, loading = false) }
        .catch { emit(ScheduleState(loading = false, error = "시간표를 읽지 못했습니다. 앱을 다시 실행해 주세요.")) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ScheduleState())
    var busy = MutableStateFlow(false)
        private set
    val message = MutableStateFlow<String?>(null)

    fun save(items: List<ScheduleItem>, done: () -> Unit) = mutate({ repository.upsertBatch(items) }, done)
    fun delete(id: String, entireSeries: Boolean, done: () -> Unit) = mutate({ repository.delete(id, entireSeries) }, done)
    private fun mutate(write: suspend () -> Unit, done: () -> Unit) {
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            try {
                write()
                // 디스크 저장 후 갱신 작업 등록. ViewModel 종료 뒤에도 Worker가 실행됩니다.
                WidgetRefreshWorker.enqueue(getApplication())
                message.value = null
                done()
                // 대기 없는 직접 갱신도 요청합니다. 실패해도 저장 성공은 유지됩니다.
                try { TimetableWidget().updateAll(getApplication()) }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { /* 이미 등록된 Worker에서 재시도 */ }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { message.value = e.message ?: "저장에 실패했습니다."
            } finally { busy.value = false }
        }
    }
}
```

## 9. app/src/main/java/com/example/timetable/MainActivity.kt

```kotlin
package com.example.timetable

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
        setContent { MaterialTheme { TimetableScreen() } }
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
                    open(ScheduleItem(subjectName = "", room = "", dayOfWeek = 1,
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
                        Text("${DAY_LABELS[item.dayOfWeek - 1]} ${item.subjectName} · ${item.room}")
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
    var room by rememberSaveable { mutableStateOf(draft.room) }
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
            OutlinedTextField(room, { room = it }, label = { Text("장소 / 메모") }, singleLine = true, enabled = !busy)
            OutlinedTextField(date, { date = it }, label = { Text(if (repeat) "시작일 YYYY-MM-DD" else "날짜 YYYY-MM-DD") }, singleLine = true, enabled = !busy)
            OutlinedTextField(start, { start = it }, label = { Text("시작 HH:mm · 00분/30분") }, singleLine = true, enabled = !busy)
            OutlinedTextField(end, { end = it }, label = { Text("종료 HH:mm · 00분/30분") }, singleLine = true, enabled = !busy)
            Row { PASTELS.forEachIndexed { index, hex ->
                TextButton(onClick = { color = hex }, enabled = !busy, modifier = Modifier.weight(1f), contentPadding = PaddingValues(0.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = androidx.compose.ui.graphics.Color(android.graphics.Color.parseColor(hex)))) {
                    Text(if (color == hex) "✓" else "${index + 1}")
                }
            } }
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
                val template = draft.copy(subjectName = subject.trim(), room = room.trim(), dayOfWeek = selectedDate.dayOfWeek.value,
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
```

## 10. app/src/main/java/com/example/timetable/widget/TimetableWidget.kt

```kotlin
package com.example.timetable.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.timetable.MainActivity
import com.example.timetable.data.*
import kotlinx.coroutines.flow.first

class TimetableWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = ScheduleRepository(context)
        val initial = repository.items.first()
        provideContent {
            val items by repository.items.collectAsState(initial)
            WidgetTable(items)
        }
    }
}
class TimetableReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimetableWidget()
}

@Composable
private fun WidgetTable(items: List<ScheduleItem>) {
    // 8dp padding 양쪽 + 24dp 요일 헤더 + 16dp 종료시각 footer
    val week = weekStart()
    val currentItems = items.filter { it.dateEpochDay?.let { d -> d in week.toEpochDay()..week.plusDays(6).toEpochDay() } == true }
    val gridHeight = (LocalSize.current.height - 76.dp).coerceAtLeast(1.dp)
    val minuteHeight = gridHeight / TOTAL_MINUTES.toFloat()
    val textStyle = TextStyle(color = ColorProvider(Color(0xFF263238)), fontSize = 10.sp)
    Column(GlanceModifier.fillMaxSize().background(Color.White).padding(8.dp)
        .clickable(actionStartActivity<MainActivity>())) {
        Text("${week.monthValue}.${week.dayOfMonth}–${week.plusDays(6).monthValue}.${week.plusDays(6).dayOfMonth}", style = textStyle, modifier = GlanceModifier.height(20.dp))
        Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(GlanceModifier.width(34.dp))
            DAY_LABELS.forEach { day ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) { Text(day, style = textStyle) }
            }
        }
        Row(GlanceModifier.fillMaxWidth().height(gridHeight)) {
            Column(GlanceModifier.width(34.dp)) {
                (9..17).forEach { hour ->
                    Box(GlanceModifier.height(gridHeight / 9)) { Text("%02d".format(hour), style = textStyle) }
                }
            }
            (1..7).forEach { day ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    // 시간 격자 배경. 분 단위 수업은 그 위에 연속 구간으로 배치합니다.
                    Column(GlanceModifier.fillMaxSize()) {
                        repeat(9) {
                            Box(GlanceModifier.fillMaxWidth().height(gridHeight / 9)
                                .background(Color(0xFFE9ECF0)).padding(top = 1.dp, start = 1.dp)) {
                                Box(GlanceModifier.fillMaxSize().background(Color.White)) {}
                            }
                        }
                    }
                    Column(GlanceModifier.fillMaxSize()) {
                        DaySegments(buildSegments(currentItems.filter { it.dayOfWeek == day }), minuteHeight, textStyle)
                    }
                }
            }
        }
        Text("18:00", style = textStyle, modifier = GlanceModifier.height(16.dp))
    }
}

private data class Segment(val minutes: Int, val item: ScheduleItem? = null)
private fun buildSegments(items: List<ScheduleItem>): List<Segment> = buildList {
    var cursor = START_MINUTE
    items.sortedBy { it.startTotal }.forEach { item ->
        if (item.startTotal > cursor) add(Segment(item.startTotal - cursor))
        add(Segment(item.duration, item))
        cursor = item.endTotal
    }
    if (cursor < END_MINUTE) add(Segment(END_MINUTE - cursor))
}

@Composable
private fun DaySegments(segments: List<Segment>, minuteHeight: androidx.compose.ui.unit.Dp, style: TextStyle) {
    // RemoteViews의 단일 컨테이너 자식 수 제한을 피하도록 분할합니다.
    if (segments.size > 8) {
        val split = segments.size / 2
        Column(GlanceModifier.fillMaxWidth()) { DaySegments(segments.take(split), minuteHeight, style) }
        Column(GlanceModifier.fillMaxWidth()) { DaySegments(segments.drop(split), minuteHeight, style) }
    } else segments.forEach { segment ->
        val item = segment.item
        if (item == null) Spacer(GlanceModifier.height(minuteHeight * segment.minutes))
        else Column(GlanceModifier.fillMaxWidth().height(minuteHeight * segment.minutes)
            .padding(1.dp).background(Color(android.graphics.Color.parseColor(item.colorHex)))) {
            Text(item.subjectName, style = TextStyle(color = ColorProvider(Color(0xFF263238)), fontSize = 10.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(horizontal = 2.dp), maxLines = 2)
            Text(item.room, style = style, modifier = GlanceModifier.padding(horizontal = 2.dp), maxLines = 2)
        }
    }
}
```

## 11. app/src/main/java/com/example/timetable/widget/WidgetRefreshWorker.kt

```kotlin
package com.example.timetable.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.work.*
import kotlinx.coroutines.CancellationException

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        TimetableWidget().updateAll(applicationContext)
        Result.success()
    } catch (e: CancellationException) { throw e
    } catch (_: Exception) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "refresh-weekly-timetable", ExistingWorkPolicy.APPEND_OR_REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>().build()
            )
        }
    }
}
```

## 12. app/src/main/AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:icon="@mipmap/ic_launcher" android:roundIcon="@mipmap/ic_launcher" android:label="주간 시간표" android:theme="@android:style/Theme.Material.Light.NoActionBar" android:allowBackup="false" android:supportsRtl="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <receiver android:name=".widget.TimetableReceiver" android:exported="false">
            <intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter>
            <meta-data android:name="android.appwidget.provider" android:resource="@xml/timetable_widget_info" />
        </receiver>
    </application>
</manifest>
```

## 13. app/src/main/res/xml/timetable_widget_info.xml

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="250dp" android:minHeight="250dp"
    android:minResizeWidth="250dp" android:minResizeHeight="250dp"
    android:targetCellWidth="4" android:targetCellHeight="4"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/glance_default_loading_layout"
    android:widgetCategory="home_screen" />
```

## 14. app/src/test/java/com/example/timetable/ScheduleTest.kt

```kotlin
package com.example.timetable
import com.example.timetable.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private fun lesson(startH: Int, startM: Int, endH: Int, endM: Int, id: String = "new") =
        ScheduleItem(id, "수학", "101", 1, startH, startM, endH, endM, dateEpochDay = LocalDate.parse("2026-10-12").toEpochDay())
    @Test fun acceptsEndBoundary() { assertNull(validationError(lesson(17, 30, 18, 0), emptyList())) }
    @Test fun rejectsOutsideWindow() { assertNotNull(validationError(lesson(8, 59, 10, 0), emptyList())) }
    @Test fun rejectsOverlapButAllowsTouching() {
        val old = listOf(lesson(9, 30, 11, 0, "old"))
        assertNotNull(validationError(lesson(10, 30, 11, 30), old))
        assertNull(validationError(lesson(11, 0, 12, 0), old))
    }
    @Test fun editingItselfIsAllowed() {
        val item = lesson(9, 0, 10, 0)
        assertNull(validationError(item.copy(room = "102"), listOf(item)))
    }
    @Test fun rejectsReverseAndInvalidMinutes() {
        assertNotNull(validationError(lesson(11, 0, 10, 0), emptyList()))
        assertNotNull(validationError(lesson(9, 60, 11, 0), emptyList()))
    }

    @Test fun repeatIncludesBoundsAndSelectedWeekdays() {
        val result = expandRepeat(lesson(10, 0, 11, 0), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-23"), setOf(1, 3))
        assertEquals(4, result.size)
        assertEquals(listOf("2026-10-12", "2026-10-14", "2026-10-19", "2026-10-21"), result.map { LocalDate.ofEpochDay(it.dateEpochDay!!).toString() })
        assertEquals(1, result.map { it.seriesId }.distinct().size)
        assertEquals(4, result.map { it.id }.distinct().size)
    }
    @Test fun sameTimeOnDifferentDatesIsAllowed() {
        val first = lesson(9, 0, 10, 0, "first")
        val second = first.copy(id = "second", dateEpochDay = first.dateEpochDay!! + 7)
        validateBatch(listOf(first), listOf(second))
    }
    @Test(expected = IllegalArgumentException::class) fun batchRejectsConflictsOnAnyDate() {
        val repeated = expandRepeat(lesson(9, 0, 10, 0), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19"), setOf(1))
        validateBatch(listOf(repeated.last().copy(id = "existing")), repeated)
    }
    @Test fun sameDayRepeatIncludesEndDate() {
        val date = LocalDate.parse("2026-10-12")
        assertEquals(1, expandRepeat(lesson(9, 0, 10, 0), date, date, setOf(1)).size)
    }
    @Test(expected = IllegalArgumentException::class) fun repeatRejectsEmptyDays() {
        expandRepeat(lesson(9, 0, 10, 0), LocalDate.parse("2026-10-12"), LocalDate.parse("2026-10-19"), emptySet())
    }

    @Test fun rejectsNonHalfHourTimes() {
        assertNotNull(validationError(lesson(9, 15, 10, 0), emptyList()))
        assertNotNull(validationError(lesson(9, 0, 10, 45), emptyList()))
        assertNull(validationError(lesson(9, 30, 10, 0), emptyList()))
    }
}
```

## 15. app/src/main/res/values/icon_colors.xml

```xml
<resources>
    <color name="ic_launcher_background">#F7F3E9</color>
</resources>
```

## 16. app/src/main/res/drawable/ic_launcher_foreground.xml

```xml
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetLeft="14dp" android:insetTop="14dp"
    android:insetRight="14dp" android:insetBottom="14dp">
    <bitmap android:src="@drawable/ic_launcher_art" android:gravity="fill" android:filter="true" />
</inset>
```

## 17. app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```
