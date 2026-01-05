package com.seno.chirp.domain.event

import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.ChatMessageId

data class MessageDeletedEvent(
    val chatId: ChatId,
    val messageId: ChatMessageId
)
