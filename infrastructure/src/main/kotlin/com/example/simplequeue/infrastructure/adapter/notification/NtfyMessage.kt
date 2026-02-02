package com.example.simplequeue.infrastructure.adapter.notification

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class NtfyMessage(
    val topic: String,
    val message: String,
    val title: String? = null,
    val priority: Int? = null,
    val tags: List<String>? = null,
    val click: String? = null
)
