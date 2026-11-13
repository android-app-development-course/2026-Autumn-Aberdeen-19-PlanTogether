package com.plantogether.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.plantogether.app.data.ApiClient
import com.plantogether.app.data.AuthResponse
import com.plantogether.app.data.JoinTripRequest
import com.plantogether.app.data.LoginRequest
import com.plantogether.app.data.ReadStateStore
import com.plantogether.app.data.RegisterRequest
import com.plantogether.app.data.TokenStore
import com.plantogether.app.data.TripRequest
import com.plantogether.app.data.TripResponse
import com.plantogether.app.data.errorMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val tokenStore = TokenStore(application.applicationContext)
    private val readStateStore = ReadStateStore(application.applicationContext)

    /** 我每趟行程看到的最新 revision；null 表示本地存储还没读出来。 */
    val seenRevisions: StateFlow<Map<Long, Long>?> = readStateStore.seenRevisions
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ---- 启动引导（恢复本地 token） ----

    sealed interface BootState {
        data object Loading : BootState
        data object Ready : BootState
    }

    private val _bootState = MutableStateFlow<BootState>(BootState.Loading)
    val bootState: StateFlow<BootState> = _bootState

    // ---- 登录/注册 ----

    sealed interface AuthState {
        data object Idle : AuthState
        data object Loading : AuthState
        data class Error(val message: String) : AuthState
        data class Success(val auth: AuthResponse) : AuthState
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    val authState: StateFlow<AuthState> = _authState

    // ---- 行程列表 ----

    sealed interface TripsState {
        data object Loading : TripsState
        data class Success(val trips: List<TripResponse>) : TripsState
        data class Error(val message: String) : TripsState
    }

    private val _tripsState = MutableStateFlow<TripsState>(TripsState.Loading)
    val tripsState: StateFlow<TripsState> = _tripsState

    // ---- 创建行程 ----

    sealed interface CreateState {
        data object Idle : CreateState
        data object Loading : CreateState
        data object Success : CreateState
        data class Error(val message: String) : CreateState
    }

    private val _createState = MutableStateFlow<CreateState>(CreateState.Idle)
    val createState: StateFlow<CreateState> = _createState

    // ---- 加入行程 ----

    sealed interface JoinState {
        data object Idle : JoinState
        data object Loading : JoinState
        data class Success(val trip: TripResponse) : JoinState
        data class Error(val message: String) : JoinState
    }

    private val _joinState = MutableStateFlow<JoinState>(JoinState.Idle)
    val joinState: StateFlow<JoinState> = _joinState

    init {
        restoreSession()
    }

    /**
     * 冷启动时用本地 token 调 /auth/me 验证：
     * - 401：token 失效，清空并回登录页
     * - 网络异常：先信任本地登录态（离线时不会突然被登出）
     */
    private fun restoreSession() {
        viewModelScope.launch {
            val stored = runCatching { tokenStore.read() }.getOrNull()
            if (stored == null) {
                _bootState.value = BootState.Ready
                return@launch
            }
            ApiClient.authToken = stored.token
            try {
                val me = ApiClient.api.me()
                val session = AuthResponse(
                    token = stored.token,
                    userId = me.userId,
                    email = me.email,
                    displayName = me.displayName,
                )
                _authState.value = AuthState.Success(session)
                runCatching { tokenStore.save(session.token, session.userId, session.email, session.displayName) }
            } catch (e: HttpException) {
                if (e.code() == 401) {
                    ApiClient.authToken = null
                    runCatching { tokenStore.clear() }
                } else {
                    _authState.value = AuthState.Success(
                        AuthResponse(stored.token, stored.userId, stored.email, stored.displayName),
                    )
                }
            } catch (_: Exception) {
                // 离线：保留本地登录态
                _authState.value = AuthState.Success(
                    AuthResponse(stored.token, stored.userId, stored.email, stored.displayName),
                )
            }
            _bootState.value = BootState.Ready
        }
    }

    fun login(email: String, password: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val auth = ApiClient.api.login(LoginRequest(email, password))
                ApiClient.authToken = auth.token
                runCatching { tokenStore.save(auth.token, auth.userId, auth.email, auth.displayName) }
                _authState.value = AuthState.Success(auth)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(errorMessage(e))
            }
        }
    }

    fun register(email: String, password: String, displayName: String) {
        _authState.value = AuthState.Loading
        viewModelScope.launch {
            try {
                val auth = ApiClient.api.register(RegisterRequest(email, password, displayName))
                ApiClient.authToken = auth.token
                runCatching { tokenStore.save(auth.token, auth.userId, auth.email, auth.displayName) }
                _authState.value = AuthState.Success(auth)
            } catch (e: Exception) {
                _authState.value = AuthState.Error(errorMessage(e))
            }
        }
    }

    fun resetAuthState() {
        _authState.value = AuthState.Idle
    }

    fun logout() {
        ApiClient.authToken = null
        _authState.value = AuthState.Idle
        _tripsState.value = TripsState.Loading
        viewModelScope.launch { runCatching { tokenStore.clear() } }
    }

    // ---- 行程 ----

    fun loadTrips() {
        _tripsState.value = TripsState.Loading
        viewModelScope.launch {
            try {
                _tripsState.value = TripsState.Success(ApiClient.api.listTrips())
            } catch (e: Exception) {
                _tripsState.value = TripsState.Error(errorMessage(e))
            }
        }
    }

    fun createTrip(request: TripRequest) {
        _createState.value = CreateState.Loading
        viewModelScope.launch {
            try {
                val trip = ApiClient.api.createTrip(request)
                markTripSeen(trip.id, trip.revision)
                _createState.value = CreateState.Success
                loadTrips()
            } catch (e: Exception) {
                _createState.value = CreateState.Error(errorMessage(e))
            }
        }
    }

    fun resetCreateState() {
        _createState.value = CreateState.Idle
    }

    fun joinTrip(inviteCode: String) {
        _joinState.value = JoinState.Loading
        viewModelScope.launch {
            try {
                val trip = ApiClient.api.joinTrip(JoinTripRequest(inviteCode.trim().uppercase()))
                markTripSeen(trip.id, trip.revision)
                _joinState.value = JoinState.Success(trip)
                loadTrips()
            } catch (e: Exception) {
                _joinState.value = JoinState.Error(errorMessage(e))
            }
        }
    }

    fun resetJoinState() {
        _joinState.value = JoinState.Idle
    }

    // ---- 未读标记 ----

    fun markTripSeen(tripId: Long, revision: Long) {
        viewModelScope.launch { runCatching { readStateStore.markSeen(tripId, revision) } }
    }

    // ---- 深链邀请 ----

    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode

    /** 从 plantogether://join/CODE 进来时调用。 */
    fun deliverInviteCode(code: String) {
        _pendingInviteCode.value = code.trim().uppercase()
    }

    fun clearPendingInviteCode() {
        _pendingInviteCode.value = null
    }

    // ---- 系统分享 ----

    data class SharedLink(val url: String, val title: String?, val eventId: Long)

    private val _pendingShare = MutableStateFlow<SharedLink?>(null)
    val pendingShare: StateFlow<SharedLink?> = _pendingShare

    fun setPendingShare(url: String, title: String?, eventId: Long) {
        _pendingShare.value = SharedLink(url, title, eventId)
    }

    fun clearPendingShare() {
        _pendingShare.value = null
    }

    fun addLinkFromShare(tripId: Long, url: String, title: String?, notes: String?, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                ApiClient.api.addLink(
                    tripId,
                    com.plantogether.app.data.LinkRequest(
                        url = url,
                        title = title,
                        notes = notes,
                    ),
                )
                onDone()
                clearPendingShare()
            } catch (e: Exception) {
                _shareError.value = errorMessage(e)
            }
        }
    }

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError

    fun clearShareError() {
        _shareError.value = null
    }
}
