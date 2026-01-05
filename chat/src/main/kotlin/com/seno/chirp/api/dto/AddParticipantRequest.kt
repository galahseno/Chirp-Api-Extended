package com.seno.chirp.api.dto

import com.seno.chirp.domain.type.UserId
import jakarta.validation.constraints.Size

data class AddParticipantRequest(
    @field:Size(min = 1)
    val userIds: List<UserId>
)
