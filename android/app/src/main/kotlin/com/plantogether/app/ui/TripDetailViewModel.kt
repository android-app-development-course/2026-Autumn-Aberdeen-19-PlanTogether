package com.plantogether.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plantogether.app.data.ActivityResponse
import com.plantogether.app.data.ApiClient
import com.plantogether.app.data.ChecklistRequest
import com.plantogether.app.data.ChecklistResponse
import com.plantogether.app.data.CommentRequest
import com.plantogether.app.data.CommentResponse
import com.plantogether.app.data.DecisionRequest
import com.plantogether.app.data.DecisionResponse
import com.plantogether.app.data.DecisionStatusRequest
import com.plantogether.app.data.LinkRequest
import com.plantogether.app.data.LinkResponse
import com.plantogether.app.data.MemberResponse
import com.plantogether.app.data.MemberRoleRequest
import com.plantogether.app.data.OptionRequest
import com.plantogether.app.data.PlaceRequest
import com.plantogether.app.data.PlaceResponse
import com.plantogether.app.data.TripRealtimeClient
import com.plantogether.app.data.TripRequest
import com.plantogether.app.data.TripResponse
import com.plantogether.app.data.errorMessage
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * 单个行程的详情状态：地点 / 链接 / 评论 / 清单 / 决策 / 成员 / 动态。
 *
 * 协作相关：
 * - [startAutoRefresh] 在页面可见时每 12 秒问一次 revision，变大了才重新拉数据
 * - 写操作带上 version，服务端发现被别人先改过会返回 409，这里自动刷新并提示
 * - 投票遇到 409 会自动重试一次（并发点赞同一条选项时）
 */
class TripDetailViewModel(private val tripId: Long) : ViewModel() {

    private val api = ApiClient.api

    private val _trip = MutableStateFlow<TripResponse?>(null)
    val trip: StateFlow<TripResponse?> = _trip.asStateFlow()

    private val _places = MutableStateFlow<List<PlaceResponse>>(emptyList())
    val places: StateFlow<List<PlaceResponse>> = _places.asStateFlow()

    private val _links = MutableStateFlow<List<LinkResponse>>(emptyList())
    val links: StateFlow<List<LinkResponse>> = _links.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentResponse>>(emptyList())
    val comments: StateFlow<List<CommentResponse>> = _comments.asStateFlow()

    private val _checklist = MutableStateFlow<List<ChecklistResponse>>(emptyList())
    val checklist: StateFlow<List<ChecklistResponse>> = _checklist.asStateFlow()

    private val _decisions = MutableStateFlow<List<DecisionResponse>>(emptyList())
    val decisions: StateFlow<List<DecisionResponse>> = _decisions.asStateFlow()

    private val _members = MutableStateFlow<List<MemberResponse>>(emptyList())
    val members: StateFlow<List<MemberResponse>> = _members.asStateFlow()

    private val _activity = MutableStateFlow<List<ActivityResponse>>(emptyList())
    val activity: StateFlow<List<ActivityResponse>> = _activity.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** 当前看到的变更计数，界面用它算未读、并在离开时回写「已读到哪」。 */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** 最近一次同步到的变更计数。 */
    private var lastRevision = 0L

    /** 已经拉到本地的那个 revision，避免同一变更重复拉取。 */
    private var lastSeenDataRevision = 0L
    private var lastLoadAt = 0L
    private var autoRefreshJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0
    private val realtime = TripRealtimeClient()

    init {
        refreshAll()
    }

    fun consumeMessage() {
        _message.value = null
    }

    // ---------------- 加载与自动同步 ----------------

