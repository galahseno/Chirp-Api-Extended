package com.seno.chirp.infra.database.mappers

import com.seno.chirp.domain.model.Chat
import com.seno.chirp.domain.model.ChatMessage
import com.seno.chirp.domain.model.ChatParticipant
import com.seno.chirp.infra.database.entities.ChatEntity
import com.seno.chirp.infra.database.entities.ChatMessageEntity
import com.seno.chirp.infra.database.entities.ChatParticipantEntity

fun ChatEntity.toChat(lastMessage: ChatMessage? = null): Chat {
    return Chat(
        id = id!!,
        participants = participants.map {
            it.toChatParticipant()
        }.toSet(),
        creator = creator.toChatParticipant(),
        lastActivityAt = lastMessage?.createdAt ?: createdAt,
        createdAt = createdAt,
        lastMessage = lastMessage
    )
}

fun ChatParticipantEntity.toChatParticipant(): ChatParticipant {
    return ChatParticipant(
        id = userId,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}

fun ChatMessageEntity.toChatMessage(): ChatMessage {
    return ChatMessage(
        id = id!!,
        chatId = chatId,
        content = content,
        createdAt = createdAt,
        sender = sender.toChatParticipant()
    )
}