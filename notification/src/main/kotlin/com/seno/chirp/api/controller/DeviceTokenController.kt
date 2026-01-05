package com.seno.chirp.api.controller

import com.seno.chirp.api.dto.DeviceTokenDto
import com.seno.chirp.api.dto.RegisterDeviceRequest
import com.seno.chirp.api.mappers.toDeviceTokenDto
import com.seno.chirp.api.mappers.toPlatform
import com.seno.chirp.api.util.requestUserId
import com.seno.chirp.service.PushNotificationService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/notification")
class DeviceTokenController(
    private val pushNotificationService: PushNotificationService
) {

    @PostMapping("/register")
    fun registerDeviceToken(
        @Valid @RequestBody body: RegisterDeviceRequest
    ): DeviceTokenDto {
        return pushNotificationService.registerDevice(
            userId = requestUserId,
            token = body.token,
            platform = body.platform.toPlatform()
        ).toDeviceTokenDto()
    }

    @DeleteMapping("/{token}")
    fun unregisterDeviceToken(
        @PathVariable token: String
    ) {
        pushNotificationService.unregisterDevice(
            token = token
        )
    }
}