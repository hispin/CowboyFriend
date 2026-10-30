package com.israel.cowboyfriend.classes

import com.israel.cowboyfriend.global.CowIssues
import com.israel.cowboyfriend.global.CowLabels
import java.util.Calendar

/**
 * The numbers of the ranch dashboard, calculated from the list of the cows (no extra request to the database):
 * the herd by type, what needs attention, the health problems, and the births and deaths by year of birth.
 * the calculation is separate from the screen, so it can be tested.
 */
class RanchStats(cows: List<CowDetails>, now: Long = System.currentTimeMillis()) {

    //the herd: the living cows, the dead ones, and the living cows by type (calf, first cow, cow, bull)
    val alive: Int = cows.count { !it.isCorpse }
    val dead: Int = cows.count { it.isCorpse }
    val aliveByKind: Map<Int, Int> = cows.filter { !it.isCorpse }
        .groupingBy { CowLabels.typeIndex(it.cowType) }.eachCount()

    //what needs attention (see CowIssues): the number of cows for every problem, and how many cows have at least one
    val attentionByKey: Map<String, Int>
    val attentionCows: Int

    //the health problems of the living cows: the number of cows for every problem code ("other" for every free text)
    val healthByCode: List<Pair<String, Int>>
    val cowsWithHealthProblem: Int

    //births and deaths: for every year of birth, how many cows were registered and how many of them are dead
    val years: List<YearStat>

    //the places of the living cows (longitude, latitude), and of the ones that need attention: for the heat map
    val allPoints: List<Pair<Double, Double>>
    val attentionPoints: List<Pair<Double, Double>>

    init {
        val byKey = LinkedHashMap<String, Int>()
        var withIssue = 0
        val health = HashMap<String, Int>()
        var withHealth = 0
        val all = ArrayList<Pair<Double, Double>>()
        val attention = ArrayList<Pair<Double, Double>>()
        for (cow in cows) {
            if (cow.isCorpse) continue
            val issues = CowIssues.of(cow, now)
            if (issues.isNotEmpty()) withIssue++
            for (issue in issues) byKey[issue] = (byKey[issue] ?: 0) + 1
            if (cow.healthIssues.isNotEmpty()) withHealth++
            for (code in HealthIssues.selectedCodes(cow.healthIssues)) health[code] = (health[code] ?: 0) + 1
            val lat = cow.latitude
            val lon = cow.longitude
            if (lat != null && lon != null) {
                all.add(lon to lat)
                if (issues.isNotEmpty()) attention.add(lon to lat)
            }
        }
        attentionByKey = byKey
        attentionCows = withIssue
        healthByCode = health.entries.sortedByDescending { it.value }.map { it.key to it.value }
        cowsWithHealthProblem = withHealth
        allPoints = all
        attentionPoints = attention

        val registered = HashMap<Int, Int>()
        val deadByYear = HashMap<Int, Int>()
        for (cow in cows) {
            val born = cow.created_at ?: continue
            val year = Calendar.getInstance().apply { timeInMillis = born }.get(Calendar.YEAR)
            registered[year] = (registered[year] ?: 0) + 1
            if (cow.isCorpse) deadByYear[year] = (deadByYear[year] ?: 0) + 1
        }
        years = registered.keys.sortedDescending().map { YearStat(it, registered[it] ?: 0, deadByYear[it] ?: 0) }
    }

    //the cows of one year of birth: [registered] cows, [dead] of them are dead
    data class YearStat(val year: Int, val registered: Int, val dead: Int) {
        //the share of the dead cows of the year, in percent (0 when nothing was registered)
        val mortalityPercent: Int get() = if (registered == 0) 0 else Math.round(dead * 100f / registered)
    }
}
