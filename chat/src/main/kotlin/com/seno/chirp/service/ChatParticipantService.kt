package com.seno.chirp.service

import com.seno.chirp.domain.type.UserId
import com.seno.chirp.api.mappers.toChatParticipantEntity
import com.seno.chirp.domain.model.ChatParticipant
import com.seno.chirp.infra.database.mappers.toChatParticipant
import com.seno.chirp.infra.database.repositories.ChatParticipantRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

@Service
class ChatParticipantService(
    private val chatParticipantRepository: ChatParticipantRepository
) {

    fun createChatParticipants(chatParticipant: ChatParticipant) {
        chatParticipantRepository.save(
            chatParticipant.toChatParticipantEntity()
        )
    }

    fun findChatParticipantById(userId: UserId): ChatParticipant? {
        return chatParticipantRepository.findByIdOrNull(userId)?.toChatParticipant()
    }

    fun findChatParticipantByEmailOrUsername(query: String): ChatParticipant? {
        val normalizedQuery = query.lowercase().trim()

        return chatParticipantRepository.findByEmailOrUsername(normalizedQuery)?.toChatParticipant()
    }
}