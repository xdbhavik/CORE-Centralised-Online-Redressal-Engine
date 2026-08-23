package com.SIH.mark1.ivr.model;

import com.SIH.mark1.model.PreferredLanguage;

/**
 * Languages offered on the IVR language-selection menu (v1: Hindi, English, Gujarati).
 *
 * <p>Each entry maps a DTMF keypress to both the Sarvam BCP-47 language code used for
 * TTS/STT and the existing {@link PreferredLanguage} enum stored on the user record.</p>
 */
public enum IvrLanguage {

    HINDI("1", "hi-IN", PreferredLanguage.HINDI, "meera"),
    ENGLISH("2", "en-IN", PreferredLanguage.ENGLISH, "anushka"),
    GUJARATI("3", "gu-IN", PreferredLanguage.GUJARATI, "anushka");

    private final String digit;
    private final String sarvamCode;
    private final PreferredLanguage preferredLanguage;
    private final String speaker;

    IvrLanguage(String digit, String sarvamCode, PreferredLanguage preferredLanguage, String speaker) {
        this.digit = digit;
        this.sarvamCode = sarvamCode;
        this.preferredLanguage = preferredLanguage;
        this.speaker = speaker;
    }

    public String digit() {
        return digit;
    }

    /** BCP-47 code sent to Sarvam TTS / STT (e.g. {@code hi-IN}). */
    public String sarvamCode() {
        return sarvamCode;
    }

    public PreferredLanguage preferredLanguage() {
        return preferredLanguage;
    }

    /** Preferred Sarvam bulbul speaker voice for this language. */
    public String speaker() {
        return speaker;
    }

    /**
     * Resolves a DTMF keypress to a language, defaulting to Hindi for any
     * unrecognised or absent input so the call can always continue.
     */
    public static IvrLanguage fromDigit(String pressed) {
        if (pressed != null) {
            String trimmed = pressed.trim();
            for (IvrLanguage language : values()) {
                if (language.digit.equals(trimmed)) {
                    return language;
                }
            }
        }
        return HINDI;
    }

    /** Maps a stored user preference back to an IVR language, defaulting to Hindi. */
    public static IvrLanguage fromPreferredLanguage(PreferredLanguage preferred) {
        if (preferred != null) {
            for (IvrLanguage language : values()) {
                if (language.preferredLanguage == preferred) {
                    return language;
                }
            }
        }
        return HINDI;
    }
}
