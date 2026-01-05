package com.seno.chirp.service

import com.seno.chirp.api.dto.ChatMessageDto
import com.seno.chirp.api.mappers.toChatMessageDto
import com.seno.chirp.domain.event.ChatCreatedEvent
import com.seno.chirp.domain.event.ChatParticipantLeftEvent
import com.seno.chirp.domain.event.ChatParticipantsJoinedEvent
import com.seno.chirp.domain.exception.ChatNotFoundException
import com.seno.chirp.domain.exception.ChatParticipantNotFoundException
import com.seno.chirp.domain.exception.ForbiddenException
import com.seno.chirp.domain.exception.InvalidChatSizeException
import com.seno.chirp.domain.model.Chat
import com.seno.chirp.domain.model.ChatMessage
import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.ChatEntity
import com.seno.chirp.infra.database.mappers.toChat
import com.seno.chirp.infra.database.mappers.toChatMessage
import com.seno.chirp.infra.database.repositories.ChatMessageRepository
import com.seno.chirp.infra.database.repositories.ChatParticipantRepository
import com.seno.chirp.infra.database.repositories.ChatRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val applicationEventPublisher: ApplicationEventPublisher
) {

    @Cacheable(
        value = ["messages"],
        key = "#chatId",
        condition = "#before == null && #pageSize <= 50",
        sync = true
    )
    fun getChatMessages(
        chatId: ChatId,
        before: Instant?,
        pageSize: Int
    ): List<ChatMessageDto> {
        return chatMessageRepository.findByChatIdBefore(
            chatId = chatId,
            before = before ?: Instant.now(),
            pageable = PageRequest.of(0, pageSize)
        )
            .content
            .asReversed()
            .map { it.toChatMessage().toChatMessageDto() }
    }

    fun getChatById(
        chatId: ChatId,
        userId: UserId
    ): Chat {
        return chatRepository
            .findChatById(chatId, userId)
            ?.toChat(lastMessage = lastMessageForChat(chatId))
            ?: throw ChatNotFoundException()
    }

    fun findChatByUser(userId: UserId): List<Chat> {
        val chat = chatRepository.findAllByUserId(userId)
        val chatIds = chat.mapNotNull { it.id }

        val latestMessage = chatMessageRepository
            .findLatestMessageByChatIds(chatIds.toSet())
            .associateBy { it.chatId }

        return chat
            .map {
                it.toChat(lastMessage = latestMessage[it.id]?.toChatMessage())
            }
            .sortedByDescending { it.lastActivityAt }
    }

    @Transactional
    fun createChat(
        creatorId: UserId,
        othersUserIds: Set<UserId>
    ): Chat {
        val otherParticipants = chatParticipantRepository.findByUserIdIn(
            userIds = othersUserIds
        )

        val allParticipants = (otherParticipants + creatorId)
        if (allParticipants.size < 2) {
            throw InvalidChatSizeException()
        }

        val creator = chatParticipantRepository.findByIdOrNull(creatorId)
            ?: throw ChatParticipantNotFoundException(creatorId)

        return chatRepository.saveAndFlush(
            ChatEntity(
                creator = creator,
                participants = setOf(creator) + otherParticipants
            )
        )
            .toChat(lastMessage = null)
            .also { entity ->
                applicationEventPublisher.publishEvent(
                    ChatCreatedEvent(
                        chatId = entity.id,
                        participantsIds = entity.participants.map { it.id }
                    )
                )
            }
    }

    @Transactional
    fun addParticipantsToChat(
        requestUserId: UserId,
        chatId: ChatId,
        userIds: Set<UserId>
    ): Chat {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw ChatNotFoundException()
        val isRequestingUserInChat = chat.participants.any { it.userId == requestUserId }

        if (!isRequestingUserInChat) {
            throw ForbiddenException()
        }

        val users = userIds.map { userId ->
            chatParticipantRepository.findByIdOrNull(userId)
                ?: throw ChatParticipantNotFoundException(userId)
        }

        val lastMessage = lastMessageForChat(chatId)
        val updatedChat = chatRepository.save(
            chat.apply {
                this.participants = chat.participants + users
            }
        ).toChat(lastMessage)

        applicationEventPublisher.publishEvent(
            ChatParticipantsJoinedEvent(
                chatId = chatId,
                userIds = userIds
            )
        )

        return updatedChat
    }

    @Transactional
    fun removeParticipantFromChat(
        chatId: ChatId,
        userId: UserId
    ) {
        val chat = chatRepository.findByIdOrNull(chatId)
            ?: throw ChatNotFoundException()
        val participant = chat.participants.find { it.userId == userId }
            ?: throw ChatParticipantNotFoundException(userId)

        val newParticipantSize = chat.participants.size - 1
        if (newParticipantSize == 0) {
            chatRepository.deleteById(chatId)
            return
        }

        chatRepository.save(
            chat.apply {
                this.participants = chat.participants - participant
            }
        )

        applicationEventPublisher.publishEvent(
            ChatParticipantLeftEvent(
                chatId = chatId,
                userId = userId
            )
        )
    }

    private fun lastMessageForChat(chatId: ChatId): ChatMessage? {
        return chatMessageRepository
            .findLatestMessageByChatIds(setOf(chatId))
            .firstOrNull()
            ?.toChatMessage()
    }
}