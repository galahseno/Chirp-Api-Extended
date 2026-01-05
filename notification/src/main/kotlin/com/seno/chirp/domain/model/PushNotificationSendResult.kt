package com.seno.chirp.domain.model

data class PushNotificationSendResult(
    val succeeded: List<DeviceToken>,
    val temporaryFailures: List<DeviceToken>,
    val permanentFailures: List<DeviceToken>
)
