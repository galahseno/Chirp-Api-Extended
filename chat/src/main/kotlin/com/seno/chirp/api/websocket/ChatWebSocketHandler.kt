package com.seno.chirp.api.websocket

import com.seno.chirp.api.dto.ws.*
import com.seno.chirp.api.mappers.toChatMessageDto
import com.seno.chirp.domain.event.*
import com.seno.chirp.domain.type.ChatId
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.service.ChatMessageService
import com.seno.chirp.service.ChatService
import com.seno.chirp.service.JwtService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener
import org.springframework.web.socket.*
import org.springframework.web.socket.handler.TextWebSocketHandler
import tools.jackson.core.JacksonException
import tools.jackson.databind.ObjectMapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@Component
class ChatWebSocketHandler(
    private val chatMessageService: ChatMessageService,
    private val objectMapper: ObjectMapper,
    private val chatService: ChatService,
    private val jwtService: JwtService
) : TextWebSocketHandler() {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val connectionLock = ReentrantReadWriteLock()

    private val sessions = ConcurrentHashMap<String, UserSession>()
    private val userToSessions = ConcurrentHashMap<UserId, MutableSet<String>>()
    private val userChatIds = ConcurrentHashMap<UserId, MutableSet<ChatId>>()
    private val chatToSessions = ConcurrentHashMap<ChatId, MutableSet<String>>()

    override fun afterConnectionEstablished(session: WebSocketSession) {
        val authHeader = session.handshakeHeaders.getFirst(HttpHeaders.AUTHORIZATION) ?: run {
            logger.warn("Session ${session.id} was closed due to missing Authorization header")
            session.close(CloseStatus.SERVER_ERROR.withReason("Authentication failed"))
            return
        }

        val userId = jwtService.getUserIdFromToken(authHeader)
        val userSession = UserSession(userId = userId, session = session)

        connectionLock.write {
            sessions[session.id] = userSession

            userToSessions.compute(userId) { _, existingSession ->
                (existingSession ?: mutableSetOf()).apply {
                    add(session.id)
                }
            }

            val chatIds = userChatIds.computeIfAbsent(userId) {
                val chatIds = chatService.findChatByUser(userId).map { it.id }
                ConcurrentHashMap.newKeySet<ChatId>().apply {
                    addAll(chatIds)
                }
            }

            chatIds.forEach { chatId ->
                chatToSessions.compute(chatId) { _, existingSession ->
                    (existingSession ?: mutableSetOf()).apply {
                        add(session.id)
                    }
                }
            }
        }

        logger.info("Websocket connection established for user $userId")
    }

    override fun afterConnectionClosed(session: WebSocketSession, status: CloseStatus) {
        connectionLock.write {
            sessions.remove(session.id)?.let { userSession ->
                val userId = userSession.userId

                val remainingSessions = userToSessions.compute(userId) { _, sessions ->
                    sessions
                        ?.apply { remove(session.id) }
                        ?.takeIf { it.isNotEmpty() }
                }

                userChatIds[userId]?.forEach { chatId ->
                    chatToSessions.compute(chatId) { _, sessions ->
                        sessions
                            ?.apply { remove(session.id) }
                            ?.takeIf { it.isNotEmpty() }
                    }
                }

                if (remainingSessions == null) {
                    userChatIds.remove(userId)
                }

                logger.info("Websocket session close for user $userId")
            }
        }
    }

    override fun handleTransportError(session: WebSocketSession, exception: Throwable) {
        logger.error("Transport error for session ${session.id}", exception)
        session.close(CloseStatus.SERVER_ERROR.withReason("Transport error"))
    }

    override fun handleTextMessage(session: WebSocketSession, message: TextMessage) {
        logger.debug("Receive message ${message.payload}")

        val userSession = connectionLock.read {
            sessions[session.id] ?: return
        }

        try {
            val webSocketMessage = objectMapper.readValue(
                message.payload,
                IncomingWebSocketMessage::class.java
            )

            when (webSocketMessage.type) {
                IncomingWebSocketMessageType.NEW_MESSAGE -> {
                    val dto = objectMapper.readValue(
                        webSocketMessage.payload,
                        SendMessageDto::class.java
                    )
                    handleSendMessage(
                        dto = dto,
                        senderId = userSession.userId
                    )
                }
            }
        } catch (e: JacksonException) {
            logger.warn("Couldn't not parse message ${message.payload}", e)
            sendError(
                session = userSession.session,
                error = ErrorDto(
                    code = "INVALID_JSON",
                    message = "Incoming JSON or UUID is invalid"
                )
            )
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onDeleteMessage(event: MessageDeletedEvent) {
        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.MESSAGE_DELETED,
                payload = objectMapper.writeValueAsString(
                    DeleteMessageDto(
                        chatId = event.chatId,
                        messageId = event.messageId
                    )
                )
            )
        )
    }

    private fun updateChatForUser(
        chatId: ChatId,
        userIds: List<UserId>
    ) {
        connectionLock.write {
            userIds.forEach { userId ->
                userChatIds.compute(userId) { _, chatIds ->
                    (chatIds ?: mutableSetOf()).apply {
                        add(chatId)
                    }
                }

                userToSessions[userId]?.forEach { sessionId ->
                    chatToSessions.compute(chatId) { _, sessions ->
                        (sessions ?: mutableSetOf()).apply {
                            add(sessionId)
                        }
                    }
                }
            }
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onChatCreated(event: ChatCreatedEvent) {
        updateChatForUser(
            chatId = event.chatId,
            userIds = event.participantsIds
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onJoinChat(event: ChatParticipantsJoinedEvent) {
        updateChatForUser(
            chatId = event.chatId,
            userIds = event.userIds.toList()
        )

        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CHAT_PARTICIPANTS_CHANGED,
                payload = objectMapper.writeValueAsString(
                    ChatParticipantsChangedDto(
                        chatId = event.chatId
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onLeftChat(event: ChatParticipantLeftEvent) {
        connectionLock.write {
            userChatIds.compute(event.userId) { _, chatIds ->
                chatIds
                    ?.apply { remove(event.chatId) }
                    ?.takeIf { it.isNotEmpty() }
            }

            userToSessions[event.userId]?.forEach { sessionId ->
                chatToSessions.compute(event.chatId) { _, sessions ->
                    sessions
                        ?.apply { remove(sessionId) }
                        ?.takeIf { it.isNotEmpty() }
                }
            }
        }

        broadcastToChat(
            chatId = event.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.CHAT_PARTICIPANTS_CHANGED,
                payload = objectMapper.writeValueAsString(
                    ChatParticipantsChangedDto(
                        chatId = event.chatId
                    )
                )
            )
        )
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onProfilePictureUpdated(event: ProfilePictureUpdatedEvent) {
        val userChats = connectionLock.read {
            userChatIds[event.userId]?.toList() ?: emptyList()
        }

        val sessionIds = mutableSetOf<String>()
        userChats.forEach { chatId ->
            connectionLock.read {
                chatToSessions[chatId]?.let { sessions ->
                    sessionIds.addAll(sessions)
                }
            }
        }

        val message = OutgoingWebSocketMessage(
            type = OutgoingWebSocketMessageType.PROFILE_PICTURE_UPDATED,
            payload = objectMapper.writeValueAsString(
                ProfilePictureUpdateDto(
                    userId = event.userId,
                    newUrl = event.newUrl
                )
            )
        )
        val messageJson = objectMapper.writeValueAsString(message)

        sessionIds.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId]
            } ?: return@forEach
            try {
                if (userSession.session.isOpen) {
                    userSession.session.sendMessage(TextMessage(messageJson))
                }
            } catch (e: Exception) {
                logger.error("Could not send profile picture update to session $sessionId", e)
            }
        }
    }

    @Scheduled(fixedDelay = PING_INTERVAL_MS)
    fun pingClients() {
        val currentTime = System.currentTimeMillis()
        val sessionToClose = mutableListOf<String>()

        val sessionSnapshot = connectionLock.read { sessions.toMap() }

        sessionSnapshot.forEach { (sessionId, userSession) ->
            try {
                if (userSession.session.isOpen) {
                    val lastPong = userSession.lastPongTimestamp
                    if (currentTime - lastPong > PONG_TIMEOUT_MS) {
                        logger.warn("Session $sessionId has time out, closing connection")
                        sessionToClose.add(sessionId)
                        return@forEach
                    }

                    userSession.session.sendMessage(PingMessage())
                    logger.debug("Sent ping to {}", userSession.userId)
                }
            } catch (e: Exception) {
                logger.error("Couldn't ping session $sessionId", e)
                sessionToClose.add(sessionId)
            }
        }

        sessionToClose.forEach { sessionId ->
            connectionLock.read {
                sessions[sessionId]?.session?.let { session ->
                    try {
                        session.close(CloseStatus.GOING_AWAY.withReason("Ping timeout"))
                    } catch (e: Exception) {
                        logger.error("Couldn't close sessions for session ${session.id}", e)
                    }
                }
            }
        }
    }

    override fun handlePongMessage(session: WebSocketSession, message: PongMessage) {
        connectionLock.write {
            sessions.compute(session.id) { _, userSession ->
                userSession?.copy(
                    lastPongTimestamp = System.currentTimeMillis()
                )
            }
        }
        logger.debug("Received pong from ${session.id}")
    }

    private fun sendError(
        session: WebSocketSession,
        error: ErrorDto
    ) {
        val webSocketMessage = objectMapper.writeValueAsString(
            OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.ERROR,
                payload = objectMapper.writeValueAsString(error)
            )
        )

        try {
            session.sendMessage(TextMessage(webSocketMessage))
        } catch (e: Exception) {
            logger.warn("Couldn't send error message", e)
        }
    }

    private fun broadcastToChat(
        chatId: ChatId,
        message: OutgoingWebSocketMessage
    ) {
        val chatSessions = connectionLock.read {
            chatToSessions[chatId]?.toList() ?: emptyList()
        }

        chatSessions.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId] ?: return@forEach
            }

            sendToUser(
                userId = userSession.userId,
                message = message
            )
        }
    }

    private fun handleSendMessage(
        dto: SendMessageDto,
        senderId: UserId
    ) {
        val userChatIds = connectionLock.read { this@ChatWebSocketHandler.userChatIds[senderId] } ?: return

        if (dto.chatId !in userChatIds) {
            return
        }

        val savedMessage = chatMessageService.sendMessage(
            chatId = dto.chatId,
            senderId = senderId,
            content = dto.content,
            messageId = dto.messageId
        )

        broadcastToChat(
            chatId = dto.chatId,
            message = OutgoingWebSocketMessage(
                type = OutgoingWebSocketMessageType.NEW_MESSAGE,
                payload = objectMapper.writeValueAsString(
                    savedMessage.toChatMessageDto()
                )
            )
        )
    }

    private fun sendToUser(userId: UserId, message: OutgoingWebSocketMessage) {
        val userSessions = connectionLock.read {
            userToSessions[userId] ?: emptySet()
        }

        userSessions.forEach { sessionId ->
            val userSession = connectionLock.read {
                sessions[sessionId] ?: return@forEach
            }
            if (userSession.session.isOpen) {
                try {
                    val messageJson = objectMapper.writeValueAsString(message)

                    userSession.session.sendMessage(TextMessage(messageJson))
                    logger.debug("Send message to user {}: {}", userId, messageJson)
                } catch (e: Exception) {
                    logger.error("Error while sending message to $userId", e)
                }
            }
        }
    }

    private data class UserSession(
        val userId: UserId,
        val session: WebSocketSession,
        val lastPongTimestamp: Long = System.currentTimeMillis()
    )

    companion object {
        private const val PING_INTERVAL_MS = 30_000L
        private const val PONG_TIMEOUT_MS = 60_000L
    }
}