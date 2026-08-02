package com.lijialin.myplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.util.UnstableApi
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

@UnstableApi
class MainActivity : FlutterActivity() {
    private val scannerExecutor = Executors.newSingleThreadExecutor()
    private var nativeChannel: MethodChannel? = null
    private var scanEvents: EventChannel.EventSink? = null
    private var playerEvents: EventChannel.EventSink? = null
    private var pendingFolderPick: MethodChannel.Result? = null

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
    }

    override fun onDestroy() {
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

    private fun getSettings(): Map<String, Any> {
        val prefs = getPreferences(MODE_PRIVATE)
        return mapOf(
            "defaultPlaybackSpeed" to prefs.getFloat(KEY_DEFAULT_PLAYBACK_SPEED, 1.0f),
            "randomIncludeSubfolders" to prefs.getBoolean(KEY_RANDOM_INCLUDE_SUBFOLDERS, false)
        )
    }

    private companion object {
        const val REQUEST_OPEN_TREE = 5107
        const val KEY_DIRECTORY_URI = "directory_uri"
        const val KEY_RANDOM_PLAYED_URIS = "random_played_uris"
        const val KEY_DEFAULT_PLAYBACK_SPEED = "default_playback_speed"
        const val KEY_RANDOM_INCLUDE_SUBFOLDERS = "random_include_subfolders"
    }
}
