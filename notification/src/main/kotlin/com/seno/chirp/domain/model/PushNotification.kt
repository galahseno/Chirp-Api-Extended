package com.seno.chirp.domain.model

import com.seno.chirp.domain.type.ChatId
import java.util.*

data class PushNotification(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val recipients: List<DeviceToken>,
    val message: String,
    val chatId: ChatId,
    val data: Map<String, String>
)
