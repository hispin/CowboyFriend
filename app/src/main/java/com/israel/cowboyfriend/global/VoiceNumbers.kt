package com.israel.cowboyfriend.global

/**
 * Turns spoken sentences into numbers, shared by the voice input of the new cow screen and the voice search
 * of the cattle tour screen.
 */
object VoiceNumbers {

    /**
     * pull a number out of a recognized sentence: "123", "1 2 3", "מספר 45", "ארבע".
     * returns the number as digits, or null when the sentence has no number (noise, other words)
     */
    fun extractNumber(text: String): String? {
        if (text.matches(Regex("""[\d\s]+"""))) {
            return text.replace(" ", "").toIntOrNull()?.toString()
        }
        Regex("""\d+""").find(text)?.let { match ->
            match.value.toIntOrNull()?.let { return it.toString() }
        }
        for (word in text.split(" ", ",", ".")) {
            hebrewWordToDigit(word)?.let { return it }
        }
        return null
    }


    /**
     * pull several numbers out of one spoken sentence, in the order they were said: "12 7 31" -> [12, 7, 31].
     * every separate number (digits or a Hebrew number word, also with the "ו" prefix like "ושבע") counts as one,
     * other words are ignored and the same number is returned once
     */
    fun extractNumbers(text: String): List<Int> {
        val numbers = LinkedHashSet<Int>()
        for (token in text.split(Regex("""[\s,.]+""")).filter { it.isNotBlank() }) {
            val digitGroups = Regex("""\d+""").findAll(token).mapNotNull { it.value.toIntOrNull() }.toList()
            if (digitGroups.isNotEmpty()) {
                numbers.addAll(digitGroups)
                continue
            }
            val word = hebrewWordToDigit(token)
                ?: if (token.length > 1 && token.startsWith("ו")) hebrewWordToDigit(token.substring(1)) else null
            word?.toIntOrNull()?.let { numbers.add(it) }
        }
        return numbers.toList()
    }
    //the words that separate one cow from the next one in a spoken search ("comma"; the Hebrew recognizer also writes
    //the English word "comma" as a Hebrew word, so those spellings count too) and the characters a recognizer may write
    private val SEPARATOR_WORDS = setOf("פסיק", "comma", "קומה", "קומא", "קומה.", ",")

    /**
     * turns a spoken search into a list of cow queries, in the order they were said. the grammar:
     * - a cow is a number, optionally followed by the last two digits of the year of birth: "2 26" = cow 2 born in 2026
     *   (a four digit year like 2026 is also understood)
     * - cows are separated by the word "פסיק" / "comma": "2 26 פסיק 7 פסיק 15 24"
     * - a number without a year means every cow with that number, in any year
     * words that are not numbers or separators (the end word, noise) are ignored
     */
    fun parseCowQueries(text: String): List<CowQuery> {
        val queries = ArrayList<CowQuery>()
        var numbers = ArrayList<Int>()

        //a finished group of numbers becomes one query: the first number is the cow, the second one the year
        fun closeGroup() {
            if (numbers.isNotEmpty()) {
                queries.add(CowQuery(numbers[0], numbers.getOrNull(1)?.let { it % 100 }))
            }
            numbers = ArrayList()
        }

        //a comma written as a character is separated from the words around it
        val tokens = text.replace(",", " , ").split(Regex("""\s+""")).filter { it.isNotBlank() }
        for (token in tokens) {
            if (token.trim('.', '!', '?').lowercase() in SEPARATOR_WORDS || token == ",") {
                closeGroup()
                continue
            }
            val digitGroups = Regex("""\d+""").findAll(token).mapNotNull { it.value.toIntOrNull() }.toList()
            if (digitGroups.isNotEmpty()) {
                numbers.addAll(digitGroups)
                continue
            }
            //a Hebrew number word, also with the "ו" prefix ("ושבע")
            val word = hebrewWordToDigit(token)
                ?: if (token.length > 1 && token.startsWith("ו")) hebrewWordToDigit(token.substring(1)) else null
            word?.toIntOrNull()?.let { numbers.add(it) }
        }
        closeGroup()
        return queries
    }

    /**
     * map a Hebrew number word (0-10) to its digit string, or null if not recognized
     */
    fun hebrewWordToDigit(text: String): String? {
        //the English number words are understood too (the English recognizer writes a spoken number as a word sometimes)
        englishWordToDigit(text)?.let { return it }
        return when(text){
            "אפס"-> "0"
            "אחד","אחת"-> "1"
            "שתיים","שניים","שתים","שנים"-> "2"
            "שלוש","שלושה"-> "3"
            "ארבע","ארבעה"-> "4"
            "חמש","חמישה"-> "5"
            "שש","שישה"-> "6"
            "שבע","שבעה"-> "7"
            "שמונה"-> "8"
            "תשע","תשעה"-> "9"
            "עשר","עשרה"-> "10"
            else-> null
        }
    }
}

/**
 * map an English number word (0-10) to its digit string, or null if not recognized ("to" / "for" are not numbers here:
 * they are common words and would turn ordinary sentences into numbers)
 */
private fun englishWordToDigit(text: String): String? {
    return when (text.trim('.', '!', '?').lowercase()) {
        "zero" -> "0"
        "one" -> "1"
        "two" -> "2"
        "three" -> "3"
        "four" -> "4"
        "five" -> "5"
        "six" -> "6"
        "seven" -> "7"
        "eight" -> "8"
        "nine" -> "9"
        "ten" -> "10"
        else -> null
    }
}

/**
 * one cow asked for by voice: its number, and the last two digits of its year of birth when the year was said
 * (null = any year)
 */
data class CowQuery(val number: Int, val year: Int?) {
    //how the query is shown on the search chip: "2/26" or just "7"
    fun label(): String = if (year != null) "$number/${"%02d".format(year)}" else number.toString()
}
