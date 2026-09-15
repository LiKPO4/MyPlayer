package com.lijialin.myplayer

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.EOFException
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.math.max
import kotlin.math.min

@UnstableApi
class EncryptedVideoDataSource(
    context: Context,
    private val lookupVideo: (Uri) -> EncryptedVideo?
) : BaseDataSource(false) {
    private val resolver = context.applicationContext.contentResolver
    private val boundaryPrefs = context.applicationContext.getSharedPreferences(
        "playback_boundaries",
        Context.MODE_PRIVATE
    )
    private var parcelFileDescriptor: ParcelFileDescriptor? = null
    private var inputStream: FileInputStream? = null
    private var currentUri: Uri? = null
    private var readPosition = 0L
    private var bytesRemaining = 0L
    private var xorUntilOffset = 0L
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        val video = lookupVideo(dataSpec.uri)
            ?: throw FileNotFoundException("No encrypted video metadata for ${dataSpec.uri}")

        transferInitializing(dataSpec)
        val pfd = resolver.openFileDescriptor(dataSpec.uri, "r")
            ?: throw FileNotFoundException("Unable to open ${dataSpec.uri}")
        val stream = FileInputStream(pfd.fileDescriptor)
        stream.channel.position(dataSpec.position)

        parcelFileDescriptor = pfd
        inputStream = stream
        currentUri = dataSpec.uri
        readPosition = dataSpec.position
        xorUntilOffset = resolveXorUntilOffset(video)
        bytesRemaining = resolveLength(dataSpec, video)
        opened = true
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT

        val stream = inputStream ?: throw IOException("DataSource is not open")
        val bytesToRead = if (bytesRemaining == C.LENGTH_UNSET.toLong()) {
            length
        } else {
            min(length.toLong(), bytesRemaining).toInt()
        }
        val bytesRead = stream.read(buffer, offset, bytesToRead)
        if (bytesRead == -1) {
            if (bytesRemaining != C.LENGTH_UNSET.toLong() && bytesRemaining > 0L) throw EOFException()
            return C.RESULT_END_OF_INPUT
        }

        EncryptedVideoFormat.transformReadBuffer(buffer, offset, bytesRead, readPosition, xorUntilOffset)
        readPosition += bytesRead
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= bytesRead
        bytesTransferred(bytesRead)
        return bytesRead
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        inputStream?.close()
        inputStream = null
        parcelFileDescriptor?.close()
        parcelFileDescriptor = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private fun resolveLength(dataSpec: DataSpec, video: EncryptedVideo): Long {
        if (dataSpec.length != C.LENGTH_UNSET.toLong()) return dataSpec.length
        if (video.size <= 0L) return C.LENGTH_UNSET.toLong()
        return max(0L, video.size - dataSpec.position)
    }

    private fun resolveXorUntilOffset(video: EncryptedVideo): Long {
        if (video.xorUntilOffset == 0L) return 0L

        val key = EncryptedVideoFormat.boundaryCacheKey(
            uri = video.uri.toString(),
            fileSize = video.size,
            lastModified = video.lastModified
        )
        if (boundaryPrefs.contains(key)) {
            return boundaryPrefs.getLong(key, video.xorUntilOffset).coerceIn(0L, video.size)
        }

        val fallback = video.xorUntilOffset.takeIf { it >= 0L } ?: video.size
        val resolved = runCatching {
            resolver.openInputStream(video.uri)?.use { stream ->
                val boundary = EncryptedVideoFormat.findEncryptedPrefixEnd(stream, video.size)
                // 与扫描路径一致：moov 等大 box 尾部跨过 1MB 边界时，首个明文 box
                // 的起点不是 XOR 结束点（否则旧扫描缓存里的错误边界会再次黑屏）。
                if (boundary > EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH) {
                    resolver.openInputStream(video.uri)?.use { verifyStream ->
                        EncryptedVideoFormat.refineBoundaryForOversizedBox(
                            verifyStream,
                            video.size,
                            boundary
                        )
                    } ?: boundary
                } else {
                    boundary
                }
            }
        }.getOrNull()?.takeIf { it >= 0L } ?: fallback

        val safeOffset = resolved.coerceIn(0L, video.size)
        boundaryPrefs.edit().putLong(key, safeOffset).apply()
        return safeOffset
    }

    class Factory(
        private val context: Context,
        private val lookupVideo: (Uri) -> EncryptedVideo?
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVideoDataSource(context, lookupVideo)
    }
}
