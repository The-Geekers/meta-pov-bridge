package fr.thefrenchgeekers.metapov

import java.io.ByteArrayOutputStream

/** Minimal video-only MPEG-TS muxer for Annex-B HEVC access units. */
class MpegTsMuxer {
    private var patCc = 0
    private var pmtCc = 0
    private var videoCc = 0
    private var frames = 0

    private val pmtPid = 0x1000
    private val videoPid = 0x0100

    fun muxHevc(accessUnit: ByteArray, ptsUs: Long): ByteArray {
        val out = ByteArrayOutputStream()

        if (frames++ % 30 == 0) {
            out.write(sectionPacket(0, patSection(), patCc++ and 0x0F))
            out.write(sectionPacket(pmtPid, pmtSection(), pmtCc++ and 0x0F))
        }

        val pts90 = (ptsUs * 90L / 1000L) and 0x1FFFFFFFFL

        val pes = ByteArrayOutputStream().apply {
            write(byteArrayOf(
                0x00, 0x00, 0x01, 0xE0.toByte(),
                0x00, 0x00,
                0x80.toByte(), 0x80.toByte(), 0x05
            ))
            write(encodePts(pts90))
            write(accessUnit)
        }.toByteArray()

        packetizePes(out, pes, pts90)
        return out.toByteArray()
    }

    private fun packetizePes(out: ByteArrayOutputStream, pes: ByteArray, pcr90: Long) {
        var offset = 0
        var first = true

        while (offset < pes.size) {
            val packet = ByteArray(188) { 0xFF.toByte() }
            packet[0] = 0x47
            packet[1] = (((videoPid shr 8) and 0x1F) or if (first) 0x40 else 0x00).toByte()
            packet[2] = (videoPid and 0xFF).toByte()

            val remaining = pes.size - offset
            val wantPcr = first
            val minAdaptation = if (wantPcr) 8 else 0
            val payloadMax = 184 - minAdaptation
            val payloadLength = minOf(remaining, payloadMax)
            val adaptationLength = 184 - payloadLength

            var pos = 4

            if (adaptationLength > 0) {
                packet[3] = (0x30 or (videoCc++ and 0x0F)).toByte()
                packet[pos++] = (adaptationLength - 1).toByte()

                if (adaptationLength > 1) {
                    packet[pos++] = (if (wantPcr) 0x10 else 0x00).toByte()

                    if (wantPcr) {
                        writePcr(packet, pos, pcr90)
                        pos += 6
                    }

                    while (pos < 4 + adaptationLength) {
                        packet[pos++] = 0xFF.toByte()
                    }
                }
            } else {
                packet[3] = (0x10 or (videoCc++ and 0x0F)).toByte()
            }

            System.arraycopy(pes, offset, packet, pos, payloadLength)
            offset += payloadLength
            out.write(packet)
            first = false
        }
    }

    private fun writePcr(buffer: ByteArray, offset: Int, pcr90: Long) {
        val base = pcr90 and 0x1FFFFFFFFL
        buffer[offset] = (base shr 25).toByte()
        buffer[offset + 1] = (base shr 17).toByte()
        buffer[offset + 2] = (base shr 9).toByte()
        buffer[offset + 3] = (base shr 1).toByte()
        buffer[offset + 4] = (((base and 1L) shl 7) or 0x7EL).toByte()
        buffer[offset + 5] = 0x00
    }

    private fun encodePts(value: Long): ByteArray {
        return byteArrayOf(
            ((0x2 shl 4) or ((((value shr 30) and 0x07).toInt()) shl 1) or 0x01).toByte(),
            ((value shr 22) and 0xFF).toByte(),
            (((((value shr 15) and 0x7F).toInt()) shl 1) or 0x01).toByte(),
            ((value shr 7) and 0xFF).toByte(),
            ((((value and 0x7F).toInt()) shl 1) or 0x01).toByte(),
        )
    }

    private fun patSection(): ByteArray {
        val section = byteArrayOf(
            0x00,
            0xB0.toByte(), 0x0D,
            0x00, 0x01,
            0xC1.toByte(),
            0x00, 0x00,
            0x00, 0x01,
            (0xE0 or (pmtPid shr 8)).toByte(),
            (pmtPid and 0xFF).toByte(),
        )
        return withCrc(section)
    }

    private fun pmtSection(): ByteArray {
        val section = byteArrayOf(
            0x02,
            0xB0.toByte(), 0x12,
            0x00, 0x01,
            0xC1.toByte(),
            0x00, 0x00,
            (0xE0 or (videoPid shr 8)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
            0x24,
            (0xE0 or (videoPid shr 8)).toByte(),
            (videoPid and 0xFF).toByte(),
            0xF0.toByte(), 0x00,
        )
        return withCrc(section)
    }

    private fun withCrc(section: ByteArray): ByteArray {
        val crc = crc32Mpeg(section)
        return section + byteArrayOf(
            (crc ushr 24).toByte(),
            (crc ushr 16).toByte(),
            (crc ushr 8).toByte(),
            crc.toByte(),
        )
    }

    private fun sectionPacket(pid: Int, section: ByteArray, continuityCounter: Int): ByteArray {
        val packet = ByteArray(188) { 0xFF.toByte() }
        packet[0] = 0x47
        packet[1] = (((pid shr 8) and 0x1F) or 0x40).toByte()
        packet[2] = (pid and 0xFF).toByte()
        packet[3] = (0x10 or (continuityCounter and 0x0F)).toByte()
        packet[4] = 0x00
        System.arraycopy(section, 0, packet, 5, section.size)
        return packet
    }

    private fun crc32Mpeg(data: ByteArray): Int {
        var crc = 0xFFFFFFFF.toInt()
        for (value in data) {
            crc = crc xor ((value.toInt() and 0xFF) shl 24)
            repeat(8) {
                crc = if ((crc and 0x80000000.toInt()) != 0) {
                    (crc shl 1) xor 0x04C11DB7
                } else {
                    crc shl 1
                }
            }
        }
        return crc
    }
}
