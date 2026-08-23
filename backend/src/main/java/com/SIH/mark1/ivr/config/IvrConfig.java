package com.SIH.mark1.ivr.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the IVR configuration property beans.
 *
 * <p>The HTTP client for Sarvam calls is the shared {@code aiRestClient} already provided
 * by {@code AIConfig}, so no additional client bean is required here.</p>
 */
@Configuration
@EnableConfigurationProperties({IvrProperties.class, SarvamVoiceProperties.class})
public class IvrConfig {
}
