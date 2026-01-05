package com.seno.chirp.api.dto

import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.ChatMessageId
import com.seno.chirp.domain.type.UserId
import java.time.Instant

data class ChatMessageDto(
    val id: ChatMessageId,
    val chatId: ChatId,
    val content: String,
    val createdAt: Instant,
    val senderId: UserId
)
