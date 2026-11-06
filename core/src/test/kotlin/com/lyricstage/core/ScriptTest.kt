package com.lyricstage.core

import com.lyricstage.core.text.Script
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptTest {
    @Test fun detectsEachIndicScript() {
        assertEquals(Script.DEVANAGARI, Script.detect("हम तेरे बिन अब रह नहीं सकते"))
        assertEquals(Script.BENGALI, Script.detect("আমরা তোমার ছায়ায় রব না পথে"))
        assertEquals(Script.GURMUKHI, Script.detect("ਮੈਂ ਹਾਂ ਤੇਰੀ ਗੀਤ ਦੀ ਰਾਗਨੀ"))
        assertEquals(Script.GUJARATI, Script.detect("તું જ મારી આખી દુનિયા છે"))
        assertEquals(Script.ODIA, Script.detect("ତୁଁ ମୋ ପ୍ରାଣର ସଙ୍ଗୀତ"))
        assertEquals(Script.TAMIL, Script.detect("இசை என் உயிரின் மொழி"))
        assertEquals(Script.TELUGU, Script.detect("నువ్వే నా పాటల వెలుగు"))
        assertEquals(Script.KANNADA, Script.detect("ನೀ ನನ್ನ ಹಾಡಿನ ಬೆಳಕು"))
        assertEquals(Script.MALAYALAM, Script.detect("നീ എന്റെ പാട്ടിന്റെ നിറം"))
    }

    @Test fun dominantScriptWinsOverSprinkles() {
        // a Tamil line with an English word stays Tamil
        assertEquals(Script.TAMIL, Script.detect("இசை என் உயிரின் மொழி music is my soul"))
        // mostly Latin with one Hindi word stays Latin
        assertEquals(Script.LATIN, Script.detect("the light of music रोशनी"))
        // plain Latin and Japanese keep working
        assertEquals(Script.LATIN, Script.detect("never gonna give you up"))
        assertEquals(Script.JAPANESE, Script.detect("夢ならばどれほどよかったでしょう"))
        assertEquals(Script.KOREAN, Script.detect("나의 목소리를 들어줘"))
    }

    @Test fun everyIndicScriptCountsAsIndic() {
        for (s in listOf(Script.DEVANAGARI, Script.BENGALI, Script.GURMUKHI, Script.GUJARATI, Script.ODIA, Script.TAMIL, Script.TELUGU, Script.KANNADA, Script.MALAYALAM)) {
            assertTrue("$s should be indic", s.isIndic)
        }
        assertTrue(!Script.LATIN.isIndic && !Script.JAPANESE.isIndic && !Script.KOREAN.isIndic && !Script.OTHER.isIndic)
    }

    @Test fun aksharaPacingAppliesToAllIndicScripts() {
        for (s in listOf(Script.DEVANAGARI, Script.TAMIL, Script.MALAYALAM)) {
            assertEquals(210.0, com.lyricstage.core.lyrics.WordTimingEstimator.msPerWeight(s), 0.0)
        }
        assertEquals(320.0, com.lyricstage.core.lyrics.WordTimingEstimator.msPerWeight(Script.LATIN), 0.0)
    }
}
