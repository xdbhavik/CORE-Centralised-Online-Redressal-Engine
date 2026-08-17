package com.SIH.mark1.model;

/**
 * Channel a complaint was lodged through.
 *
 * <p>Kept as String constants (rather than an enum) to match the existing
 * {@link SlaStatus} convention on {@link Complaint}, which keeps the column
 * human-readable in SQL and avoids ordinal-vs-name migration pitfalls.</p>
 */
public final class SourceChannel {

    /** Lodged from the citizen mobile app or web portal. */
    public static final String APP = "APP";

    /** Lodged by voice through the IVR. */
    public static final String IVR = "IVR";

    /** Created on a citizen's behalf by an admin or officer. */
    public static final String ADMIN = "ADMIN";

    private SourceChannel() {
        // constants holder
    }

    public static boolean isValid(String value) {
        return APP.equals(value) || IVR.equals(value) || ADMIN.equals(value);
    }
}
