package com.example.timetable
import com.example.timetable.data.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleTest {
    private fun lesson(startH: Int, startM: Int, endH: Int, endM: Int, id: String = "new") =
        ScheduleItem(id, "수학", 1, startH, startM, endH, endM, dateEpochDay = LocalDate.parse("2026-10-12").toEpochDay())
    @Test fun acceptsBoundaries() {
        assertNull(validationError(lesson(7, 0, 8, 0), emptyList()))
        assertNull(validationError(lesson(23, 30, 24, 0), emptyList()))
    }
    @Test fun rejectsOutsideWindow() {
        assertNotNull(validationError(lesson(6, 30, 8, 0), emptyList()))
        assertNotNull(validationError(lesson(23, 30, 24, 30), emptyList()))
    }
    @Test fun displaysAfternoonMinusTwelve() {
        assertEquals(listOf(7, 12, 1, 11, 12), listOf(7, 12, 13, 23, 24).map(::displayHour))
        assertEquals("1:30", displayTime(13 * 60 + 30))
    }
    @Test fun rejectsOverlapButAllowsTouching() {
        val old = listOf(lesson(9, 30, 11, 0, "old"))
        assertNotNull(validationError(lesson(10, 30, 11, 30), old))
        assertNull(validationError(lesson(11, 0, 12, 0), old))
    }
    @Test fun editingItselfIsAllowed() {
        val item = lesson(9, 0, 10, 0)
        assertNull(validationError(item.copy(subjectName = "영어"), listOf(item)))
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

    @Test fun legacyPastelMapsToDarkColorByIndex() {
        assertEquals(SCHEDULE_COLORS[3], lesson(9, 0, 10, 0).copy(colorHex = "#E8DDF5").withCurrentColor().colorHex)
        assertEquals(SCHEDULE_COLORS[1], lesson(9, 0, 10, 0).copy(colorHex = "#1e88e5").withCurrentColor().colorHex)
        assertEquals("#123456", lesson(9, 0, 10, 0).copy(colorHex = "#123456").withCurrentColor().colorHex)
    }
    @Test fun inkContrastsWithBackground() {
        assertEquals(0xFFFFFFFF, inkOn("#1A237E"))
        SCHEDULE_COLORS.forEach { assertEquals(0xFF212121, inkOn(it)) }
    }
    @Test fun rejectsNonHalfHourTimes() {
        assertNotNull(validationError(lesson(9, 15, 10, 0), emptyList()))
        assertNotNull(validationError(lesson(9, 0, 10, 45), emptyList()))
        assertNull(validationError(lesson(9, 30, 10, 0), emptyList()))
    }
}
