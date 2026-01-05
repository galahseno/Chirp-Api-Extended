package com.seno.chirp.domain.model

import java.time.Instant

data class ProfilePictureUploadCredentials(
    val uploadUrl: String,
    val publicUrl: String,
    val headers: Map<String, String>,
    val expiredAt: Instant
)
