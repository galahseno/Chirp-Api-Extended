package com.seno.chirp.domain.event

import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.UserId

data class ChatCreatedEvent(
    val chatId: ChatId,
    val participantsIds: List<UserId>
)