    fun refreshAll(silent: Boolean = false) {
        lastLoadAt = System.currentTimeMillis()
        if (!silent) _loading.value = true
        viewModelScope.launch {
            val errors = mutableListOf<String>()
            coroutineScope {
                val tripJob = async { runCatching { api.getTrip(tripId) } }
                val placesJob = async { runCatching { api.listPlaces(tripId) } }
                val linksJob = async { runCatching { api.listLinks(tripId) } }
                val commentsJob = async { runCatching { api.listComments(tripId) } }
                val checklistJob = async { runCatching { api.listChecklist(tripId) } }
                val decisionsJob = async { runCatching { api.listDecisions(tripId) } }
                val membersJob = async { runCatching { api.listMembers(tripId) } }
                val activityJob = async { runCatching { api.listActivity(tripId) } }

                tripJob.await().onSuccess { _trip.value = it }.onFailure { errors += errorMessage(it) }
                placesJob.await().onSuccess { _places.value = it }.onFailure { errors += errorMessage(it) }
                linksJob.await().onSuccess { _links.value = it }.onFailure { errors += errorMessage(it) }
                commentsJob.await().onSuccess { _comments.value = it }.onFailure { errors += errorMessage(it) }
                checklistJob.await().onSuccess { _checklist.value = it }.onFailure { errors += errorMessage(it) }
                decisionsJob.await().onSuccess { _decisions.value = it }.onFailure { errors += errorMessage(it) }
                membersJob.await().onSuccess { _members.value = it }.onFailure { errors += errorMessage(it) }
                activityJob.await().onSuccess { _activity.value = it }.onFailure { errors += errorMessage(it) }
            }
            syncRevision()
            if (!silent) _loading.value = false
            // 静默刷新时不要用「无权限」这类错误打断用户
            if (!silent && errors.isNotEmpty()) _message.value = errors.first()
        }
    }

