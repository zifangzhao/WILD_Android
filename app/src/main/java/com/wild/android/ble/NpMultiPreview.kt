package com.wild.android.ble

import java.io.IOException

/** The PC console's compact NP1 and checked/replayable NP2 preview contracts. */
internal object NpMultiPreview {
    private fun u(b: ByteArray, at: Int) = NpBleProtocol.u32(b, at)
    private fun word(b: ByteArray, at: Int) = (b[at].toInt() and 255) or ((b[at+1].toInt() and 255) shl 8)
    private fun put16(b: ByteArray, at: Int, value: Int) { b[at]=value.toByte(); b[at+1]=(value ushr 8).toByte() }
    private fun zero(b: ByteArray, from: Int, to: Int) = (from until to).all { b[it]==0.toByte() }
    fun matches(b: ByteArray, id: Long) = b.size==224 && u(b,0)==0x3256504eL && u(b,4)==id
    private fun request(kind: Int, id: Long): ByteArray {
        require(id in 1..0xffffffffL)
        return ByteArray(64).also { b -> listOf(0x3256504eL,1,kind.toLong(),id).forEachIndexed { i,v -> NpBleProtocol.put(b,i*4,v) } }
    }
    fun capabilities(id: Long) = NpBleProtocol.seal(request(8,id))
    /** Only the old firmware's typed unsupported reply permits legacy fallback. */
    fun supported(b: ByteArray, id: Long): Boolean {
        require(matches(b,id))
        if ((u(b,28) ushr 8) and 255L == 129L) return false
        require(NpBleProtocol.checked(b) && u(b,28)==(8L shl 8) && u(b,24)==0L && u(b,32)==1L && u(b,36)==2L &&
            u(b,40)==500L && u(b,44)==40L && u(b,48) in 12L..64L && u(b,52)==7L && zero(b,60,220) &&
            u(b,12).toInt() in NpBleProtocol.rates && u(b,8)!=0L) { "Invalid multi-channel preview capabilities; no legacy fallback guessed." }
        checkStatus(u(b,56)); return true
    }
    fun descriptor(s: NpPreviewSelection): Int { require(s.probe in 1..2 && s.channel in 0..383); return s.channel or ((s.probe-1) shl 9) }
    fun offset(s: NpPreviewSelection, phase: Int): Int {
        require(phase in 0 until 1600); descriptor(s)
        val c=s.channel%128; return (1600-phase+(c%32)/2*100+4+((c/32)*2+(c and 1))*12)%1600
    }
    fun configure(id: Long, session: Long, selections: List<NpPreviewSelection>, phases: List<Int>): ByteArray {
        require(session in 1..0xffffffffL && selections.size in 1..2 && selections.distinct().size==selections.size && phases.size==selections.size)
        val b=request(6,id); NpBleProtocol.put(b,16,500); NpBleProtocol.put(b,20,selections.size.toLong()); NpBleProtocol.put(b,24,session)
        selections.forEachIndexed { i,s -> offset(s,phases[i]); put16(b,32+i*2,descriptor(s)); put16(b,40+i*2,phases[i]) }
        return NpBleProtocol.seal(b)
    }
    fun feedback(id: Long, session: Long, ack: Long, missing: Long=0, stop: Boolean=false): ByteArray {
        require(session in 1..0xffffffffL && ack in 0..0xffffffffL && missing in 0..0xffffffffL)
        val b=request(7,id)
        listOf(session,ack,if(stop)0L else missing,if(stop)1L else 0L).forEachIndexed { i,v -> NpBleProtocol.put(b,16+i*4,v) }
        return NpBleProtocol.seal(b)
    }
    private fun checkStatus(status: Long) {
        if(status!=0L) throw IOException(when(status) {
            1L -> "Preview settings rejected."; 2L -> "Preview waiting for recording."; 3L -> "Preview recording changed."
            4L -> "Preview source interrupted."; 5L -> "Preview ended with recording."; 6L -> "Preview paused for recording bandwidth."
            7L,9L -> "Preview replay window exhausted."; 8L -> "Preview signal exceeded display range."
            else -> "Preview unavailable (reason $status)."
        }+" Recording is independent; re-enable preview after checking status.")
    }
    data class Frame(val sequence: Long, val first: Long, val stride: Long, val crc: Long, val traces: List<List<NpPreviewPoint>>)
    /** Bounded out-of-order queue; only checked, contiguous original samples are published. */
    class Receiver(val id: Long, val session: Long, val hz: Int, private val selections: List<NpPreviewSelection>, private val phases: List<Int>) {
        private val waiting=sortedMapOf<Long,Frame>()
        private var next=1L; private var nextTick: Long?=null; private var checksumLoss=false
        val acknowledged get()=next-1
        val missing get()=if(waiting.isNotEmpty() || checksumLoss) next else 0L
        fun feedback(stalled: Boolean=false) = feedback(id,session,acknowledged,if(stalled) next else missing)
        fun accept(b: ByteArray): List<Frame> {
            if(!matches(b,id)) return emptyList()
            if(!NpBleProtocol.checked(b)) { checksumLoss=true; return emptyList() }
            require(u(b,8)==session && u(b,12)==hz.toLong() && hz in NpBleProtocol.rates && u(b,28)==((6L shl 8) or (selections.size.toLong() shl 16))) { "Preview session/rate/selection changed." }
            checkStatus(u(b,56))
            val delay=when(hz) {7500->208;15000->418;20000->538;30000->838;else->error("Invalid preview source rate")}
            require(u(b,24)==((160L shl 16) or 40) && u(b,32)!=0L && u(b,36)==500L && zero(b,44,48) && word(b,52)==1 && word(b,54)==delay) { "Invalid filtered preview metadata." }
            val first=u(b,16) or (u(b,20) shl 32); val stride=hz/500*1600L
            require(first>=0 && first<=Long.MAX_VALUE-40*stride-1599) { "Preview timestamp overflow." }
            for(i in 0..1) {
                if(i<selections.size) require(word(b,40+i*2)==descriptor(selections[i]) && word(b,48+i*2)==offset(selections[i],phases[i])) { "Preview channel alignment changed." }
                else require(word(b,40+i*2)==0 && word(b,48+i*2)==0 && (0 until 40).all { word(b,60+(it*2+i)*2)==0 }) { "Nonzero inactive preview lane." }
            }
            val sequence=u(b,32); val distance=(sequence-next).toInt()
            if(distance<0) return emptyList()
            require(distance<64) { "Preview replay window exceeded." }
            waiting[sequence]?.let { require(it.crc==u(b,220)) { "Preview replay changed its samples." }; return emptyList() }
            // Firmware values are signed Q3 nominal counts, already low-pass filtered.
            val traces=selections.indices.map { lane -> (0 until 40).map { row -> NpPreviewPoint(first+word(b,48+lane*2)+row*stride,word(b,60+(row*2+lane)*2).toShort()/8.0) } }
            waiting[sequence]=Frame(sequence,first,stride,u(b,220),traces)
            val ready=mutableListOf<Frame>()
            while(true) {
                val frame=waiting.remove(next)?:break
                require(nextTick==null || frame.first==nextTick) { "Preview source timestamps are discontinuous." }
                nextTick=frame.first+40*stride; ready.add(frame); next++
                require(next<=0xffffffffL) { "Preview sequence exhausted; reopen preview." }; checksumLoss=false
            }
            return ready
        }
    }
    fun compactRequest(id: Long, kind: Int, probe: Int, channels: List<Int>, phase: Int, session: Long, band: Int): ByteArray {
        require(kind in listOf(5,3,4) && channels.size in 1..4 && channels.distinct().size==channels.size && channels.all { it in 0..383 })
        val b=NpBlePreview.request(1,id,if(kind==5)2 else kind,probe,channels.first(),phase,session,band)
        NpBleProtocol.put(b,8,kind.toLong()); NpBleProtocol.put(b,36,channels.size.toLong())
        channels.drop(1).forEachIndexed { i,ch -> NpBleProtocol.put(b,40+i*4,ch.toLong()) }; return NpBleProtocol.seal(b)
    }
    private fun sourceOffset(channel: Int, band: Int, phase: Int): Int {
        val adc=channel/24*2+(channel and 1); val sample=channel%24/2; val w=if(adc<30)adc%5 else adc-30
        return phase+(if(band==0)(sample+1)*60 else sample*780)+w*10
    }
    fun compact(b: ByteArray, id: Long, session: Long, hz: Int, probe: Int, channels: List<Int>, band: Int, phase: Int): Frame? {
        if(b.size!=224 || u(b,0)!=0x3156504eL || u(b,4)!=id) return null
        val stride=u(b,192); val first=u(b,16) or (u(b,20) shl 32); val period=if(band==0)780 else 9360
        require(channels.size in 1..4 && channels.distinct().size==channels.size && channels.all { it in 0..383 } && phase in 0 until 9360)
        require(NpBleProtocol.checked(b) && session!=0L && u(b,8)==session && u(b,12)==hz.toLong() && hz in NpBleProtocol.rates &&
            u(b,28)==(probe.toLong() or (5L shl 8) or (band.toLong() shl 12) or (channels.first().toLong() shl 16)) &&
            u(b,24)==((128L shl 16) or 16) && u(b,200)==channels.size.toLong() && u(b,204)==1L && zero(b,208,220) &&
            (if(band==0)stride==(hz+499)/500*780L else stride==(hz+5999)/6000*9360L || stride==maxOf(1,hz/6000)*9360L) &&
            first>=0 && first<=Long.MAX_VALUE-16*stride-period) { "Invalid compact NP1 preview metadata." }
        val origin=sourceOffset(channels.first(),band,phase)%period
        require(first%period==origin.toLong()) { "NP1 preview timestamp/phase mismatch." }
        var any=0
        val traces=mutableListOf<List<NpPreviewPoint>>()
        for(lane in 0..3) {
            val ch=word(b,160+lane*2); val mask=word(b,168+lane*2); val offset=u(b,176+lane*4)
            if(lane>=channels.size) require(ch==0 && mask==0 && offset==0L && zero(b,32+lane*32,64+lane*32)) { "Nonzero inactive NP1 lane." }
            else {
                require(ch==channels[lane] && offset==((sourceOffset(ch,band,phase)%period+period-origin)%period).toLong()) { "NP1 channel/timestamp changed." }
                any=any or mask
                traces.add((0 until 16).map { row ->
                    val raw=word(b,32+lane*32+row*2); require(raw<=1023) { "Invalid NP1 ADC code." }
                    NpPreviewPoint(first+offset+row*stride,if(mask and (1 shl row)==0) Double.NaN else 512.0-raw)
                })
            }
        }
        require(any!=0) { "Empty NP1 compact preview." }; return Frame(0,first,stride,u(b,220),traces)
    }
}
