package com.seno.chirp.infra.database.mappers

import com.seno.chirp.domain.model.User
import com.seno.chirp.infra.database.entities.UserEntity

fun UserEntity.toUser(): User {
    return User(
        id = id!!,
        username = username,
        email = email,
        hasEmailVerified = hasVerifiedEmail
    )
}