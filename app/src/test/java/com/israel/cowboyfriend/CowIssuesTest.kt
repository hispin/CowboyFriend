package com.israel.cowboyfriend

import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.global.CowIssues
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

//the rules of "needs attention" must agree with the view cow_status of the database (supabase/tours_schema.sql)
class CowIssuesTest {

    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun cow(type: String, lastSeen: Long?, withMom: Long? = null, tag: Boolean? = true, lat: Double? = 1.0) =
        CowDetails(1, null, null, null, null, null, lat, lat, null, lastSeen, type, withMom, tag)

    @Test
    fun healthyCowSeenRecentlyHasNoIssue() {
        assertTrue(CowIssues.of(cow("פרה", now - day), now).isEmpty())
    }

    @Test
    fun calfWithoutMomOrTagIsReported() {
        val issues = CowIssues.of(cow("עגל", now - day, withMom = null, tag = false), now)
        assertEquals(listOf("calf_without_mom", "calf_without_tag"), issues)
    }

    @Test
    fun calfSeenWithItsMotherIsFine() {
        assertTrue(CowIssues.of(cow("עגל", now - day, withMom = now - day), now).isEmpty())
    }

    @Test
    fun notSeenForFourteenDaysAndNeverSeen() {
        assertEquals(listOf("not_seen_14d"), CowIssues.of(cow("פרה", now - 14 * day), now))
        assertEquals(listOf("never_seen"), CowIssues.of(cow("פרה", null), now))
    }

    @Test
    fun missingLocationAndHealthProblem() {
        val c = cow("שור", now, lat = null)
        c.healthIssues = listOf("lameness")
        assertEquals(listOf("health_issue", "no_location"), CowIssues.of(c, now))
    }

    @Test
    fun corpseNeverNeedsAttention() {
        val c = cow("עגל", null, tag = false)
        c.isCorpse = true
        assertTrue(CowIssues.of(c, now).isEmpty())
    }

    //a hard calving (a hard pull or worse) in the last 21 days: the cow is watched for the problems that follow it
    @Test
    fun hardCalvingInTheLastThreeWeeksIsReported() {
        val c = cow("פרה", now - day)
        c.lastCalvingAt = now - 20 * day
        c.lastCalvingDifficulty = 2
        assertEquals(listOf("hard_calving"), CowIssues.of(c, now))
    }

    @Test
    fun hardCalvingOlderThanThreeWeeksOrEasyCalvingIsNotReported() {
        val old = cow("פרה", now - day)
        old.lastCalvingAt = now - 21 * day
        old.lastCalvingDifficulty = 3
        assertTrue(CowIssues.of(old, now).isEmpty())

        val easy = cow("פרה", now - day)
        easy.lastCalvingAt = now - day
        easy.lastCalvingDifficulty = 1
        assertTrue(CowIssues.of(easy, now).isEmpty())
    }

    //"pregnant" is a state of the cow, not a problem: it does not make the cow need attention
    @Test
    fun aPregnantCowDoesNotNeedAttention() {
        val c = cow("פרה", now - day)
        c.healthIssues = listOf("pregnant")
        assertTrue(CowIssues.of(c, now).isEmpty())
    }

    @Test
    fun aPregnantCowWithARealProblemIsReported() {
        val c = cow("פרה", now - day)
        c.healthIssues = listOf("pregnant", "lameness")
        assertEquals(listOf("health_issue"), CowIssues.of(c, now))
    }
}
