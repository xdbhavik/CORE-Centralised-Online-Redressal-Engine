package com.SIH.mark1.ai.config;

import com.SIH.mark1.ai.qdrant.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.client.RestClient;

/**
 * Core AI module configuration.
 * <p>
 * - Registers {@link AIProperties} and {@link RagProperties} config beans
 * - Provides shared {@link RestClient} for all AI HTTP calls
 * - Provides {@link ObjectMapper} for Qdrant JSON parsing
 * - Enables {@code @Async} for the {@code KnowledgeEventListener}
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({AIProperties.class, RagProperties.class, DuplicateProperties.class})
public class AIConfig {

    @Bean
    public RestClient aiRestClient(RestClient.Builder builder) {
        return builder.build();
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}

