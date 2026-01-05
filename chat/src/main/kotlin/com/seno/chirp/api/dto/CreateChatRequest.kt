package com.seno.chirp.api.dto

import com.seno.chirp.domain.type.UserId
import jakarta.validation.constraints.Size

data class CreateChatRequest(
    @field:Size(
        min = 1,
        message = "Chat must have at least 2 unique participants"
    )
    val otherUserIds: List<UserId>
)
