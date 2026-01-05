package com.seno.chirp.domain.exception

import com.seno.chirp.domain.type.ChatMessageId

class MessageNotFoundException(
    private val id: ChatMessageId
) : RuntimeException(
    "Message with ID $id not found"
)