package com.SIH.mark1.service;

import com.SIH.mark1.model.SystemSetting;
import com.SIH.mark1.repository.SystemSettingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runtime system settings (key-value). Currently used for the
 * AUTO_ASSIGNMENT_ENABLED toggle that switches officer assignment
 * between AI mode (automatic) and MANUAL mode (admin queue).
 */
@Service
public class SystemSettingService {

    private static final Logger log = LoggerFactory.getLogger(SystemSettingService.class);

    /** Setting key: "true" = AI auto-assigns officers, "false" = admin assigns manually. */
    public static final String KEY_AUTO_ASSIGNMENT_ENABLED = "AUTO_ASSIGNMENT_ENABLED";

    /** SLA resolution window (hours) for HIGH priority complaints. */
    public static final String KEY_SLA_HIGH_HOURS = "SLA_HIGH_HOURS";

    /** SLA resolution window (hours) for MEDIUM priority complaints. */
    public static final String KEY_SLA_MEDIUM_HOURS = "SLA_MEDIUM_HOURS";

    /** SLA resolution window (hours) for LOW priority complaints. */
    public static final String KEY_SLA_LOW_HOURS = "SLA_LOW_HOURS";

    /** Percentage of the SLA window after which a complaint is flagged NEAR_BREACH. */
    public static final String KEY_SLA_NEAR_BREACH_PERCENT = "SLA_NEAR_BREACH_PERCENT";


    private final SystemSettingRepository systemSettingRepository;

    public SystemSettingService(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = systemSettingRepository;
    }

    /**
     * Returns true when AI auto-assignment mode is ON.
     * Defaults to false (manual mode) when the setting row is missing.
     */
    @Transactional(readOnly = true)
    public boolean isAutoAssignmentEnabled() {
        return systemSettingRepository.findBySettingKey(KEY_AUTO_ASSIGNMENT_ENABLED)
                .map(setting -> "true".equalsIgnoreCase(setting.getSettingValue().trim()))
                .orElse(false);
    }

    /**
     * Switches AI auto-assignment ON/OFF. Creates the setting row if absent.
     *
     * @param enabled    new mode
     * @param adminUserId id of the admin performing the change (may be null)
     * @return the persisted setting
     */
    @Transactional
    public SystemSetting setAutoAssignmentEnabled(boolean enabled, Long adminUserId) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(KEY_AUTO_ASSIGNMENT_ENABLED)
                .orElseGet(() -> {
                    SystemSetting fresh = SystemSetting.builder()
                            .settingKey(KEY_AUTO_ASSIGNMENT_ENABLED)
                            .settingValue("false")
                            .description("When true, AI automatically assigns new complaints to the best-matched officer of the detected department. When false, complaints wait in the admin assignment queue.")
                            .build();
                    if (adminUserId != null) {
                        fresh.setCreatedBy(adminUserId);
                    }
                    return fresh;
                });
        setting.setSettingValue(String.valueOf(enabled));
        if (adminUserId != null) {
            setting.setUpdatedBy(adminUserId);
        }
        SystemSetting saved = systemSettingRepository.save(setting);
        log.info("Auto-assignment mode changed to {} by admin userId={}", enabled, adminUserId);
        return saved;
    }

    /**
     * Returns the raw setting row (for admin display) or null when not seeded yet.
     */
    @Transactional(readOnly = true)
    public SystemSetting getAutoAssignmentSetting() {
        return systemSettingRepository.findBySettingKey(KEY_AUTO_ASSIGNMENT_ENABLED).orElse(null);
    }

    // ──────────────────────────────────────────
    // Generic readers (used by SlaService)
    // ──────────────────────────────────────────

    /**
     * Reads a raw setting value, falling back to the supplied default when the row
     * is missing, blank, or the lookup fails (e.g. table not yet created).
     */
    @Transactional(readOnly = true)
    public String getSetting(String key, String fallback) {
        try {
            return systemSettingRepository.findBySettingKey(key)
                    .map(SystemSetting::getSettingValue)
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .orElse(fallback);
        } catch (Exception ex) {
            log.warn("Failed to read system setting '{}', using fallback {}: {}", key, fallback, ex.getMessage());
            return fallback;
        }
    }

    /**
     * Reads a positive integer setting. Non-numeric or non-positive values are
     * rejected in favour of the fallback so a bad admin entry can never produce
     * a zero-length SLA window.
     */
    @Transactional(readOnly = true)
    public int getIntSetting(String key, int fallback) {
        String raw = getSetting(key, null);
        if (raw == null) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(raw);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException ex) {
            log.warn("System setting '{}' is not a valid number ('{}'), using fallback {}", key, raw, fallback);
            return fallback;
        }
    }
}


