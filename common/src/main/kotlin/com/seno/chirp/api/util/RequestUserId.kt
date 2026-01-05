package com.seno.chirp.api.util

import com.seno.chirp.domain.exception.UnauthorizedException
import com.seno.chirp.domain.type.UserId
import org.springframework.security.core.context.SecurityContextHolder

val requestUserId: UserId
    get() = SecurityContextHolder.getContext().authentication?.principal as? UserId
        ?: throw UnauthorizedException()