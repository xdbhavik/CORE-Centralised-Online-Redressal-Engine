package com.SIH.mark1.ivr.service;

import com.SIH.mark1.ivr.model.IvrLanguage;
import org.springframework.stereotype.Component;

/**
 * Citizen-facing copy for verification follow-ups, per language.
 *
 * <p>Only the <em>written</em> messages live here — app notifications and SMS. The spoken
 * side of verification is no longer built in code: with Sarvam AI Voice Agents the
 * conversation script (greeting, question, retries, sign-off) is authored in the Sarvam
 * dashboard, so the prompts that this class used to hold for Twilio's TwiML — "press 1 for
 * yes", the PIN challenge, the invalid-input re-ask — would now be a second, silently
 * diverging copy of the script rather than the thing citizens actually hear.</p>
 *
 * <p>What remains is what the agent cannot say: messages sent <em>after</em> a call failed to
 * reach anyone. Every message exists in all three languages; {@link #forLanguage} falls back
 * to Hindi rather than returning null so a missing translation degrades to a language the
 * citizen may still read instead of producing an empty notification.</p>
 */
@Component
public class VerificationPrompts {

    public String pendingNotificationTitle(IvrLanguage language) {
        return switch (forLanguage(language)) {
            case ENGLISH -> "Verification pending";
            case GUJARATI -> "ખરાઈ બાકી";
            default -> "सत्यापन लंबित";
        };
    }

    /**
     * Sent after a failed attempt when more retries remain.
     *
     * <p>Mentions the app as an alternative because a citizen whose phone we cannot reach
     * would otherwise have no way to move their complaint forward.</p>
     */
    public String pendingNotificationBody(IvrLanguage language, String complaintNo) {
        return switch (forLanguage(language)) {
            case ENGLISH -> "We tried to call you to verify complaint " + complaintNo
                    + " but could not reach you. We will try again shortly. "
                    + "You can also confirm it from the app.";
            case GUJARATI -> "ફરિયાદ " + complaintNo + " ની ખરાઈ માટે અમે તમને કોલ કર્યો પણ સંપર્ક થઈ શક્યો નહીં. "
                    + "અમે થોડા સમયમાં ફરી પ્રયાસ કરીશું. તમે એપ્લિકેશનમાંથી પણ ખરાઈ કરી શકો છો.";
            default -> "शिकायत " + complaintNo + " के सत्यापन के लिए हमने आपको कॉल किया लेकिन संपर्क नहीं हो सका। "
                    + "हम कुछ ही समय में पुनः प्रयास करेंगे। आप ऐप से भी इसकी पुष्टि कर सकते हैं।";
        };
    }

    /** Sent once every call attempt is spent; the app is now the only way to confirm. */
    public String unreachableNotificationBody(IvrLanguage language, String complaintNo) {
        return switch (forLanguage(language)) {
            case ENGLISH -> "We could not reach you by phone to verify complaint " + complaintNo
                    + ". Please open the app to confirm it.";
            case GUJARATI -> "ફરિયાદ " + complaintNo + " ની ખરાઈ માટે અમે તમારો ફોન પર સંપર્ક કરી શક્યા નહીં. "
                    + "કૃપા કરીને ખરાઈ કરવા એપ્લિકેશન ખોલો.";
            default -> "शिकायत " + complaintNo + " के सत्यापन के लिए हम आपसे फोन पर संपर्क नहीं कर सके। "
                    + "कृपया पुष्टि करने के लिए ऐप खोलें।";
        };
    }

    /**
     * Renders a complaint number so text-to-speech reads it intelligibly.
     *
     * <p>Passed to the Sarvam agent as a variable rather than spoken by us. Still needed
     * because "GRV-2026-000020" read verbatim comes out as an unintelligible run of
     * characters: the prefix is dropped and the digits spaced out so they are read one at a
     * time. The citizen only needs enough to recognise which complaint is being discussed.</p>
     */
    public String spokenComplaintNumber(String complaintNo) {
        if (complaintNo == null || complaintNo.isBlank()) {
            return "";
        }
        String digitsOnly = complaintNo.replaceAll("\\D", "");
        String tail = digitsOnly.length() > 6 ? digitsOnly.substring(digitsOnly.length() - 6) : digitsOnly;
        StringBuilder spaced = new StringBuilder(tail.length() * 2);
        for (int i = 0; i < tail.length(); i++) {
            if (i > 0) {
                spaced.append(' ');
            }
            spaced.append(tail.charAt(i));
        }
        return spaced.toString();
    }

    private IvrLanguage forLanguage(IvrLanguage language) {
        return language == null ? IvrLanguage.HINDI : language;
    }
}
