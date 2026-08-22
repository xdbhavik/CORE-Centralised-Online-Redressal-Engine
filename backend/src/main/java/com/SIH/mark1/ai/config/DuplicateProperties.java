package com.SIH.mark1.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Duplicate detection tuning — bound from {@code ai.duplicate.*} in application.properties.
 */
@ConfigurationProperties(prefix = "ai.duplicate")
public record DuplicateProperties(
        boolean enabled,
        int topK,
        double threshold,
        double highConfidence
) {
    public DuplicateProperties {
        if (topK <= 0) {
            topK = 10;
        }
        if (threshold <= 0) {
            threshold = 0.82;
        }
        if (highConfidence <= 0) {
            highConfidence = 0.90;
        }
    }
}
