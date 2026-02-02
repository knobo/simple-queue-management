package com.example.simplequeue.infrastructure

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(scanBasePackages = ["com.example.simplequeue"])
@EnableScheduling
open class SimpleQueueApplication

fun main(args: Array<String>) {
    runApplication<SimpleQueueApplication>(*args)
}
