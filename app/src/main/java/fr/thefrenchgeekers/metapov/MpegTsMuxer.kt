package fr.thefrenchgeekers.metapov

import java.io.ByteArrayOutputStream

/** Minimal video-only MPEG-TS muxer for Annex-B HEVC access units. */
class MpegTsMuxer {
    private var patCc = 0; private var pmtCc = 0; private var videoCc = 0; private var frames = 0
    private val pmtPid = 0x1000; private val videoPid = 0x0100

    fun muxHevc(accessUnit: ByteArray, ptsUs: Long): ByteArray {
        val out = ByteArrayOutputStream()
        if (frames++ % 30 == 0) { out.write(sectionPacket(0, patSection(), patCc++ and 15)); out.write(sectionPacket(pmtPid, pmtSection(), pmtCc++ and 15)) }
        val pts90 = (ptsUs * 90L / 1000L) and 0x1FFFFFFFFL
        val pes = ByteArrayOutputStream().apply {
            write(byteArrayOf(0,0,1,0xE0.toByte(),0,0,0x80.toByte(),0x80.toByte(),5))
            write(encodePts(pts90)); write(accessUnit)
        }.toByteArray()
        packetizePes(out, pes, pts90)
        return out.toByteArray()
    }

    private fun packetizePes(out: ByteArrayOutputStream, pes: ByteArray, pcr90: Long) {
        var off=0; var first=true
        while(off<pes.size){
            val packet=ByteArray(188){0xFF.toByte()}; packet[0]=0x47
            packet[1]=(((videoPid shr 8) and 0x1F) or if(first)0x40 else 0).toByte(); packet[2]=(videoPid and 0xFF).toByte()
            val remaining=pes.size-off
            val wantPcr=first
            val minAdapt=if(wantPcr) 8 else 0
            val payloadMax=184-minAdapt
            val payloadLen=minOf(remaining,payloadMax)
            val adaptLen=184-payloadLen
            var pos=4
            if(adaptLen>0){
                packet[3]=(0x30 or (videoCc++ and 15)).toByte()
                packet[pos++]=(adaptLen-1).toByte()
                if(adaptLen>1){
                    packet[pos++]=(if(wantPcr)0x10 else 0).toByte()
                    if(wantPcr){ writePcr(packet,pos,pcr90); pos+=6 }
                    while(pos<4+adaptLen) packet[pos++]=0xFF.toByte()
                }
            } else packet[3]=(0x10 or (videoCc++ and 15)).toByte()
            System.arraycopy(pes,off,packet,pos,payloadLen); off+=payloadLen; out.write(packet); first=false
        }
    }

    private fun writePcr(b:ByteArray,o:Int,pcr90:Long){ val base=pcr90 and 0x1FFFFFFFFL; b[o]=(base shr 25).toByte(); b[o+1]=(base shr 17).toByte(); b[o+2]=(base shr 9).toByte(); b[o+3]=(base shr 1).toByte(); b[o+4]=(((base and 1) shl 7) or 0x7E).toByte(); b[o+5]=0 }
    private fun encodePts(v:Long)=byteArrayOf(((0x2 shl 4)|(((v shr 30)&7).toInt() shl 1)|1).toByte(),(v shr 22).toByte(),((((v shr 15)&0x7F).toInt() shl 1)|1).toByte(),(v shr 7).toByte(),(((v and 0x7F).toInt() shl 1)|1).toByte())

    private fun patSection():ByteArray { val s=byteArrayOf(0,0xB0.toByte(),0x0D,0,1,0xC1.toByte(),0,0,0,1,(0xE0 or (pmtPid shr 8)).toByte(),(pmtPid and 255).toByte()); return withCrc(s) }
    private fun pmtSection():ByteArray { val s=byteArrayOf(2,0xB0.toByte(),0x12,0,1,0xC1.toByte(),0,0,(0xE0 or (videoPid shr 8)).toByte(),(videoPid and 255).toByte(),0xF0.toByte(),0,0x24,(0xE0 or (videoPid shr 8)).toByte(),(videoPid and 255).toByte(),0xF0.toByte(),0); return withCrc(s) }
    private fun withCrc(s:ByteArray):ByteArray { val c=crc32mpeg(s); return s+byteArrayOf((c shr 24).toByte(),(c shr 16).toByte(),(c shr 8).toByte(),c.toByte()) }
    private fun sectionPacket(pid:Int,section:ByteArray,cc:Int):ByteArray { val p=ByteArray(188){0xFF.toByte()}; p[0]=0x47;p[1]=(((pid shr 8) and 0x1F) or 0x40).toByte();p[2]=(pid and 255).toByte();p[3]=(0x10 or cc).toByte();p[4]=0;System.arraycopy(section,0,p,5,section.size);return p }
    private fun crc32mpeg(data:ByteArray):Int { var crc=0xFFFFFFFF.toInt(); for(x in data){ crc=crc xor ((x.toInt() and 255) shl 24); repeat(8){ crc=if(crc and 0x80000000.toInt()!=0)(crc shl 1) xor 0x04C11DB7 else crc shl 1 } }; return crc }
}
