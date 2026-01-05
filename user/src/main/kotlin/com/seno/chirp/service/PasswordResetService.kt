package com.seno.chirp.service

import com.seno.chirp.domain.events.user.UserEvent
import com.seno.chirp.domain.exception.InvalidCredentialsException
import com.seno.chirp.domain.exception.InvalidTokenException
import com.seno.chirp.domain.exception.SamePasswordException
import com.seno.chirp.domain.exception.UserNotFoundException
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.PasswordResetTokenEntity
import com.seno.chirp.infra.database.repository.PasswordResetTokenRepository
import com.seno.chirp.infra.database.repository.RefreshTokenRepository
import com.seno.chirp.infra.database.repository.UserRepository
import com.seno.chirp.infra.message_queue.EventPublisher
import com.seno.chirp.infra.security.PasswordEncoder
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.repository.findByIdOrNull
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class PasswordResetService(
    private val userRepository: UserRepository,
    private val passwordResetTokenRepository: PasswordResetTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val refreshTokenRepository: RefreshTokenRepository,
    @param:Value("\${chirp.email.reset-password.expiry-minutes}")
    private val expiryMinutes: Long,
    private val eventPublisher: EventPublisher
) {
    @Transactional
    fun requestPasswordReset(email: String) {
        val user = userRepository.findUserByEmail(email) ?: return

        passwordResetTokenRepository.invalidateActiveTokensForUser(user)

        val token = PasswordResetTokenEntity(
            user = user, expiresAt = Instant.now().plus(expiryMinutes, ChronoUnit.MINUTES)
        )
        passwordResetTokenRepository.save(token)

        eventPublisher.publish(
            event = UserEvent.RequestResetPassword(
                userId = user.id!!,
                username = user.username,
                email = user.email,
                passwordResetToken = token.token,
                expiresInMinutes = expiryMinutes
            )
        )
    }

    @Transactional
    fun resetPassword(token: String, newPassword: String) {
        val resetToken = passwordResetTokenRepository.findByToken(token)
            ?: throw InvalidTokenException("Invalid password reset token")

        if (resetToken.isUsed) {
            throw InvalidTokenException("Email Verification token is already used")
        }

        if (resetToken.isExpired) {
            throw InvalidTokenException("Email Verification token is has already expired")
        }

        val user = resetToken.user

        if (passwordEncoder.matches(newPassword, user.hashPassword)) {
            throw SamePasswordException()
        }

        val hashedNewPassword = passwordEncoder.encode(newPassword)

        userRepository.save(user.apply {
            this.hashPassword = hashedNewPassword!!
        })

        passwordResetTokenRepository.save(
            resetToken.apply {
                this.usedAt = Instant.now()
            })

        refreshTokenRepository.deleteByUserId(user.id!!)
    }

    @Transactional
    fun changePassword(
        userId: UserId,
        oldPassword: String,
        newPassword: String,
    ) {
        val user = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()

        if (!passwordEncoder.matches(oldPassword, user.hashPassword)) {
            throw InvalidCredentialsException()
        }

        if (oldPassword == newPassword) {
            throw SamePasswordException()
        }

        refreshTokenRepository.deleteByUserId(user.id!!)

        val newHashedPassword = passwordEncoder.encode(newPassword)!!
        userRepository.save(user.apply {
            this.hashPassword = newHashedPassword
        })
    }

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupExpiredTokens() {
        passwordResetTokenRepository.deleteByExpiresAtLessThan(
            now = Instant.now()
        )
    }
}