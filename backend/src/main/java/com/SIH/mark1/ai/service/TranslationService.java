package com.SIH.mark1.ai.service;

import com.SIH.mark1.ai.client.OpenRouterClient;
import com.SIH.mark1.ai.client.SarvamTranslationClient;
import com.SIH.mark1.ai.dto.TranslationRequest;
import com.SIH.mark1.ai.dto.TranslationResponse;
import com.SIH.mark1.ai.util.LanguageUtil;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class TranslationService {

    private final LanguageUtil languageUtil;
    private final OpenRouterClient openRouterClient;
    private final SarvamTranslationClient sarvamTranslationClient;
    private final Map<String, String> civicPhraseMap = new LinkedHashMap<>();

    public TranslationService(LanguageUtil languageUtil,
                              OpenRouterClient openRouterClient,
                              SarvamTranslationClient sarvamTranslationClient) {
        this.languageUtil = languageUtil;
        this.openRouterClient = openRouterClient;
        this.sarvamTranslationClient = sarvamTranslationClient;
        civicPhraseMap.put("pani nahi aa raha", "no water supply");
        civicPhraseMap.put("पानी नहीं आ रहा", "no water supply");
        civicPhraseMap.put("paani nahi aa raha", "no water supply");
        civicPhraseMap.put("pani", "water");
        civicPhraseMap.put("paani", "water");
        civicPhraseMap.put("jal", "water");
        civicPhraseMap.put("bijli nahi aa rahi", "power outage");
        civicPhraseMap.put("बिजली नहीं आ रही", "power outage");
        civicPhraseMap.put("bijli", "electricity");
        civicPhraseMap.put("sadak", "road");
        civicPhraseMap.put("सड़क", "road");
        civicPhraseMap.put("rasta", "road");
        civicPhraseMap.put("raasta", "road");
        civicPhraseMap.put("rastha", "road");
        civicPhraseMap.put("rasta kharab", "damaged road");
        civicPhraseMap.put("raasta kharab", "damaged road");
        civicPhraseMap.put("sadak kharab", "damaged road");
        civicPhraseMap.put("gaddha", "pothole");
        civicPhraseMap.put("गड्ढा", "pothole");
        civicPhraseMap.put("kachra", "garbage");
        civicPhraseMap.put("कचरा", "garbage");
        civicPhraseMap.put("safai", "sanitation");
        civicPhraseMap.put("ganda pani", "dirty water");
        civicPhraseMap.put("गंदा पानी", "dirty water");
        civicPhraseMap.put("street light", "street light");
        civicPhraseMap.put("नाली", "drainage");
        civicPhraseMap.put("nali", "drainage");
        civicPhraseMap.put("naali", "drainage");
        civicPhraseMap.put("sewer", "sewer");
        // Hindi light / electricity phrases
        civicPhraseMap.put("लाइट नहीं आ रही", "power outage");
        civicPhraseMap.put("लाइट नहीं आ रहा", "power outage");
        civicPhraseMap.put("लाइट नहीं", "no light");
        civicPhraseMap.put("लाइट बंद", "power outage");
        civicPhraseMap.put("बत्ती नहीं", "no light");
        civicPhraseMap.put("बत्ती बंद", "power outage");
        civicPhraseMap.put("बिजली कटी", "power cut");
        civicPhraseMap.put("बिजली बंद", "power outage");
        civicPhraseMap.put("लाइट", "light");
        civicPhraseMap.put("बत्ती", "light");
        civicPhraseMap.put("बिजली", "electricity");
        civicPhraseMap.put("करंट", "electric current");
        civicPhraseMap.put("ट्रांसफार्मर", "transformer");
        // Common Hindi household / duration words
        civicPhraseMap.put("घर के अंदर", "inside the house");
        civicPhraseMap.put("घर", "house");
        civicPhraseMap.put("दस दिन", "ten days");
        civicPhraseMap.put("दिन से", "days");
        civicPhraseMap.put("दिन", "days");
        civicPhraseMap.put("जल्दी", "quickly");
        civicPhraseMap.put("फिक्स", "fix");
        civicPhraseMap.put("करो", "do");
        civicPhraseMap.put("नहीं आ रही", "not coming");
        civicPhraseMap.put("नहीं आ रहा", "not coming");
        // Gujarati words
        civicPhraseMap.put("પાણી", "water");
        civicPhraseMap.put("પાઇપ", "pipe");
        civicPhraseMap.put("નળ", "tap");
        civicPhraseMap.put("વીજળી", "electricity");
        civicPhraseMap.put("કરંટ", "current");
        civicPhraseMap.put("રોડ", "road");
        civicPhraseMap.put("રસ્તો", "road");
        civicPhraseMap.put("રસ્તા", "road");
        civicPhraseMap.put("સડક", "road");
        civicPhraseMap.put("ખાડો", "pothole");
        civicPhraseMap.put("ખાડા", "pothole");
        civicPhraseMap.put("કચરો", "garbage");
        civicPhraseMap.put("સફાઈ", "cleanliness");
        civicPhraseMap.put("નાળું", "drain");
        civicPhraseMap.put("ગટર", "drain");
        civicPhraseMap.put("પુલ", "bridge");
        civicPhraseMap.put("સેતુ", "bridge");
        civicPhraseMap.put("અકસ્માત", "accident");
        civicPhraseMap.put("તાત્કાલિક", "urgent");
        civicPhraseMap.put("આગ", "fire");
        civicPhraseMap.put("ખરાબ", "bad");
        civicPhraseMap.put("મહંગા", "expensive");
        civicPhraseMap.put("વડોદરા", "Vadodara");
        civicPhraseMap.put("અમદાવાદ", "Ahmedabad");
        civicPhraseMap.put("વિસ્તાર", "area");
    }

    public String normalizeForAI(String text, String language) {
        String detected = language == null || language.isBlank() ? languageUtil.detect(text) : language;
        // Process if Hindi/Hinglish OR any Indian language with known civic phrases
        if (!"hi".equalsIgnoreCase(detected) && !containsKnownIndianLanguage(text)) {
            return text;
        }
        String normalized = text;
        String lower = normalized.toLowerCase(Locale.ROOT);
        // Replace Indian language phrases/words with English equivalents
        for (Map.Entry<String, String> entry : civicPhraseMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (lower.contains(key.toLowerCase(Locale.ROOT))) {
                normalized = normalized.replace(key, value);
            }
        }
        return normalized;
    }


    /**
     * Translates any Indian language complaint text to English.
     * Order: already-English short-circuit → Sarvam AI translate → Nemotron (NIM) → keyword map.
     */
    public String translateToEnglish(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        String detected = languageUtil.detect(text);
        boolean hasIndianPhrases = containsKnownIndianLanguage(text);
        // Already English (no Indic script) and no known Indian-language phrases → nothing to do
        if ("en".equalsIgnoreCase(detected) && !hasIndianPhrases) {
            return text;
        }
        // Hinglish (Latin script + Indian phrases) → let Sarvam auto-detect instead of forcing en-IN
        String sarvamSource = "en".equalsIgnoreCase(detected) && hasIndianPhrases
                ? "auto"
                : toSarvamLanguageCode(detected);
        // 1) Sarvam AI translation (primary)
        Optional<String> sarvamResult = sarvamTranslationClient.translateToEnglish(text, sarvamSource);
        if (sarvamResult.isPresent() && !sarvamResult.get().isBlank()) {
            return sarvamResult.get();
        }
        // 2) Nemotron (NVIDIA NIM) translation fallback
        String prompt = "You are a translation engine for a government grievance system. "
                + "Translate the following citizen complaint to English. "
                + "Keep it natural and preserve all details. Return ONLY the English translation, no explanations.\n\n"
                + text;
        String aiResult = openRouterClient.complete(prompt).orElse(null);
        if (aiResult != null && !aiResult.isBlank()) {
            return aiResult;
        }
        // 3) Last resort: keyword-based replacement for Indian languages
        return normalizeForAI(text, detected);
    }

    /**
     * Maps internal language codes (hi, gu, en, UNKNOWN) to Sarvam BCP-47 codes.
     * Returns "auto" when the language cannot be determined.
     */
    private String toSarvamLanguageCode(String detected) {
        if (detected == null || detected.isBlank() || "UNKNOWN".equalsIgnoreCase(detected)) {
            return "auto";
        }
        String lower = detected.toLowerCase(Locale.ROOT);
        // Already a BCP-47 code like hi-IN / gu-IN
        if (lower.contains("-")) {
            return detected;
        }
        return switch (lower) {
            case "hi" -> "hi-IN";
            case "gu" -> "gu-IN";
            case "bn" -> "bn-IN";
            case "ta" -> "ta-IN";
            case "te" -> "te-IN";
            case "mr" -> "mr-IN";
            case "pa" -> "pa-IN";
            case "kn" -> "kn-IN";
            case "ml" -> "ml-IN";
            case "od" -> "od-IN";
            case "en" -> "en-IN";
            default -> "auto";
        };
    }

    /**
     * Guarantees the returned text is English. If the input still contains
     * Devanagari (detected as non-English), it is translated via Sarvam AI
     * (with Nemotron/keyword fallbacks). English input is returned unchanged.
     */
    public String ensureEnglish(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        if ("en".equalsIgnoreCase(languageUtil.detect(text))) {
            return text;
        }
        return translateToEnglish(text);
    }

    public String summarizeInEnglish(String text) {
        String normalized = normalizeForAI(text, null).toLowerCase(Locale.ROOT);
        if ((normalized.contains("pani") || normalized.contains("water"))
                && (normalized.contains("nahi aa raha") || normalized.contains("no water supply"))) {
            String duration = normalized.contains("3 din") || normalized.contains("three days") || normalized.contains("3 days")
                    ? " for the past three days"
                    : "";
            return "Residents are facing no water supply" + duration + ".";
        }
        if (normalized.contains("bijli") || normalized.contains("electric") || normalized.contains("power outage")) {
            String duration = normalized.contains("3 din") || normalized.contains("three days") || normalized.contains("3 days")
                    ? " for the past three days"
                    : "";
            return "Residents are facing a power outage" + duration + ".";
        }
        if (normalized.contains("garbage") || normalized.contains("kachra")) {
            return "Residents are facing a garbage collection issue.";
        }
        if (normalized.contains("road") || normalized.contains("sadak") || normalized.contains("rasta")
                || normalized.contains("raasta") || normalized.contains("rastha") || normalized.contains("pothole")
                || normalized.contains("damaged road")) {
            return "Residents are facing a road maintenance issue.";
        }
        // If no pattern matches, ensure we return English text by translating
        String englishText = translateToEnglish(text);
        String cleaned = englishText == null ? "" : englishText.replaceAll("\\s+", " ").trim();
        if (cleaned.length() <= 140) {
            return cleaned;
        }
        return cleaned.substring(0, 137).trim() + "...";
    }

    public TranslationResponse translate(TranslationRequest request) {
        String source = request.sourceLanguage() == null || request.sourceLanguage().isBlank()
                ? languageUtil.detect(request.text())
                : request.sourceLanguage();
        String target = request.targetLanguage() == null || request.targetLanguage().isBlank()
                ? "en"
                : request.targetLanguage();
        return new TranslationResponse(source, target, normalizeForAI(request.text(), source));
    }

    private boolean containsKnownHindiOrHinglish(String text) {
        return containsKnownIndianLanguage(text);
    }

    private boolean containsKnownIndianLanguage(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        return civicPhraseMap.keySet().stream().anyMatch(key -> lower.contains(key.toLowerCase(Locale.ROOT)));
    }
}