package com.israel.cowboyfriend

import com.israel.cowboyfriend.global.CowQuery
import com.israel.cowboyfriend.global.VoiceNumbers
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * the spoken search grammar of the cattle tour screen: number, optional year of birth, "comma" between cows
 */
class VoiceNumbersTest {

    @Test
    fun numberAndYear_isOneCowWithYear() {
        assertEquals(listOf(CowQuery(2, 26)), VoiceNumbers.parseCowQueries("2 26"))
    }

    @Test
    fun numberWithoutYear_isOneCowAnyYear() {
        assertEquals(listOf(CowQuery(7, null)), VoiceNumbers.parseCowQueries("7"))
    }

    @Test
    fun commaSeparatesCows() {
        assertEquals(
            listOf(CowQuery(2, 26), CowQuery(7, null), CowQuery(15, 24)),
            VoiceNumbers.parseCowQueries("2 26 פסיק 7 פסיק 15 24")
        )
    }

    @Test
    fun englishCommaAndCharacterComma() {
        assertEquals(listOf(CowQuery(3, null), CowQuery(4, 25)), VoiceNumbers.parseCowQueries("3 comma 4 25"))
        assertEquals(listOf(CowQuery(3, null), CowQuery(4, 25)), VoiceNumbers.parseCowQueries("3, 4 25"))
    }


    //the recognizer often writes "2 ... 26" as a time or a decimal number ("2:26", "2.26"), the digits still give the cow and the year
    @Test
    fun timeOrDecimalWrittenByTheRecognizer() {
        assertEquals(listOf(CowQuery(2, 26)), VoiceNumbers.parseCowQueries("2:26"))
        assertEquals(listOf(CowQuery(2, 26), CowQuery(7, null)), VoiceNumbers.parseCowQueries("2.26, 7"))
    }
    @Test
    fun fourDigitYear_isShortened() {
        assertEquals(listOf(CowQuery(2, 26)), VoiceNumbers.parseCowQueries("2 2026"))
    }

    @Test
    fun hebrewNumberWords() {
        assertEquals(listOf(CowQuery(4, null), CowQuery(7, null)), VoiceNumbers.parseCowQueries("ארבע פסיק ושבע"))
    }

    //the English recognizer may write a spoken number as a word (English app language)
    @Test
    fun englishNumberWords() {
        assertEquals(listOf(CowQuery(4, null), CowQuery(7, null)), VoiceNumbers.parseCowQueries("Four comma seven"))
        assertEquals("3", VoiceNumbers.extractNumber("number three"))
        assertEquals(listOf(12, 7), VoiceNumbers.extractNumbers("12 seven"))
        //ordinary words like "to" and "for" are not numbers
        assertEquals(emptyList<CowQuery>(), VoiceNumbers.parseCowQueries("going to look for cows"))
    }

    @Test
    fun noiseWordsAndEmptyGroupsAreIgnored() {
        assertEquals(listOf(CowQuery(5, null)), VoiceNumbers.parseCowQueries("פסיק פרה 5 פסיק פסיק"))
        assertEquals(emptyList<CowQuery>(), VoiceNumbers.parseCowQueries("שלום עולם"))
    }

    @Test
    fun labelOfAQuery() {
        assertEquals("2/26", CowQuery(2, 26).label())
        assertEquals("7", CowQuery(7, null).label())
        assertEquals("3/05", CowQuery(3, 5).label())
    }
}
