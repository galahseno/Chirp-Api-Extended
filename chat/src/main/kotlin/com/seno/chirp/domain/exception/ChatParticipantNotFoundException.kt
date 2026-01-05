package com.seno.chirp.domain.exception

import com.seno.chirp.domain.type.UserId

class ChatParticipantNotFoundException(
    private val id: UserId
) : RuntimeException(
    "The chat participant with the ID $id was not found"
)