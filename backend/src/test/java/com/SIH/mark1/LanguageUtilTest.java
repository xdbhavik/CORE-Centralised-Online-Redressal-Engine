package com.SIH.mark1;

import com.SIH.mark1.ai.util.LanguageUtil;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class LanguageUtilTest {

    private final LanguageUtil languageUtil = new LanguageUtil();

    @Test
    void detectsEnglish() {
        Assertions.assertEquals("en", languageUtil.detect("The street light is not working"));
    }

    @Test
    void detectsHinglishAsEnglishScript() {
        // Latin-script Hinglish has no Indic characters → detected as "en",
        // TranslationService routes such text to Sarvam with source "auto"
        Assertions.assertEquals("en", languageUtil.detect("bijli nahi aa rahi 3 din se"));
    }

    @Test
    void detectsHindi() {
        Assertions.assertEquals("hi", languageUtil.detect("ये घर के अंदर दस दिन से लाइट नहीं आ रही है"));
    }

    @Test
    void detectsGujarati() {
        Assertions.assertEquals("gu", languageUtil.detect("અમારા વિસ્તારમાં વીજળી નથી આવતી"));
    }

    @Test
    void detectsBengali() {
        Assertions.assertEquals("bn", languageUtil.detect("আমাদের এলাকায় জল আসছে না"));
    }

    @Test
    void detectsTamil() {
        Assertions.assertEquals("ta", languageUtil.detect("எங்கள் பகுதியில் மின்சாரம் இல்லை"));
    }

    @Test
    void detectsTelugu() {
        Assertions.assertEquals("te", languageUtil.detect("మా ప్రాంతంలో విద్యుత్ లేదు"));
    }

    @Test
    void detectsKannada() {
        Assertions.assertEquals("kn", languageUtil.detect("ನಮ್ಮ ಪ್ರದೇಶದಲ್ಲಿ ವಿದ್ಯುತ್ ಇಲ್ಲ"));
    }

    @Test
    void detectsMalayalam() {
        Assertions.assertEquals("ml", languageUtil.detect("ഞങ്ങളുടെ പ്രദേശത്ത് വൈദ്യുതി ഇല്ല"));
    }

    @Test
    void detectsPunjabi() {
        Assertions.assertEquals("pa", languageUtil.detect("ਸਾਡੇ ਇਲਾਕੇ ਵਿੱਚ ਬਿਜਲੀ ਨਹੀਂ ਆ ਰਹੀ"));
    }

    @Test
    void detectsOdia() {
        Assertions.assertEquals("od", languageUtil.detect("ଆମ ଅଞ୍ଚଳରେ ବିଦ୍ୟୁତ୍ ନାହିଁ"));
    }

    @Test
    void handlesBlankInput() {
        Assertions.assertEquals("UNKNOWN", languageUtil.detect(""));
        Assertions.assertEquals("UNKNOWN", languageUtil.detect(null));
        Assertions.assertEquals("UNKNOWN", languageUtil.detect("   "));
    }
}
