package com.israel.cowboyfriend.global

import com.israel.cowboyfriend.classes.CalvingDifficulty
import com.israel.cowboyfriend.classes.CowDetails
import com.israel.cowboyfriend.classes.HealthIssues

/**
 * Which cows need attention. the rules are the same as in the database (the view cow_status in supabase/tours_schema.sql),
 * so the list of the app and the summary of a tour agree. a corpse never needs attention.
 */
object CowIssues {

    //a cow that was not seen for this many days needs attention (the same number as in the view cow_status)
    const val NOT_SEEN_DAYS = 14

    private const val DAY_MILLIS = 86_400_000L

    /**
     * the problems of a cow, as the keys of the summary of a tour: calf_without_mom, calf_without_tag, health_issue,
     * hard_calving, not_seen_14d, never_seen, no_location (empty = nothing to attend)
     */
    fun of(cow: CowDetails, now: Long = System.currentTimeMillis()): List<String> {
        if (cow.isCorpse) return emptyList()
        val issues = ArrayList<String>()
        if (CowLabels.typeIndex(cow.cowType) == CowLabels.TYPE_CALF) {
            //the calf was not seen together with its mother at its last visit
            val withMom = cow.with_mom != null && cow.with_mom == cow.last_seen_at
            if (!withMom) issues.add("calf_without_mom")
            if (cow.isMarkedTag != true) issues.add("calf_without_tag")
        }
        if (HealthIssues.hasProblem(cow.healthIssues)) issues.add("health_issue")
        //a hard calving in the last days: the cow is watched for the problems that follow it
        if (isInHardCalvingPeriod(cow, now)) issues.add("hard_calving")
        val lastSeen = cow.last_seen_at
        if (lastSeen == null) issues.add("never_seen")
        else if ((now - lastSeen) / DAY_MILLIS >= NOT_SEEN_DAYS) issues.add("not_seen_14d")
        if (cow.latitude == null || cow.longitude == null) issues.add("no_location")
        return issues
    }

    //true when the last calving of the cow was hard and it was less than CalvingDifficulty.HARD_DAYS days ago
    fun isInHardCalvingPeriod(cow: CowDetails, now: Long = System.currentTimeMillis()): Boolean {
        val at = cow.lastCalvingAt ?: return false
        val difficulty = cow.lastCalvingDifficulty ?: return false
        return CalvingDifficulty.isHard(difficulty) && (now - at) / DAY_MILLIS < CalvingDifficulty.HARD_DAYS
    }

    //true when the cow has at least one problem
    fun needsAttention(cow: CowDetails): Boolean = of(cow).isNotEmpty()
}
