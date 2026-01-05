package com.seno.chirp.api.dto.ws

import com.seno.chirp.domain.type.ChatId

data class ChatParticipantsChangedDto(
    val chatId: ChatId
)
