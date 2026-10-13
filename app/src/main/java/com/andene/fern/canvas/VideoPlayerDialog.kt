package com.andene.fern.canvas

import com.andene.fern.FernIcons

import com.andene.fern.FernPanel
import com.andene.fern.FernTextButton
import android.media.MediaPlayer
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import com.andene.fern.R

/**
 * Plays an embedded video (roadmap item 107) over the canvas: the platform's video view with
 * its media controller (play, pause, seek bar, time), starting at once, and a mute toggle.
 * Volume follows the device's media volume. Closing stops playback.
 */
@Composable
fun VideoPlayerDialog(video: ImageItem, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember(video) { video.videoFileName?.let { CanvasStorage.videoFile(context, it) } }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var muted by remember { mutableStateOf(false) }
    var view by remember { mutableStateOf<VideoView?>(null) }
    DisposableEffect(Unit) { onDispose { view?.stopPlayback() } }
    FernPanel(
        scrollContent = true,
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.video_play)) },
        text = {
            Column {
                if (file == null || !file.exists()) {
                    Text(stringResource(R.string.video_missing))
                    return@Column
                }
                AndroidView(
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            val controller = MediaController(ctx)
                            controller.setAnchorView(this)
                            setMediaController(controller)
                            setOnPreparedListener { mp ->
                                player = mp
                                mp.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                                start()
                                controller.show()
                            }
                            setVideoPath(file.absolutePath)
                            view = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().aspectRatio((video.widthWorld / video.heightWorld).toFloat().coerceIn(0.25f, 4f)),
                )
                IconButton(onClick = {
                    muted = !muted
                    player?.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f)
                }) {
                    Icon(
                        if (muted) FernIcons.VolumeOff else FernIcons.VolumeUp,
                        contentDescription = stringResource(if (muted) R.string.video_unmute else R.string.video_mute),
                    )
                }
            }
        },
        confirmButton = { FernTextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) } },
    )
}
