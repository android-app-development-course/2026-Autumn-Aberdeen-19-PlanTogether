package com.plantogether.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "plantogether_auth")

/** 已保存到本机的登录态。 */
data class StoredSession(
    val token: String,
    val userId: Long,
    val email: String,
    val displayName: String,
)

/**
 * 用 DataStore 保存 token，App 重启后无需重新登录。
 * 只存 token 与展示用的账号信息，不保存密码。
 */
class TokenStore(private val context: Context) {

    private val tokenKey = stringPreferencesKey("token")
    private val userIdKey = longPreferencesKey("user_id")
    private val emailKey = stringPreferencesKey("email")
    private val displayNameKey = stringPreferencesKey("display_name")

    suspend fun save(token: String, userId: Long, email: String, displayName: String) {
        context.authDataStore.edit { prefs ->
            prefs[tokenKey] = token
            prefs[userIdKey] = userId
            prefs[emailKey] = email
            prefs[displayNameKey] = displayName
        }
    }

    suspend fun read(): StoredSession? {
        val prefs = context.authDataStore.data.first()
        val token = prefs[tokenKey] ?: return null
        return StoredSession(
            token = token,
            userId = prefs[userIdKey] ?: 0L,
            email = prefs[emailKey].orEmpty(),
            displayName = prefs[displayNameKey].orEmpty(),
        )
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }

    /** 便于将来做「记住的账号」提示，暂时未使用。 */
    fun emailFlow() = context.authDataStore.data.map { it[emailKey] }
}
