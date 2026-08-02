package com.lijialin.myplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("myplayer", MODE_PRIVATE) }
    private val scannerExecutor = Executors.newSingleThreadExecutor()
    private val videoByUri = mutableMapOf<String, EncryptedVideo>()
    private val videos = mutableListOf<EncryptedVideo>()
    private var sortMode = SortMode.TIME
    private var activeDirectoryUri: Uri? = null
    private var scanGeneration = 0

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var adapter: VideoAdapter
    private lateinit var statusText: TextView
    private lateinit var chooseFolderButton: Button
    private lateinit var refreshButton: Button
    private lateinit var sortButton: Button
    private lateinit var randomSwitch: Switch

    private val openDirectoryLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            persistDirectory(uri)
            scanDirectory(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupPlayer()
        setupList()
        setupActions()
        restorePreviousDirectory()
    }

    override fun onDestroy() {
        playerView.player = null
        player.release()
        scannerExecutor.shutdownNow()
        super.onDestroy()
    }

    private fun bindViews() {
        playerView = findViewById(R.id.playerView)
        statusText = findViewById(R.id.statusText)
        chooseFolderButton = findViewById(R.id.chooseFolderButton)
        refreshButton = findViewById(R.id.refreshButton)
        sortButton = findViewById(R.id.sortButton)
        randomSwitch = findViewById(R.id.randomSwitch)
    }

    private fun setupPlayer() {
        val dataSourceFactory = EncryptedVideoDataSource.Factory(this) { uri ->
            videoByUri[uri.toString()]
        }
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        playerView.player = player
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                adapter.setCurrentIndex(player.currentMediaItemIndex)
                updateStatus()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateStatus()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                statusText.text = "播放失败：${error.errorCodeName}"
            }
        })
    }

    private fun setupList() {
        adapter = VideoAdapter { index -> playAt(index) }
        findViewById<RecyclerView>(R.id.videoList).apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = this@MainActivity.adapter
        }
    }

    private fun setupActions() {
        chooseFolderButton.setOnClickListener {
            openDirectoryLauncher.launch(null)
        }
        refreshButton.setOnClickListener {
            activeDirectoryUri?.let(::scanDirectory) ?: run {
                statusText.text = getString(R.string.empty_state)
            }
        }
        sortButton.setOnClickListener {
            sortMode = if (sortMode == SortMode.TIME) SortMode.NAME else SortMode.TIME
            applySortedVideos(videos, preservePlayback = true)
        }
        randomSwitch.setOnCheckedChangeListener { _, checked ->
            player.shuffleModeEnabled = checked
            updateStatus()
        }
        updateSortButton()
    }

    private fun restorePreviousDirectory() {
        val uri = prefs.getString(KEY_DIRECTORY_URI, null)?.let(Uri::parse)
        if (uri == null) {
            statusText.text = getString(R.string.empty_state)
            return
        }

        if (hasPersistedReadPermission(uri)) {
            activeDirectoryUri = uri
            scanDirectory(uri)
        } else {
            prefs.edit().remove(KEY_DIRECTORY_URI).apply()
            statusText.text = "目录授权已失效，请重新选择文件夹"
        }
    }

    private fun persistDirectory(uri: Uri) {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        prefs.edit().putString(KEY_DIRECTORY_URI, uri.toString()).apply()
        activeDirectoryUri = uri
    }

    private fun hasPersistedReadPermission(uri: Uri): Boolean {
        return contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    private fun scanDirectory(uri: Uri) {
        val generation = ++scanGeneration
        activeDirectoryUri = uri
        setScanningEnabled(false)
        statusText.text = "正在扫描目录..."

        scannerExecutor.execute {
            val result = EncryptedVideoScanner.scan(this, uri)
            runOnUiThread {
                if (generation != scanGeneration) return@runOnUiThread
                setScanningEnabled(true)
                applySortedVideos(result.videos, preservePlayback = false)
                val countText = "找到 ${result.videos.size} 个可播放文件"
                val skipText = if (result.skippedUnsupported > 0) "，跳过 ${result.skippedUnsupported} 个不支持文件" else ""
                val failText = if (result.failed > 0) "，${result.failed} 个读取失败" else ""
                statusText.text = countText + skipText + failText
            }
        }
    }

    private fun setScanningEnabled(enabled: Boolean) {
        chooseFolderButton.isEnabled = enabled
        refreshButton.isEnabled = enabled
        sortButton.isEnabled = enabled
    }

    private fun applySortedVideos(newVideos: List<EncryptedVideo>, preservePlayback: Boolean) {
        val currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
        val currentPosition = player.currentPosition
        val wasPlaying = player.playWhenReady

        videos.clear()
        videos.addAll(VideoSorter.sort(newVideos, sortMode))
        videoByUri.clear()
        videos.associateByTo(videoByUri) { it.uri.toString() }
        adapter.submitList(videos)
        updateSortButton()
        rebuildPlayerQueue(currentUri, currentPosition, wasPlaying, preservePlayback)
        updateStatus()
    }

    private fun rebuildPlayerQueue(
        currentUri: String?,
        currentPosition: Long,
        wasPlaying: Boolean,
        preservePlayback: Boolean
    ) {
        player.stop()
        player.clearMediaItems()
        if (videos.isEmpty()) return

        val mediaItems = videos.map { video ->
            MediaItem.Builder()
                .setUri(video.uri)
                .setMimeType(MimeTypes.VIDEO_MP4)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(video.displayName)
                        .build()
                )
                .build()
        }
        val startIndex = if (preservePlayback && currentUri != null) {
            videos.indexOfFirst { it.uri.toString() == currentUri }.takeIf { it >= 0 } ?: 0
        } else {
            0
        }
        val startPosition = if (preservePlayback && currentUri != null) currentPosition else 0L

        player.setMediaItems(mediaItems, startIndex, startPosition)
        player.shuffleModeEnabled = randomSwitch.isChecked
        player.prepare()
        player.playWhenReady = preservePlayback && wasPlaying
        adapter.setCurrentIndex(if (preservePlayback && wasPlaying) startIndex else RecyclerView.NO_POSITION)
    }

    private fun playAt(index: Int) {
        if (index !in videos.indices) return
        if (player.mediaItemCount != videos.size) {
            rebuildPlayerQueue(null, 0L, wasPlaying = false, preservePlayback = false)
        }
        player.seekTo(index, 0L)
        player.playWhenReady = true
        player.prepare()
        adapter.setCurrentIndex(index)
        updateStatus()
    }

    private fun updateSortButton() {
        sortButton.text = when (sortMode) {
            SortMode.TIME -> getString(R.string.sort_by_time)
            SortMode.NAME -> getString(R.string.sort_by_name)
        }
    }

    private fun updateStatus() {
        if (videos.isEmpty()) {
            statusText.text = getString(R.string.empty_state)
            return
        }

        val index = player.currentMediaItemIndex
        val current = videos.getOrNull(index)
        val mode = if (player.shuffleModeEnabled) "随机" else "顺序"
        statusText.text = if (current != null) {
            "${mode}播放：${current.displayName}  (${index + 1}/${videos.size})"
        } else {
            "找到 ${videos.size} 个可播放文件，点击列表开始播放"
        }
    }

    private companion object {
        const val KEY_DIRECTORY_URI = "directory_uri"
    }
}
