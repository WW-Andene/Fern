package com.andene.fern.canvas

import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * Removes every element matching [predicate]; returns whether any was removed. Use instead of
 * `removeAll { }` on a [SnapshotStateList]: that is Kotlin's generic MutableList extension,
 * which removes matches one at a time, and each removal rebuilds the snapshot list's persistent
 * structure - erasing one stroke from 10k took 411 ms (ScaleBenchmark), against 1.4 ms for
 * this single filter-and-replace (two list mutations however many elements go).
 */
fun <T> SnapshotStateList<T>.removeWhere(predicate: (T) -> Boolean): Boolean {
    val kept = filterNot(predicate)
    if (kept.size == size) return false
    clear()
    addAll(kept)
    return true
}
