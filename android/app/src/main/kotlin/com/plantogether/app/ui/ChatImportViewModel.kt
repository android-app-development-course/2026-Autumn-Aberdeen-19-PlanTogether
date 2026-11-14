package com.plantogether.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantogether.app.data.ApiClient
import com.plantogether.app.data.ChatExtraction
import com.plantogether.app.data.ChatSuggestion
import com.plantogether.app.data.ChatTranscript
import com.plantogether.app.data.ChatTranscriptParser
import com.plantogether.app.data.ChatTripExtractor
import com.plantogether.app.data.ChecklistRequest
import com.plantogether.app.data.DecisionRequest
import com.plantogether.app.data.PlaceRequest
import com.plantogether.app.data.SuggestionKind
import com.plantogether.app.data.TripRequest
import com.plantogether.app.data.TripResponse
import com.plantogether.app.data.errorMessage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 「把一份聊天记录变成行程」的完整流程：
 * 读文件 → 解压解析 → 认出建议 → 用户勾选/改 → 写进指定行程。
 *
 * 所有解析在本地做，不上传聊天原文；只有用户勾选后的条目才会写进后端。
 */
class ChatImportViewModel : ViewModel() {

    private val api = ApiClient.api

    sealed interface State {
        data object Idle : State
        data object Reading : State
        data class Ready(val extraction: ChatExtraction, val transcript: ChatTranscript) : State
        data class Failed(val message: String) : State
        data class Importing(val done: Int, val total: Int) : State
        data class Finished(
            val written: Int,
            val failed: Int,
            val skipped: Int,
            val tripId: Long,
        ) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _trips = MutableStateFlow<List<TripResponse>>(emptyList())
    val trips: StateFlow<List<TripResponse>> = _trips.asStateFlow()

    /** 建议列表可编辑，所以单独存一份。 */
    private val _items = MutableStateFlow<List<ChatSuggestion>>(emptyList())
    val items: StateFlow<List<ChatSuggestion>> = _items.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private var extraction: ChatExtraction? = null

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------- 读入 ----------------

    /** 微信「转发到其他应用」给的是一个 ZIP 文件（Uri）。 */
    fun loadZip(bytes: ByteArray, fileName: String) {
        _state.value = State.Reading
        viewModelScope.launch {
            val transcript = runCatching { ChatTranscriptParser.parseZip(bytes, fileName) }.getOrNull()
            if (transcript == null || transcript.messages.isEmpty()) {
                _state.value = State.Failed(
                    "这个文件里没找到聊天记录。\n" +
                        "确认它是微信「多选消息 → 转发到其他应用」导出的压缩包，" +
                        "里面应该有一份「聊天记录.txt」。"
                )
                return@launch
            }
            applyTranscript(transcript)
        }
    }

    /** 也支持直接粘贴文本（拿不到文件时的退路）。 */
    fun loadText(text: String, name: String = "粘贴的聊天记录") {
        _state.value = State.Reading
        viewModelScope.launch {
            val transcript = runCatching { ChatTranscriptParser.parseText(text, name) }.getOrNull()
            if (transcript == null) {
                _state.value = State.Failed(
                    "这段文字不像微信导出的聊天记录。\n" +
                        "每一段应该长这样：\n·昵称\n2026年11月7日 09:12\n说的话"
                )
                return@launch
            }
            applyTranscript(transcript)
        }
    }

    private fun applyTranscript(transcript: ChatTranscript) {
        val result = ChatTripExtractor.extract(transcript, LocalDate.now())
        extraction = result
        _items.value = result.suggestions
        _state.value = State.Ready(result, transcript)
    }

    fun loadTrips() {
        viewModelScope.launch {
            runCatching { api.listTrips() }.onSuccess { all ->
                _trips.value = all.filter { it.canWrite }
            }
        }
    }

    // ---------------- 编辑建议 ----------------

    fun toggle(id: Long) {
        _items.value = _items.value.map {
            if (it.id == id) it.copy(selected = !it.selected) else it
        }
    }

    fun setAllSelected(selected: Boolean) {
        _items.value = _items.value.map { it.copy(selected = selected) }
    }

    fun rename(id: Long, title: String) {
        _items.value = _items.value.map { if (it.id == id) it.copy(title = title) else it }
    }

    fun drop(id: Long) {
        _items.value = _items.value.filterNot { it.id == id }
    }

    val selectedCount: Int get() = _items.value.count { it.selected }

    // ---------------- 写进行程 ----------------

