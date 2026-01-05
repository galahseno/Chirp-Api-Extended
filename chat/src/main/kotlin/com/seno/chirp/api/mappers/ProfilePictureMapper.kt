package com.seno.chirp.api.mappers

import com.seno.chirp.api.dto.PictureUploadDto
import com.seno.chirp.domain.model.ProfilePictureUploadCredentials

fun ProfilePictureUploadCredentials.toResponse(): PictureUploadDto {
    return PictureUploadDto(
        uploadUrl = uploadUrl,
        publicUrl = publicUrl,
        headers = headers,
        expiredAt = expiredAt
    )
}