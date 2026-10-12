package com.andene.fern.canvas

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/**
 * Voice notes attached to a document, as AAC files under `audio/`, named
 * `<documentId>_<timestamp>.m4a` so each document's notes can be listed and deleted with it.
 * Recording needs the RECORD_AUDIO permission, requested by the UI before [start].
 */
class AudioNotes(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var player: MediaPlayer? = null

    val isRecording: Boolean get() = recorder != null

    fun list(documentId: String): List<File> =
        dir(context).listFiles { file -> file.name.startsWith("${documentId}_") }.orEmpty().sortedByDescending { it.name }

    /** Starts recording a new note for [documentId]; returns false if the recorder couldn't start. */
    fun start(documentId: String): Boolean {
        stopPlayback()
        val file = File(dir(context), "${documentId}_${System.currentTimeMillis()}.m4a")
        val newRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return try {
            newRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            newRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            newRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            newRecorder.setOutputFile(file.absolutePath)
            newRecorder.prepare()
            newRecorder.start()
            recorder = newRecorder
            true
        } catch (e: Exception) {
            newRecorder.release()
            file.delete()
            false
        }
    }

    fun stop() {
        val active = recorder ?: return
        recorder = null
        // stop() throws if nothing was captured (stopped immediately); the file is then unusable.
        runCatching { active.stop() }
        active.release()
    }

    fun play(file: File, onDone: () -> Unit) {
        stopPlayback()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                stopPlayback()
                onDone()
            }
            prepare()
            start()
        }
    }

    fun stopPlayback() {
        player?.release()
        player = null
    }

    companion object {
        fun dir(context: Context): File = File(context.filesDir, "audio").apply { mkdirs() }

        /** Deletes every note belonging to [documentId] (called when the document is deleted). */
        fun deleteAll(context: Context, documentId: String) {
            dir(context).listFiles { file -> file.name.startsWith("${documentId}_") }?.forEach { it.delete() }
        }
    }
}
