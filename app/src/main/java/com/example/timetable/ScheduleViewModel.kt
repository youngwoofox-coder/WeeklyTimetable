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
