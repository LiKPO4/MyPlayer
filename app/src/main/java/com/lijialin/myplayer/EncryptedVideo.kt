package com.lijialin.myplayer

import android.net.Uri

data class EncryptedVideo(
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val lastModified: Long,
    val xorUntilOffset: Long,
    val durationMs: Long? = null
)

data class ScanResult(
    val videos: List<EncryptedVideo>,
    val skippedUnsupported: Int,
    val failed: Int
)
