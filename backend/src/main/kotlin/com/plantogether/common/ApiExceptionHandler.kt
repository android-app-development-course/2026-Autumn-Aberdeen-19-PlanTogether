package com.plantogether.common

import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

@RestControllerAdvice
class ApiExceptionHandler {

    private val log = LoggerFactory.getLogger(ApiExceptionHandler::class.java)

    @ExceptionHandler(NotFoundException::class)
    fun handleNotFound(ex: NotFoundException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (ex.message ?: "资源不存在")))

    @ExceptionHandler(ForbiddenException::class)
    fun handleForbidden(ex: ForbiddenException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (ex.message ?: "无权访问")))

    @ExceptionHandler(ConflictException::class)
    fun handleConflict(ex: ConflictException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(mapOf("error" to (ex.message ?: "内容已被其他人修改")))

    /** Hibernate 在真正并发写同一行时抛出的乐观锁异常，统一转成 409。 */
    @ExceptionHandler(ObjectOptimisticLockingFailureException::class)
    fun handleOptimisticLock(ex: ObjectOptimisticLockingFailureException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(mapOf("error" to "有人同时修改了同一条内容，请刷新后重试"))

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (ex.message ?: "请求参数错误")))

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(mapOf("error" to (ex.message ?: "未认证")))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<Map<String, String>> {
        val message = ex.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "参数校验失败"
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to message))
    }

    /**
     * 请求体读不出来：JSON 语法错误、字段类型不匹配、日期格式不对（如 "2026/11/7"）。
     * 默认会抛出一个只有英文技术细节的 500，用户看到完全无法理解，
     * 这里翻成看得懂的一句话。
     */
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadable(ex: HttpMessageNotReadableException): ResponseEntity<Map<String, String>> {
        val raw = ex.mostSpecificCause.message.orEmpty()
        val message = when {
            raw.contains("LocalDate", ignoreCase = true) ->
                "日期格式不对，请用 2026-11-07 这种写法（年-月-日）"
            raw.contains("LocalDateTime", ignoreCase = true) ->
                "时间格式不对，请用 2026-11-07T09:00 这种写法"
            raw.contains("NumberFormat", ignoreCase = true) || raw.contains("Integer", ignoreCase = true) ->
                "数字格式不对，请只填阿拉伯数字"
            raw.contains("Unexpected end-of-input", ignoreCase = true) || raw.contains("Unrecognized", ignoreCase = true) ->
                "请求内容格式有误，可能是客户端版本过旧"
            else -> "请求内容格式有误，请检查填写的内容"
        }
        log.debug("请求体解析失败: {}", raw)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to message))
    }

    /** 路径里传了非数字的 id（例如 /api/trips/abc）会走到这里。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "参数「${ex.name}」的格式不对"))

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParam(ex: MissingServletRequestParameterException): ResponseEntity<Map<String, String>> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to "缺少必要参数「${ex.parameterName}」"))

    /**
     * 兜底：任何没被上面接住的异常都记一条带堆栈的日志，
     * 但只把一句通用提示返回给客户端，避免把内部实现细节（表名、类名）暴露出去。
     */
    @ExceptionHandler(Exception::class)
    fun handleUnexpected(ex: Exception): ResponseEntity<Map<String, String>> {
        log.error("未处理的服务端异常", ex)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(mapOf("error" to "服务器出了点问题，请稍后重试"))
    }
}
