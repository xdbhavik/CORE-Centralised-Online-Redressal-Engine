package com.SIH.mark1.ivr.model;

/**
 * States of the IVR call state machine.
 *
 * <p>Inbound registration walks:
 * {@code GREETING → LANG_SELECT → MAIN_MENU → RECORDING → TRANSCRIBING → CONFIRMING → COMPLETED}.</p>
 *
 * <p>Outbound verification calls start directly at {@link #VERIFYING_REGISTRATION} or
 * {@link #VERIFYING_RESOLUTION}. Terminal states are {@link #COMPLETED}, {@link #FAILED}
 * and {@link #ABANDONED}.</p>
 */
public enum IvrCallState {

    /** Call answered, welcome prompt played. */
    GREETING,

    /** Waiting for the language DTMF keypress. */
    LANG_SELECT,

    /** Waiting for the main-menu DTMF keypress (register / status). */
    MAIN_MENU,

    /** Waiting for the provider callback carrying the complaint audio. */
    RECORDING,

    /** Recording received; Sarvam speech-to-text-translate in progress. */
    TRANSCRIBING,

    /** Transcript read back; waiting for confirm (1) or re-record (2). */
    CONFIRMING,

    /** Caller is being asked for a complaint number to look up. */
    STATUS_LOOKUP,

    /** Outbound verification #1: did the citizen really register this complaint? */
    VERIFYING_REGISTRATION,

    /** Outbound verification #2: is the officer's resolution acceptable? */
    VERIFYING_RESOLUTION,

    /** Recording the caller's reason why a resolution was rejected. */
    RECORDING_REJECTION_REASON,

    /** Flow finished successfully. */
    COMPLETED,

    /** Unrecoverable error (Sarvam failure, repeated invalid input, etc.). */
    FAILED,

    /** Caller hung up before completing the flow. */
    ABANDONED;

    /** True when no further webhook is expected for this session. */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == ABANDONED;
    }
}
