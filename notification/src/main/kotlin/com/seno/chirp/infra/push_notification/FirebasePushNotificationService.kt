package com.seno.chirp.infra.push_notification

import com.google.auth.oauth2.GoogleCredentials
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.*
import com.seno.chirp.domain.model.DeviceToken
import com.seno.chirp.domain.model.PushNotification
import com.seno.chirp.domain.model.PushNotificationSendResult
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ResourceLoader
import org.springframework.stereotype.Service

@Service
class FirebasePushNotificationService(
    @param:Value("\${firebase.credentials-path}")
    private val credentialsPath: String,
    private val resourceLoader: ResourceLoader
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @PostConstruct
    fun initialize() {
        try {
            val serviceAccount = resourceLoader.getResource(credentialsPath)

            val options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount.inputStream))
                .build()

            FirebaseApp.initializeApp(options)
            logger.info("Firebase Admin SDK initialize successfully")
        } catch (e: Exception) {
            logger.error("Error initialize Firebase Admin SDK", e)
            throw e
        }
    }

    fun isValidToken(token: String): Boolean {
        val message = Message.builder()
            .setToken(token)
            .build()

        return try {
            FirebaseMessaging.getInstance().send(message, true)
            true
        } catch (e: FirebaseMessagingException) {
            logger.warn("Failed to validate Firebase token", e)
            false
        }
    }

    fun sendNotification(notification: PushNotification): PushNotificationSendResult {
        val message = notification.recipients.map { recipient ->
            Message.builder()
                .setToken(recipient.token)
                .setNotification(
                    Notification.builder()
                        .setTitle(notification.title)
                        .setBody(notification.message)
                        .build()
                )
                .apply {
                    notification.data.forEach { (key, value) ->
                        putData(key, value)
                    }

                    when (recipient.platform) {
                        DeviceToken.Platform.ANDROID -> {
                            setAndroidConfig(
                                AndroidConfig.builder()
                                    .setPriority(AndroidConfig.Priority.HIGH)
                                    .setCollapseKey(notification.chatId.toString())
                                    .setRestrictedPackageName("com.seno.chirp")
                                    .build()
                            )
                        }

                        DeviceToken.Platform.IOS -> {
                            setApnsConfig(
                                ApnsConfig.builder()
                                    .setAps(
                                        Aps.builder()
                                            .setSound("default")
                                            .setThreadId(notification.chatId.toString())
                                            .build()
                                    )
                                    .build()
                            )
                        }
                    }
                }
                .build()
        }

        return FirebaseMessaging.getInstance()
            .sendEach(message)
            .toSendResult(notification.recipients)
    }

    private fun BatchResponse.toSendResult(
        allDeviceToken: List<DeviceToken>
    ): PushNotificationSendResult {
        val succeeded = mutableListOf<DeviceToken>()
        val temporaryFailures = mutableListOf<DeviceToken>()
        val permanentFailures = mutableListOf<DeviceToken>()

        responses.forEachIndexed { index, sendResponse ->
            val deviceToken = allDeviceToken[index]
            if (sendResponse.isSuccessful) {
                succeeded.add(deviceToken)
            } else {
                val errorCode = sendResponse.exception?.messagingErrorCode
                logger.warn("Failed to send notification to token ${deviceToken.token}: $errorCode")

                when (errorCode) {
                    MessagingErrorCode.UNREGISTERED,
                    MessagingErrorCode.SENDER_ID_MISMATCH,
                    MessagingErrorCode.INVALID_ARGUMENT,
                    MessagingErrorCode.THIRD_PARTY_AUTH_ERROR -> {
                        permanentFailures.add(deviceToken)
                    }

                    MessagingErrorCode.INTERNAL,
                    MessagingErrorCode.QUOTA_EXCEEDED,
                    MessagingErrorCode.UNAVAILABLE,
                    null -> {
                        temporaryFailures.add(deviceToken)
                    }
                }
            }
        }

        logger.debug(
            "Push notifications sent. Succeeded: ${succeeded.size}, " +
                    "Temporary failures: ${temporaryFailures.size}, Permanent failures: ${permanentFailures.size}"
        )

        return PushNotificationSendResult(
            succeeded = succeeded.toList(),
            temporaryFailures = temporaryFailures.toList(),
            permanentFailures = permanentFailures.toList()
        )
    }
}