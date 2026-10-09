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
    private fun decode(raw: String?): List<ScheduleItem> = if (raw == null) SAMPLE_ITEMS else json.decodeFromString<List<ScheduleItem>>(raw).map { it.withCurrentColor() }
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
