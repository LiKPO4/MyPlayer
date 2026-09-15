package com.lijialin.myplayer

import java.io.InputStream
import kotlin.math.min

object EncryptedVideoFormat {
    const val XOR_KEY: Int = 0x12
    const val ENCRYPTED_PREFIX_LENGTH: Long = 1024L * 1024L
    // 边界判定算法版本：每次调整算法必须 +1，让旧缓存（键带 v 前缀）自动失效重算。
    // v14: 1MB 经验规则快路径——加密工具固定 XOR 文件前 1MB（ffmpeg 实证：四个真实文件
    //      在 K=1MB 解密均 0 错误）；差分证据吻合时只扫约 1.25MB 即返回，证据不足再退回
    //      累积差分 argmin 全扫。修复 64 位扩展长度 mdat + 高噪数据导致的误判与全量扫描卡顿。
    const val BOUNDARY_ANALYSIS_VERSION = 15

    // mdat 差分扫描的读取块大小。
    private const val MEDIA_SCAN_CHUNK_SIZE = 256 * 1024

    // 1MB 快路径验证：从 1MB 点再往后扫的跨度；1MB 处差分距最低谷的容差。
    private const val ONE_MB_VALIDATION_SPAN = 256L * 1024L
    private const val ONE_MB_VALIDATION_MARGIN = 32L

    // 判定「切换为明文」所需的差分净增量（谷底深度 / 1MB 之后明文证据 / argmin 路径后缀）。
    private const val MIN_PLAIN_EVIDENCE = 24L

