package com.lijialin.myplayer

import java.io.InputStream
import kotlin.math.min

object EncryptedVideoFormat {
    const val XOR_KEY: Int = 0x12
    const val ENCRYPTED_PREFIX_LENGTH: Long = 1024L * 1024L
    private const val MEDIA_TRANSITION_SCAN_LIMIT = 8L * 1024L * 1024L
    private const val MEDIA_TRANSITION_WINDOW = 128 * 1024
    private const val MIN_NAL_EVIDENCE = 5
    private val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    private val MOOV = byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())
    private val TOP_LEVEL_BOXES = setOf("ftyp", "free", "mdat", "wide", "skip", "moov", "styp", "moof", "sidx")

    fun hasEncryptedMp4Header(header: ByteArray, bytesRead: Int = header.size): Boolean {
        if (bytesRead < 8) return false
        for (index in FTYP.indices) {
            val decoded = header[index + 4].toInt() xor XOR_KEY
            if (decoded.toByte() != FTYP[index]) return false
        }
        return true
    }

    fun xorUntilFromMoovIndex(moovIndex: Long): Long {
        return if (moovIndex >= 4L) moovIndex - 4L else -1L
    }

    fun findEncryptedPrefixEnd(inputStream: InputStream, fileSize: Long): Long {
        var offset = 0L
        var guard = 0

        while (offset + 8L <= fileSize && guard++ < 4096) {
            val header = inputStream.readExact(8)
            if (header.size < 8) return fileSize

            val plainSize = header.readUInt32Plain(0)
            val plainType = header.copyOfRange(4, 8).toAsciiString()
            if (plainType in TOP_LEVEL_BOXES && plainSize >= 8L && offset + plainSize <= fileSize) {
                return offset
            }

            val decodedSize = header.readUInt32Xor(offset = 0)
            val decodedType = header.copyOfRange(4, 8).xorBytes().toAsciiString()
            if (decodedType !in TOP_LEVEL_BOXES || decodedSize < 8L) {
                return fileSize
            }

            val headerSize = if (decodedSize == 1L) 16L else 8L
            val boxSize = if (decodedSize == 1L) {
                val extendedSize = inputStream.readExact(8)
                if (extendedSize.size < 8) return fileSize
                extendedSize.readUInt64Xor(offset = 0)
            } else {
                decodedSize
            }
            if (boxSize < headerSize || offset + boxSize > fileSize) {
                return fileSize
            }

            val payloadSize = boxSize - headerSize
            if (decodedType == "mdat") {
                val transition = inputStream.findPlainMediaTransition(offset + headerSize, payloadSize)
                if (transition >= 0L) return transition
            } else {
                inputStream.skipFully(payloadSize)
            }
            offset += boxSize
        }

        return fileSize
    }

    fun findPlainMoovOffset(inputStream: InputStream): Long {
        val buffer = ByteArray(64 * 1024)
        var absolutePosition = 0L
        var matched = 0

        while (true) {
            val bytesRead = inputStream.read(buffer)
            if (bytesRead == -1) return -1L

            for (index in 0 until bytesRead) {
                val current = buffer[index]
                matched = when {
                    current == MOOV[matched] -> matched + 1
                    current == MOOV[0] -> 1
                    else -> 0
                }

                if (matched == MOOV.size) {
                    return absolutePosition - MOOV.size + 1L
                }
                absolutePosition++
            }
        }
    }

    fun transformReadBuffer(
        buffer: ByteArray,
        offset: Int,
        length: Int,
        absolutePosition: Long,
        xorUntilOffset: Long
    ) {
        val xorLength = min(length.toLong(), xorUntilOffset - absolutePosition).coerceAtLeast(0L).toInt()
        for (index in 0 until xorLength) {
            val target = offset + index
            buffer[target] = (buffer[target].toInt() xor XOR_KEY).toByte()
        }
    }

    private fun ByteArray.readUInt32Plain(offset: Int): Long {
        var value = 0L
        for (index in 0 until 4) {
            value = (value shl 8) or (this[offset + index].toInt() and 0xFF).toLong()
        }
        return value
    }

    private fun ByteArray.readUInt32Xor(offset: Int): Long {
        var value = 0L
        for (index in 0 until 4) {
            value = (value shl 8) or ((this[offset + index].toInt() xor XOR_KEY) and 0xFF).toLong()
        }
        return value
    }

    private fun ByteArray.readUInt64Xor(offset: Int): Long {
        var value = 0L
        for (index in 0 until 8) {
            value = (value shl 8) or ((this[offset + index].toInt() xor XOR_KEY) and 0xFF).toLong()
        }
        return value
    }

    private fun ByteArray.readUInt32At(offset: Int, xor: Boolean): Long {
        var value = 0L
        for (index in 0 until 4) {
            val byte = this[offset + index].toInt()
            value = (value shl 8) or ((if (xor) byte xor XOR_KEY else byte) and 0xFF).toLong()
        }
        return value
    }

    private fun ByteArray.xorBytes(): ByteArray {
        return ByteArray(size) { index -> (this[index].toInt() xor XOR_KEY).toByte() }
    }

    private fun ByteArray.toAsciiString(): String {
        return String(this, Charsets.US_ASCII)
    }

    private fun InputStream.readExact(length: Int): ByteArray {
        val buffer = ByteArray(length)
        var total = 0
        while (total < length) {
            val read = read(buffer, total, length - total)
            if (read == -1) break
            total += read
        }
        return if (total == length) buffer else buffer.copyOf(total)
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes.coerceAtLeast(0L)
        val scratch = ByteArray(8192)
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                val read = read(scratch, 0, min(scratch.size.toLong(), remaining).toInt())
                if (read == -1) return
                remaining -= read
            }
        }
    }

    private fun InputStream.findPlainMediaTransition(payloadStart: Long, payloadSize: Long): Long {
        val scanSize = min(payloadSize, MEDIA_TRANSITION_SCAN_LIMIT).toInt()
        if (scanSize <= 0) return -1L

        val buffer = readExact(scanSize)
        val transition = buffer.findPlainNalTransition()
        skipFully(payloadSize - buffer.size)
        return if (transition >= 0) payloadStart + transition else -1L
    }

    private fun ByteArray.findPlainNalTransition(): Int {
        var index = 0
        while (index + 6 < size) {
            if (isNalStart(index, xor = false) && !isNalStart(index, xor = true) && hasTransitionEvidence(index)) {
                return index
            }
            index++
        }
        return -1
    }

    private fun ByteArray.hasTransitionEvidence(index: Int): Boolean {
        val beforeStart = (index - MEDIA_TRANSITION_WINDOW).coerceAtLeast(0)
        val afterEnd = (index + MEDIA_TRANSITION_WINDOW).coerceAtMost(size)
        var encryptedBefore = 0
        var plainAfter = 0

        var before = beforeStart
        while (before < index && encryptedBefore < MIN_NAL_EVIDENCE) {
            if (isNalStart(before, xor = true) && !isNalStart(before, xor = false)) encryptedBefore++
            before++
        }

        var after = index
        while (after < afterEnd && plainAfter < MIN_NAL_EVIDENCE) {
            if (isNalStart(after, xor = false) && !isNalStart(after, xor = true)) plainAfter++
            after++
        }

        return encryptedBefore >= MIN_NAL_EVIDENCE && plainAfter >= MIN_NAL_EVIDENCE
    }

    private fun ByteArray.isNalStart(offset: Int, xor: Boolean): Boolean {
        if (offset + 5 >= size) return false
        val nalLength = readUInt32At(offset, xor)
        if (nalLength <= 0L || nalLength > 256L * 1024L) return false
        val header = (this[offset + 4].toInt() xor if (xor) XOR_KEY else 0) and 0xFF
        if ((header and 0x80) != 0) return false
        val nalType = header and 0x1F
        return nalType == 1 || nalType == 5 || nalType == 6 || nalType == 7 || nalType == 8
    }
}
