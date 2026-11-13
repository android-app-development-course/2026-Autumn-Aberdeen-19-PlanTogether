package com.plantogether.common

/** 资源不存在 → HTTP 404 */
class NotFoundException(message: String) : RuntimeException(message)

/** 已登录但无权访问/修改 → HTTP 403 */
class ForbiddenException(message: String) : RuntimeException(message)

/** 乐观锁冲突（别人先改了同一份数据） → HTTP 409 */
class ConflictException(message: String) : RuntimeException(message)
