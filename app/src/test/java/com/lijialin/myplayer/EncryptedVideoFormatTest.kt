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
        val encryptedSamples = nalSample(0x65, 12).xor() + repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainSamples = repeatedSamples(64) { nalSample(0x41, 10) }
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
    fun ignoresFakePlainNalPatternsInsideEncryptedMedia() {
        val encryptedFtyp = box("ftyp", 24).xor()
        // 密文区中混入的假明文 NAL（真实样本里随机字节也会产生这类命中），不得被当成切换点。
        val fakePlainNal = byteArrayOf(0, 0, 1, 0, 0x41) + ByteArray(251) { 0x5A }
        val encryptedSamples = (
            repeatedSamples(24) { nalSample(0x41, 10).xor() } +
                repeatedSamples(3) { fakePlainNal } +
                repeatedSamples(24) { nalSample(0x41, 10).xor() }
            ).padToBlock()
        val plainSamples = repeatedSamples(64) { nalSample(0x41, 10) }
        val encryptedMdatHeader = boxHeader("mdat", 8 + encryptedSamples.size + plainSamples.size).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + encryptedSamples + plainSamples
        val expectedTransition = encryptedFtyp.size + encryptedMdatHeader.size + encryptedSamples.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun fullyEncryptedMdatHasNoMediaTransition() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedSamples = repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val encryptedMdatHeader = boxHeader("mdat", 8 + encryptedSamples.size).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + encryptedSamples

        assertEquals(
            bytes.size.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun fragmentedMp4CanTransitionToPlainMediaInsideMdat() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedMoof = box("moof", 24).xor()
        val encryptedSamples = nalSample(0x65, 12).xor() + repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainLookingSamples = repeatedSamples(64) { nalSample(0x41, 10) }
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

    @Test
    fun findsTransitionBeyondFormerEightMegabyteLimit() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedPadding = ByteArray(8 * 1024 * 1024 + 64 * 1024) { 0x12 }
        val encryptedEvidence = repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainEvidence = repeatedSamples(64) { nalSample(0x41, 10) }
        val payload = encryptedPadding + encryptedEvidence + plainEvidence
        val encryptedMdatHeader = boxHeader("mdat", 8 + payload.size).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + payload
        val expectedTransition = encryptedFtyp.size + encryptedMdatHeader.size +
            encryptedPadding.size + encryptedEvidence.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun findsHevcTransitionInsideEncryptedMdat() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedSamples = repeatedSamples(24) { hevcNalSample(nalType = 0, payloadSize = 10).xor() }
        val plainSamples = repeatedSamples(64) { hevcNalSample(nalType = 0, payloadSize = 10) }
        val encryptedMdatHeader = boxHeader(
            "mdat",
            8 + encryptedSamples.size + plainSamples.size
        ).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + encryptedSamples + plainSamples
        val expectedTransition = encryptedFtyp.size + encryptedMdatHeader.size + encryptedSamples.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun findsTransitionInsideExtendedLengthMdat() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedSamples = repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainSamples = repeatedSamples(64) { nalSample(0x41, 10) }
        val payload = encryptedSamples + plainSamples
        // 64 位扩展长度头：size 字段固定为 1，真实长度放在随后 8 字节。
        val mdatSize = 16L + payload.size
        val extendedHeader = ByteArray(16)
        extendedHeader[3] = 1
        "mdat".toByteArray(Charsets.US_ASCII).copyInto(extendedHeader, destinationOffset = 4)
        for (index in 0..7) {
            extendedHeader[8 + index] = (mdatSize shr (56 - 8 * index)).toByte()
        }
        val bytes = encryptedFtyp + extendedHeader.xor() + payload
        val expectedTransition = encryptedFtyp.size + 16 + encryptedSamples.size

        assertEquals(
            expectedTransition.toLong(),
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun prefersOneMegabyteBoundaryWhenEvidenceSupportsIt() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val encryptedMdatHeaderPlaceholderSize = 8
        val payloadStart = encryptedFtyp.size + encryptedMdatHeaderPlaceholderSize
        val oneMbPos = EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH - payloadStart
        val encryptedEvidence = repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainEvidence = repeatedSamples(96) { nalSample(0x41, 10) }
        // 0x12 填充对明文/密文 NAL 判定均为中性，模拟 1MB 之前的无命中区。
        val paddingBeforeOneMb = ByteArray((oneMbPos - encryptedEvidence.size).toInt()) { 0x12 }
        val tailPadding = ByteArray(384 * 1024) { 0x12 }
        val payload = encryptedEvidence + paddingBeforeOneMb + plainEvidence + tailPadding
        val encryptedMdatHeader = boxHeader("mdat", 8 + payload.size).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + payload

        assertEquals(
            EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH,
            EncryptedVideoFormat.findEncryptedPrefixEnd(ByteArrayInputStream(bytes), bytes.size.toLong())
        )
    }

    @Test
    fun rejectsOneMegabyteBoundaryWhenTransitionIsLater() {
        val encryptedFtyp = box("ftyp", 24).xor()
        val payloadStart = encryptedFtyp.size + 8
        val oneMbPos = EncryptedVideoFormat.ENCRYPTED_PREFIX_LENGTH - payloadStart
        val encryptedEvidence = repeatedSamples(24) { nalSample(0x41, 10).xor() }
        val plainEvidence = repeatedSamples(96) { nalSample(0x41, 10) }
        // 加密区中性填充延到 1MB 之后 300KB，1MB 快路径必须因证据不足被否决。
        val plainStartInPayload = oneMbPos + 300L * 1024L
        val padding = ByteArray((plainStartInPayload - encryptedEvidence.size).toInt()) { 0x12 }
        val payload = encryptedEvidence + padding + plainEvidence
        val encryptedMdatHeader = boxHeader("mdat", 8 + payload.size).xor()
        val bytes = encryptedFtyp + encryptedMdatHeader + payload
        val expectedTransition = payloadStart + plainStartInPayload

        assertEquals(
            expectedTransition,
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

    private fun hevcNalSample(nalType: Int, payloadSize: Int): ByteArray {
        val nalLength = payloadSize + 2
        val bytes = ByteArray(4 + nalLength)
        bytes[3] = nalLength.toByte()
        bytes[4] = (nalType shl 1).toByte()
        bytes[5] = 1
        for (index in 6 until bytes.size) {
            bytes[index] = (index and 0x7F).toByte()
        }
        return bytes
    }

    private fun repeatedSamples(count: Int, factory: () -> ByteArray): ByteArray {
        var result = ByteArray(0)
        repeat(count) { result += factory() }
        return result
    }

    private fun ByteArray.padToBlock(): ByteArray {
        val remainder = size % 1024
        if (remainder == 0) return this
        return this + ByteArray(1024 - remainder) { 0x12 }
    }

    private fun ByteArray.xor(): ByteArray {
        return ByteArray(size) { index -> this[index].toInt().xor(0x12).toByte() }
    }
}
