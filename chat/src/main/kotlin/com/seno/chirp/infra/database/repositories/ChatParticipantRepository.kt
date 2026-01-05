package com.seno.chirp.infra.database.repositories

import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.ChatParticipantEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ChatParticipantRepository : JpaRepository<ChatParticipantEntity, UserId> {
    fun findByUserIdIn(userIds: Set<UserId>): Set<ChatParticipantEntity>

    @Query(
        """
        SELECT p
        FROM ChatParticipantEntity p
        WHERE LOWER(p.username) = :query or LOWER(p.email) = :query
    """
    )
    fun findByEmailOrUsername(query: String): ChatParticipantEntity?
}