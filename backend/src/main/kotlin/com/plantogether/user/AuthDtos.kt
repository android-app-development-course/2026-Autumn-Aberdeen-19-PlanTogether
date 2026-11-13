package com.plantogether.user

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank(message = "邮箱不能为空")
    @field:Email(message = "邮箱格式不正确")
    val email: String,

    @field:NotBlank(message = "密码不能为空")
    @field:Size(min = 6, message = "密码至少 6 位")
    val password: String,

    @field:NotBlank(message = "昵称不能为空")
    val displayName: String,
)

data class LoginRequest(
    @field:NotBlank(message = "邮箱不能为空")
    val email: String,

    @field:NotBlank(message = "密码不能为空")
    val password: String,
)

data class AuthResponse(
    val token: String,
    val userId: Long,
    val email: String,
    val displayName: String,
)

/** 用已保存的 token 恢复登录态时，返回当前用户信息（不含新 token）。 */
data class MeResponse(
    val userId: Long,
    val email: String,
    val displayName: String,
)
