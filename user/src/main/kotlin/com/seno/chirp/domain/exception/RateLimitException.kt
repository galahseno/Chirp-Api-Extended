package com.seno.chirp.domain.exception

class RateLimitException(
    val resetInSeconds: Long
): RuntimeException(
    "Rate limit exceeded. Please try again in $resetInSeconds seconds"
)