    fun startAutoRefresh() {
        autoRefreshJob?.cancel()
        if (System.currentTimeMillis() - lastLoadAt > 2_000) refreshAll(silent = true)
        reconnectAttempts = 0
        openRealtime()
        autoRefreshJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                checkForUpdates()
            }
        }
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        reconnectJob?.cancel()
        reconnectJob = null
        realtime.close()
    }

    private fun openRealtime() {
        realtime.connect(
            tripId = tripId,
            onRevision = { revision -> viewModelScope.launch { onRemoteRevision(revision) } },
            onConnected = { connected ->
                if (connected) {
                    reconnectAttempts = 0
                } else {
                    scheduleReconnect()
                }
            },
        )
    }

    /** SSE 断开后（例如后端重启）有限次重连，期间靠 30 秒兜底轮询。 */
    private fun scheduleReconnect() {
        if (autoRefreshJob == null || reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) return
        reconnectAttempts++
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            delay(RECONNECT_DELAY_MS)
            if (autoRefreshJob != null) openRealtime()
        }
    }

    override fun onCleared() {
        stopAutoRefresh()
        super.onCleared()
    }

    /** SSE 推送到达：计数变大才重新拉数据。 */
    private suspend fun onRemoteRevision(revision: Long) {
        lastRevision = maxOf(lastRevision, revision)
        _revision.value = lastRevision
        if (revision > lastSeenDataRevision) {
            lastSeenDataRevision = revision
            refreshAll(silent = true)
        }
    }

    private suspend fun checkForUpdates() {
        try {
            onRemoteRevision(api.getRevision(tripId).revision)
        } catch (_: Exception) {
            // 兜底轮询失败不打扰用户，下一轮再试
        }
    }

    private suspend fun syncRevision() {
        runCatching { api.getRevision(tripId).revision }.onSuccess { revision ->
            lastRevision = revision
            lastSeenDataRevision = revision
            _revision.value = revision
        }
    }

    fun refreshMembersAndActivity() {
        viewModelScope.launch {
            runCatching { api.listMembers(tripId) }.onSuccess { _members.value = it }
            runCatching { api.listActivity(tripId) }.onSuccess { _activity.value = it }
        }
    }

    private fun submit(
        successMessage: String? = null,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                runCatching { api.getTrip(tripId) }.onSuccess { _trip.value = it }
                syncRevision()
                onSuccess()
                successMessage?.let { _message.value = it }
            } catch (e: Exception) {
                _message.value = errorMessage(e)
                if (e is HttpException && e.code() == 409) {
                    refreshAll(silent = true)
                }
            }
        }
    }

    // ---------------- 行程信息 ----------------

    fun updateTrip(request: TripRequest, onDone: () -> Unit = {}) = submit("已保存行程信息", onDone) {
        _trip.value = api.updateTrip(tripId, request)
    }

    fun rotateInviteCode() = submit("邀请码已更新，旧邀请码立即失效") {
        _trip.value = api.rotateInviteCode(tripId)
        runCatching { api.listActivity(tripId) }.onSuccess { _activity.value = it }
    }

    /** 删除整个行程（含所有子数据）。仅创建者可调用。 */
    fun deleteTrip(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.deleteTrip(tripId)
                if (!response.isSuccessful) {
                    _message.value = "删除失败（HTTP ${response.code()}）"
                    return@launch
                }
                onDeleted()
            } catch (e: Exception) {
                _message.value = errorMessage(e)
            }
        }
    }

    /** 成员主动退出；创建者会被服务端拒绝。 */
    fun leaveTrip(onLeft: () -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.leaveTrip(tripId)
                if (!response.isSuccessful) {
                    _message.value = "退出失败（HTTP ${response.code()}）"
                    return@launch
                }
                onLeft()
            } catch (e: Exception) {
                _message.value = errorMessage(e)
            }
        }
    }

    fun exportMarkdown(onReady: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val response = api.exportTrip(tripId)
                val body = response.body()
                if (!response.isSuccessful || body == null) {
                    _message.value = "导出失败（HTTP ${response.code()}）"
                    return@launch
                }
                onReady(body.string())
            } catch (e: Exception) {
                _message.value = errorMessage(e)
            }
        }
    }

    // ---------------- 地点 ----------------

    fun addPlace(request: PlaceRequest, onDone: () -> Unit = {}) = submit("已添加地点", onDone) {
        api.addPlace(tripId, request)
        _places.value = api.listPlaces(tripId)
        runCatching { api.listActivity(tripId) }.onSuccess { _activity.value = it }
    }

    fun updatePlace(placeId: Long, request: PlaceRequest, onDone: () -> Unit = {}) =
        submit("已保存地点修改", onDone) {
            api.updatePlace(tripId, placeId, request)
            _places.value = api.listPlaces(tripId)
        }

    fun deletePlace(placeId: Long) = submit("已删除地点") {
        api.deletePlace(tripId, placeId)
        _places.value = api.listPlaces(tripId)
        runCatching { api.listComments(tripId) }.onSuccess { _comments.value = it }
    }

    fun movePlace(placeId: Long, delta: Int) {
        val sorted = _places.value.sortedBy { it.sortOrder }
        val index = sorted.indexOfFirst { it.id == placeId }
        val target = index + delta
        if (index < 0 || target !in sorted.indices) return
        val ids = sorted.map { it.id }.toMutableList()
        val moved = ids[index]
        ids[index] = ids[target]
        ids[target] = moved
        reorderPlaces(ids)
    }

    /** 全局顺序重排（拖拽结束后调用）。 */
    fun reorderPlaces(orderedIds: List<Long>) {
        viewModelScope.launch {
            try {
                _places.value = api.reorderPlaces(tripId, orderedIds)
                syncRevision()
            } catch (e: Exception) {
                _message.value = errorMessage(e)
            }
        }
    }

    // ---------------- 链接 ----------------

    fun addLink(request: LinkRequest, onDone: () -> Unit = {}) = submit("已收藏链接", onDone) {
        api.addLink(tripId, request)
        _links.value = api.listLinks(tripId)
    }

    fun updateLink(linkId: Long, request: LinkRequest, onDone: () -> Unit = {}) = submit("已保存链接", onDone) {
        api.updateLink(tripId, linkId, request)
        _links.value = api.listLinks(tripId)
    }

    fun deleteLink(linkId: Long) = submit("已删除链接") {
        api.deleteLink(tripId, linkId)
        _links.value = api.listLinks(tripId)
    }

    // ---------------- 评论 ----------------

    fun addComment(body: String, placeId: Long? = null, onDone: () -> Unit = {}) = submit(null, onDone) {
        api.addComment(tripId, CommentRequest(body = body, placeId = placeId))
        _comments.value = api.listComments(tripId)
    }

    fun deleteComment(commentId: Long) = submit(null) {
        api.deleteComment(tripId, commentId)
        _comments.value = api.listComments(tripId)
    }

    // ---------------- 清单 ----------------

    fun addChecklistItem(title: String, assignee: String?, onDone: () -> Unit = {}) =
        submit(null, onDone) {
            api.addChecklistItem(tripId, ChecklistRequest(title = title, assignee = assignee))
            _checklist.value = api.listChecklist(tripId)
        }

    /** 勾选只是一次状态设置，冲突时后写覆盖前写即可，因此不带 version。 */
    fun setChecklistDone(item: ChecklistResponse, done: Boolean) = submit(null) {
        api.updateChecklistItem(
            tripId,
            item.id,
            ChecklistRequest(title = item.title, done = done, assignee = item.assignee),
        )
        _checklist.value = api.listChecklist(tripId)
    }

    fun updateChecklistItem(item: ChecklistResponse, title: String, assignee: String?) = submit(null) {
        api.updateChecklistItem(
            tripId,
            item.id,
            ChecklistRequest(title = title, done = item.done, assignee = assignee, version = item.version),
        )
        _checklist.value = api.listChecklist(tripId)
    }

    fun deleteChecklistItem(itemId: Long) = submit(null) {
        api.deleteChecklistItem(tripId, itemId)
        _checklist.value = api.listChecklist(tripId)
    }

    // ---------------- 决策投票 ----------------

    fun createDecision(request: DecisionRequest, onDone: () -> Unit = {}) = submit(null, onDone) {
        api.createDecision(tripId, request)
        _decisions.value = api.listDecisions(tripId)
    }

    fun addDecisionOption(decisionId: Long, title: String, onDone: () -> Unit = {}) =
        submit(null, onDone) {
            api.addDecisionOption(tripId, decisionId, OptionRequest(title = title))
            _decisions.value = api.listDecisions(tripId)
        }

    fun removeDecisionOption(decisionId: Long, optionId: Long) = submit(null) {
        api.removeDecisionOption(tripId, decisionId, optionId)
        _decisions.value = api.listDecisions(tripId)
    }

    /** 投票是开关操作；并发撞车（409）时刷新后重试一次，避免用户白点。 */
    fun vote(decisionId: Long, optionId: Long) {
        viewModelScope.launch {
            try {
                try {
                    api.voteDecisionOption(tripId, decisionId, optionId)
                } catch (e: HttpException) {
                    if (e.code() != 409) throw e
                    // 有人同时在投同一个选项：先拿到最新票数再重试一次
                    runCatching { _decisions.value = api.listDecisions(tripId) }
                    api.voteDecisionOption(tripId, decisionId, optionId)
                }
                _decisions.value = api.listDecisions(tripId)
                syncRevision()
            } catch (e: Exception) {
                _message.value = errorMessage(e)
                if (e is HttpException && e.code() == 409) refreshAll(silent = true)
            }
        }
    }

    fun decide(decision: DecisionResponse, optionId: Long) = submit("已定案") {
        api.setDecisionStatus(
            tripId,
            decision.id,
            DecisionStatusRequest("DECIDED", optionId, decision.version),
        )
        _decisions.value = api.listDecisions(tripId)
    }

    fun reopenDecision(decision: DecisionResponse) = submit("已重新开放投票") {
        api.setDecisionStatus(
            tripId,
            decision.id,
            DecisionStatusRequest("OPEN", null, decision.version),
        )
        _decisions.value = api.listDecisions(tripId)
    }

    fun cancelDecision(decision: DecisionResponse) = submit("已取消该决策") {
        api.setDecisionStatus(
            tripId,
            decision.id,
            DecisionStatusRequest("CANCELLED", null, decision.version),
        )
        _decisions.value = api.listDecisions(tripId)
    }

    fun deleteDecision(decisionId: Long) = submit("已删除决策") {
        api.deleteDecision(tripId, decisionId)
        _decisions.value = api.listDecisions(tripId)
    }

    // ---------------- 成员 ----------------

    fun setMemberRole(memberId: Long, role: String) = submit(null) {
        api.updateMemberRole(tripId, memberId, MemberRoleRequest(role))
        _members.value = api.listMembers(tripId)
    }

    fun removeMember(memberId: Long) = submit("已移除成员") {
        api.removeMember(tripId, memberId)
        _members.value = api.listMembers(tripId)
    }

    private companion object {
        /** SSE 断开时的兜底轮询间隔。 */
        const val POLL_INTERVAL_MS = 30_000L
        const val RECONNECT_DELAY_MS = 5_000L
        const val MAX_RECONNECT_ATTEMPTS = 5
    }
}
