package com.andene.fern.canvas

import java.io.File

/**
 * A voice note being played back in step with the ink drawn while it was recorded (roadmap
 * item 130, Notability's audio sync): the recording ran from [startMillis] to [endMillis]
 * (epoch time), and playback is [positionMillis] into it. Ink drawn during the recording
 * ([Stroke.drawnAt]) that playback hasn't reached yet is shown faded.
 */
data class AudioReplay(val startMillis: Long, val endMillis: Long, val positionMillis: Long) {
    /** Drawn while this note was being recorded. */
    fun covers(stroke: Stroke): Boolean = stroke.drawnAt in startMillis..endMillis

    /** Drawn during the recording, later than playback has reached. */
    fun isAhead(stroke: Stroke): Boolean = covers(stroke) && stroke.drawnAt > startMillis + positionMillis
}

/** When note [file] started recording: its name ends in `_<epoch millis>.m4a` (see [AudioNotes.start]). */
fun recordingStart(file: File): Long? = file.nameWithoutExtension.substringAfterLast('_').toLongOrNull()

/** Where in the recording [stroke] was drawn, for jumping playback to it; null when it wasn't drawn during it. */
fun AudioReplay.positionOf(stroke: Stroke): Long? = if (covers(stroke)) stroke.drawnAt - startMillis else null
