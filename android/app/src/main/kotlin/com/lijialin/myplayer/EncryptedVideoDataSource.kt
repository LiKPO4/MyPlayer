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
        xorUntilOffset = if (video.xorUntilOffset >= 0L) video.xorUntilOffset else video.size
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

    class Factory(
        private val context: Context,
        private val lookupVideo: (Uri) -> EncryptedVideo?
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource = EncryptedVideoDataSource(context, lookupVideo)
    }
}
