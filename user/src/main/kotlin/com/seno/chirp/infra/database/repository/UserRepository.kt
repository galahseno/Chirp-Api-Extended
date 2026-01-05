package com.seno.chirp.infra.database.repository

import com.seno.chirp.domain.type.UserId
import com.seno.chirp.infra.database.entities.UserEntity
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<UserEntity, UserId> {
    fun findUserByEmail(email: String): UserEntity?
    fun findUserByEmailOrUsername(email: String, username: String): UserEntity?
}