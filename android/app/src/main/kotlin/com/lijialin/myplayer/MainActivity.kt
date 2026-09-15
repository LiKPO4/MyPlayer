package com.lijialin.myplayer

import android.app.Activity
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.media3.common.util.UnstableApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@UnstableApi
class MainActivity : FlutterActivity() {
    private val scannerExecutor = Executors.newSingleThreadExecutor()
    private var nativeChannel: MethodChannel? = null
    private var scanEvents: EventChannel.EventSink? = null
    private var playerEvents: EventChannel.EventSink? = null
    private var pendingFolderPick: MethodChannel.Result? = null
    private var pendingUpdateDownloadId: Long? = null
    private val updateDownloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            if (downloadId != pendingUpdateDownloadId) return
            pendingUpdateDownloadId = null
            openDownloadedUpdate(downloadId)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        flutterEngine.platformViewsController.registry.registerViewFactory("myplayer/player", PlayerViewFactory())

        nativeChannel = MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "myplayer/native").also { channel ->
            channel.setMethodCallHandler { call, result ->
                when (call.method) {
                    "chooseFolder" -> chooseFolder(result)
                    "scanSavedFolder" -> scanSavedFolder(result)
                    "setPlaylist" -> {
                        val videos = parseVideos(call.argument<List<Map<String, Any>>>("videos"))
                        PlayerBridge.setPlaylist(videos)
                        result.success(null)
                    }
                    "playAt" -> {
                        PlayerBridge.playAt(call.argument<Int>("index") ?: 0)
                        result.success(null)
                    }
                    "setShuffle" -> {
                        PlayerBridge.setShuffle(call.argument<Boolean>("enabled") ?: false)
                        result.success(null)
                    }
                    "playPause" -> {
                        PlayerBridge.playPause()
                        result.success(null)
                    }
                    "seekBy" -> {
                        PlayerBridge.seekBy((call.argument<Number>("deltaMs") ?: 0).toLong())
                        result.success(null)
                    }
                    "seekTo" -> {
                        PlayerBridge.seekTo((call.argument<Number>("positionMs") ?: 0).toLong())
                        result.success(null)
                    }
                    "next" -> {
                        PlayerBridge.next()
                        result.success(null)
                    }
                    "previous" -> {
                        PlayerBridge.previous()
                        result.success(null)
                    }
                    "setSpeed" -> {
                        PlayerBridge.setSpeed((call.argument<Number>("speed") ?: 1.0).toFloat())
                        result.success(null)
                    }
                    "setEndAction" -> {
                        val action = call.argument<String>("action") ?: "next"
                        getPreferences(MODE_PRIVATE).edit().putString(KEY_PLAYBACK_END_ACTION, action).apply()
                        PlayerBridge.setEndAction(action)
                        result.success(null)
                    }
                    "setScreenBrightness" -> {
                        val value = (call.argument<Number>("value") ?: -1.0).toFloat()
                        // 仅作用于本页窗口（0..1），-1 表示恢复跟随系统，不改系统设置。
                        window.attributes = window.attributes.apply { screenBrightness = value }
                        result.success(null)
                    }
                    "getScreenBrightness" -> result.success(getScreenBrightness())
                    "setMusicVolume" -> {
                        val ratio = (call.argument<Number>("value") ?: 0.0).toFloat()
                        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
                        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        val target = (ratio * max).roundToInt().coerceIn(0, max)
                        audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        result.success(null)
                    }
                    "getMusicVolume" -> result.success(getMusicVolume())
                    "getRandomPlayedUris" -> {
                        result.success(getRandomPlayedUris().toList())
                    }
                    "markRandomPlayed" -> {
                        val uri = call.argument<String>("uri")
                        if (uri != null) markRandomPlayed(uri)
                        result.success(null)
                    }
                    "clearRandomPlayed" -> {
                        getPreferences(MODE_PRIVATE).edit().remove(KEY_RANDOM_PLAYED_URIS).apply()
                        result.success(null)
                    }
                    "getSettings" -> {
                        result.success(getSettings())
                    }
                    "setDefaultPlaybackSpeed" -> {
                        val speed = (call.argument<Number>("speed") ?: 1.0).toFloat()
                        getPreferences(MODE_PRIVATE).edit().putFloat(KEY_DEFAULT_PLAYBACK_SPEED, speed).apply()
                        result.success(null)
                    }
                    "setRandomIncludeSubfolders" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_RANDOM_INCLUDE_SUBFOLDERS, enabled).apply()
                        result.success(null)
                    }
                    "setDefaultShuffle" -> {
                        val enabled = call.argument<Boolean>("enabled") ?: false
                        getPreferences(MODE_PRIVATE).edit().putBoolean(KEY_DEFAULT_SHUFFLE, enabled).apply()
                        result.success(null)
                    }
                    "setSortMode" -> {
                        val mode = call.argument<String>("mode") ?: "name"
                        getPreferences(MODE_PRIVATE).edit().putString(KEY_SORT_MODE, mode).apply()
                        result.success(null)
                    }
                    "getAppVersion" -> {
                        result.success(getAppVersion())
                    }
                    "downloadAndInstallUpdate" -> {
                        downloadAndInstallUpdate(
                            call.argument<String>("url"),
                            call.argument<String>("fileName"),
                            result
                        )
                    }
                    "revealInFileManager" -> {
                        result.success(
                            revealInFileManager(
                                call.argument<String>("uri"),
                                call.argument<String>("parentUri")
                            )
                        )
                    }
                    else -> result.notImplemented()
                }
            }
        }

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "myplayer/scan_events").setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    scanEvents = events
                }

                override fun onCancel(arguments: Any?) {
                    scanEvents = null
                }
            }
        )

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "myplayer/player_events").setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    playerEvents = events
                    PlayerBridge.setEventSink(events)
                }

                override fun onCancel(arguments: Any?) {
                    playerEvents = null
                    PlayerBridge.setEventSink(null)
                }
            }
        )

        EventChannel(flutterEngine.dartExecutor.binaryMessenger, "myplayer/player_ui_events").setStreamHandler(
            object : EventChannel.StreamHandler {
                override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                    PlayerBridge.setUiEventSink(events)
                }

                override fun onCancel(arguments: Any?) {
                    PlayerBridge.setUiEventSink(null)
                }
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(updateDownloadReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(updateDownloadReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(updateDownloadReceiver)
        scannerExecutor.shutdownNow()
        super.onDestroy()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_OPEN_TREE) return

        val result = pendingFolderPick
        pendingFolderPick = null
        val uri = data?.data
        if (resultCode != Activity.RESULT_OK || uri == null) {
            result?.success(null)
            return
        }

        val flags = data.flags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        contentResolver.takePersistableUriPermission(uri, flags and Intent.FLAG_GRANT_READ_URI_PERMISSION)
        getPreferences(MODE_PRIVATE).edit().putString(KEY_DIRECTORY_URI, uri.toString()).apply()
        scan(uri, result)
    }

    private fun chooseFolder(result: MethodChannel.Result) {
        if (pendingFolderPick != null) {
            result.error("folder_picker_busy", "Folder picker is already open", null)
            return
        }
        pendingFolderPick = result
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }
        startActivityForResult(intent, REQUEST_OPEN_TREE)
    }

    private fun scanSavedFolder(result: MethodChannel.Result) {
        val uriString = getPreferences(MODE_PRIVATE).getString(KEY_DIRECTORY_URI, null)
        if (uriString == null) {
            result.success(null)
            return
        }
        val uri = Uri.parse(uriString)
        val allowed = contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
        if (!allowed) {
            getPreferences(MODE_PRIVATE).edit().remove(KEY_DIRECTORY_URI).apply()
            result.success(null)
            return
        }
        scan(uri, result)
    }

    private fun scan(uri: Uri, result: MethodChannel.Result?) {
        scannerExecutor.execute {
            var resultSent = false
            try {
                val scanner = EncryptedVideoScanner(this) { event -> emitScanEvent(event) }
                val snapshot = scanner.loadSnapshot(uri)
                if (snapshot != null) {
                    resultSent = true
                    resultOnUi(result) { success(snapshot.toMap()) }
                }

                val scanResult = scanner.scan(uri, emitReset = snapshot == null)
                if (!resultSent) {
                    resultOnUi(result) { success(scanResult.toMap()) }
                }
                scanner.enrichTitles(uri, scanResult.videos)
            } catch (error: Throwable) {
                if (!resultSent) {
                    resultOnUi(result) { error("scan_failed", error.message, null) }
                }
            }
        }
    }

    private fun emitScanEvent(event: Map<String, Any?>) {
        runOnUiThread {
            scanEvents?.success(event)
        }
    }

    private fun resultOnUi(result: MethodChannel.Result?, block: MethodChannel.Result.() -> Unit) {
        if (result == null) return
        runOnUiThread { result.block() }
    }

    private fun parseVideos(items: List<Map<String, Any>>?): List<EncryptedVideo> {
        return items.orEmpty().map { EncryptedVideo.fromMap(it) }
    }

    private fun getRandomPlayedUris(): Set<String> {
        return getPreferences(MODE_PRIVATE).getStringSet(KEY_RANDOM_PLAYED_URIS, emptySet()).orEmpty()
    }

    private fun markRandomPlayed(uri: String) {
        val next = getRandomPlayedUris().toMutableSet()
        next += uri
        getPreferences(MODE_PRIVATE).edit().putStringSet(KEY_RANDOM_PLAYED_URIS, next).apply()
    }

    @Suppress("DEPRECATION")
    private fun getAppVersion(): Map<String, Any> {
        val packageInfo = packageManager.getPackageInfo(packageName, 0)
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            packageInfo.versionCode.toLong()
        }
        return mapOf(
            "name" to packageInfo.versionName.orEmpty(),
            "code" to versionCode
        )
    }

    private fun downloadAndInstallUpdate(
        url: String?,
        requestedFileName: String?,
        result: MethodChannel.Result
    ) {
        if (url.isNullOrBlank()) {
            result.error("invalid_update_url", "Update URL is empty", null)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName")
                )
            )
            result.success("permission_required")
            return
        }

        val fileName = requestedFileName
            ?.replace(Regex("[^A-Za-z0-9._+-]"), "_")
            ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: "MyPlayer-update.apk"
        val request = DownloadManager.Request(Uri.parse(url))
            .setTitle("MyPlayer 更新")
            .setDescription(fileName)
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        pendingUpdateDownloadId = downloadManager.enqueue(request)
        result.success("downloading")
    }

    private fun openDownloadedUpdate(downloadId: Long) {
        val downloadManager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val completed = downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use false
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            status == DownloadManager.STATUS_SUCCESSFUL
        } ?: false
        if (!completed) return
        val apkUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(installIntent) }
    }

    // 打开视频所在目录。优先转真实文件路径（file://）：国产文件管理器对 file:// 目录
    // 支持最稳（魅族上 SAF document URI 会被错误路由，如打开成 .flymeSafeBox）；
    // file:// 不可用或被系统拦截时回退 SAF document URI 打开目录；都不行再打开视频本身。
    private fun revealInFileManager(uri: String?, parentUri: String?): String {
        if (uri.isNullOrEmpty()) return "failed"

        // Flyme 等 ROM 会把"打开目录" Intent 的 data 错误解析成保险箱（.flymeSafeBox），
        // 选哪个应用都打开错对象。故改为对视频文件本身弹"选择打开方式"，
        // data 指向具体文件，由用户从列表中选择文件管理器/应用打开它。
        val videoIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(uri), ANY_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(videoIntent, "选择打开方式")
        return try {
            startActivity(chooser)
            "opened_chooser"
        } catch (error: Exception) {
            "failed"
        }
    }

    private fun getSettings(): Map<String, Any> {
        val prefs = getPreferences(MODE_PRIVATE)
        return mapOf(
            "defaultPlaybackSpeed" to prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f),
            "randomIncludeSubfolders" to prefs.getBoolean(KEY_RANDOM_INCLUDE_SUBFOLDERS, false),
            "defaultShuffle" to prefs.getBoolean(KEY_DEFAULT_SHUFFLE, false),
            "sortMode" to (prefs.getString(KEY_SORT_MODE, "name") ?: "name"),
            "playbackEndAction" to (prefs.getString(KEY_PLAYBACK_END_ACTION, "next") ?: "next")
        )
    }

    /** 当前窗口亮度；若已跟随系统（-1）则按系统亮度换算成 0..1，供亮度手势作起点。 */
    private fun getScreenBrightness(): Float {
        window.attributes.screenBrightness.takeIf { it >= 0f }?.let { return it }
        return try {
            Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
        } catch (error: Exception) {
            0.5f
        }
    }

    private fun getMusicVolume(): Float {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        return audio.getStreamVolume(AudioManager.STREAM_MUSIC) / max.toFloat()
    }

    private companion object {
        const val REQUEST_OPEN_TREE = 5107
        const val KEY_DIRECTORY_URI = "directory_uri"
        const val KEY_RANDOM_PLAYED_URIS = "random_played_uris"
        const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        const val KEY_RANDOM_INCLUDE_SUBFOLDERS = "random_include_subfolders"
        const val KEY_DEFAULT_SHUFFLE = "default_shuffle"
        const val KEY_SORT_MODE = "sort_mode"
        const val KEY_PLAYBACK_END_ACTION = "playback_end_action"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val ANY_MIME_TYPE = "*/*"
    }
}
