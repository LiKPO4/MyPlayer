package com.lijialin.myplayer

import android.content.Context
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.MotionEvent
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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.platform.PlatformView
import kotlin.math.max

object PlayerBridge {
    private var playerView: NativePlayerPlatformView? = null
    private var eventSink: EventChannel.EventSink? = null
    private var uiEventSink: EventChannel.EventSink? = null
    private var pendingVideos: List<EncryptedVideo> = emptyList()
    private val videosByUri = mutableMapOf<String, EncryptedVideo>()

    fun lookup(uri: android.net.Uri): EncryptedVideo? = videosByUri[uri.toString()]

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

    fun playAt(index: Int) = playerView?.playAt(index) ?: Unit
    fun setShuffle(enabled: Boolean) = playerView?.setShuffle(enabled) ?: Unit
    fun playPause() = playerView?.playPause() ?: Unit
    fun seekBy(deltaMs: Long) = playerView?.seekBy(deltaMs) ?: Unit
    fun seekTo(positionMs: Long) = playerView?.seekTo(positionMs) ?: Unit
    fun next() = playerView?.next() ?: Unit
    fun previous() = playerView?.previous() ?: Unit
    fun setSpeed(speed: Float) = playerView?.setSpeed(speed) ?: Unit
    fun setEndAction(action: String) = playerView?.setEndAction(action) ?: Unit

    /** 标题在后台扫描后解析出来时，直接刷新播放列表与当前页标题，不重建播放器。 */
    fun updateTitle(uri: String, title: String) {
        playerView?.updateTitle(uri, title)
    }

    fun setEventSink(sink: EventChannel.EventSink?) {
        eventSink = sink
        playerView?.emitState()
    }

    fun emitState(state: Map<String, Any?>) {
        eventSink?.success(state)
    }

    fun setUiEventSink(sink: EventChannel.EventSink?) {
        uiEventSink = sink
    }

    fun emitUiEvent(event: String) {
        uiEventSink?.success(event)
    }
}

/**
 * Native short-video feed: ViewPager2 owns vertical touch/settling while a three-player
 * pool keeps only the selected page and its immediate neighbours prepared.
 */
@UnstableApi
class NativePlayerPlatformView(context: Context) : PlatformView {
    private val handler = Handler(Looper.getMainLooper())
    private val tapGestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(event: MotionEvent): Boolean = true

            // 抬指即通知：菜单可见时立即隐藏，不等双击确认窗口。
            override fun onSingleTapUp(event: MotionEvent): Boolean {
                PlayerBridge.emitUiEvent("tap_up")
                return true
            }

