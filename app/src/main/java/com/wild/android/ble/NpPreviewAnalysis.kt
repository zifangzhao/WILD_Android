package com.wild.android.ble

import java.nio.ByteBuffer
import java.security.MessageDigest
import kotlin.math.*

/** Host-only factory correction. Operator serial/gains are evidence, not a probe identity readback. */
data class NpPreviewCalibration(
    val version: Int, val serial: String, val apGain: Int, val lfpGain: Int,
    val apFactors: List<Double>, val lfpFactors: List<Double>,
    val thresholds: List<Int> = emptyList(), val offsets: List<Int> = emptyList(),
    val gainSha256: String, val adcSha256: String = "",
) {
    fun validate() {
        require(version in 1..2 && serial.toULongOrNull()?.let { it>0uL }==true) { "Enter the physical probe's numeric serial." }
        require(apFactors.size==384 && lfpFactors.size==384 && (apFactors+lfpFactors).all { it.isFinite() && it>0 && it<=32 }) { "Invalid gain correction table." }
        require((thresholds.isEmpty() && offsets.isEmpty()) || version==1 && thresholds.size==32 && offsets.size==32 && (thresholds+offsets).all { it in 0..1023 })
        require(gainSha256.matches(Regex("[a-f0-9]{64}")) && (adcSha256.isEmpty() || adcSha256.matches(Regex("[a-f0-9]{64}"))))
        require(version==2 || apGain in gains && lfpGain in gains) { "Select the actual acquisition gains." }
    }
    fun correct(nominal: Double, channel: Int, band: Int, filtered: Boolean): Double {
        require(channel in 0..383 && band in 0..(if(version==1)1 else 0))
        if(!nominal.isFinite()) return Double.NaN
        val factor=(if(band==0)apFactors else lfpFactors)[channel]
        // NP2 kind6 has already been filtered: gain commutes with the FIR;
        // nonlinear raw-ADC threshold correction does not. NP2 has no NP1 ADC trims.
        if(filtered) { require(version==2 && thresholds.isEmpty()); return nominal*factor }
        val midpoint=if(version==1)512 else 2048; val raw=midpoint-nominal
        require(raw in 0.0..(midpoint*2-1).toDouble() && raw==floor(raw)) { "Calibration requires uncorrected ADC counts." }
        val adc=channel/24*2+(channel and 1)
        return (nominal+(if(thresholds.isNotEmpty() && raw>thresholds[adc])offsets[adc] else 0))*factor
    }
    companion object {
        val gains=listOf(50,125,250,500,1000,1500,2000,3000)
        const val maxBytes=2*1024*1024
        private fun lines(bytes: ByteArray): List<String> {
            require(bytes.isNotEmpty() && bytes.size<=maxBytes) { "Choose a factory CSV up to 2 MiB." }
            val text=Charsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF")
            return text.trimEnd('\r','\n').lineSequence().map { it.trimEnd('\r') }.toList().also { require(it.size>=2) { "Truncated calibration CSV." } }
        }
        private fun hash(bytes: ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        fun parse(gain: ByteArray, adc: ByteArray?, version: Int, serial: String, apGain: Int, lfpGain: Int): NpPreviewCalibration {
            val expected=serial.toULongOrNull()?.takeIf { it>0uL }?.toString() ?: error("Enter the physical probe's numeric serial.")
            val rows=lines(gain); require(rows[0].trim().toULongOrNull()?.toString()==expected) { "Factory file serial does not match the selected probe." }
            val columns=rows[1].split(',').size
            require(version in 1..2 && columns==(if(version==1)17 else 2)) { "Factory gain CSV belongs to another probe family." }
            require(rows.size==385 || version==1 && rows.size==961) { "Expected 384 channels (or 960 NP1 electrodes)." }
            require(version==2 || apGain in gains && lfpGain in gains) { "Select the actual AP and LFP gains." }
            val factors=rows.drop(1).mapIndexed { i,line ->
                val fields=line.split(','); require(fields.size==columns && fields[0].trim().toIntOrNull()==i) { "CSV indices must be ordered, complete and unique." }
                fields.drop(1).map { value -> value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it>0 && it<=32 } ?: error("Gain corrections must be finite and in (0, 32].") }
            }
            fun band(column: Int): List<Double> {
                if(factors.size==384) return factors.map { it[column] }
                val nonReference=factors.map { it[column] }.filter { it!=1.0 }.distinct()
                require(nonReference.size<=1) { "Nonuniform 960-electrode gains need confirmed channel-to-site routing; not guessed." }
                return List(384) { nonReference.firstOrNull()?:1.0 }
            }
            var thresholds=emptyList<Int>(); var offsets=emptyList<Int>()
            if(adc!=null) {
                require(version==1) { "NP1 ADC files cannot be applied to NP2." }
                val entries=lines(adc); require(entries.size==33 && entries[0].trim().toULongOrNull()?.toString()==expected) { "ADC CSV needs matching serial and 32 rows." }
                val maxima=listOf(31,31,7,3,3,15,1023,1023)
                val values=entries.drop(1).mapIndexed { i,line ->
                    val f=line.split(',').map { it.trim().toIntOrNull()?:error("Invalid ADC integer.") }
                    require(f.size==9 && f[0]==i && maxima.indices.all { f[it+1] in 0..maxima[it] }) { "Invalid ADC index/parameter range." }; f
                }
                offsets=values.map { it[7] }; thresholds=values.map { it[8] }
            }
            return NpPreviewCalibration(version,expected,if(version==1)apGain else 0,if(version==1)lfpGain else 0,
                band(if(version==1)gains.indexOf(apGain) else 0),band(if(version==1)gains.indexOf(lfpGain)+8 else 0),thresholds,offsets,hash(gain),adc?.let(::hash)?:"").also { it.validate() }
        }
    }
}