    // argmin 全扫的早退：差分自最低点回升超过该值即可提前结束（边界一定在最低点之后）。
    private const val EARLY_EXIT_RISE = 512L
    private const val MAX_NAL_SIZE = 8L * 1024L * 1024L
    private val FTYP = byteArrayOf('f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
    private val MOOV = byteArrayOf('m'.code.toByte(), 'o'.code.toByte(), 'o'.code.toByte(), 'v'.code.toByte())
    private val TOP_LEVEL_BOXES = setOf("ftyp", "free", "mdat", "wide", "skip", "moov", "styp", "moof", "sidx")

    // 完整性检测遍历的顶层 box 上限：分片 MP4 的 moof/mdat 对会很多，超过即放弃判定。
    private const val MAX_COMPLETENESS_BOXES = 256

    // 跨界边界修正的探针参数。
    private const val BOUNDARY_PROBE_WINDOW = 256L * 1024L
    private const val PLAIN_TABLE_ZERO_RATIO = 0.08
    private val BOX_TYPE_PROBES = listOf(
        "mvhd", "trak", "tkhd", "mdia", "mdhd", "hdlr", "minf", "stbl", "stsd", "stts",
        "stss", "stsc", "stsz", "stco", "co64", "udta", "meta", "ilst", "mvex", "trex",
        "smhd", "vmhd", "dinf"
    )

    fun hasEncryptedMp4Header(header: ByteArray, bytesRead: Int = header.size): Boolean {
        if (bytesRead < 8) return false
        for (index in FTYP.indices) {
            val decoded = header[index + 4].toInt() xor XOR_KEY
            if (decoded.toByte() != FTYP[index]) return false
        }
        return true
    }

    fun boundaryCacheKey(uri: String, fileSize: Long, lastModified: Long): String {
        return "v$BOUNDARY_ANALYSIS_VERSION:$fileSize:$lastModified:$uri"
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
            // size==1 是 64 位扩展长度标记，不能当作非法长度拦截（否则扩展长度 mdat 直接全文件 XOR）。
            if (decodedType !in TOP_LEVEL_BOXES || (decodedSize != 1L && decodedSize < 8L)) {
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

    /** 加密工具固定 XOR 文件前 1MB。当 findEncryptedPrefixEnd 返回的边界超过 1MB
     *  （moov 等超大 box 的尾部跨过 1MB，首个全明文 box 在其后出现）时，检查
     *  [1MB, 边界) 按明文读是否呈现 MP4 结构痕迹：命中已知 box 类型 ASCII，或
     *  样本表特有的高 0x00 字节率（密文按明文读 0x00 率约为随机 0.4%）。命中则
     *  真实边界是 1MB——这段是明文，按原边界播放会把明文再 XOR 一遍导致 moov
     *  解析失败（打开黑屏，ffmpeg 实证 `missing mandatory atoms`）。 */
    fun refineBoundaryForOversizedBox(inputStream: InputStream, fileSize: Long, candidate: Long): Long {
        if (candidate <= ENCRYPTED_PREFIX_LENGTH || candidate > fileSize) return candidate
        val probeLength = minOf(candidate, ENCRYPTED_PREFIX_LENGTH + BOUNDARY_PROBE_WINDOW) - ENCRYPTED_PREFIX_LENGTH
        if (probeLength < 16L) return candidate
        inputStream.skipFully(ENCRYPTED_PREFIX_LENGTH)
        val probe = inputStream.readExact(probeLength.toInt())
        if (probe.size < probeLength.toInt()) return candidate

        val plainBoxTypeHit = BOX_TYPE_PROBES.any { probe.indexOf(it.toByteArray(Charsets.US_ASCII)) >= 0 }
        if (plainBoxTypeHit) return ENCRYPTED_PREFIX_LENGTH

        val zeroRatio = probe.count { it == 0.toByte() }.toDouble() / probe.size
        return if (zeroRatio >= PLAIN_TABLE_ZERO_RATIO) ENCRYPTED_PREFIX_LENGTH else candidate
    }

    /** 顶层 box 遍历的完整性检测：依次走过顶层 box，若某个 box 的声明长度超出文件末尾，
     *  返回缺失的字节数；结构完整、或无法解析出可信结构时返回 0（不判定）。
     *  截断的文件（如 mdat 声明 15.9MB 而文件只有 1MB）moov 仍可解析、时长正常，
     *  但采样表指向的媒体数据不存在，播放到断点即 EOF。 */
    fun findMissingTailBytes(inputStream: InputStream, fileSize: Long): Long {
        var offset = 0L
        var guard = 0
        while (offset + 8L <= fileSize && guard++ < MAX_COMPLETENESS_BOXES) {
            val header = inputStream.readExact(8)
            if (header.size < 8) return 0L

            // 同一段字节只有一种读法能命中顶层 box 名（顶层类型异或 0x12 后都不再是顶层类型）。
            val plainSize = header.readUInt32Plain(0)
            val plainType = header.copyOfRange(4, 8).toAsciiString()
            val usePlain = plainType in TOP_LEVEL_BOXES && plainSize >= 8L

            val decodedSize = header.readUInt32Xor(offset = 0)
            val decodedType = header.copyOfRange(4, 8).xorBytes().toAsciiString()
            if (!usePlain && (decodedType !in TOP_LEVEL_BOXES || (decodedSize != 1L && decodedSize < 8L))) {
                return 0L
            }

            val headerSize: Long
            val boxSize: Long
            if (usePlain) {
                headerSize = 8L
                boxSize = plainSize
            } else if (decodedSize == 1L) {
                val extendedSize = inputStream.readExact(8)
                if (extendedSize.size < 8) return 0L
                headerSize = 16L
                boxSize = extendedSize.readUInt64Xor(offset = 0)
            } else {
                headerSize = 8L
                boxSize = decodedSize
            }
            if (boxSize < headerSize) return 0L
            if (offset + boxSize > fileSize) return offset + boxSize - fileSize

            inputStream.skipFully(boxSize - headerSize)
            offset += boxSize
        }
        return 0L
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

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        outer@ for (index in 0..size - pattern.size) {
            for (patternIndex in pattern.indices) {
                if (this[index + patternIndex] != pattern[patternIndex]) continue@outer
            }
            return index
        }
        return -1
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

    // 经验规则：加密工具固定 XOR 文件前 1MB。先用累积「明文-密文 NAL 命中差分」
    // 验证 1MB 处是否处于差分最低谷且其后转明文，命中则直接返回 1MB（只需扫约 1.25MB）。
    // 验证失败退化为全 payload 差分扫描：最低点之后的首个明文 NAL 作为边界。
    private fun InputStream.findPlainMediaTransition(payloadStart: Long, payloadSize: Long): Long {
        if (payloadSize <= 0L) return -1L

        val oneMbPos = ENCRYPTED_PREFIX_LENGTH - payloadStart
        val hasOneMbCandidate = oneMbPos >= 0L && oneMbPos < payloadSize

        var delta = 0L
        var minDelta = 0L
        var minPos = -1L
        var firstPlainHit = -1L
        var boundaryAfterMin = -1L
        var deltaAtOneMb: Long? = null
        var oneMbEvaluated = !hasOneMbCandidate
        var offset = 0L

        fun oneMbValidated(): Boolean {
            val d1 = deltaAtOneMb ?: return false
            return minDelta <= -MIN_PLAIN_EVIDENCE &&
                d1 - minDelta <= ONE_MB_VALIDATION_MARGIN &&
                delta - d1 >= MIN_PLAIN_EVIDENCE
        }

        while (offset < payloadSize) {
            val wanted = min(MEDIA_SCAN_CHUNK_SIZE.toLong(), payloadSize - offset).toInt()
            val chunk = readExact(wanted)
            if (chunk.isEmpty()) break
            var index = 0
            while (index + 5 < chunk.size) {
                val bytesAvailable = payloadSize - offset - index
                val plain = chunk.isNalStart(index, xor = false, bytesAvailable)
                val xor = chunk.isNalStart(index, xor = true, bytesAvailable)
                if (plain && !xor) {
                    delta++
                    if (firstPlainHit < 0) firstPlainHit = offset + index
                    if (minPos >= 0 && boundaryAfterMin < 0) boundaryAfterMin = offset + index
                } else if (xor && !plain) {
                    delta--
                    if (delta < minDelta) {
                        minDelta = delta
                        minPos = offset + index
                        boundaryAfterMin = -1
                    }
                }
                if (!oneMbEvaluated && deltaAtOneMb == null && offset + index >= oneMbPos) {
                    deltaAtOneMb = delta
                }
                index++
            }
            offset += chunk.size

            if (!oneMbEvaluated && offset >= oneMbPos + ONE_MB_VALIDATION_SPAN) {
                oneMbEvaluated = true
                if (oneMbValidated()) {
                    skipFully(payloadSize - offset)
                    return ENCRYPTED_PREFIX_LENGTH
                }
            }
            if (oneMbEvaluated && minPos >= 0 && delta - minDelta >= EARLY_EXIT_RISE) break
        }
        skipFully((payloadSize - offset).coerceAtLeast(0L))

        if (!oneMbEvaluated && oneMbValidated()) return ENCRYPTED_PREFIX_LENGTH

        if (minPos >= 0) {
            if (delta - minDelta < MIN_PLAIN_EVIDENCE || boundaryAfterMin < 0) return -1L
            return payloadStart + boundaryAfterMin
        }
        if (delta >= MIN_PLAIN_EVIDENCE && firstPlainHit >= 0) {
            return payloadStart + firstPlainHit
        }
        return -1L
    }

    private fun ByteArray.isNalStart(offset: Int, xor: Boolean, bytesAvailable: Long): Boolean {
        if (offset + 5 >= size) return false
        val nalLength = readUInt32At(offset, xor)
        if (nalLength <= 0L || nalLength > MAX_NAL_SIZE || nalLength + 4L > bytesAvailable) return false

        val firstHeader = (this[offset + 4].toInt() xor if (xor) XOR_KEY else 0) and 0xFF
        if ((firstHeader and 0x80) != 0) return false

        val h264Type = firstHeader and 0x1F
        val isH264 = h264Type in 1..12

        val secondHeader = (this[offset + 5].toInt() xor if (xor) XOR_KEY else 0) and 0xFF
        val h265Type = (firstHeader shr 1) and 0x3F
        val isH265 = h265Type in 0..40 && (secondHeader and 0x07) != 0
        return isH264 || isH265
    }
}
