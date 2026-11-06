package com.wild.android.ble

import kotlin.math.*
import kotlin.test.*

class NpPreviewAnalysisTest {
    private fun csv(version:Int=2,factor:Double=1.25,rows:Int=384)= ("123456\n"+(0 until rows).joinToString("\n") { ch -> "$ch,"+List(if(version==1)16 else 1) {factor}.joinToString(",") }).toByteArray()
    @Test fun factoryCorrectionChecksIdentityGainsAndPolarityWithoutDoubleCentering() {
        val np2=NpPreviewCalibration.parse(csv(),null,2,"123456",0,0)
        assertEquals(12.5,np2.correct(10.0,0,0,false));assertEquals(-12.5,np2.correct(-10.0,383,0,true));assertTrue(np2.correct(Double.NaN,0,0,false).isNaN())
        assertFails {NpPreviewCalibration.parse(csv(),null,2,"999",0,0)}
        assertFails {NpPreviewCalibration.parse(csv(),null,1,"123456",500,50)}
        assertFails {NpPreviewCalibration.parse(csv(1),null,1,"123456",100,50)}
        assertFails {NpPreviewCalibration.parse(csv(factor=Double.NaN),null,2,"123456",0,0)}
        val np1=NpPreviewCalibration.parse(csv(1),null,1,"123456",500,50)
        assertEquals(12.5,np1.correct(10.0,0,1,false));assertFails {np1.correct(10.0,0,0,true)}
        assertFails {np2.correct(5000.0,0,0,false)}
    }
    @Test fun np1AdcCorrectionIsThresholdedBeforeGainAndRejectsUnmappedElectrodeTables() {
        val adc=("123456\n"+(0..31).joinToString("\n") { "$it,0,0,0,0,0,0,20,500" }).toByteArray()
        val cal=NpPreviewCalibration.parse(csv(1),adc,1,"123456",500,50)
        assertEquals(37.5,cal.correct(10.0,0,0,false)) // raw502 >500: (10+20)*1.25
        assertEquals(25.0,cal.correct(20.0,0,0,false)) // raw492 <=500
        assertFails {NpPreviewCalibration.parse(csv(),adc,2,"123456",0,0)}
    }
    @Test fun nonuniform960SiteGainsCannotBeAssignedToChannelsByGuessing() {
        val bytes=csv(1,1.25,960).toString(Charsets.UTF_8).replace("0,1.25", "0,1.5").toByteArray()
        assertFails {NpPreviewCalibration.parse(bytes,null,1,"123456",50,50)}
        assertEquals(384,NpPreviewCalibration.parse(csv(1,1.25,960),null,1,"123456",50,50).apFactors.size)
    }
    private fun tone(n:Int=512,hz:Double=500.0)=List(n) { i -> NpPreviewPoint(i.toLong(),10+3*sin(2*PI*64*i/512)) }
    @Test fun spectrumMeasuresFrequencyAndPeakAmplitudeAtPreviewRate() {
        val bins=NpPreviewAnalysis.spectrum(tone(),500.0,1);val peak=bins.drop(1).maxBy {it.amplitude}
        assertEquals(62.5,peak.hz,1e-9);assertEquals(3.0,peak.amplitude,0.01)
        val gap=tone().toMutableList();gap[480]=NpPreviewPoint(480,Double.NaN)
        assertTrue(NpPreviewAnalysis.spectrum(gap,500.0,1).isEmpty())
        val discontinuous=tone().mapIndexed {i,p->if(i>480)p.copy(tick=p.tick+1)else p}
        assertTrue(NpPreviewAnalysis.spectrum(discontinuous,500.0,1).isEmpty())
    }
    @Test fun highPassRejectsDcAndResetsAtNaNsAndTimestampGaps() {
        val input=List(100) {NpPreviewPoint(it.toLong(),10.0)}
        assertTrue(NpPreviewAnalysis.highPass(input,500.0,1,5.0).all {it.value==0.0})
        val changed=input+listOf(NpPreviewPoint(100,Double.NaN),NpPreviewPoint(101,500.0),NpPreviewPoint(105,20.0))
        val out=NpPreviewAnalysis.highPass(changed,500.0,1,5.0)
        assertTrue(out[100].value.isNaN());assertEquals(0.0,out[101].value);assertEquals(0.0,out[102].value)
        assertEquals(input,NpPreviewAnalysis.highPass(input,500.0,1,0.0))
        assertFails {NpPreviewAnalysis.highPass(input,500.0,1,250.0)}
    }
    @Test fun directionalPinchHasIndependentAxesAndBoundedRanges() {
        val start=NpPreviewAnalysis.View()
        val horizontal=NpPreviewAnalysis.pinch(start,200f,30f,100f,30f)
        assertEquals(1f,horizontal.seconds);assertEquals(1f,horizontal.gain)
        val vertical=NpPreviewAnalysis.pinch(start,30f,200f,30f,100f)
        assertEquals(2f,vertical.seconds);assertEquals(2f,vertical.gain)
        assertEquals(start,NpPreviewAnalysis.pinch(start,0f,0f,0f,0f))
        assertEquals(100f,NpPreviewAnalysis.pinch(start,30f,100000f,30f,24f).gain)
    }
}
