package com.lijialin.myplayer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class EncryptedVideoFormatTest {
    @Test
    fun encryptedMp4HeaderIsRecognizedAfterXor() {
        val header = byteArrayOf(
            0x12, 0x12, 0x12, 0x32,
            'f'.code.xor(0x12).toByte(),
            't'.code.xor(0x12).toByte(),
            'y'.code.xor(0x12).toByte(),
            'p'.code.xor(0x12).toByte()
        )

        assertTrue(EncryptedVideoFormat.hasEncryptedMp4Header(header))
    }

    @Test
    fun plainHeaderIsNotTreatedAsEncrypted() {
        val header = byteArrayOf(0, 0, 0, 32, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())

        assertFalse(EncryptedVideoFormat.hasEncryptedMp4Header(header))
    }

    @Test
    fun findsPlainMoovOffset() {
        val bytes = "xxxxmdat----moovtail".toByteArray()

        assertEquals(12L, EncryptedVideoFormat.findPlainMoovOffset(ByteArrayInputStream(bytes)))
        assertEquals(8L, EncryptedVideoFormat.xorUntilFromMoovIndex(12L))
    }

    @Test
    fun transformOnlyXorsBytesBeforeBoundary() {
        val chunk = byteArrayOf(
            2.xor(0x12).toByte(),
            3.xor(0x12).toByte(),
            4,
            5
        )

        EncryptedVideoFormat.transformReadBuffer(
            buffer = chunk,
            offset = 0,
            length = chunk.size,
            absolutePosition = 2L,
            xorUntilOffset = 4L
        )

        assertArrayEquals(byteArrayOf(2, 3, 4, 5), chunk)
    }

    @Test
    fun transformUsesDiscoveredBoundaryPastLegacyPrefix() {
        val chunk = byteArrayOf(
            6.xor(0x12).toByte(),
            7.xor(0x12).toByte(),
            8,
            9
        )

        EncryptedVideoFormat.transformReadBuffer(
            buffer = chunk,
            offset = 0,
            length = chunk.size,
            absolutePosition = EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH,
            xorUntilOffset = EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH + 2
        )

        assertArrayEquals(byteArrayOf(6, 7, 8, 9), chunk)
    }

    @Test
    fun findsEncryptedPrefixEndAtPlainBoxTransition() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedMoov = box("moov", 16).xor()
        val plainStyp = box("styp", 24)
        val bytes = encryptedFtyp + encryptedMoov + plainStyp

        assertEquals(40L, EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong()))
    }

    @Test
    fun findsPlainMediaTransitionInsideEncryptedMdat() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedSamples = ByteArray(0) +
            nalSample(0x65, 12).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor()
        val plainSamples = ByteArray(0) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10)
        val encryptedMdatHeader = boxHeader("mdat", 8 + encryptedSamples.size + plainSamples.size).xor()
        val plainMoov = box("moov", 16)
        val bytes = encryptedFtyp + encryptedMdatHeader + encryptedSamples + plainSamples + plainMoov
        val expectedTransition = encryptedFtyp.size + encryptedMdatHeader.size + encryptedSamples.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun fragmentedMp4CanTransitionToPlainMediaInsideMdat() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedMoof = box("moof", 24).xor()
        val encryptedSamples = ByteArray(0) +
            nalSample(0x65, 12).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor() +
            nalSample(0x41, 10).xor()
        val plainLookingSamples = ByteArray(0) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10) +
            nalSample(0x41, 10)
        val encryptedMdatHeader = boxHeader(
            "mdat",
            8 + encryptedSamples.size + plainLookingSamples.size
        ).xor()
        val mixedMdat = encryptedMdatHeader + encryptedSamples + plainLookingSamples
        val plainMoof = box("moof", 24)
        val plainMdat = box("mdat", 24)
        val bytes = encryptedFtyp + encryptedMoof + mixedMdat + plainMoof + plainMdat
        val expectedTransition = encryptedFtyp.size + encryptedMoof.size + encryptedMdatHeader.size + encryptedSamples.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    private fun box(type: String, size: Int): ByteArray {
        val bytes = ByteArray(size)
        boxHeader(type, size).copyInto(bytes)
        return bytes
    }

    private fun boxHeader(type: String, size: Int): ByteArray {
        val bytes = ByteArray(8)
        bytes[0] = (size shr 24).toByte()
        bytes[1] = (size shr 16).toByte()
        bytes[2] = (size shr 8).toByte()
        bytes[3] = size.toByte()
        type.toByteArray(Charsets.US_ASCII).copyInto(bytes, destinationOffset = 4)
        return bytes
    }

    private fun nalSample(nalHeader: Int, payloadSize: Int): ByteArray {
        val bytes = ByteArray(4 + payloadSize)
        bytes[3] = payloadSize.toByte()
        bytes[4] = nalHeader.toByte()
        for (index in 5 until bytes.size) {
            bytes[index] = (index and 0x7F).toByte()
        }
        return bytes
    }

    private fun ByteArray.xor(): ByteArray {
        return ByteArray(size) { index -> this[index].toInt().xor(0x12).toByte() }
    }
}
