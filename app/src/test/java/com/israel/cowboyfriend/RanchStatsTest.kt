package com.israel.cowboyfriend

import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.RanchStats
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class RanchStatsTest {

    private val now = 1_800_000_000_000L
    private val day = 86_400_000L

    private fun born(year: Int) = Calendar.getInstance().apply { set(year, Calendar.JUNE, 15) }.timeInMillis

    private fun cow(type: String, year: Int, dead: Boolean = false, lastSeen: Long? = now, health: List<String> = emptyList()): CowDetails {
        val c = CowDetails(1, null, null, null, null, null, 1.0, 2.0, null, lastSeen, type, lastSeen, true)
        c.isCorpse = dead
        c.created_at = born(year)
        c.healthIssues = health
        return c
    }

    @Test
    fun herdCountsAliveDeadAndTypes() {
        val stats = RanchStats(listOf(cow("עגל", 2026), cow("עגל", 2026), cow("פרה", 2020), cow("שור", 2019, dead = true)), now)
        assertEquals(3, stats.alive)
        assertEquals(1, stats.dead)
        assertEquals(2, stats.aliveByKind[0])
        assertEquals(1, stats.aliveByKind[2])
    }

    @Test
    fun healthProblemsAreCountedPerCode() {
        val stats = RanchStats(
            listOf(
                cow("פרה", 2020, health = listOf("lameness", "swollen_udder")),
                cow("פרה", 2021, health = listOf("lameness", "other:limping")),
                cow("פרה", 2022)
            ), now
        )
        assertEquals(2, stats.cowsWithHealthProblem)
        assertEquals("lameness" to 2, stats.healthByCode.first())
        assertEquals(1, stats.healthByCode.first { it.first == "other" }.second)
    }

    @Test
    fun mortalityByYearOfBirth() {
        val stats = RanchStats(
            listOf(cow("עגל", 2025), cow("עגל", 2025, dead = true), cow("עגל", 2025), cow("פרה", 2024)), now
        )
        val y2025 = stats.years.first { it.year == 2025 }
        assertEquals(3, y2025.registered)
        assertEquals(1, y2025.dead)
        assertEquals(33, y2025.mortalityPercent)
        assertEquals(listOf(2025, 2024), stats.years.map { it.year })
    }

    @Test
    fun attentionCountsCowsAndPoints() {
        //a cow not seen for 20 days needs attention; a cow seen now does not
        val stats = RanchStats(listOf(cow("פרה", 2020, lastSeen = now - 20 * day), cow("פרה", 2021)), now)
        assertEquals(1, stats.attentionCows)
        assertEquals(1, stats.attentionByKey["not_seen_14d"])
        assertEquals(2, stats.allPoints.size)
        assertEquals(1, stats.attentionPoints.size)
    }
}
