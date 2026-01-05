package com.seno.chirp.service

import com.seno.chirp.domain.exception.InvalidDeviceTokenException
import com.seno.chirp.domain.model.DeviceToken
import com.seno.chirp.domain.model.PushNotification
import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.DeviceTokenEntity
import com.seno.chirp.infra.database.DeviceTokenRepository
import com.seno.chirp.infra.mappers.toDeviceToken
import com.seno.chirp.infra.mappers.toPlatformEntity
import com.seno.chirp.infra.push_notification.FirebasePushNotificationService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentSkipListMap

@Service
class PushNotificationService(
    private val deviceTokenRepository: DeviceTokenRepository,
    private val firebasePushNotificationService: FirebasePushNotificationService
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val retryQueue = ConcurrentSkipListMap<Long, MutableList<RetryData>>()

    @Transactional
    fun registerDevice(
        userId: UserId,
        token: String,
        platform: DeviceToken.Platform
    ): DeviceToken {
        val trimmedToken = token.trim()
        val existing = deviceTokenRepository.findByToken(trimmedToken)

        if (existing == null && !firebasePushNotificationService.isValidToken(trimmedToken)) {
            throw InvalidDeviceTokenException()
        }

        val entity = if (existing != null) {
            deviceTokenRepository.save(
                existing.apply {
                    this.userId = userId
                }
            )
        } else {
            deviceTokenRepository.save(
                DeviceTokenEntity(
                    userId = userId,
                    token = token,
                    platform = platform.toPlatformEntity()
                )
            )
        }

        return entity.toDeviceToken()
    }

    @Transactional
    fun unregisterDevice(token: String) {
        deviceTokenRepository.deleteByToken(token.trim())
    }

    fun sendNewMessageNotification(
        recipientUserIds: List<UserId>,
        senderUserId: UserId,
        senderUsername: String,
        message: String,
        chatId: ChatId
    ) {
        val deviceTokens = deviceTokenRepository.findByUserIdIn(recipientUserIds)
        if (deviceTokens.isEmpty()) {
            logger.info("No device token found for $recipientUserIds")
            return
        }

        val recipients = deviceTokens
            .filter { it.userId != senderUserId }
            .map { it.toDeviceToken() }

        val notification = PushNotification(
            title = "New message from $senderUsername",
            recipients = recipients,
            message = message,
            chatId = chatId,
            data = mapOf(
                "chatId" to chatId.toString(),
                "type" to "new_message"
            )
        )

        sendWithRetry(notification = notification)
    }

    private fun sendWithRetry(
        notification: PushNotification,
        attempt: Int = 0
    ) {
        val result = firebasePushNotificationService.sendNotification(notification)

        result.permanentFailures.forEach {
            deviceTokenRepository.deleteByToken(it.token)
        }

        if (result.temporaryFailures.isNotEmpty() && attempt < RETRY_DELAY_SECONDS.size) {
            val retryNotification = notification.copy(
                recipients = result.temporaryFailures
            )
            scheduleRetry(retryNotification, attempt.plus(1))
        }

        if (result.succeeded.isNotEmpty()) {
            logger.info("Successfully sent notification to ${result.succeeded.size} devices")
        }
    }

    private fun scheduleRetry(
        notification: PushNotification,
        attempt: Int
    ) {
        val delay = RETRY_DELAY_SECONDS.getOrElse(attempt.minus(1)) {
            RETRY_DELAY_SECONDS.last()
        }
        val executedAt = Instant.now()
            .plusSeconds(delay)
            .toEpochMilli()

        val retryData = RetryData(
            notification = notification,
            attempt = attempt,
            createdAt = Instant.now()
        )

        retryQueue.compute(executedAt) { _, retries ->
            (retries ?: mutableListOf()).apply {
                add(retryData)
            }
        }

        logger.info("Scheduled retry $attempt for ${notification.id} in $delay seconds")
    }

    @Scheduled(fixedDelay = 15_000L)
    fun processRetries() {
        val now = Instant.now()
        val nowMillis = now.toEpochMilli()

        val toProcess = retryQueue.headMap(nowMillis, true)

        if (toProcess.isEmpty()) {
            return
        }

        val entries = toProcess.entries.toList()
        entries.forEach { (timeMillis, retries) ->
            retryQueue.remove(timeMillis)

            retries.forEach { retry ->
                try {
                    val age = Duration.between(retry.createdAt, now)
                    if (age.toMinutes() > MAX_RETRY_AGE_MINUTES) {
                        logger.warn("Dropping old retry ${age.toMinutes()} old")
                        return@forEach
                    }

                    sendWithRetry(
                        notification = retry.notification,
                        attempt = retry.attempt
                    )
                } catch (e: Exception) {
                    logger.warn("Error processing retry ${retry.notification.id}", e)
                }
            }
        }
    }

    private data class RetryData(
        val notification: PushNotification,
        val attempt: Int,
        val createdAt: Instant
    )

    companion object {
        private val RETRY_DELAY_SECONDS = listOf(
            30L,
            60L,
            120L,
            300L,
            600L
        )
        const val MAX_RETRY_AGE_MINUTES = 30L
    }
}