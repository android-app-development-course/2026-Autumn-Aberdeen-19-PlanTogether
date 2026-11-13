package com.plantogether.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.settingsDataStore by preferencesDataStore(name = "plantogether_settings")

/**
 * App 级别的本机设置。目前只存后端服务器地址——
 * 之前地址是写死的 127.0.0.1，只能靠 adb reverse 转发，装到别人手机上根本连不上。
 */
class SettingsStore(private val context: Context) {

    private val baseUrlKey = stringPreferencesKey("base_url")

    /** 读回保存过的地址；没存过就返回 null，调用方保持默认值。 */
    suspend fun readBaseUrl(): String? =
        runCatching { context.settingsDataStore.data.first()[baseUrlKey] }.getOrNull()

    suspend fun saveBaseUrl(url: String) {
        context.settingsDataStore.edit { it[baseUrlKey] = url }
    }

    suspend fun clearBaseUrl() {
        context.settingsDataStore.edit { it.remove(baseUrlKey) }
    }
}
