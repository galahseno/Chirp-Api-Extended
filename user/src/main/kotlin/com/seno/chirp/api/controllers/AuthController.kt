package com.seno.chirp.api.controllers

import com.seno.chirp.api.config.IpRateLimit
import com.seno.chirp.api.dto.*
import com.seno.chirp.api.mappers.toAuthenticatedUserDto
import com.seno.chirp.api.mappers.toUserDto
import com.seno.chirp.api.util.requestUserId
import com.seno.chirp.infra.rate_limiting.EmailRateLimiter
import com.seno.chirp.service.AuthService
import com.seno.chirp.service.EmailVerificationService
import com.seno.chirp.service.PasswordResetService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*
import java.util.concurrent.TimeUnit

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService,
    private val emailVerificationService: EmailVerificationService,
    private val passwordResetService: PasswordResetService,
    private val emailRateLimiter: EmailRateLimiter
) {

    @PostMapping("/register")
    @IpRateLimit(
        request = 10,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun register(
        @Valid @RequestBody body: RegisterRequest
    ): UserDto {
        return authService.register(
            email = body.email,
            username = body.username,
            password = body.password
        ).toUserDto()
    }

    @PostMapping("/login")
    @IpRateLimit(
        request = 10,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun login(
        @RequestBody body: LoginRequest
    ): AuthenticatedUserDto {
        return authService.login(
            email = body.email,
            password = body.password
        ).toAuthenticatedUserDto()
    }

    @PostMapping("/refresh")
    @IpRateLimit(
        request = 10,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun refresh(
        @RequestBody body: RefreshRequest
    ): AuthenticatedUserDto {
        return authService.refresh(refreshToken = body.refreshToken).toAuthenticatedUserDto()
    }

    @PostMapping("/logout")
    fun logout(
        @RequestBody body: RefreshRequest
    ) {
        authService.logout(refreshToken = body.refreshToken)
    }

    @PostMapping("/resend-verification")
    @IpRateLimit(
        request = 10,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun resendVerification(
        @Valid @RequestBody body: EmailRequest
    ) {
        emailRateLimiter.withRateLimit(
            email = body.email,
            action = {
                emailVerificationService.resendVerificationEmail(email = body.email)
            }
        )
    }

    @GetMapping("/verify")
    fun verifyEmail(
        @RequestParam token: String
    ) {
        emailVerificationService.verifyEmail(token = token)
    }

    @PostMapping("/forgot-password")
    @IpRateLimit(
        request = 10,
        duration = 1L,
        unit = TimeUnit.HOURS
    )
    fun forgotPassword(
        @Valid @RequestBody body: EmailRequest
    ) {
        passwordResetService.requestPasswordReset(
            email = body.email
        )
    }

    @PostMapping("/reset-password")
    fun resetPassword(
        @Valid @RequestBody body: ResetPasswordRequest
    ) {
        passwordResetService.resetPassword(
            token = body.token,
            newPassword = body.newPassword
        )
    }

    @PostMapping("/change-password")
    fun changePassword(
        @Valid @RequestBody body: ChangePasswordRequest
    ) {
        passwordResetService.changePassword(
            userId = requestUserId,
            oldPassword = body.oldPassword,
            newPassword = body.newPassword,
        )
    }
}