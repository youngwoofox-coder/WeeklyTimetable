package com.example.timetable.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
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
    val colors = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.background(colors.surface)) {
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
                        Text(label, fontSize = 11.sp, color = colors.onSurfaceVariant)
                        Text("${week.plusDays(i.toLong()).dayOfMonth}", fontSize = 12.sp, color = colors.onSurfaceVariant)
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
                            ghost = original ?: ScheduleItem(subjectName = "새 일정", dayOfWeek = anchorDate.dayOfWeek.value,
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
                // 정시 줄은 표 왼쪽으로 tick만큼 튀어나와 시간 라벨과 이어집니다.
                val tick = 6.dp
                Canvas(Modifier.offset(x = axisWidth - tick).width(dayWidth * 7 + tick).height(height)) {
                    val tickPx = tick.toPx()
                    val slots = TOTAL_MINUTES / SLOT_MINUTES
                    repeat(slots + 1) { i ->
                        val y = size.height * i / slots
                        if (i % 2 == 0) drawLine(colors.outline.copy(alpha = 0.7f), Offset(0f, y), Offset(size.width, y))
                        else drawLine(colors.outlineVariant.copy(alpha = 0.5f), Offset(tickPx, y), Offset(size.width, y))
                    }
                    repeat(8) { i ->
                        val x = tickPx + (size.width - tickPx) * i / 7
                        drawLine(colors.outlineVariant, Offset(x, 0f), Offset(x, size.height))
                    }
                }
                (START_MINUTE / 60..END_MINUTE / 60).forEach { h ->
                    Box(Modifier.offset(y = height * ((h * 60 - START_MINUTE) / TOTAL_MINUTES.toFloat()) - 7.dp)
                        .width(axisWidth - tick - 2.dp).height(14.dp), contentAlignment = Alignment.CenterEnd) {
                        Text("${displayHour(h)}:00", fontSize = 10.sp, lineHeight = 14.sp, color = colors.onSurfaceVariant)
                    }
                }
                val activeDraft = ghost ?: pendingDraft
                val render = shown.filterNot { it.id == activeDraft?.id } + listOfNotNull(activeDraft)
                render.forEach { item ->
                    Column(Modifier.offset(x = axisWidth + dayWidth * (item.dateEpochDay!! - week.toEpochDay()).toInt(),
                        y = height * ((item.startTotal - START_MINUTE) / TOTAL_MINUTES.toFloat()))
                        .width(dayWidth).height(height * (item.duration / TOTAL_MINUTES.toFloat()))
                        .padding(1.dp).background(Color(android.graphics.Color.parseColor(item.colorHex)))
                        .then(if (item.id == activeDraft?.id) Modifier.border(2.dp, colors.onSurface) else Modifier)
                        .then(if (!editing) Modifier.clickable { onSelect(item) } else Modifier).padding(horizontal = 3.dp, vertical = 1.dp)) {
                        Text(item.subjectName, color = Color(inkOn(item.colorHex)), fontSize = 11.sp, lineHeight = 13.sp,
                            fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 420, heightDp = 700)
@Composable
private fun TimetablePreview() { WeeklyTimetable(SAMPLE_ITEMS, weekStart(), false, {}, {}, Modifier.fillMaxSize()) }