    fun importInto(tripId: Long, onDone: (Int) -> Unit = {}) {
        val chosen = _items.value.filter { it.selected }
        if (chosen.isEmpty()) {
            _message.value = "一条都没选，先勾上要写进去的"
            return
        }
        val current = _state.value
        if (current is State.Importing) return

        viewModelScope.launch {
            var written = 0
            var failed = 0
            var skipped = 0
            try {
                val trip = api.getTrip(tripId)
                fillTripBasics(trip, extraction)

                // 同一份聊天倒两次不该倒出两份东西，所以先看看行程里已经有什么
                val existing = runCatching { snapshot(tripId) }.getOrElse { Existing() }

                for ((index, suggestion) in chosen.withIndex()) {
                    _state.value = State.Importing(index, chosen.size)
                    if (existing.contains(suggestion)) {
                        skipped++
                        continue
                    }
                    val ok = runCatching { write(tripId, suggestion) }.isSuccess
                    if (ok) {
                        written++
                        existing.remember(suggestion)
                    } else {
                        failed++
                    }
                }

                _state.value = State.Finished(written, failed, skipped, tripId)
                onDone(written)
            } catch (e: Exception) {
                _state.value = State.Finished(written, failed, skipped, tripId)
                _message.value = errorMessage(e)
            }
        }
    }

    /** 行程本身缺目的地/日期/预算时，用聊天里认出来的补上（已有的绝不覆盖）。 */
    private suspend fun fillTripBasics(trip: TripResponse, extraction: ChatExtraction?) {
        if (extraction == null) return
        val newDestination = trip.destination?.takeIf { it.isNotBlank() } ?: extraction.destination
        val newStart = trip.startDate ?: extraction.startDate
        val newEnd = trip.endDate ?: extraction.endDate
        val newBudget = trip.budgetTarget ?: extraction.budgetTarget
        val newSize = trip.groupSize ?: extraction.groupSize
        if (newDestination == trip.destination && newStart == trip.startDate &&
            newEnd == trip.endDate && newBudget == trip.budgetTarget && newSize == trip.groupSize
        ) {
            return
        }
        runCatching {
            api.updateTrip(
                trip.id,
                TripRequest(
                    title = trip.title,
                    destination = newDestination,
                    startDate = newStart,
                    endDate = newEnd,
                    groupSize = newSize,
                    budgetTarget = newBudget,
                    visibility = trip.visibility,
                    version = trip.version,
                ),
            )
        }
    }

    /** 行程里已有的东西，用来判重。名字先做归一化，免得「楼外楼」和「楼外楼 」算两条。 */
    private class Existing {
        private val places = HashSet<String>()
        private val todos = HashSet<String>()
        private val decisions = HashSet<String>()

        fun contains(suggestion: ChatSuggestion): Boolean = when (suggestion.kind) {
            SuggestionKind.PLACE -> normalize(suggestion.title) in places
            SuggestionKind.TODO -> normalize(suggestion.title) in todos
            SuggestionKind.DECISION -> normalize(suggestion.title) in decisions
            SuggestionKind.BUDGET -> false
        }

        fun remember(suggestion: ChatSuggestion) {
            when (suggestion.kind) {
                SuggestionKind.PLACE -> places += normalize(suggestion.title)
                SuggestionKind.TODO -> todos += normalize(suggestion.title)
                SuggestionKind.DECISION -> decisions += normalize(suggestion.title)
                SuggestionKind.BUDGET -> Unit
            }
        }

        fun addPlace(name: String) {
            places += normalize(name)
        }

        fun addTodo(title: String) {
            todos += normalize(title)
        }

        fun addDecision(title: String) {
            decisions += normalize(title)
        }

        private fun normalize(value: String) = value.replace(" ", "").replace("　", "").trim()
    }

    private suspend fun snapshot(tripId: Long): Existing {
        val existing = Existing()
        runCatching { api.listPlaces(tripId) }.getOrNull()?.forEach { existing.addPlace(it.name) }
        runCatching { api.listChecklist(tripId) }.getOrNull()?.forEach { existing.addTodo(it.title) }
        runCatching { api.listDecisions(tripId) }.getOrNull()?.forEach { existing.addDecision(it.title) }
        return existing
    }

    private suspend fun write(tripId: Long, suggestion: ChatSuggestion) {
        when (suggestion.kind) {
            SuggestionKind.PLACE -> api.addPlace(
                tripId,
                PlaceRequest(
                    name = suggestion.title,
                    date = suggestion.date,
                    startTime = suggestion.startTime,
                    endTime = suggestion.endTime,
                    budget = suggestion.budget,
                    notes = suggestion.detail,
                ),
            )

            SuggestionKind.TODO -> api.addChecklistItem(
                tripId,
                ChecklistRequest(title = suggestion.title, done = false),
            )

            SuggestionKind.DECISION -> api.createDecision(
                tripId,
                DecisionRequest(
                    title = suggestion.title,
                    notes = suggestion.detail ?: "来自聊天记录：${suggestion.quote}",
                    options = suggestion.options,
                ),
            )

            // 预算已经在 fillTripBasics 里落到行程上了，这里不用再写
            SuggestionKind.BUDGET -> Unit
        }
    }

    fun reset() {
        _state.value = State.Idle
        _items.value = emptyList()
        extraction = null
        _message.value = null
    }

    /** 一次性并发写，供预览页「全部导入」按钮用（当前逐条串行，保留给以后优化）。 */
    @Suppress("unused")
    private suspend fun writeAll(tripId: Long, chosen: List<ChatSuggestion>) = coroutineScope {
        chosen.map { item -> async { runCatching { write(tripId, item) } } }.awaitAll()
    }
}
