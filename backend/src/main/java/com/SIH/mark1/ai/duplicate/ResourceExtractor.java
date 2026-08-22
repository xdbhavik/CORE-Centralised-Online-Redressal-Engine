package com.SIH.mark1.ai.duplicate;

import com.SIH.mark1.ai.dto.AIResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ResourceExtractor {

    private static final Pattern METER_PATTERN = Pattern.compile("\\b(?:meter|mtr|मीटर)\\s*(?:id|no|number|#)?\\s*[:\\-]?\\s*([a-z0-9][a-z0-9\\-_/]{2,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONNECTION_PATTERN = Pattern.compile("\\b(?:connection|conn)\\s*(?:id|no|number|#)?\\s*[:\\-]?\\s*([a-z0-9][a-z0-9\\-_/]{2,})", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\b(?:property|tax)\\s*(?:id|no|number|#)?\\s*[:\\-]?\\s*([a-z0-9][a-z0-9\\-_/]{2,})", Pattern.CASE_INSENSITIVE);

    public ExtractedResource fromAnalysis(AIResponse analysis, String text) {
        if (analysis != null && analysis.resourceIdentifier() != null && !analysis.resourceIdentifier().isBlank()) {
            String type = analysis.resourceType() == null || analysis.resourceType().isBlank()
                    ? "UNKNOWN"
                    : analysis.resourceType().trim().toUpperCase(Locale.ROOT);
            String identifier = analysis.resourceIdentifier().trim().toUpperCase(Locale.ROOT);
            return new ExtractedResource(type, identifier, hash(type + ":" + identifier));
        }
        return extract(text, analysis != null ? analysis.department() : null, analysis != null ? analysis.category() : null);
    }

    public ExtractedResource extract(String text, String department, String category) {
        String combined = normalize(text + " " + nullSafe(department) + " " + nullSafe(category));
        ExtractedResource meter = match(METER_PATTERN, text, "ELECTRICITY_METER");
        if (meter.hasIdentifier()) {
            return meter;
        }
        ExtractedResource connection = match(CONNECTION_PATTERN, text, combined.contains("water") ? "WATER_CONNECTION" : "UTILITY_CONNECTION");
        if (connection.hasIdentifier()) {
            return connection;
        }
        ExtractedResource property = match(PROPERTY_PATTERN, text, "PROPERTY");
        if (property.hasIdentifier()) {
            return property;
        }
        if (combined.contains("meter")) {
            return new ExtractedResource("ELECTRICITY_METER", null, null);
        }
        if (combined.contains("water connection") || combined.contains("connection")) {
            return new ExtractedResource("WATER_CONNECTION", null, null);
        }
        if (combined.contains("property tax") || combined.contains("property")) {
            return new ExtractedResource("PROPERTY", null, null);
        }
        return new ExtractedResource("UNKNOWN", null, null);
    }

    public String areaKey(String address, String ward, String city, String state, String pincode) {
        String raw = normalize(String.join(" ", nullSafe(ward), nullSafe(pincode), nullSafe(city), nullSafe(state), nullSafe(address)));
        return raw.isBlank() ? null : hash(raw);
    }

    private ExtractedResource match(Pattern pattern, String text, String type) {
        Matcher matcher = pattern.matcher(nullSafe(text));
        if (!matcher.find()) {
            return new ExtractedResource(type, null, null);
        }
        String identifier = matcher.group(1).trim().toUpperCase(Locale.ROOT);
        return new ExtractedResource(type, identifier, hash(type + ":" + identifier));
    }

    private String normalize(String text) {
        return nullSafe(text).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 12 && i < bytes.length; i++) {
                builder.append(String.format("%02x", bytes[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException ex) {
            return Integer.toHexString(value.hashCode());
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    public record ExtractedResource(String type, String identifier, String key) {
        public boolean hasIdentifier() {
            return identifier != null && !identifier.isBlank() && key != null && !key.isBlank();
        }
    }
}
