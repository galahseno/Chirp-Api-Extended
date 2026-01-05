package com.seno.chirp.infra.database.entities

import com.seno.chirp.domain.type.ChatId
import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import java.time.Instant

@Entity
@Table(
    name = "chats",
    schema = "chat_service"
)
class ChatEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: ChatId? = null,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "creator_id",
        nullable = false
    )
    var creator: ChatParticipantEntity,
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "chat_participants_cross_ref",
        schema = "chat_service",
        joinColumns = [JoinColumn(name = "chat_id")],
        inverseJoinColumns = [JoinColumn(name = "user_id")],
        indexes = [
            // Answer efficiently:
            // Who is in chat_x?
            Index(
                name = "idx_chat_participant_chat_id_user_id",
                columnList = "chat_id, user_id",
                unique = true
            ),
            // Answer efficiently:
            // What chats is user_x in?
            Index(
                name = "idx_chat_participant_user_id_chat_id",
                columnList = "user_id, chat_id",
                unique = true
            )
        ]
    )
    var participants: Set<ChatParticipantEntity> = emptySet(),
    @CreationTimestamp
    var createdAt: Instant = Instant.now()
)