package com.israel.cowboyfriend

import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.offline.CowJson
import com.israel.cowboyfriend.offline.LocalSummary
import com.israel.cowboyfriend.offline.LocalTour
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

//the cows and the tour as they are kept on the phone for the work without a network
class OfflineLocalTest {

    private val day = 86_400_000L
    private val tourStart = 1_000_000_000_000L
    private val tourEnd = tourStart + 90 * 60_000L

    private fun cow(number: Int, type: String, lastSeen: Long?, id: Int = number): CowDetails {
        val cow = CowDetails(number, null, null, null, "a@b.c", null, 32.9, 35.7, null, lastSeen, type, null, true)
        cow.id = id
        return cow
    }

    @Test
    fun aCowSurvivesTheJsonOfThePhone() {
        val cow = cow(12, "פרה", tourStart)
        cow.comment = "limping"
        cow.healthIssues = listOf("lameness", "other:thorn")
        cow.num_of_calvings = 3
        cow.momId = 7
        cow.created_at = tourStart - 400 * day
        cow.lastCalvingAt = tourStart - 3 * day
        cow.lastCalvingDifficulty = 2
        cow.hardCalvings = 1
        cow.pendingSync = true
        cow.createdLocallyAt = tourStart

        val back = CowJson.fromJson(JSONObject(CowJson.toJson(cow).toString()))

        assertEquals(12, back.number)
        assertEquals("limping", back.comment)
        assertEquals(listOf("lameness", "other:thorn"), back.healthIssues)
        assertEquals(3, back.num_of_calvings)
        assertEquals(7, back.momId)
        assertEquals(tourStart - 400 * day, back.created_at)
        assertEquals(2, back.lastCalvingDifficulty)
        assertEquals(1, back.hardCalvings)
        assertTrue(back.pendingSync)
        assertEquals(tourStart, back.createdLocallyAt)
    }

    @Test
    fun emptyValuesStayEmpty() {
        val cow = cow(5, "עגל", null)
        cow.latitude = null
        cow.longitude = null
        val back = CowJson.fromJson(JSONObject(CowJson.toJson(cow).toString()))
        assertNull(back.latitude)
        assertNull(back.last_seen_at)
        assertNull(back.comment)
        assertNull(back.momId)
        assertNull(back.num_of_calvings)
        assertFalse(back.isCorpse)
        assertFalse(back.pendingSync)
    }

    @Test
    fun theTourBarCountsTheCowsSeenSinceTheStart() {
        val tour = LocalTour("local-1", null, tourStart, "a@b.c", null)
        val cows = listOf(
            cow(1, "פרה", tourStart + 5 * 60_000L),
            cow(2, "פרה", tourStart - day),
            cow(3, "פרה", tourStart + 10 * 60_000L).also { it.isCorpse = true }
        )
        val state = tour.state(cows)
        //the dead cow is not counted at all
        assertEquals(2, state.alive)
        assertEquals(1, state.seen)
        assertEquals("local-1", state.openTourId)
        tour.serverId = "server-1"
        assertEquals("server-1", tour.state(cows).openTourId)
    }

    @Test
    fun theTemporarySummaryHasTheNumbersOfThePhone() {
        val tour = LocalTour("local-1", null, tourStart, "a@b.c", null)
        tour.touched.addAll(listOf(1, 2))
        tour.commentsAdded = 1
        tour.newCalves = 1
        val cows = listOf(
            cow(1, "פרה", tourStart + 5 * 60_000L),
            cow(2, "עגל", tourStart + 6 * 60_000L).also { it.isMarkedTag = false },
            cow(3, "פרה", tourStart - day)
        )

        val summary = LocalSummary.build(tour, cows, tourEnd)

        assertTrue(summary.getBoolean("provisional"))
        assertEquals(90, summary.getInt("duration_minutes"))
        assertEquals(3, summary.getJSONObject("herd").getInt("alive"))
        assertEquals(2, summary.getInt("seen"))
        assertEquals(1, summary.getJSONObject("not_seen").getInt("count"))
        assertEquals(3, summary.getJSONObject("not_seen").getJSONArray("numbers").getInt(0))
        assertEquals(2, summary.getInt("changed"))
        assertEquals(1, summary.getInt("comments_added"))
        assertEquals(1, summary.getJSONObject("new_calves").getInt("count"))
        //the calf has no ear tag and was not seen with its mother
        assertEquals(1, summary.getJSONObject("problems").getInt("calf_without_tag"))
        assertEquals(1, summary.getJSONObject("problems").getInt("calf_without_mom"))
    }

    @Test
    fun theTourRecordSurvivesTheJsonOfThePhone() {
        val tour = LocalTour("local-1", "server-1", tourStart, "a@b.c", tourEnd)
        tour.touched.add(4)
        tour.newCorpses = 2

        val back = LocalTour.fromJson(JSONObject(tour.toJson().toString()))

        assertEquals("local-1", back.localId)
        assertEquals("server-1", back.serverId)
        assertEquals(tourEnd, back.endedAt)
        assertFalse(back.isOpen)
        assertEquals(setOf(4), back.touched)
        assertEquals(2, back.newCorpses)
    }
}
