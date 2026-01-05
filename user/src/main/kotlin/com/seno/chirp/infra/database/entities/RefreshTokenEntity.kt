package com.seno.chirp.infra.database.entities

import com.seno.chirp.domain.type.UserId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "refresh_token",
    schema = "user_service",
    indexes = [
        Index("idx_refresh_token_user_id", "user_id"),
        Index("idx_refresh_token_user_tone", "user_id,hashed_token"),
    ]
)
class RefreshTokenEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,
    @Column(nullable = false)
    var userId: UserId,
    @Column(nullable = false)
    var expiredAt: Instant,
    @Column(nullable = false)
    var hashedToken: String,
    @CreationTimestamp
    var createdAt: Instant = Instant.now(),
)