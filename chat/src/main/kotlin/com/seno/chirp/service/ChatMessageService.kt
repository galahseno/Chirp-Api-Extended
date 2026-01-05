package com.seno.chirp.service

import com.seno.chirp.domain.event.MessageDeletedEvent
import com.seno.chirp.domain.events.chat.ChatEvent
import com.seno.chirp.domain.exception.ChatNotFoundException
import com.seno.chirp.domain.exception.ChatParticipantNotFoundException
import com.seno.chirp.domain.exception.ForbiddenException
import com.seno.chirp.domain.exception.MessageNotFoundException
import com.seno.chirp.domain.model.ChatMessage
import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.ChatMessageId
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.ChatMessageEntity
import com.seno.chirp.infra.database.mappers.toChatMessage
import com.seno.chirp.infra.database.repositories.ChatMessageRepository
import com.seno.chirp.infra.database.repositories.ChatParticipantRepository
import com.seno.chirp.infra.database.repositories.ChatRepository
import com.seno.chirp.infra.message_queue.EventPublisher
import org.springframework.cache.annotation.CacheEvict
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
class ChatMessageService(
    private val chatRepository: ChatRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    private val eventPublisher: EventPublisher,
    private val chatMessageEvictionHelper: ChatMessageEvictionHelper
) {

    @Transactional
    @CacheEvict(
        value = ["messages"],
        key = "#chatId",
    )
    fun sendMessage(
        chatId: ChatId,
        senderId: UserId,
        content: String,
        messageId: ChatMessageId? = null
    ): ChatMessage {
        val chat = chatRepository.findChatById(chatId, senderId)
            ?: throw ChatNotFoundException()
        val sender = chatParticipantRepository.findByIdOrNull(senderId)
            ?: throw ChatParticipantNotFoundException(senderId)

        val savedMessage = chatMessageRepository.saveAndFlush(
            ChatMessageEntity(
                id = messageId ?: UUID.randomUUID(),
                content = content.trim(),
                chatId = chatId,
                chat = chat,
                sender = sender,
            )
        )

        eventPublisher.publish(
            ChatEvent.NewMessage(
                senderId = sender.userId,
                senderUsername = sender.username,
                recipientIds = chat.participants.map { it.userId }.toSet(),
                chatId = chatId,
                message = savedMessage.content
            )
        )

        return savedMessage.toChatMessage()
    }

    @Transactional
    fun deleteMessage(
        messageId: ChatMessageId,
        requestUserId: UserId
    ) {
        val message = chatMessageRepository.findByIdOrNull(messageId)
            ?: throw MessageNotFoundException(messageId)

        if (message.sender.userId != requestUserId) {
            throw ForbiddenException()
        }

        chatMessageRepository.delete(message)

        applicationEventPublisher.publishEvent(
            MessageDeletedEvent(
                chatId = message.chatId,
                messageId = messageId
            )
        )

        chatMessageEvictionHelper.evictMessagesCache(message.chatId)
    }
}