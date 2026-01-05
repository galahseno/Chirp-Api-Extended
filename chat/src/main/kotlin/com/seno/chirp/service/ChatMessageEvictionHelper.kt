package com.seno.chirp.service

import com.seno.chirp.domain.type.ChatId
import org.springframework.cache.annotation.CacheEvict
import org.springframework.stereotype.Component

@Component
class ChatMessageEvictionHelper {

    @CacheEvict(
        value = ["messages"],
        key = "#chatId",
    )
    fun evictMessagesCache(chatId: ChatId) {
        // NO_OP: Let spring handle the cache evict
    }
}