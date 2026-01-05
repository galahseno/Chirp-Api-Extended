package com.seno.chirp.api.dto.ws

import com.seno.chirp.domain.type.UserId

data class ProfilePictureUpdateDto(
    val userId: UserId,
    val newUrl: String?
)
