package com.seno.chirp.service

import com.seno.chirp.domain.events.user.UserEvent
import com.seno.chirp.domain.exception.InvalidTokenException
import com.seno.chirp.domain.exception.UserNotFoundException
import com.seno.chirp.domain.model.EmailVerificationToken
import com.seno.chirp.infra.database.entities.EmailVerificationTokenEntity
import com.seno.chirp.infra.database.mappers.toEmailVerificationToken
import com.seno.chirp.infra.database.repository.EmailVerificationTokenRepository
import com.seno.chirp.infra.database.repository.UserRepository
import com.seno.chirp.infra.message_queue.EventPublisher
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Service
class EmailVerificationService(
    private val emailVerificationTokenRepository: EmailVerificationTokenRepository,
    private val eventPublisher: EventPublisher,
    private val userRepository: UserRepository,
    @param:Value("\${chirp.email.verification.expiry-hours}")
    private val expiryHours: Long
) {

    @Transactional
    fun resendVerificationEmail(email: String) {
        val token = createVerificationToken(email)

        if (token.user.hasEmailVerified) {
            return
        }

        eventPublisher.publish(
            event = UserEvent.RequestResendVerification(
                userId = token.user.id,
                email = token.user.email,
                username = token.user.username,
                verificationToken = token.token,
            )
        )
    }

    @Transactional
    fun createVerificationToken(email: String): EmailVerificationToken {
        val userEntity = userRepository.findUserByEmail(email)
            ?: throw UserNotFoundException()
        emailVerificationTokenRepository.invalidateActiveTokensForUser(userEntity)

        val token = EmailVerificationTokenEntity(
            expiresAt = Instant.now().plus(expiryHours, ChronoUnit.HOURS),
            user = userEntity
        )

        return emailVerificationTokenRepository.save(token).toEmailVerificationToken()
    }

    @Transactional
    fun verifyEmail(token: String) {
        val verificationToken = emailVerificationTokenRepository.findByToken(token)
            ?: throw InvalidTokenException("Email Verification token is invalid")

        if (verificationToken.isUsed) {
            throw InvalidTokenException("Email Verification token is already used")
        }

        if (verificationToken.isExpired) {
            throw InvalidTokenException("Email Verification token is has already expired")
        }

        emailVerificationTokenRepository.save(
            verificationToken.apply {
                this.usedAt = Instant.now()
            }
        )

        userRepository.save(
            verificationToken.user.apply {
                this.hasVerifiedEmail = true
            }
        )

        eventPublisher.publish(
            UserEvent.Verified(
                userId = verificationToken.user.id!!,
                email = verificationToken.user.email,
                username = verificationToken.user.username
            )
        )
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    fun cleanUpExpiredToken() {
        emailVerificationTokenRepository.deleteByExpiresAtLessThan(
            now = Instant.now()
        )
    }
}