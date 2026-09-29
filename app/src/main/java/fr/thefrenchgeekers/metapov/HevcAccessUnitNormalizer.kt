package fr.thefrenchgeekers.metapov

data class NormalizedHevcAccessUnit(
    val data: ByteArray,
    val isKeyFrame: Boolean,
)

class HevcAccessUnitNormalizer {
    private val parameterSets = linkedMapOf<Int, ByteArray>()

    fun normalize(data: ByteArray, isCodecConfig: Boolean): NormalizedHevcAccessUnit? {
        if (data.isEmpty()) return null

        var isKeyFrame = false
        var hasVps = false
        var hasSps = false
        var hasPps = false

        forEachNalUnit(data) { start, type, end ->
            when (type) {
                NAL_VPS, NAL_SPS, NAL_PPS -> {
                    parameterSets[type] = data.copyOfRange(start, end)
                    when (type) {
                        NAL_VPS -> hasVps = true
                        NAL_SPS -> hasSps = true
                        NAL_PPS -> hasPps = true
                    }
                }
            }
            if (type in NAL_IRAP_FIRST..NAL_IRAP_LAST) {
                isKeyFrame = true
            }
        }

        val completeCsd = completeCodecConfig()
        val alreadyHasCompleteCsd = hasVps && hasSps && hasPps

        val normalized =
            if (isKeyFrame && completeCsd != null && !alreadyHasCompleteCsd) {
                completeCsd + data
            } else {
                data
            }

        // Codec-config-only access units are intentionally kept in-band. The cached copy is also
        // prepended to later keyframes so a receiver that joins after stream start can still parse
        // the HEVC format (width/height and decoder configuration).
        return NormalizedHevcAccessUnit(
            data = normalized,
            isKeyFrame = isKeyFrame,
        )
    }

    fun reset() {
        parameterSets.clear()
    }

    private fun completeCodecConfig(): ByteArray? {
        val vps = parameterSets[NAL_VPS] ?: return null
        val sps = parameterSets[NAL_SPS] ?: return null
        val pps = parameterSets[NAL_PPS] ?: return null
        return vps + sps + pps
    }

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
