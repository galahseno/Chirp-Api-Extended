package com.seno.chirp.api.dto

import com.seno.chirp.domain.type.UserId

data class ChatParticipantDto(
    val id: UserId,
    val username: String,
    val email: String,
    val profilePictureUrl: String?
)
