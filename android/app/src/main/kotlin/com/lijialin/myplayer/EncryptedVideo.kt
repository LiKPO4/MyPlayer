package com.lijialin.myplayer

import android.net.Uri

data class EncryptedVideo(
    val uri: Uri,
    val displayName: String,
    val fileName: String,
    val size: Long,
    val lastModified: Long,
    val xorUntilOffset: Long
) {
    fun toMap(): Map<String, Any> = mapOf(
        "uri" to uri.toString(),
        "displayName" to displayName,
        "fileName" to fileName,
        "size" to size,
        "lastModified" to lastModified,
        "xorUntilOffset" to xorUntilOffset
    )

    companion object {
        fun fromMap(map: Map<*, *>): EncryptedVideo {
            return EncryptedVideo(
                uri = Uri.parse(map["uri"] as String),
                displayName = map["displayName"] as String,
                fileName = (map["fileName"] as? String) ?: map["displayName"] as String,
                size = (map["size"] as Number).toLong(),
                lastModified = (map["lastModified"] as Number).toLong(),
                xorUntilOffset = (map["xorUntilOffset"] as Number).toLong()
            )
        }
    }
}

data class ScanResult(
    val entries: List<BrowseEntry>,
    val videos: List<EncryptedVideo>,
    val skippedUnsupported: Int,
    val failed: Int
) {
    fun toMap(): Map<String, Any> = mapOf(
        "entries" to entries.map { it.toMap() },
        "videos" to videos.map { it.toMap() },
        "skippedUnsupported" to skippedUnsupported,
        "failed" to failed
    )
}

data class BrowseEntry(
    val type: String,
    val name: String,
    val uri: Uri,
    val parentUri: String?,
    val size: Long,
    val lastModified: Long,
    val videoCount: Int,
    val video: EncryptedVideo? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "type" to type,
        "name" to name,
        "uri" to uri.toString(),
        "parentUri" to parentUri,
        "size" to size,
        "lastModified" to lastModified,
        "videoCount" to videoCount,
        "video" to video?.toMap()
    )
}
