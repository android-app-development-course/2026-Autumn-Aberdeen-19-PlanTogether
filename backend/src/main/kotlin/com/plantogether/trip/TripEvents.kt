package com.plantogether.trip

import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

/**
 * 把「事务提交后」的推送包起来：如果提交前就推，客户端可能立刻来拉，
 * 结果读到的是还没提交的旧数据。
 */
@Component
class TripEvents(
    private val broker: TripEventBroker,
) {

    fun publishAfterCommit(tripId: Long, revision: Long) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCommit() {
                        broker.publish(tripId, revision)
                    }
                },
            )
        } else {
            broker.publish(tripId, revision)
        }
    }
}
