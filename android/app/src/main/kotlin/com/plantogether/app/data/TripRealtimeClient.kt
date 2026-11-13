package com.plantogether.app.data

import com.google.gson.Gson
import okhttp3.Request
import okhttp3.Response
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import okhttp3.sse.EventSources

/**
 * 行程实时通道（SSE）。只接收「变更计数」，真正的数据仍然走普通接口拉取，
 * 这样权限与序列化逻辑只有一套。
 */
class TripRealtimeClient {

    private val gson = Gson()
    private var eventSource: EventSource? = null

    fun connect(
        tripId: Long,
        onRevision: (Long) -> Unit,
        onConnected: (Boolean) -> Unit,
    ) {
        close()
        val request = Request.Builder()
            .url("${ApiClient.baseUrl}api/trips/$tripId/stream")
            .header("Accept", "text/event-stream")
            .build()

        eventSource = EventSources.createFactory(ApiClient.httpClient).newEventSource(
            request,
            object : EventSourceListener() {
                override fun onOpen(eventSource: EventSource, response: Response) {
                    onConnected(true)
                }

                override fun onEvent(
                    eventSource: EventSource,
                    id: String?,
                    type: String?,
                    data: String,
                ) {
                    if (type != "revision") return
                    runCatching { gson.fromJson(data, RevisionResponse::class.java).revision }
                        .onSuccess(onRevision)
                }

                override fun onClosed(eventSource: EventSource) {
                    onConnected(false)
                }

                override fun onFailure(
                    eventSource: EventSource,
                    t: Throwable?,
                    response: Response?,
                ) {
                    onConnected(false)
                }
            },
        )
    }

    fun close() {
        eventSource?.cancel()
        eventSource = null
    }
}
