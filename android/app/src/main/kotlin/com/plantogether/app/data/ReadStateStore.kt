package com.plantogether.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.readStateStore by preferencesDataStore(name = "plantogether_read")

/**
 * 记录「每趟行程我看到哪一条动态了」，用来在列表和页签上显示未读红点。
 * 只存 revision（数字），不存内容。
 */
class ReadStateStore(private val context: Context) {

    val seenRevisions: Flow<Map<Long, Long>> = context.readStateStore.data.map { prefs ->
        prefs.asMap().entries.mapNotNull { (key, value) ->
            val tripId = key.name.removePrefix(KEY_PREFIX).toLongOrNull()
            val revision = value as? Long
            if (tripId != null && revision != null) tripId to revision else null
        }.toMap()
    }

    suspend fun markSeen(tripId: Long, revision: Long) {
        if (revision <= 0) return
        context.readStateStore.edit { prefs ->
            val key = longPreferencesKey("$KEY_PREFIX$tripId")
            val current = prefs[key] ?: 0L
            if (revision > current) prefs[key] = revision
        }
    }

    private companion object {
        const val KEY_PREFIX = "trip_"
    }
}
