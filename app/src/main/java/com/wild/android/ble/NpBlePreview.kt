package com.wild.android.ble

/** Wire decoders retain raw codes; published traces use midpoint-minus-raw counts once. */
data class NpPreviewPoint(val tick: Long, val value: Double)
data class NpPreviewSelection(val probe: Int = 1, val channel: Int = 0)
data class NpPreviewTrace(val selection: NpPreviewSelection, val points: List<NpPreviewPoint> = emptyList())
data class NpPreviewUi(val enabled: Boolean = false, val message: String = "Preview is off; it never starts acquisition.",
    val points: List<NpPreviewPoint> = emptyList(), val sampleHz: Double = 0.0, val bits: Int = 0, val gaps: Long = 0,
    val probe: Int = 1, val channel: Int = 0, val band: Int = 0,
    val traces: List<NpPreviewTrace> = emptyList(), val stride: Long = 0, val sourceHz: Int = 0,
    val filtered: Boolean = false, val updatedAtMs: Long = 0)

internal object NpBlePreview {
    fun request(version: Int, id: Long, kind: Int, probe: Int, channel: Int, phase: Int = 0, session: Long = 0, band: Int = 0): ByteArray {
        require(version in 1..2 && id in 1..0xffffffffL && kind in listOf(0,2,3,4) && probe in 1..(if (version == 1) 3 else 2) && channel in 0..383 && phase in 0 until (if (version == 1) 9360 else 1600) && band in 0..(if (version == 1) 1 else 0))
        val b = ByteArray(64)
        listOf(if (version == 1) 0x3156504eL else 0x3256504eL,1,kind.toLong(),id,probe.toLong(),channel.toLong(),phase.toLong(),session,band.toLong())
            .forEachIndexed { i,v -> NpBleProtocol.put(b,i*4,v) }
        return NpBleProtocol.seal(b)
    }
    class Burst(val version: Int, val id: Long) {
        var session = 0L; private set
        var hz = 0; private set
        var kind = 0; private set
        var probe = 0; private set
        var channel = 0; private set
        var band = 0; private set
        var first = 0L; private set
        var stride = 0L; private set
        var valid = 0L; private set
        private var data: ByteArray? = null
        private var received = 0
        fun accept(b: ByteArray): Boolean {
            if (b.size != 224 || NpBleProtocol.u32(b,0) != (if (version == 1) 0x3156504eL else 0x3256504eL) || NpBleProtocol.u32(b,4) != id) return false
            fun u(at: Int) = NpBleProtocol.u32(b,at)
            val select = u(28); val rawKind = ((select ushr 8) and 255).toInt()
            require(if (version == 2) rawKind and 128 == 0 else rawKind and 15 != 6) { "Preview unavailable; device recording/state/bandwidth takes priority." }
            val k = if (version == 2) rawKind and 63 else rawKind and 15
            val p = (select and 255).toInt(); val c = (select ushr 16).toInt(); val bandNow = if (version == 1) rawKind ushr 4 else 0
            val total = (u(24) ushr 16).toInt(); var offset = (u(24) and 65535).toInt()
            val tick = u(16) or (u(20) shl 32); val rate = u(12).toInt()
            val expected = if (version == 1) { if (k == 0) 1560 else 160 } else if (k == 0) 3200 else 192
            require(k in listOf(0,2) && p in 1..(if (version == 1) 3 else 2) && c in 0..383 && bandNow in 0..(if (version == 1) 1 else 0) && rate in NpBleProtocol.rates && u(8) != 0L && total == expected && tick >= 0) { "Invalid preview metadata." }
            val step = if (version == 2) (rate/500).toLong()*1600 else u(192)
            if (version == 1) require(if (bandNow == 0) step == ((rate+499)/500).toLong()*780 else step == ((rate+5999)/6000).toLong()*9360 || step == maxOf(1,rate/6000).toLong()*9360)
            if (k == 2) { valid = offset.toLong(); require(valid != 0L); offset = 0 }
            if (data == null) {
                require(offset == 0) { "Preview fragment missing; no partial waveform displayed." }
                data = ByteArray(total); session = u(8); hz = rate; probe = p; channel = c; band = bandNow; first = tick; kind = k; stride = step
            }
            require(offset == received && total == data!!.size && session == u(8) && hz == rate && probe == p && channel == c && first == tick && kind == k && band == bandNow && stride == step) { "Mixed preview session/selection; reopen preview." }
            val n = minOf(if (version == 1) 160 else 192,total-offset)
            require(n > 0)
            if (version == 2 && rawKind and 64 != 0) require((32 until 32+n).all { b[it].toInt() and 255 < 64 })
            b.copyInto(data!!,offset,32,32+n); received += n
            return received == total
        }
        fun phase(selectedProbe: Int = probe): Int {
            val bytes = requireNotNull(data); require(kind == 0 && received == bytes.size)
            if (version == 2) {
                require(selectedProbe in 1..2)
                val sync = intArrayOf(0xd3,0x6a,0x59,0xc5,0x3e,0xa7,0x1d,0xe4,0xb2,0x4f,0x78,0x96)
                val phases = (0 until 1600).filter { phase ->
                    bytes.indices.all { i ->
                        val pos = ((first%1600).toInt()+phase+i)%1600; val tick = pos%100; val frame = pos/100
                        var expected = if (tick == 0 && frame == 0) 7 else 0
                        if (tick in 2..3) for (lane in 0..2) { val bit = frame*6+lane*2+tick-2; expected = expected or (((sync[bit/8] ushr (bit%8)) and 1) shl lane) }
                        tick >= 4 || ((bytes[i].toInt() ushr ((selectedProbe-1)*3)) and 7) == expected
                    }
                }
                require(phases.size == 1) { "No unique NP2 SuperSync framing; no waveform plotted." }; return phases.single()
            }
            require(first == 0L) { "NP1 phase needs a session-origin snapshot." }
            fun word(at: Int,line: Int,word: Int): Int { var v=0; for (bit in 0..9) v=(v shl 1) or ((bytes[at+10*word+bit].toInt() ushr line) and 1); return v }
            for (at in 0 until 780) {
                val fixed = word(at,7,2); if (fixed !in listOf(0x11,0x22)) continue
                var previous = 0; var good = true
                for (f in 0..12) {
                    val p = at+f*60
                    if (word(p,0,0) != if (f == 0) 0x330 else 0xcf) { good=false; break }
                    if (word(p,7,2) != fixed) { good=false; break }
                    var last=bytes[p+10].toInt() and 1; var changes=0
                    for (t in 11 until 60) { val v=bytes[p+t].toInt() and 1; if (v!=last) { last=v; changes++ } }
                    if (changes>1 || (50 until 60).any { bytes[p+it].toInt() and 0xfe != 0 }) { good=false; break }
                    val counter=(word(p,7,3) shl 10) or word(p,7,4)
                    if (f>0 && counter != ((previous+1) and 0xfffff)) { good=false; break }; previous=counter
                }
                if (good) return at
            }
            error("No validated NP1 origin superframe; phase is never guessed.")
        }
        fun decode(phase: Int): List<NpPreviewPoint> {
            val bytes=requireNotNull(data); require(kind==2 && received==bytes.size)
            val offset: Int; val line: Int; val bits: Int
            if (version==2) {
                val c=channel%128; offset=(1600-phase+(c%32)/2*100+4+((c/32)*2+(c and 1))*12)%1600
                line=(probe-1)*3+channel/128; bits=12
            } else {
                val adc=(channel/24)*2+(channel and 1); val sample=(channel%24)/2; val word=if (adc<30) adc%5 else adc-30
                val period=if (band==0) 780 else 9360
                offset=(phase+(if (band==0) (sample+1)*60 else sample*780)+word*10)%period
                line=if (adc<30) adc/5+1 else 7; bits=10
            }
            require(first%(if (version==2) 1600 else if (band==0) 780 else 9360) == offset.toLong()) { "Preview timestamp/phase mismatch." }
            return (0 until 16).map { row ->
                var value=0
                for (bit in 0 until bits) value=(value shl 1) or ((bytes[row*bits+bit].toInt() ushr line) and 1)
                NpPreviewPoint(first+row*stride,if (valid and (1L shl row)==0L) Double.NaN else value.toDouble())
            }
        }
    }
}
