package com.seno.chirp.service

import com.seno.chirp.domain.events.user.UserEvent
import com.seno.chirp.domain.exception.*
import com.seno.chirp.domain.model.AuthenticatedUser
import com.seno.chirp.domain.model.User
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.RefreshTokenEntity
import com.seno.chirp.infra.database.entities.UserEntity
import com.seno.chirp.infra.database.mappers.toUser
import com.seno.chirp.infra.database.repository.RefreshTokenRepository
import com.seno.chirp.infra.database.repository.UserRepository
import com.seno.chirp.infra.message_queue.EventPublisher
import com.seno.chirp.infra.security.PasswordEncoder
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.*

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val emailVerificationService: EmailVerificationService,
    private val eventPublisher: EventPublisher
) {

    @Transactional
    fun register(email: String, username: String, password: String): User {
        val trimmedEmail = email.trim()
        val user = userRepository.findUserByEmailOrUsername(
            trimmedEmail, username.trim()
        )

        if (user != null) {
            throw UserAlreadyExistException()
        }

        val savedUser = userRepository.saveAndFlush(
            UserEntity(
                id = null,
                username = username.trim(),
                email = trimmedEmail,
                hashPassword = passwordEncoder.encode(password)!!
            )
        ).toUser()

        val token = emailVerificationService.createVerificationToken(trimmedEmail)

        eventPublisher.publish(
            event = UserEvent.Created(
                userId = savedUser.id,
                username = savedUser.username,
                email = savedUser.email,
                verificationToken = token.token
            )
        )
        return savedUser
    }

    fun login(email: String, password: String): AuthenticatedUser {
        val user = userRepository.findUserByEmail(email.trim()) ?: throw InvalidCredentialsException()

        if (!passwordEncoder.matches(password, user.hashPassword)) {
            throw InvalidCredentialsException()
        }

        if (!user.hasVerifiedEmail) {
            throw EmailNotVerifiedException()
        }

        return user.id?.let { userId ->
            val accessToken = jwtService.generateAccessToken(userId)
            val refreshToken = jwtService.generateRefreshToken(userId)

            storeRefreshToken(refreshToken, userId)

            AuthenticatedUser(
                user = user.toUser(),
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        } ?: throw UserNotFoundException()
    }

    @Transactional
    fun refresh(refreshToken: String): AuthenticatedUser {
        if (!jwtService.validateRefreshToken(refreshToken)) {
            throw InvalidTokenException(
                message = "Invalid refresh token"
            )
        }

        val userId = jwtService.getUserIdFromToken(refreshToken)
        val user = userRepository.findByIdOrNull(userId) ?: throw UserNotFoundException()

        val hashed = hashToken(refreshToken)

        return user.id?.let { userId ->
            refreshTokenRepository.findByUserIdAndHashedToken(
                userId = userId,
                hashedToken = hashed
            ) ?: throw InvalidTokenException(message = "Invalid refresh token")

            refreshTokenRepository.deleteByUserIdAndHashedToken(
                userId = userId,
                hashedToken = hashed
            )

            val newAccessToken = jwtService.generateAccessToken(userId)
            val newRefreshToken = jwtService.generateRefreshToken(userId)

            storeRefreshToken(token = newRefreshToken, userId = userId)

            AuthenticatedUser(
                user = user.toUser(),
                accessToken = newAccessToken,
                refreshToken = newRefreshToken
            )
        } ?: throw UserNotFoundException()
    }

    @Transactional
    fun logout(refreshToken: String) {
        val userId = jwtService.getUserIdFromToken(refreshToken)
        val hashed = hashToken(refreshToken)
        refreshTokenRepository.deleteByUserIdAndHashedToken(
            userId = userId,
            hashedToken = hashed
        )
    }

    private fun storeRefreshToken(token: String, userId: UserId) {
        val hashed = hashToken(token)
        val expiryMs = jwtService.refreshTokenValidityMs
        val expiresAt = Instant.now().plusMillis(expiryMs)

        refreshTokenRepository.save(
            RefreshTokenEntity(
                userId = userId,
                expiredAt = expiresAt,
                hashedToken = hashed
            )
        )
    }

    private fun hashToken(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(token.encodeToByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }
}