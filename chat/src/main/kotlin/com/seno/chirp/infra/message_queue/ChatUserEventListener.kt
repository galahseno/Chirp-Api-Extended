package com.seno.chirp.infra.message_queue

import com.seno.chirp.domain.events.user.UserEvent
import com.seno.chirp.domain.model.ChatParticipant
import com.seno.chirp.service.ChatParticipantService
import org.springframework.amqp.rabbit.annotation.RabbitListener
import org.springframework.stereotype.Component

@Component
class ChatUserEventListener(
    private val chatParticipantService: ChatParticipantService
) {

    @RabbitListener(queues = [MessageQueue.CHAT_USER_EVENTS])
    fun handleUserEvent(event: UserEvent) {
        when (event) {
            is UserEvent.Verified -> {
                chatParticipantService.createChatParticipants(
                    chatParticipant = ChatParticipant(
                        id = event.userId,
                        username = event.username,
                        email = event.email,
                        profilePictureUrl = null
                    )
                )
            }

            else -> Unit
        }
    }
}