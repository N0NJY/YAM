package com.rickd.mushclient.data

/** How a pattern (a [Trigger] or a custom slash command's argument matcher) matches text. */
enum class PatternMatchType {
    /** `*` (0+ chars) and `?` (1 char), each captured in order into %0-%9. */
    WILDCARD,

    /** A Java regex; capture groups 1-10 map to %0-%9. */
    REGEXP,

    /** Plain substring match, no captures. */
    CONTAINS,
}
