package com.israel.cowboyfriend.classes

import com.israel.cowboyfriend.R

/**
 * How a calving went, as it is chosen on the new cow screen and saved in the column difficulty of the table calvings. the
 * numbers are the ones that are saved (do not change them): 0 no help, 1 an easy pull, 2 a hard pull, 3 a caesarean, 4 a calf
 * in a wrong position. a calving of 2 or more is a hard calving: the cow is watched for [HARD_DAYS] days after it
 * (see CowIssues), because the problems after a hard calving (infections of the udder and the womb) show up in these days.
 */
object CalvingDifficulty {

    const val NONE = 0
    const val EASY_PULL = 1
    const val HARD_PULL = 2
    const val CAESAREAN = 3
    const val WRONG_POSITION = 4

    //the names that are shown, in the order of the numbers above (the position in the list is the saved number)
    val LABELS = listOf(
        R.string.calving_diff_none,
        R.string.calving_diff_easy_pull,
        R.string.calving_diff_hard_pull,
        R.string.calving_diff_caesarean,
        R.string.calving_diff_wrong_position
    )

    //for how many days after a hard calving the cow needs attention
    const val HARD_DAYS = 21

    //true for a calving that needed a hard pull or more
    fun isHard(difficulty: Int): Boolean = difficulty >= HARD_PULL
}
