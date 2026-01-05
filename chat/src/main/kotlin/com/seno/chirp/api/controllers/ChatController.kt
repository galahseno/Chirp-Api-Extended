package com.seno.chirp.api.controllers

import com.seno.chirp.api.dto.AddParticipantRequest
import com.seno.chirp.api.dto.ChatDto
import com.seno.chirp.api.dto.ChatMessageDto
import com.seno.chirp.api.dto.CreateChatRequest
import com.seno.chirp.api.mappers.toChatDto
import com.seno.chirp.api.util.requestUserId
import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.service.ChatService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.Instant

@RestController
@RequestMapping("/api/chat")
class ChatController(
    private val chatService: ChatService,
) {

    @GetMapping("/{chatId}/messages")
    fun getMessagesForChat(
        @PathVariable chatId: ChatId,
        @RequestParam("before", required = false) before: Instant? = null,
        @RequestParam("pageSize", required = false) pageSize: Int = DEFAULT_PAGE_SIZE,
    ): List<ChatMessageDto> {
        return chatService.getChatMessages(
            chatId = chatId,
            before = before,
            pageSize = pageSize
        )
    }

    @GetMapping("/{chatId}")
    fun getChat(
        @PathVariable chatId: ChatId
    ): ChatDto {
        return chatService.getChatById(chatId, requestUserId).toChatDto()
    }

    @GetMapping
    fun getChatsForUser(): List<ChatDto> {
        return chatService.findChatByUser(requestUserId)
            .map { it.toChatDto() }
    }

    @PostMapping
    fun createChat(
        @Valid @RequestBody body: CreateChatRequest
    ): ChatDto {
        return chatService.createChat(
            creatorId = requestUserId,
            othersUserIds = body.otherUserIds.toSet()
        ).toChatDto()
    }

    @PostMapping("/{chatId}/add")
    fun addChatParticipant(
        @PathVariable chatId: ChatId,
        @Valid @RequestBody body: AddParticipantRequest
    ): ChatDto {
        return chatService.addParticipantsToChat(
            requestUserId = requestUserId,
            chatId = chatId,
            userIds = body.userIds.toSet()
        ).toChatDto()
    }

    @DeleteMapping("/{chatId}/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leaveChat(
        @PathVariable chatId: ChatId,
    ) {
        chatService.removeParticipantFromChat(
            userId = requestUserId,
            chatId = chatId,
        )
    }

    companion object {
        private const val DEFAULT_PAGE_SIZE = 20
    }
}