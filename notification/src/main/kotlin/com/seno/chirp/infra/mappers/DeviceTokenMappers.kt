package com.seno.chirp.infra.mappers

import com.seno.chirp.domain.model.DeviceToken
import com.seno.chirp.infra.database.DeviceTokenEntity

fun DeviceTokenEntity.toDeviceToken(): DeviceToken {
    return DeviceToken(
        id = id,
        userId = userId,
        token = token,
        platform = platform.toPlatform(),
        createdAt = createdAt
    )
}