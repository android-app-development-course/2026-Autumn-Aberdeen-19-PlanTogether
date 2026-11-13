package com.plantogether.trip

import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 行程级的 SSE 推送：同一行程的所有在线成员各持有一个连接。
 *
 * 只推送「变更计数」（最新动态 id），不推业务数据 —— App 收到后自己去拉，
 * 这样权限、序列化逻辑都只有一套，推送通道压力也小。
 */
@Component
class TripEventBroker {

    private val logger = LoggerFactory.getLogger(TripEventBroker::class.java)

    /** tripId -> 在线连接集合 */
    private val emitters = ConcurrentHashMap<Long, MutableSet<SseEmitter>>()

    fun subscribe(tripId: Long): SseEmitter {
        // 0 = 不主动超时，靠心跳和客户端断开清理
        val emitter = SseEmitter(0L)
        emitters.computeIfAbsent(tripId) { CopyOnWriteArraySet() }.add(emitter)
        emitter.onCompletion { remove(tripId, emitter) }
        emitter.onTimeout { remove(tripId, emitter) }
        emitter.onError { remove(tripId, emitter) }
        // 立刻发一条，让客户端确认通道已建立
        runCatching { emitter.send(SseEmitter.event().name("connected").data(mapOf("tripId" to tripId))) }
        return emitter
    }

    fun publish(tripId: Long, revision: Long) {
        val set = emitters[tripId] ?: return
        val payload = mapOf("revision" to revision, "tripId" to tripId)
        set.forEach { emitter -> send(tripId, emitter, SseEmitter.event().name("revision").data(payload)) }
    }

    /** 定期心跳，避免代理/移动网络把空闲连接掐掉。 */
    @Scheduled(fixedRate = 20_000)
    fun heartbeat() {
        emitters.forEach { (tripId, set) ->
            set.forEach { emitter -> send(tripId, emitter, SseEmitter.event().comment("ping")) }
        }
    }

    private fun send(tripId: Long, emitter: SseEmitter, event: SseEmitter.SseEventBuilder) {
        try {
            synchronized(emitter) { emitter.send(event) }
        } catch (e: Exception) {
            logger.debug("SSE 连接已断开 tripId={}: {}", tripId, e.message)
            remove(tripId, emitter)
        }
    }

    private fun remove(tripId: Long, emitter: SseEmitter) {
        emitters[tripId]?.let { set ->
            set.remove(emitter)
            if (set.isEmpty()) emitters.remove(tripId, set)
        }
        runCatching { emitter.complete() }
    }
}
