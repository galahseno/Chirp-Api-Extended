package com.seno.chirp.api.exception_handling

import com.seno.chirp.domain.exception.*
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class ChatExceptionHandler {

    @ExceptionHandler(
        ChatNotFoundException::class,
        ChatParticipantNotFoundException::class,
        MessageNotFoundException::class
    )
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun onChatNotFound(
        e: Exception
    ) = mapOf(
        "code" to "NOT_FOUND",
        "message" to e.message
    )

    @ExceptionHandler(InvalidChatSizeException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onInvalidChatSize(
        e: InvalidChatSizeException
    ) = mapOf(
        "code" to "INVALID_CHAT_SIZE",
        "message" to e.message
    )

    @ExceptionHandler(InvalidProfilePictureException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun onInvalidProfilePicture(
        e: InvalidProfilePictureException
    ) = mapOf(
        "code" to "INVALID_PROFILE_PICTURE",
        "message" to e.message
    )

    @ExceptionHandler(StorageException::class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    fun onStorageError(
        e: StorageException
    ) = mapOf(
        "code" to "STORAGE_ERROR",
        "message" to e.message
    )
}