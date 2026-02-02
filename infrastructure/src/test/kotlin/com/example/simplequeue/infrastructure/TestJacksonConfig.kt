package com.example.simplequeue.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary

@TestConfiguration
class TestJacksonConfig {

    @Bean
    @Primary
    fun testObjectMapper(): ObjectMapper {
        return ObjectMapper()
    }
}
