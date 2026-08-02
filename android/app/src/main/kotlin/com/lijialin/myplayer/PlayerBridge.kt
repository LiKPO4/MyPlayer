package com.lijialin.myplayer

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.platform.PlatformView

object PlayerBridge {
    private var playerView: NativePlayerPlatformView? = null
    private var eventSink: EventChannel.EventSink? = null
    private var pendingVideos: List<EncryptedVideo> = emptyList()
    private val videosByUri = mutableMapOf<String, EncryptedVideo>()

    fun lookup(uri: Uri): EncryptedVideo? = videosByUri[uri.toString()]

    fun attach(view: NativePlayerPlatformView) {
        playerView = view
        if (pendingVideos.isNotEmpty()) view.setPlaylist(pendingVideos)
        view.emitState()
    }

    fun detach(view: NativePlayerPlatformView) {
        if (playerView == view) playerView = null
    }

    fun setPlaylist(videos: List<EncryptedVideo>) {
        pendingVideos = videos
        videosByUri.clear()
        videos.associateByTo(videosByUri) { it.uri.toString() }
        playerView?.setPlaylist(videos)
    }

    fun playAt(index: Int) {
        playerView?.playAt(index)
    }

    fun setShuffle(enabled: Boolean) {
        playerView?.setShuffle(enabled)
    }

    fun playPause() {
        playerView?.playPause()
    }

    fun seekBy(deltaMs: Long) {
        playerView?.seekBy(deltaMs)
    }

    fun seekTo(positionMs: Long) {
        playerView?.seekTo(positionMs)
    }

    fun next() {
        playerView?.next()
    }

    fun previous() {
        playerView?.previous()
    }

    fun setSpeed(speed: Float) {
        playerView?.setSpeed(speed)
    }

    fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
        playerView?.emitState()
    }

    fun emitState(state: Map<String, Any?>) {
        eventSink?.success(state)
    }
}

@UnstableApi
class NativePlayerPlatformView(context: Context) : PlatformView {
    private val player: ExoPlayer
    private val playerView: PlayerView = PlayerView(context)
    private val handler = Handler(Looper.getMainLooper())
    private val progressTicker = object : Runnable {
        override fun run() {
            emitState()
            handler.postDelayed(this, 500L)
        }
    }
    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            emitState()
        }

        override fun onPlayerError(error: PlaybackException) {
            if (player.hasNextMediaItem()) {
                player.seekToNextMediaItem()
                player.playWhenReady = true
                player.prepare()
            }
            emitState()
        }
    }

    init {
        val dataSourceFactory = EncryptedVideoDataSource.Factory(context) { uri -> PlayerBridge.lookup(uri) }
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.addListener(listener)
        playerView.player = player
        playerView.useController = false
        PlayerBridge.attach(this)
        handler.post(progressTicker)
    }

    fun setPlaylist(videos: List<EncryptedVideo>) {
        val items = videos.map { video ->
            MediaItem.Builder()
                .setUri(video.uri)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(video.displayName).build())
                .build()
        }
        player.setMediaItems(items)
        player.prepare()
    }

    fun playAt(index: Int) {
        if (index < 0 || index >= player.mediaItemCount) return
        player.seekTo(index, 0L)
        player.playWhenReady = true
        player.prepare()
    }

    fun setShuffle(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        player.repeatMode = if (enabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        emitState()
    }

    fun playPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
            if (player.playbackState == Player.STATE_IDLE) player.prepare()
            player.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun next() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem()
            resumeAfterNavigation()
        } else if (player.shuffleModeEnabled && player.mediaItemCount > 0) {
            player.seekTo(0, 0L)
            resumeAfterNavigation()
        }
    }

    fun previous() {
        player.seekToPreviousMediaItem()
        resumeAfterNavigation()
    }

    fun setSpeed(speed: Float) {
        player.playbackParameters = PlaybackParameters(speed)
        emitState()
    }

    private fun resumeAfterNavigation() {
        player.playWhenReady = true
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        emitState()
    }

    fun emitState() {
        val duration = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
        val title = player.currentMediaItem
            ?.mediaMetadata
            ?.title
            ?.toString()
            .orEmpty()
        PlayerBridge.emitState(
            mapOf(
                "positionMs" to player.currentPosition.coerceAtLeast(0L),
                "durationMs" to duration.coerceAtLeast(0L),
                "bufferedMs" to player.bufferedPosition.coerceAtLeast(0L),
                "isPlaying" to player.isPlaying,
                "speed" to player.playbackParameters.speed,
                "currentIndex" to player.currentMediaItemIndex,
                "title" to title
            )
        )
    }

    override fun getView(): View = playerView

    override fun dispose() {
        handler.removeCallbacks(progressTicker)
        player.removeListener(listener)
        PlayerBridge.detach(this)
        playerView.player = null
        player.release()
    }
}
