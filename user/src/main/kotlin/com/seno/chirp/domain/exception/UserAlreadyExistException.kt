package com.seno.chirp.domain.exception

class UserAlreadyExistException: RuntimeException(
    "A user with this username or email already exists."
)