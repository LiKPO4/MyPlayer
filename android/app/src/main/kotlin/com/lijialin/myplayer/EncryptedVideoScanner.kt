package com.lijialin.myplayer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import org.json.JSONArray
import org.json.JSONObject

class EncryptedVideoScanner(
    private val context: Context,
    private val progress: (Map<String, Any?>) -> Unit
) {
    private val resolver = context.contentResolver
    private val cachePrefs = context.getSharedPreferences("scan_cache", Context.MODE_PRIVATE)
    private val boundaryPrefs = context.getSharedPreferences("playback_boundaries", Context.MODE_PRIVATE)
    private lateinit var treeUri: Uri
    private lateinit var cache: JSONObject
    private lateinit var nextCache: JSONObject
    private val videos = mutableListOf<EncryptedVideo>()
    private val entriesByUri = linkedMapOf<String, BrowseEntry>()
    private val folderCounts = mutableMapOf<String, Int>()
    private val counters = ScanCounters()

    fun scan(directoryUri: Uri, emitReset: Boolean = true): ScanResult {
        treeUri = directoryUri
        cache = loadCache(directoryUri)
        nextCache = JSONObject()
        videos.clear()
        entriesByUri.clear()
        folderCounts.clear()
        counters.reset()

        if (emitReset) progress(event("reset"))
        val rootDocumentId = DocumentsContract.getTreeDocumentId(directoryUri)
        scanFolder(rootDocumentId, parentUri = null, ancestorFolderUris = emptyList())

        saveCache(directoryUri, nextCache)
        val result = ScanResult(
            entries = entriesByUri.values.sortedWith(
                compareByDescending<BrowseEntry> { it.type == "folder" }
                    .thenBy { it.name.lowercase() }
            ),
            videos = videos.toList(),
            skippedUnsupported = counters.skipped,
            failed = counters.failed
        )
        saveSnapshot(directoryUri, result)
        progress(event("done"))
        return result
    }

    fun loadSnapshot(directoryUri: Uri): ScanResult? {
        return runCatching {
            val raw = readStoredValue(
                currentKey = snapshotKey(directoryUri),
                legacyKey = legacySnapshotKey(directoryUri)
            ) ?: return null
            JSONObject(raw).toScanResult()
        }.getOrNull()
    }

    fun enrichTitles(directoryUri: Uri, sourceVideos: List<EncryptedVideo>) {
        // Keep the visible title as the original file name. Metadata title probing
        // was both slow on large SAF files and unreliable for these encrypted videos.
    }

    private fun scanFolder(parentDocumentId: String, parentUri: String?, ancestorFolderUris: List<String>) {
        for (child in queryChildren(parentDocumentId)) {
            if (child.isDirectory) {
                val folderUri = child.uri.toString()
                val entry = BrowseEntry(
                    type = "folder",
                    name = child.name,
                    uri = child.uri,
                    parentUri = parentUri,
                    size = 0L,
                    lastModified = child.lastModified,
                    videoCount = folderCounts[folderUri] ?: 0
                )
                entriesByUri[folderUri] = entry
                progress(itemEvent(entry))
                scanFolder(child.documentId, parentUri = folderUri, ancestorFolderUris = ancestorFolderUris + folderUri)
            } else {
                inspectAndCollect(child, parentUri, ancestorFolderUris)
            }
        }
    }

    private fun inspectAndCollect(child: ChildInfo, parentUri: String?, ancestorFolderUris: List<String>): EncryptedVideo? {
        counters.processed++

        val cachedRecord = cache.optJSONObject(child.uri.toString())
        val cachedVideo = cachedRecord?.toVideoIfFresh(child)

        val video = when {
            cachedVideo != null -> {
                counters.cached++
                nextCache.put(child.uri.toString(), cachedRecord)
                cachedVideo
            }
            cachedRecord?.isFreshUnsupported(child) == true -> {
                counters.cached++
                counters.skipped++
                nextCache.put(child.uri.toString(), cachedRecord)
                null
            }
            else -> {
                val inspection = runCatching { inspectFileFast(child) }
                if (inspection.isFailure) {
                    counters.failed++
                    null
                } else {
                    val metadata = inspection.getOrNull()
                    if (metadata == null) {
                        counters.skipped++
                        nextCache.put(child.uri.toString(), unsupportedRecord(child))
                    } else {
                        nextCache.put(child.uri.toString(), metadata.toCacheRecord())
                    }
                    metadata
                }
            }
        }

        if (video != null) {
            videos += video
            progress(videoEvent(video))
            val entry = video.toEntry(parentUri)
            entriesByUri[entry.uri.toString()] = entry
            progress(itemEvent(entry))
            for (folderUri in ancestorFolderUris) {
                val nextCount = (folderCounts[folderUri] ?: 0) + 1
                folderCounts[folderUri] = nextCount
                entriesByUri[folderUri]?.let { existing ->
                    val updated = existing.copy(videoCount = nextCount)
                    entriesByUri[folderUri] = updated
                    progress(itemEvent(updated))
                }
            }
        }

        if (counters.processed == 1 || counters.processed % 10 == 0) {
            progress(event("scanning"))
        }
        return video
    }

    private fun queryChildren(parentDocumentId: String): List<ChildInfo> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val children = mutableListOf<ChildInfo>()
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedColumn = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)

            while (cursor.moveToNext()) {
                counters.discovered++
                val documentId = cursor.getString(idColumn)
                val name = cursor.getString(nameColumn) ?: documentId
                val mimeType = cursor.getString(mimeColumn).orEmpty()
                children += ChildInfo(
                    documentId = documentId,
                    uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                    name = name,
                    isDirectory = mimeType == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = cursor.getLongOrZero(sizeColumn),
                    lastModified = cursor.getLongOrZero(modifiedColumn)
                )

                if (counters.discovered == 1 || counters.discovered % 100 == 0) {
                    progress(event("reading"))
                }
            }
        }

        return children.sortedWith(
            compareByDescending<ChildInfo> { it.isDirectory }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun inspectFileFast(child: ChildInfo): EncryptedVideo? {
        val header = ByteArray(12)
        val headerBytes = resolver.openInputStream(child.uri)?.use { stream ->
            stream.read(header)
        } ?: return null

        if (EncryptedVideoFormat.hasPlainMp4Header(header, headerBytes)) {
            val video = EncryptedVideo(
                uri = child.uri,
                displayName = originalTitle(child),
                fileName = child.name,
                size = child.size,
                lastModified = child.lastModified,
                xorUntilOffset = 0L
            )
            rememberBoundary(video)
            return video
        }

        if (!EncryptedVideoFormat.hasEncryptedMp4Header(header, headerBytes)) return null
        val xorUntilOffset = resolver.openInputStream(child.uri)?.use { stream ->
            EncryptedVideoFormat.findEncryptedPrefixEnd(stream, child.size)
        } ?: -1L

        val video = EncryptedVideo(
            uri = child.uri,
            displayName = originalTitle(child),
            fileName = child.name,
            size = child.size,
            lastModified = child.lastModified,
            xorUntilOffset = xorUntilOffset
        )
        rememberBoundary(video)
        return video
    }

    private fun rememberBoundary(video: EncryptedVideo) {
        val key = EncryptedVideoFormat.boundaryCacheKey(
            uri = video.uri.toString(),
            fileSize = video.size,
            lastModified = video.lastModified
        )
        boundaryPrefs.edit().putLong(key, video.xorUntilOffset).apply()
    }

    private fun originalTitle(child: ChildInfo): String {
        return child.name
    }

    private fun loadCache(directoryUri: Uri): JSONObject {
        return runCatching {
            val raw = readStoredValue(
                currentKey = cacheKey(directoryUri),
                legacyKey = legacyCacheKey(directoryUri)
            )
            JSONObject(raw ?: "{}")
        }.getOrElse { JSONObject() }
    }

    private fun saveCache(directoryUri: Uri, cache: JSONObject) {
        cachePrefs.edit().putString(cacheKey(directoryUri), cache.toString()).apply()
    }

    // These keys stay stable across app versions. Cache format changes must migrate
    // individual records instead of invalidating the entire scanned library.
    private fun cacheKey(directoryUri: Uri): String = "scan:$directoryUri"

    private fun snapshotKey(directoryUri: Uri): String = "snapshot:$directoryUri"

    private fun legacyCacheKey(directoryUri: Uri): String = "scan-v11:$directoryUri"

    private fun legacySnapshotKey(directoryUri: Uri): String = "snapshot-v5:$directoryUri"

    private fun readStoredValue(currentKey: String, legacyKey: String): String? {
        cachePrefs.getString(currentKey, null)?.let { return it }
        val legacyValue = cachePrefs.getString(legacyKey, null) ?: return null
        cachePrefs.edit().putString(currentKey, legacyValue).apply()
        return legacyValue
    }

    private fun saveSnapshot(directoryUri: Uri, result: ScanResult) {
        val root = JSONObject()
            .put("entries", JSONArray(result.entries.map { it.toJson() }))
            .put("videos", JSONArray(result.videos.map { it.toJson() }))
            .put("skippedUnsupported", result.skippedUnsupported)
            .put("failed", result.failed)
        cachePrefs.edit().putString(snapshotKey(directoryUri), root.toString()).apply()
    }

    private fun event(phase: String): Map<String, Any> = mapOf(
        "phase" to phase,
        "processed" to counters.processed,
        "total" to counters.discovered,
        "found" to videos.size,
        "cached" to counters.cached,
        "skipped" to counters.skipped,
        "failed" to counters.failed
    )

    private fun itemEvent(entry: BrowseEntry): Map<String, Any?> = event("item") + mapOf("entry" to entry.toMap())

    private fun videoEvent(video: EncryptedVideo): Map<String, Any> = event("video") + mapOf("video" to video.toMap())

    private fun EncryptedVideo.toEntry(parentUri: String?): BrowseEntry = BrowseEntry(
        type = "video",
        name = displayName,
        uri = uri,
        parentUri = parentUri,
        size = size,
        lastModified = lastModified,
        videoCount = 1,
        video = this
    )

    private fun EncryptedVideo.toCacheRecord(): JSONObject = JSONObject()
        .put("kind", "video")
        .put("displayName", displayName)
        .put("fileName", fileName)
        .put("size", size)
        .put("lastModified", lastModified)
        .put("xorUntilOffset", xorUntilOffset)

    private fun EncryptedVideo.toJson(): JSONObject = JSONObject()
        .put("uri", uri.toString())
        .put("displayName", displayName)
        .put("fileName", fileName)
        .put("size", size)
        .put("lastModified", lastModified)
        .put("xorUntilOffset", xorUntilOffset)

    private fun BrowseEntry.toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("name", name)
        .put("uri", uri.toString())
        .put("parentUri", parentUri)
        .put("size", size)
        .put("lastModified", lastModified)
        .put("videoCount", videoCount)
        .put("video", video?.toJson())

    private fun unsupportedRecord(child: ChildInfo): JSONObject = JSONObject()
        .put("kind", "unsupported")
        .put("size", child.size)
        .put("lastModified", child.lastModified)

    private fun JSONObject.toVideoIfFresh(child: ChildInfo): EncryptedVideo? {
        if (optString("kind") != "video" || !isFresh(child)) return null
        return EncryptedVideo(
            uri = child.uri,
            displayName = optString("displayName", child.name),
            fileName = optString("fileName", child.name),
            size = optLong("size"),
            lastModified = optLong("lastModified"),
            xorUntilOffset = optLong("xorUntilOffset", -1L)
        )
    }

    private fun JSONObject.toScanResult(): ScanResult {
        return ScanResult(
            entries = optJSONArray("entries").orEmpty().mapNotNull { item ->
                (item as? JSONObject)?.toBrowseEntry()
            },
            videos = optJSONArray("videos").orEmpty().mapNotNull { item ->
                (item as? JSONObject)?.toVideo()
            },
            skippedUnsupported = optInt("skippedUnsupported"),
            failed = optInt("failed")
        )
    }

    private fun JSONObject.toBrowseEntry(): BrowseEntry {
        val videoObject = optJSONObject("video")
        return BrowseEntry(
            type = optString("type"),
            name = optString("name"),
            uri = Uri.parse(optString("uri")),
            parentUri = optString("parentUri").takeIf { it.isNotBlank() && it != "null" },
            size = optLong("size"),
            lastModified = optLong("lastModified"),
            videoCount = optInt("videoCount"),
            video = videoObject?.toVideo()
        )
    }

    private fun JSONObject.toVideo(): EncryptedVideo {
        return EncryptedVideo(
            uri = Uri.parse(optString("uri")),
            displayName = optString("displayName"),
            fileName = optString("fileName", optString("displayName")),
            size = optLong("size"),
            lastModified = optLong("lastModified"),
            xorUntilOffset = optLong("xorUntilOffset", -1L)
        )
    }

    private fun JSONArray?.orEmpty(): List<Any?> {
        if (this == null) return emptyList()
        return List(length()) { index -> opt(index) }
    }

    private fun JSONObject.isFreshUnsupported(child: ChildInfo): Boolean {
        return optString("kind") == "unsupported" && isFresh(child)
    }

    private fun JSONObject.isFresh(child: ChildInfo): Boolean {
        return optLong("size") == child.size && optLong("lastModified") == child.lastModified
    }

    private fun android.database.Cursor.getLongOrZero(column: Int): Long {
        return if (column >= 0 && !isNull(column)) getLong(column) else 0L
    }

    private data class ChildInfo(
        val documentId: String,
        val uri: Uri,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val lastModified: Long
    )

    private class ScanCounters {
        var discovered = 0
        var processed = 0
        var cached = 0
        var skipped = 0
        var failed = 0

        fun reset() {
            discovered = 0
            processed = 0
            cached = 0
            skipped = 0
            failed = 0
        }
    }
}
