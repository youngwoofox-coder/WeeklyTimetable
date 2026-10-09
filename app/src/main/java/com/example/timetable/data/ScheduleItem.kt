package com.example.timetable.data

import kotlinx.serialization.Serializable
import java.util.UUID
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters

const val SLOT_MINUTES = 30
const val START_MINUTE = 7 * 60
const val END_MINUTE = 24 * 60
const val TOTAL_MINUTES = END_MINUTE - START_MINUTE
val DAY_LABELS = listOf("월", "화", "수", "목", "금", "토", "일")
val SCHEDULE_COLORS = listOf("#EE8886", "#7BB8EF", "#8FC791", "#BB80CF", "#F8C871",
    "#F7956F", "#69BCB4", "#E678A4", "#A5C380", "#97A5AE")
// 이전 버전 팔레트(연한 색, 진한 색). 저장된 일정은 같은 순번의 현재 색으로 바꿔 읽습니다.
private val LEGACY_PALETTES = listOf(
    listOf("#F8D8D8", "#D8E9FA", "#DCEEDC", "#E8DDF5", "#F8E8BE", "#FAD9C1", "#D3F0EE", "#F5D5E8", "#E2E8C8", "#DADDE3"),
    listOf("#E53935", "#1E88E5", "#43A047", "#8E24AA", "#F9A825", "#F4511E", "#00897B", "#D81B60", "#689F38", "#546E7A"))
fun ScheduleItem.withCurrentColor(): ScheduleItem =
    LEGACY_PALETTES.map { palette -> palette.indexOfFirst { it.equals(colorHex, ignoreCase = true) } }
        .firstOrNull { it >= 0 }?.let { copy(colorHex = SCHEDULE_COLORS[it]) } ?: this

/** 배경색 위에서 대비가 더 큰 글자색(ARGB)을 고릅니다. */
fun inkOn(hex: String): Long {
    val rgb = hex.removePrefix("#").toLong(16)
    fun channel(shift: Int): Double = (((rgb shr shift) and 0xFF) / 255.0)
        .let { c -> if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4) }
    val luminance = 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
    return if (luminance > 0.18) 0xFF212121 else 0xFFFFFFFF
}

/** 오후 시각은 12를 뺀 값으로 표시합니다. 예) 13 → 1, 24 → 12 */
fun displayHour(hour: Int): Int = if (hour > 12) hour - 12 else hour
fun displayTime(total: Int): String = "%d:%02d".format(displayHour(total / 60), total % 60)

@Serializable
data class ScheduleItem(
    val id: String = UUID.randomUUID().toString(),
    val subjectName: String,
    val dayOfWeek: Int, // ISO: 월=1, 일=7
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val colorHex: String = SCHEDULE_COLORS.first(),
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
    item.startHour !in 0..23 || item.endHour !in 0..24 || item.startMinute !in 0..59 || item.endMinute !in 0..59 ||
        (item.endHour == 24 && item.endMinute != 0) -> "시간 형식이 올바르지 않습니다."
    item.startMinute % SLOT_MINUTES != 0 || item.endMinute % SLOT_MINUTES != 0 -> "시간은 30분 단위(00분·30분)로 입력하세요."
    item.startTotal < START_MINUTE || item.endTotal > END_MINUTE -> "07:00~24:00 안에서 입력하세요."
    item.startTotal >= item.endTotal -> "종료 시간은 시작 시간보다 늦어야 합니다."
    !Regex("#[0-9A-Fa-f]{6}").matches(item.colorHex) -> "색상 형식은 #RRGGBB입니다."
    others.any { it.id != item.id && it.dateEpochDay == item.dateEpochDay &&
        item.startTotal < it.endTotal && it.startTotal < item.endTotal } -> "같은 날짜의 다른 일정과 시간이 겹칩니다."
    else -> null
}

fun weekStart(date: LocalDate = LocalDate.now()): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

val SAMPLE_ITEMS get() = listOf(
    ScheduleItem("sample-1", "자료구조", 1, 9, 30, 11, 0, SCHEDULE_COLORS[0]),
    ScheduleItem("sample-2", "디자인", 2, 13, 0, 15, 0, SCHEDULE_COLORS[1]),
    ScheduleItem("sample-3", "영어회화", 3, 10, 0, 11, 30, SCHEDULE_COLORS[2]),
    ScheduleItem("sample-4", "앱 개발", 4, 14, 0, 16, 0, SCHEDULE_COLORS[3]),
    ScheduleItem("sample-5", "프로젝트", 5, 16, 30, 18, 0, SCHEDULE_COLORS[4])
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
