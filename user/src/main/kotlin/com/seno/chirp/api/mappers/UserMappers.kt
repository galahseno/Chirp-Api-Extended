package com.seno.chirp.api.mappers

import com.seno.chirp.api.dto.AuthenticatedUserDto
import com.seno.chirp.api.dto.UserDto
import com.seno.chirp.domain.model.AuthenticatedUser
import com.seno.chirp.domain.model.User

fun AuthenticatedUser.toAuthenticatedUserDto(): AuthenticatedUserDto {
    return AuthenticatedUserDto(
        user = user.toUserDto(),
        accessToken = accessToken,
        refreshToken = refreshToken
    )
}

fun User.toUserDto(): UserDto {
    return UserDto(
        id = id,
        email = email,
        username = username,
        hasVerifiedEmail = hasEmailVerified
    )
}