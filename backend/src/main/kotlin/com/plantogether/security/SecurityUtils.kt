package com.plantogether.security

import org.springframework.security.core.context.SecurityContextHolder

object SecurityUtils {

    /** 从当前认证上下文取登录用户 id；未登录时抛异常。 */
    fun currentUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal
            ?: throw IllegalStateException("未登录")
        return when (principal) {
            is Long -> principal
            is String -> principal.toLongOrNull() ?: throw IllegalStateException("无效的认证信息")
            else -> throw IllegalStateException("无效的认证信息")
        }
    }
}