/** Display processing only; never modifies the retained samples or SD recording. */
internal object NpPreviewAnalysis {
    fun highPass(points: List<NpPreviewPoint>, hz: Double, stride: Long, cutoff: Double): List<NpPreviewPoint> {
        require(hz.isFinite() && hz>0 && stride>0 && cutoff.isFinite() && cutoff>=0 && cutoff<hz/2)
        if(cutoff==0.0) return points
        val alpha=1/(1+2*PI*cutoff/hz); var x=0.0; var y=0.0; var last: Long?=null
        return points.map { p ->
            if(!p.value.isFinite()) { last=null; p }
            else {
                if(last==null || p.tick-last!!!=stride) { x=p.value; y=0.0 } else { y=alpha*(y+p.value-x); x=p.value }
                last=p.tick; p.copy(value=y)
            }
        }
    }
    data class Bin(val hz: Double, val amplitude: Double)
    /** Hann-windowed one-sided peak amplitude. Never bridges a missing sample. */
    fun spectrum(points: List<NpPreviewPoint>, hz: Double, stride: Long): List<Bin> {
        require(hz.isFinite() && hz>0 && stride>0)
        val tail=mutableListOf<Double>(); var last: Long?=null
        points.takeLast(512).forEach { p ->
            if(!p.value.isFinite()) { tail.clear(); last=null }
            else { if(last!=null && p.tick-last!!!=stride) tail.clear(); tail.add(p.value); last=p.tick }
        }
        var n=1; while(n*2<=tail.size)n*=2
        if(n<64) return emptyList()
        val values=tail.takeLast(n); val mean=values.average(); val real=DoubleArray(n); val imaginary=DoubleArray(n); var windowSum=0.0
        for(i in 0 until n) { val w=0.5-0.5*cos(2*PI*i/(n-1)); windowSum+=w; real[i]=(values[i]-mean)*w }
        var j=0
        for(i in 1 until n) { var bit=n shr 1; while(j and bit!=0) {j=j xor bit;bit=bit shr 1}; j=j xor bit; if(i<j) {val t=real[i];real[i]=real[j];real[j]=t} }
        var size=2
        while(size<=n) {
            for(start in 0 until n step size) for(k in 0 until size/2) {
                val angle=-2*PI*k/size; val a=start+k; val b=a+size/2
                val r=real[b]*cos(angle)-imaginary[b]*sin(angle); val im=real[b]*sin(angle)+imaginary[b]*cos(angle)
                real[b]=real[a]-r; imaginary[b]=imaginary[a]-im; real[a]+=r; imaginary[a]+=im
            }; size*=2
        }
        return (0..n/2).map { k -> Bin(k*hz/n,hypot(real[k],imaginary[k])*(if(k==0 || k==n/2)1 else 2)/windowSum) }
    }
    data class View(val seconds: Float=2f, val gain: Float=1f, val offset: Float=0f)
    fun pinch(view: View, horizontal: Float, vertical: Float, previousHorizontal: Float, previousVertical: Float): View =
        if(horizontal>=vertical && previousHorizontal>=24 && horizontal>=24) view.copy(seconds=(view.seconds*previousHorizontal/horizontal).coerceIn(0.1f,10f))
        else if(vertical>horizontal && previousVertical>=24 && vertical>=24) view.copy(gain=(view.gain*vertical/previousVertical).coerceIn(0.1f,100f)) else view
}
