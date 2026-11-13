package com.plantogether.user

import com.plantogether.security.JwtService
import com.plantogether.security.SecurityUtils
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @Transactional
    fun register(request: RegisterRequest): AuthResponse {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("该邮箱已被注册")
        }
        val user = User(
            email = request.email.trim().lowercase(),
            passwordHash = passwordEncoder.encode(request.password),
            displayName = request.displayName.trim(),
        )
        val saved = userRepository.save(user)
        return saved.toAuthResponse(jwtService.generateToken(saved.id))
    }

    @Transactional(readOnly = true)
    fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email.trim().lowercase())
            ?: throw IllegalArgumentException("邮箱或密码错误")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw IllegalArgumentException("邮箱或密码错误")
        }
        return user.toAuthResponse(jwtService.generateToken(user.id))
    }

    /** 用已保存的 token 换回当前用户信息，用于 App 冷启动恢复登录态。 */
    @Transactional(readOnly = true)
    fun me(): MeResponse {
        val user = userRepository.findById(SecurityUtils.currentUserId())
            .orElseThrow { IllegalStateException("用户不存在") }
        return MeResponse(userId = user.id, email = user.email, displayName = user.displayName)
    }

    private fun User.toAuthResponse(token: String) = AuthResponse(
        token = token,
        userId = id,
        email = email,
        displayName = displayName,
    )
}
