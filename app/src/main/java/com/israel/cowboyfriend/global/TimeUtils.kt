package com.israel.cowboyfriend.global

import java.time.Instant
import java.time.OffsetDateTime

/**
 * turns a timestamptz text from the database (like 2026-09-03T18:41:02.882076+00:00) into milliseconds,
 * or null when the text is empty or not a date
 */
fun parseTimestampMillis(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(text).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(text).toEpochMilli() }
        .getOrNull()
}
