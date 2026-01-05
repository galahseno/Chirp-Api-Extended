package com.seno.chirp.domain.model

import com.seno.chirp.domain.type.UserId

data class ChatParticipant(
    val id: UserId,
    val username: String,
    val email: String,
    val profilePictureUrl: String?
)
