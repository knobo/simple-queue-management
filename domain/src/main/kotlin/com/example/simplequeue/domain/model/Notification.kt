package com.example.simplequeue.domain.model

data class Notification(
    val topic: String,
    val message: String,
    val title: String? = null,
    val priority: Priority = Priority.DEFAULT,
    val tags: List<String> = emptyList(),
    val click: String? = null
) {
    enum class Priority(val value: Int) {
        MIN(1),
        LOW(2),
        DEFAULT(3),
        HIGH(4),
        URGENT(5)
    }
}
