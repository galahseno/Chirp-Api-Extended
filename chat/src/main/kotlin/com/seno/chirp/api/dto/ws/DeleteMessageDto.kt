package com.seno.chirp.api.dto.ws

import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.ChatMessageId

data class DeleteMessageDto(
    val chatId: ChatId,
    val messageId: ChatMessageId
)
