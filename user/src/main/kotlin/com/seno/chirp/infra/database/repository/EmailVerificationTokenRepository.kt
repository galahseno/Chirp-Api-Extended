package com.seno.chirp.infra.database.repository

import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.EmailVerificationTokenEntity
import com.seno.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant

interface EmailVerificationTokenRepository: JpaRepository<EmailVerificationTokenEntity, Long> {
    fun findByToken(token: String): EmailVerificationTokenEntity?
    fun deleteByExpiresAtLessThan(now: Instant)

    @Query("""
    SELECT e FROM EmailVerificationTokenEntity e
    WHERE e.user.id = :userId
    AND e.usedAt IS NULL
    AND e.expiresAt > :now
""")
    fun findActiveTokenByUserId(userId: UserId, now: Instant = Instant.now()): EmailVerificationTokenEntity?

    @Modifying
    @Query("""
        UPDATE EmailVerificationTokenEntity e
        SET e.usedAt = CURRENT_TIMESTAMP 
        WHERE e.user = :user
    """)
    fun invalidateActiveTokensForUser(user: UserEntity)
}