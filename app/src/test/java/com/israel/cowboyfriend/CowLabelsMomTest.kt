package com.israel.cowboyfriend

import com.israel.cowboyfriend.global.CowLabels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

//the name of a mother: number, an apostrophe and the last two digits of her birth year (12 ’24 = cow 12 born in 2024)
class CowLabelsMomTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun millis(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(utc).apply { clear(); set(year, month, day, 12, 0) }.timeInMillis

    @Test
    fun numberAndTwoDigitsOfTheBirthYear() {
        assertEquals("12 ’24",CowLabels.momLabel(12, millis(2024, Calendar.MARCH, 5), utc))
    }

    @Test
    fun yearBelowTenKeepsTwoDigits() {
        assertEquals("7 ’05",CowLabels.momLabel(7, millis(2005, Calendar.JANUARY, 20), utc))
    }

    @Test
    fun unknownBirthDateShowsOnlyTheNumber() {
        assertEquals("12", CowLabels.momLabel(12, null, utc))
    }

    @Test
    fun noNumberNoLabel() {
        assertNull(CowLabels.momLabel(null, millis(2024, Calendar.MARCH, 5), utc))
    }
}
