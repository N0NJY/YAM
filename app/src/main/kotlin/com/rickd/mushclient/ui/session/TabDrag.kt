package com.rickd.mushclient.ui.session

/**
 * Where a dragged tab would land if released now. [lefts]/[widths] describe the tabs in their
 * current order (same coordinate space); [dragged] is the dragged tab's index and [offset] how
 * far it has been dragged from its resting place. The tab's centre decides: it lands on
 * whichever tab that centre is over, and past either end it lands at that end.
 */
fun dropIndex(lefts: List<Float>, widths: List<Float>, dragged: Int, offset: Float): Int {
    if (lefts.isEmpty() || dragged !in lefts.indices) return dragged
    val centre = lefts[dragged] + widths[dragged] / 2f + offset
    lefts.indices.forEach { i -> if (centre < lefts[i] + widths[i]) return i }
    return lefts.lastIndex
}
