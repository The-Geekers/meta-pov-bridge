package fr.thefrenchgeekers.metapov

import java.io.ByteArrayOutputStream

data class HevcCodecConfig(
    val vps: ByteArray,
    val sps: ByteArray,
    val pps: ByteArray,
)

data class NormalizedHevcAccessUnit(
    val srtData: ByteArray,
    val mediaData: ByteArray,
    val isKeyFrame: Boolean,
    val codecConfig: HevcCodecConfig?,
)

class HevcAccessUnitNormalizer {
    private val parameterSets = linkedMapOf<Int, ByteArray>()

    fun normalize(data: ByteArray): NormalizedHevcAccessUnit? {
        if (data.isEmpty()) return null

        var isKeyFrame = false
        var configUpdated = false
        val media = ByteArrayOutputStream()

        forEachNalUnit(data) { start, type, end ->
            val nal = data.copyOfRange(start, end)
            when (type) {
                NAL_VPS, NAL_SPS, NAL_PPS -> {
                    parameterSets[type] = nal
                    configUpdated = true
                }
                else -> media.write(nal)
            }
            if (type in NAL_IRAP_FIRST..NAL_IRAP_LAST) {
                isKeyFrame = true
            }
        }

        val mediaData = media.toByteArray()
        val codecConfig = completeCodecConfig()
        val changedConfig = if (configUpdated) codecConfig else null

        val srtData =
            when {
                isKeyFrame && codecConfig != null -> codecConfig.asAnnexB() + mediaData
                mediaData.isEmpty() && codecConfig != null -> codecConfig.asAnnexB()
                else -> mediaData
            }

        if (srtData.isEmpty() && mediaData.isEmpty()) return null

        return NormalizedHevcAccessUnit(
            srtData = srtData,
            mediaData = mediaData,
            isKeyFrame = isKeyFrame,
            codecConfig = changedConfig,
        )
    }

    fun reset() {
        parameterSets.clear()
    }

    private fun completeCodecConfig(): HevcCodecConfig? {
        val vps = parameterSets[NAL_VPS] ?: return null
        val sps = parameterSets[NAL_SPS] ?: return null
        val pps = parameterSets[NAL_PPS] ?: return null
        return HevcCodecConfig(vps = vps, sps = sps, pps = pps)
    }

    private fun HevcCodecConfig.asAnnexB(): ByteArray = vps + sps + pps

    private inline fun forEachNalUnit(
        data: ByteArray,
        action: (startCodeOffset: Int, nalType: Int, nextNalOffset: Int) -> Unit,
    ) {
        val starts = mutableListOf<Pair<Int, Int>>()
        var i = 0

        while (i < data.size - 3) {
            val startCodeLength =
                when {
                    data[i] == 0.toByte() &&
                        data[i + 1] == 0.toByte() &&
                        data[i + 2] == 1.toByte() -> 3
                    i + 3 < data.size &&
                        data[i] == 0.toByte() &&
                        data[i + 1] == 0.toByte() &&
                        data[i + 2] == 0.toByte() &&
                        data[i + 3] == 1.toByte() -> 4
                    else -> {
                        i++
                        continue
                    }
                }

            val header = i + startCodeLength
            if (header < data.size && !startsWithStartCode(data, header)) {
                val nalType = (data[header].toInt() and 0x7E) shr 1
                starts += i to nalType
            }
            i = header
        }

        starts.forEachIndexed { index, (offset, type) ->
            val end = if (index + 1 < starts.size) starts[index + 1].first else data.size
            action(offset, type, end)
        }
    }

    private fun startsWithStartCode(data: ByteArray, offset: Int): Boolean {
        if (offset + 2 >= data.size) return false
        if (data[offset] != 0.toByte() || data[offset + 1] != 0.toByte()) return false
        if (data[offset + 2] == 1.toByte()) return true
        return offset + 3 < data.size &&
            data[offset + 2] == 0.toByte() &&
            data[offset + 3] == 1.toByte()
    }

    companion object {
        private const val NAL_VPS = 32
        private const val NAL_SPS = 33
        private const val NAL_PPS = 34
        private const val NAL_IRAP_FIRST = 16
        private const val NAL_IRAP_LAST = 21
    }
}