            // 双击确认窗口过后才通知：菜单不可见时才呼出，双击暂停全程菜单不闪现。
            override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
                PlayerBridge.emitUiEvent("tap_confirmed")
                return true
            }

            override fun onDoubleTap(event: MotionEvent): Boolean {
                PlayerBridge.emitUiEvent("double_tap_playpause")
                return true
            }
        }
    )
    private val dataSourceFactory = EncryptedVideoDataSource.Factory(context) { uri -> PlayerBridge.lookup(uri) }
    private val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(dataSourceFactory)
    private val pager = ViewPager2(context).apply {
        orientation = ViewPager2.ORIENTATION_VERTICAL
        offscreenPageLimit = 1
        setBackgroundColor(Color.BLACK)
    }
    private val players = List(3) {
        PlayerSlot(ExoPlayer.Builder(context).setMediaSourceFactory(mediaSourceFactory).build())
    }
    private val adapter = VideoPagerAdapter()
    private var videos: List<EncryptedVideo> = emptyList()
    /** Maps a pager page to the original source playlist index. */
    private var pageOrder: List<Int> = emptyList()
    private var currentPage = 0
    private var shuffleEnabled = false
    private var shouldPlay = false
    private var speed = 1f
    private var endAction = "next"
    private var disposed = false
    private var userDragging = false
    /**
     * ViewPager2 reports a new selected page before its settling animation has finished.
     * Reassigning a decoder in that window interrupts the surface currently moving on screen.
     */
    private var pageActivationPending = false

    private val progressTicker = object : Runnable {
        override fun run() {
            emitState()
            handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
        }
    }

    init {
        pager.adapter = adapter
        (pager.getChildAt(0) as? RecyclerView)?.let { recyclerView ->
            recyclerView.itemAnimator = null
            recyclerView.setOnTouchListener { _, event ->
                tapGestureDetector.onTouchEvent(event)
                false
            }
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrollStateChanged(state: Int) {
                when (state) {
                    ViewPager2.SCROLL_STATE_DRAGGING -> userDragging = true
                    ViewPager2.SCROLL_STATE_IDLE -> {
                        userDragging = false
                        if (pageActivationPending) {
                            pageActivationPending = false
                            activateCurrentPage()
                        }
                    }
                }
            }

            override fun onPageSelected(position: Int) {
                if (position == currentPage && activeSlot()?.position == position) return
                if (userDragging) shouldPlay = true
                currentPage = position
                // The old and new pages are already backed by the existing adjacent slots.
                // Keep those surfaces intact until the snap animation has fully settled.
                pageActivationPending = true
                if (pager.scrollState == ViewPager2.SCROLL_STATE_IDLE) {
                    pageActivationPending = false
                    activateCurrentPage()
                } else {
                    emitState()
                }
            }
        })
        players.forEach { slot ->
            slot.player.addListener(object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    if (
                        slot.position == currentPage &&
                        shouldPlay &&
                        player.playbackState == Player.STATE_ENDED &&
                        !slot.endHandled
                    ) {
                        slot.endHandled = true
                        handler.post {
                            if (
                                !disposed &&
                                shouldPlay &&
                                slot.position == currentPage &&
                                player.playbackState == Player.STATE_ENDED
                            ) handlePlaybackEnded(player)
                        }
                    }
                    if (slot.position == currentPage) emitState()
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (slot.position == currentPage && !disposed) {
                        val missingBytes = currentVideo()?.incompleteBytes ?: 0L
                        if (missingBytes > 0L) {
                            // 文件被截断（moov 声明的媒体范围超出文件末尾）：停下并把原因
                            // 交给 Flutter 提示，不再静默跳到下一条。
                            shouldPlay = false
                            handler.post { PlayerBridge.emitUiEvent("incomplete_media:$missingBytes") }
                            emitState()
                            return
                        }
                        handler.post { next() }
                    }
                    emitState()
                }
            })
        }
        PlayerBridge.attach(this)
        handler.post(progressTicker)
    }

    fun setPlaylist(nextVideos: List<EncryptedVideo>) {
        clearPreparedPages()
        videos = nextVideos
        shouldPlay = false
        pageOrder = videos.indices.toList()
        currentPage = 0
        adapter.notifyDataSetChanged()
        if (pageOrder.isNotEmpty()) {
            pager.setCurrentItem(0, false)
            refreshPreparedPages()
        } else {
            clearPreparedPages()
            emitState()
        }
    }

    fun playAt(sourceIndex: Int) {
        val page = pageOrder.indexOf(sourceIndex)
        if (page < 0) return
        shouldPlay = true
        moveToPage(page, smoothScroll = false)
    }

    /** 更新播放列表内某个视频的展示标题；当前页命中时立即推送状态刷新标题栏。 */
    fun updateTitle(uri: String, title: String) {
        val sourceIndex = videos.indexOfFirst { it.uri.toString() == uri }
        if (sourceIndex < 0) return
        val next = videos.toMutableList()
        next[sourceIndex] = next[sourceIndex].copy(displayName = title)
        videos = next
        if (sourceIndex == currentSourceIndex()) emitState()
    }

    fun setShuffle(enabled: Boolean) {
        if (shuffleEnabled == enabled) return
        val currentSourceIndex = currentSourceIndex()
        clearPreparedPages()
        shuffleEnabled = enabled
        pageOrder = if (enabled) shuffledPageOrder(currentSourceIndex) else videos.indices.toList()
        currentPage = max(0, pageOrder.indexOf(currentSourceIndex))
        adapter.notifyDataSetChanged()
        if (pageOrder.isNotEmpty()) {
            pager.setCurrentItem(currentPage, false)
            activateCurrentPage()
        }
        emitState()
    }

    fun playPause() {
        if (videos.isEmpty()) return
        shouldPlay = !shouldPlay
        activeSlot()?.player?.let { player ->
            if (shouldPlay) {
                if (player.playbackState == Player.STATE_IDLE) player.prepare()
                player.play()
            } else {
                player.pause()
            }
        }
        emitState()
    }

    fun seekBy(deltaMs: Long) {
        val player = activeSlot()?.player ?: return
        player.seekTo((player.currentPosition + deltaMs).coerceAtLeast(0L))
    }

    fun seekTo(positionMs: Long) {
        activeSlot()?.player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun next() {
        if (pageOrder.isEmpty()) return
        val wrapsToStart = currentPage == pageOrder.lastIndex && shuffleEnabled
        val target = when {
            currentPage < pageOrder.lastIndex -> currentPage + 1
            wrapsToStart -> 0
            else -> return
        }
        shouldPlay = true
        // A ViewPager cannot smoothly traverse last -> first without visibly flying
        // across every page. Shuffle wraps by an immediate reposition instead.
        moveToPage(target, smoothScroll = !wrapsToStart)
    }

    fun previous() {
        if (pageOrder.isEmpty()) return
        val wrapsToEnd = currentPage == 0 && shuffleEnabled
        val target = when {
            currentPage > 0 -> currentPage - 1
            wrapsToEnd -> pageOrder.lastIndex
            else -> return
        }
        shouldPlay = true
        moveToPage(target, smoothScroll = !wrapsToEnd)
    }

    fun setSpeed(nextSpeed: Float) {
        speed = nextSpeed
        players.forEach { it.player.playbackParameters = PlaybackParameters(speed) }
        emitState()
    }

    /** 播放完成后的行为：'next'（默认）/ 'replay' 重播 / 'stop' 停在结尾。 */
    fun setEndAction(action: String) {
        endAction = action
    }

    private fun handlePlaybackEnded(player: Player) {
        when (endAction) {
            "replay" -> {
                player.seekTo(0L)
                player.play()
            }
            "stop" -> {
                shouldPlay = false
                player.pause()
                emitState()
            }
            else -> next()
        }
    }

    private fun moveToPage(page: Int, smoothScroll: Boolean) {
        if (page !in pageOrder.indices) return
        if (page == currentPage) {
            activateCurrentPage()
            return
        }
        pager.setCurrentItem(page, smoothScroll)
        // Non-animated jumps may not dispatch a selection callback when recycled views are pending.
        if (!smoothScroll) {
            currentPage = page
            pageActivationPending = false
            activateCurrentPage()
        }
    }

    private fun activateCurrentPage() {
        if (currentPage !in pageOrder.indices) return
        refreshPreparedPages()
        players.forEach { slot ->
            val selected = slot.position == currentPage
            slot.player.playWhenReady = selected && shouldPlay
            if (!selected) slot.player.pause()
        }
        if (shouldPlay) activeSlot()?.player?.play()
        emitState()
    }

    /** Keeps only [currentPage - 1, currentPage, currentPage + 1] assigned to players. */
    private fun refreshPreparedPages() {
        val desired = (currentPage - 1..currentPage + 1).filter { it in pageOrder.indices }.toSet()
        desired.forEach { page ->
            if (players.none { it.position == page }) {
                val reusable = players.firstOrNull { it.position !in desired } ?: return@forEach
                reusable.position = page
                reusable.endHandled = false
                reusable.player.setMediaItem(mediaItemForPage(page))
                reusable.player.playbackParameters = PlaybackParameters(speed)
                reusable.player.prepare()
                reusable.player.pause()
            }
        }
        players.filter { it.position !in desired }.forEach { slot ->
            slot.position = NO_PAGE
            slot.player.pause()
            slot.player.clearMediaItems()
        }
        adapter.bindPreparedPlayers(players.associateBy { it.position })
    }

    private fun clearPreparedPages() {
        players.forEach { slot ->
            slot.position = NO_PAGE
            slot.player.pause()
            slot.player.clearMediaItems()
        }
        adapter.bindPreparedPlayers(emptyMap())
    }

    private fun mediaItemForPage(page: Int): MediaItem {
        val video = videos[pageOrder[page]]
        return MediaItem.Builder()
            .setUri(video.uri)
            .setMimeType(MimeTypes.VIDEO_MP4)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(video.displayName).build())
            .build()
    }

    private fun activeSlot(): PlayerSlot? = players.firstOrNull { it.position == currentPage }

    private fun currentSourceIndex(): Int = pageOrder.getOrNull(currentPage) ?: 0

    private fun currentVideo(): EncryptedVideo? = videos.getOrNull(currentSourceIndex())

    private fun shuffledPageOrder(currentSourceIndex: Int): List<Int> {
        if (videos.isEmpty()) return emptyList()
        // Every source page appears once. Flutter still receives its original index, so
        // played-history tracking remains correct even while the visual order is random.
        val order = videos.indices.toMutableList()
        order.shuffle()
        val currentPosition = order.indexOf(currentSourceIndex)
        if (currentPosition >= 0) {
            order.removeAt(currentPosition)
            order.add(0, currentSourceIndex)
        }
        return order
    }

    fun emitState() {
        val active = activeSlot()?.player
        val sourceIndex = currentSourceIndex()
        val duration = active?.duration?.takeIf { it != C.TIME_UNSET } ?: 0L
        PlayerBridge.emitState(
            mapOf(
                "positionMs" to (active?.currentPosition ?: 0L).coerceAtLeast(0L),
                "durationMs" to duration.coerceAtLeast(0L),
                "bufferedMs" to (active?.bufferedPosition ?: 0L).coerceAtLeast(0L),
                "isPlaying" to (active?.isPlaying ?: false),
                "speed" to speed,
                "currentIndex" to sourceIndex,
                "title" to videos.getOrNull(sourceIndex)?.displayName.orEmpty()
            )
        )
    }

    override fun getView(): View = pager

    override fun dispose() {
        disposed = true
        handler.removeCallbacks(progressTicker)
        pager.adapter = null
        PlayerBridge.detach(this)
        players.forEach { it.player.release() }
    }

    private class VideoHolder(view: View) : RecyclerView.ViewHolder(view) {
        val playerView: PlayerView = view.findViewById(R.id.video_player)
    }

    private inner class VideoPagerAdapter : RecyclerView.Adapter<VideoHolder>() {
        private val holders = linkedMapOf<Int, VideoHolder>()
        private var preparedPlayers: Map<Int, PlayerSlot> = emptyMap()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.video_pager_page, parent, false)
            return VideoHolder(view)
        }

        override fun onBindViewHolder(holder: VideoHolder, position: Int) {
            holders.entries.removeAll { it.value === holder }
            holders[position] = holder
            bindHolder(holder, position)
        }

        override fun onViewRecycled(holder: VideoHolder) {
            holders.entries.removeAll { it.value === holder }
            holder.playerView.player = null
            super.onViewRecycled(holder)
        }

        override fun getItemCount(): Int = pageOrder.size

        fun bindPreparedPlayers(nextPlayers: Map<Int, PlayerSlot>) {
            preparedPlayers = nextPlayers
            // Detach every stale target before reusing a player on another page.
            holders.forEach { (position, holder) ->
                val nextPlayer = preparedPlayers[position]?.player
                if (holder.playerView.player !== nextPlayer) holder.playerView.player = null
            }
            holders.forEach { (position, holder) -> bindHolder(holder, position) }
        }

        private fun bindHolder(holder: VideoHolder, position: Int) {
            holder.playerView.player = preparedPlayers[position]?.player
        }

    }

    private data class PlayerSlot(
        val player: ExoPlayer,
        var position: Int = NO_PAGE,
        var endHandled: Boolean = false
    )

    private companion object {
        const val NO_PAGE = -1
        const val PROGRESS_UPDATE_INTERVAL_MS = 500L
    }
}
