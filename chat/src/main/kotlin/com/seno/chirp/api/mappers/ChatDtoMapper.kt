package com.seno.chirp.api.mappers

import com.seno.chirp.api.dto.ChatDto
import com.seno.chirp.api.dto.ChatMessageDto
import com.seno.chirp.api.dto.ChatParticipantDto
import com.seno.chirp.domain.model.Chat
import com.seno.chirp.domain.model.ChatMessage
import com.seno.chirp.domain.model.ChatParticipant
import com.seno.chirp.infra.database.entities.ChatParticipantEntity

fun Chat.toChatDto(): ChatDto {
    return ChatDto(
        id = id,
        participants = participants.map {
            it.toChatParticipantDto()
        },
        lastActivityAt = lastActivityAt,
        lastMessage = lastMessage?.toChatMessageDto(),
        creator = creator.toChatParticipantDto()
    )
}

fun ChatMessage.toChatMessageDto(): ChatMessageDto {
    return ChatMessageDto(
        id = id,
        chatId = chatId,
        content = content,
        createdAt = createdAt,
        senderId = sender.id
    )
}

fun ChatParticipant.toChatParticipantDto(): ChatParticipantDto {
    return ChatParticipantDto(
        id = id,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}

fun ChatParticipant.toChatParticipantEntity(): ChatParticipantEntity {
    return ChatParticipantEntity(
        userId = id,
        username = username,
        email = email,
        profilePictureUrl = profilePictureUrl
    )
}