package com.lijialin.myplayer

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

object EncryptedVideoScanner {
    fun scan(context: Context, directoryUri: Uri): ScanResult {
        val directory = DocumentFile.fromTreeUri(context, directoryUri)
            ?: return ScanResult(emptyList(), skippedUnsupported = 0, failed = 1)

        val videos = mutableListOf<EncryptedVideo>()
        var skipped = 0
        var failed = 0

        for (file in directory.listFiles()) {
            if (!file.isFile) continue

            val inspection = runCatching { inspectFile(context, file) }
            if (inspection.isFailure) {
                failed++
                continue
            }

            val metadata = inspection.getOrNull()
            if (metadata == null) {
                skipped++
            } else {
                videos += metadata
            }
        }

        return ScanResult(videos, skippedUnsupported = skipped, failed = failed)
    }

    private fun inspectFile(context: Context, file: DocumentFile): EncryptedVideo? {
        val resolver = context.contentResolver
        val header = ByteArray(12)
        val headerBytes = resolver.openInputStream(file.uri)?.use { stream ->
            stream.read(header)
        } ?: return null

        if (!EncryptedVideoFormat.hasEncryptedMp4Header(header, headerBytes)) {
            return null
        }

        val xorUntilOffset = resolver.openInputStream(file.uri)?.use { stream ->
            EncryptedVideoFormat.findEncryptedPrefixEnd(stream, file.length())
        } ?: -1L
        if (xorUntilOffset < 0L) return null

        val video = EncryptedVideo(
            uri = file.uri,
            displayName = file.name ?: file.uri.lastPathSegment.orEmpty(),
            size = file.length(),
            lastModified = file.lastModified(),
            xorUntilOffset = xorUntilOffset
        )
        val key = EncryptedVideoFormat.boundaryCacheKey(
            uri = video.uri.toString(),
            fileSize = video.size,
            lastModified = video.lastModified
        )
        context.getSharedPreferences("playback_boundaries", Context.MODE_PRIVATE)
            .edit()
            .putLong(key, video.xorUntilOffset)
            .apply()
        return video
    }
}
