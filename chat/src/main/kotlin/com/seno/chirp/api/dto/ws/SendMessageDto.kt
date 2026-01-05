package com.seno.chirp.api.dto.ws

import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.ChatMessageId

data class SendMessageDto(
    val chatId: ChatId,
    val content: String,
    val messageId: ChatMessageId? = null,
)
