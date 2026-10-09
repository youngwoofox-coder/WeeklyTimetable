package com.example.timetable.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.*
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.color.ColorProvider as DayNightColor
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

// 위젯은 기기 다크 모드 설정에 따라 낮/밤 색상을 자동으로 사용합니다.
private val BG = DayNightColor(day = Color.White, night = Color(0xFF1E1F22))
private val LINE = DayNightColor(day = Color(0xFFD3D7DC), night = Color(0xFF45484E))
private val INK = DayNightColor(day = Color(0xFF263238), night = Color(0xFFE3E5E8))
private val HOURS = (START_MINUTE / 60 until END_MINUTE / 60).toList()

@Composable
private fun WidgetTable(items: List<ScheduleItem>) {
    // 8dp padding 양쪽 + 20dp 날짜 + 24dp 요일 헤더 + 16dp 종료시각 footer
    val week = weekStart()
    val currentItems = items.filter { it.dateEpochDay?.let { d -> d in week.toEpochDay()..week.plusDays(6).toEpochDay() } == true }
    val gridHeight = (LocalSize.current.height - 76.dp).coerceAtLeast(1.dp)
    val minuteHeight = gridHeight / TOTAL_MINUTES.toFloat()
    val hourHeight = gridHeight / HOURS.size
    val textStyle = TextStyle(color = INK, fontSize = 9.sp)
    Column(GlanceModifier.fillMaxSize().background(BG).padding(8.dp)
        .clickable(actionStartActivity<MainActivity>())) {
        Text("${week.monthValue}.${week.dayOfMonth}–${week.plusDays(6).monthValue}.${week.plusDays(6).dayOfMonth}", style = textStyle, modifier = GlanceModifier.height(20.dp))
        Row(GlanceModifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(GlanceModifier.width(24.dp))
            DAY_LABELS.forEach { day ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) { Text(day, style = textStyle) }
            }
        }
        Row(GlanceModifier.fillMaxWidth().height(gridHeight)) {
            Column(GlanceModifier.width(24.dp)) {
                // 정시 줄이 라벨 쪽으로 튀어나온 것처럼 보이도록 오른쪽 끝에 짧은 눈금을 그립니다.
                HourRows { hour ->
                    Row(GlanceModifier.fillMaxWidth().height(hourHeight)) {
                        Text("${displayHour(hour)}", style = textStyle, modifier = GlanceModifier.defaultWeight())
                        Box(GlanceModifier.width(5.dp).height(1.dp).background(LINE)) {}
                    }
                }
            }
            (1..7).forEach { day ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    // 시간 격자 배경. 분 단위 일정은 그 위에 연속 구간으로 배치합니다.
                    Column(GlanceModifier.fillMaxSize()) {
                        HourRows {
                            Box(GlanceModifier.fillMaxWidth().height(hourHeight)
                                .background(LINE).padding(top = 1.dp, start = 1.dp)) {
                                Box(GlanceModifier.fillMaxSize().background(BG)) {}
                            }
                        }
                    }
                    Column(GlanceModifier.fillMaxSize()) {
                        DaySegments(buildSegments(currentItems.filter { it.dayOfWeek == day }), minuteHeight)
                    }
                }
            }
        }
        Text("${displayHour(END_MINUTE / 60)}", style = textStyle, modifier = GlanceModifier.height(16.dp))
    }
}

/** RemoteViews 컨테이너당 자식 수 제한(10개)을 넘지 않도록 시간 행을 묶어서 배치합니다. */
@Composable
private fun HourRows(content: @Composable (Int) -> Unit) {
    HOURS.chunked(9).forEach { chunk -> Column(GlanceModifier.fillMaxWidth()) { chunk.forEach { content(it) } } }
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
private fun DaySegments(segments: List<Segment>, minuteHeight: Dp) {
    // RemoteViews의 단일 컨테이너 자식 수 제한을 피하도록 분할합니다.
    if (segments.size > 8) {
        val split = segments.size / 2
        Column(GlanceModifier.fillMaxWidth()) { DaySegments(segments.take(split), minuteHeight) }
        Column(GlanceModifier.fillMaxWidth()) { DaySegments(segments.drop(split), minuteHeight) }
    } else segments.forEach { segment ->
        val item = segment.item
        if (item == null) Spacer(GlanceModifier.height(minuteHeight * segment.minutes))
        else Column(GlanceModifier.fillMaxWidth().height(minuteHeight * segment.minutes)
            .padding(1.dp).background(Color(android.graphics.Color.parseColor(item.colorHex)))) {
            Text(item.subjectName, style = TextStyle(color = ColorProvider(Color(inkOn(item.colorHex))), fontSize = 9.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.padding(horizontal = 2.dp), maxLines = if (segment.minutes <= SLOT_MINUTES) 1 else 3)
        }
    }
}
