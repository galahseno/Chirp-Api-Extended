package com.seno.chirp.infra.database.mappers

import com.seno.chirp.domain.model.EmailVerificationToken
import com.seno.chirp.infra.database.entities.EmailVerificationTokenEntity

fun EmailVerificationTokenEntity.toEmailVerificationToken(): EmailVerificationToken {
    return EmailVerificationToken(
        id = id,
        token = token,
        user = user.toUser(),
    )
}