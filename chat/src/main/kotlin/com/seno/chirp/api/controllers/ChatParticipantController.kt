package com.seno.chirp.api.controllers

import com.seno.chirp.api.dto.ChatParticipantDto
import com.seno.chirp.api.dto.ConfirmProfilePictureRequest
import com.seno.chirp.api.dto.PictureUploadDto
import com.seno.chirp.api.mappers.toChatParticipantDto
import com.seno.chirp.api.mappers.toResponse
import com.seno.chirp.api.util.requestUserId
import com.seno.chirp.service.ChatParticipantService
import com.seno.chirp.service.ProfilePictureService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/participants")
class ChatParticipantController(
    private val chatParticipantService: ChatParticipantService,
    private val profilePictureService: ProfilePictureService
) {

    @GetMapping
    fun getChatParticipantByUsernameOrEmail(
        @RequestParam(required = false) query: String?
    ): ChatParticipantDto {
        val participant = if (query == null) {
            chatParticipantService.findChatParticipantById(requestUserId)
        } else {
            chatParticipantService.findChatParticipantByEmailOrUsername(query)
        }

        return participant?.toChatParticipantDto()
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
    }

    @PostMapping("/profile-picture-upload")
    fun profilePictureUploadUrl(
        @RequestParam mimeType: String,
    ): PictureUploadDto {
        return profilePictureService.generateUploadCredentials(
            userId = requestUserId,
            mimeType = mimeType
        ).toResponse()
    }

    @PostMapping("/confirm-profile-picture")
    fun confirmPictureUploadUrl(
        @Valid @RequestBody body: ConfirmProfilePictureRequest,
    ) {
        profilePictureService.confirmProfilePictureUpload(
            userId = requestUserId,
            publicUrl = body.publicUrl
        )
    }

    @DeleteMapping("/profile-picture")
    fun deleteProfilePicture() {
        profilePictureService.deleteProfilePicture(
            userId = requestUserId
        )
    }
}