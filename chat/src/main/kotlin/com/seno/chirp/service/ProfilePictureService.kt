package com.seno.chirp.service

import com.seno.chirp.domain.event.ProfilePictureUpdatedEvent
import com.seno.chirp.domain.exception.ChatParticipantNotFoundException
import com.seno.chirp.domain.exception.InvalidProfilePictureException
import com.seno.chirp.domain.model.ProfilePictureUploadCredentials
import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.repositories.ChatParticipantRepository
import com.seno.chirp.infra.storage.SupabaseStorageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfilePictureService(
    private val supabaseStorageService: SupabaseStorageService,
    private val chatParticipantRepository: ChatParticipantRepository,
    private val applicationEventPublisher: ApplicationEventPublisher,
    @param:Value("\${supabase.url}")
    private val supabaseUrl: String,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun generateUploadCredentials(
        userId: UserId,
        mimeType: String
    ): ProfilePictureUploadCredentials {
        return supabaseStorageService.generateSignedUploadUrl(
            userId = userId,
            mimeType = mimeType
        )
    }

    @Transactional
    fun deleteProfilePicture(userId: UserId) {
        val participant = chatParticipantRepository.findByIdOrNull(userId)
            ?: throw ChatParticipantNotFoundException(userId)

        participant.profilePictureUrl?.let { url ->
            chatParticipantRepository.save(
                participant.apply { profilePictureUrl = null }
            )

            supabaseStorageService.deleteFile(url)

            applicationEventPublisher.publishEvent(
                ProfilePictureUpdatedEvent(
                    userId = userId,
                    newUrl = null
                )
            )
        }
    }

    @Transactional
    fun confirmProfilePictureUpload(
        userId: UserId,
        publicUrl: String
    ) {
        if (!publicUrl.startsWith(supabaseUrl)) {
            throw InvalidProfilePictureException("Invalid profile picture URL")
        }

        val participant = chatParticipantRepository.findByIdOrNull(userId)
            ?: throw ChatParticipantNotFoundException(userId)

        val oldUrl = participant.profilePictureUrl

        chatParticipantRepository.save(
            participant.apply { profilePictureUrl = publicUrl }
        )

        try {
            oldUrl?.let {
                supabaseStorageService.deleteFile(oldUrl)
            }
        } catch (e: Exception) {
            logger.warn("Deleting old profile picture for $userId fail", e)
        }

        applicationEventPublisher.publishEvent(
            ProfilePictureUpdatedEvent(
                userId = userId,
                newUrl = publicUrl
            )
        )
    }
}