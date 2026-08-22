package com.SIH.mark1.ai.duplicate;

public enum ComplaintScope {
    INDIVIDUAL,
    LOCAL_AREA,
    PUBLIC_INFRASTRUCTURE;

    public static ComplaintScope from(String value) {
        if (value == null || value.isBlank()) {
            return LOCAL_AREA;
        }
        try {
            return ComplaintScope.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return LOCAL_AREA;
        }
    }
}
