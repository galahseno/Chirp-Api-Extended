package com.seno.chirp.api.controllers

import com.seno.chirp.api.util.requestUserId
import com.seno.chirp.domain.type.ChatMessageId
import com.seno.chirp.service.ChatMessageService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus

@Controller
@RequestMapping("/api/messages")
class ChatMessageController(
    private val chatMessageService: ChatMessageService
) {

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMessage(
        @PathVariable messageId: ChatMessageId,
    ) {
        chatMessageService.deleteMessage(messageId, requestUserId)
    }
}