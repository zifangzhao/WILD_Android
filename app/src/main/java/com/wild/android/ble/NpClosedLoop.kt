package com.wild.android.ble

data class NpDetector(val probe:Int=1,val channel:Int=0,val gain:Float=5f,val rectify:Boolean=true)
data class NpClosedLoopSettings(val detectors:List<NpDetector>,val trainingStartMs:Long=0,val trainingDurationMs:Long=10000)
data class NpClosedLoopStatus(val result:Long,val flags:Long,val configCrc:Long,val session:Long,val hz:Int,
    val maxLanes:Int,val probeMask:Int,val filterMask:Int,val channelGroups:Int,val dropped:Long,
    val reportedAtMs:Long=System.currentTimeMillis()) {
    val supported get() = result==0L && flags and 1L!=0L
    val configured get() = flags and 2L!=0L
    val active get() = flags and 4L!=0L
}

/** NPCC is distinct from CE64 DSP/stimulation. Only qualified Ripple profiles. */
internal object NpClosedLoop {
    fun request(op:Int,id:Long,hz:Int=0,settings:NpClosedLoopSettings?=null):ByteArray {
        require(op in 0..2 && id in 1..0xffffffffL && (settings==null || op==1))
        val b=ByteArray(64)
        listOf(0x4343504eL,1,op.toLong(),id).forEachIndexed { i,v -> NpBleProtocol.put(b,i*4,v) }
        if(settings!=null) {
            require(hz in NpBleProtocol.rates && settings.detectors.size in 1..2 && settings.trainingStartMs in 0..0xffffffffL && settings.trainingDurationMs in 1..0xffffffffL)
            NpBleProtocol.put(b,20,hz.toLong());NpBleProtocol.put(b,24,settings.trainingStartMs);NpBleProtocol.put(b,28,settings.trainingDurationMs);NpBleProtocol.put(b,32,settings.detectors.size.toLong())
            var rectify=0L
            settings.detectors.forEachIndexed { i,d ->
                require(d.probe in 1..3 && d.channel in 0..383 && d.gain.isFinite() && d.gain in 0.1f..1000f) { "Invalid detector source or threshold gain (0.1–1000)." }
                NpBleProtocol.put(b,36+i*8,d.probe.toLong() or (6L shl 8) or (d.channel.toLong() shl 16))
                NpBleProtocol.put(b,40+i*8,d.gain.toRawBits().toLong() and 0xffffffffL)
                if(d.rectify) rectify=rectify or (1L shl i)
            };NpBleProtocol.put(b,52,rectify)
        }
        return NpBleProtocol.seal(b)
    }
    fun matches(q:ByteArray,r:ByteArray)=r.size==64 && NpBleProtocol.checked(r) && NpBleProtocol.u32(r,0)==0x5243504eL && (4..12 step 4).all { NpBleProtocol.u32(q,it)==NpBleProtocol.u32(r,it) }
    fun parse(q:ByteArray,r:ByteArray):NpClosedLoopStatus {
        require(matches(q,r)) { "Closed-loop reply identity/op/CRC mismatch." }
        fun u(at:Int)=NpBleProtocol.u32(r,at)
        val f=u(20)
        require(u(16)<=6 && f and 31L.inv()==0L && u(40)<=2 && u(44) and 7L.inv()==0L && u(48) and 1023L.inv()==0L && u(52) and 511L.inv()==0L &&
            (f and 8L==0L || u(36)!=0L) && (f and 2L==0L || f and 1L!=0L) && (f and 20L==0L || f and 2L!=0L)) { "Invalid closed-loop capability/state flags." }
        return NpClosedLoopStatus(u(16),f,u(24),u(28),u(32).toInt(),u(40).toInt(),u(44).toInt(),u(48).toInt(),u(52).toInt(),u(56))
    }
    fun validate(s:NpDeviceStatus,caps:NpClosedLoopStatus,settings:NpClosedLoopSettings) {
        require(s.canConfigure && !s.isMedia && s.fault==0L && caps.supported) { "Stopped, fault-free recorder with closed-loop support required." }
        require(if(s.version==1) s.probeMask==3 && s.sampleRate==15000 else s.probeMask==7 && s.sampleRate==20000) { "Qualified SD profiles: NP1 P1+P2 at 15 kHz; NP2 P1+P2+P3 at 20 kHz." }
        require(settings.detectors.size in 1..2 && settings.detectors.size<=caps.maxLanes)
        require(settings.detectors.map { it.probe to it.channel }.distinct().size==settings.detectors.size) { "Choose different detector channels." }
        if(s.version==2 && settings.detectors.size==2) require(settings.detectors.all { it.probe==2 && it.channel in 0..1 }) { "Two NP2 SD detectors must use P2 channels 0 and 1." }
        settings.detectors.forEach { d ->
            require(d.probe in 1..3 && d.channel in 0..383)
            val port=1 shl (d.probe-1);val group=1 shl ((d.probe-1)*3+d.channel/128)
            require(s.probeMask and port!=0 && caps.probeMask and port!=0 && caps.channelGroups and group!=0 && caps.filterMask and (1 shl 6)!=0) { "Firmware does not admit this detector source/Ripple filter." }
        }
        request(1,1,s.sampleRate,settings) // Validate bounds before any write.
    }
}
