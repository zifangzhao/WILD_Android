package com.wild.android.ble

import java.io.IOException
import kotlinx.coroutines.*
import kotlin.test.*

class NpBleSignalsTest {
    private fun put(b:ByteArray,at:Int,v:Long)=NpBleProtocol.put(b,at,v)
    @Test fun bothClockVersionsCommitOnceAndVerifyTheirOwnSemantics()=runBlocking {
        for(version in 1..2) {
            var host=1800000000000000L; val base=host; var id=0L; var generation=1L; var uncertainty=0L
            var reference=0L; var anchor=0L; var commits=0; var before=0; var observations=0
            val (result,error)=NpBleTelemetry.synchronize(version,2,{++id},{host},{q->
                val rx=(host-base)/1000; host+=10000; val tx=rx+1
                val op=NpBleProtocol.u32(q,8)
                if(op==1L){commits++;generation++;reference=NpBleProtocol.u32(q,16);anchor=NpBleProtocol.u32(q,20) or (NpBleProtocol.u32(q,24) shl 32);uncertainty=NpBleProtocol.u32(q,28)}else observations++
                val b=ByteArray(64)
                val utc=if(commits>0) anchor+((tx-reference) and 0xffffffffL)*1000 else 0
                val flags=if(version==2) if(commits>0) 23L else 17L else 0L
                listOf(0x3252504eL,version.toLong(),op,NpBleProtocol.u32(q,12),0,rx,tx,generation,uncertainty,if(commits>0) 1L else 0L,utc and 0xffffffffL,utc ushr 32,1,if(version==2) 100L else 1000L,flags).forEachIndexed{i,v->put(b,i*4,v)}
                NpBleProtocol.seal(b)
            },{before++})
            assertEquals(1,commits);assertEquals(1,before);assertEquals(if(version==2) 9 else 16,observations)
            assertTrue(result.calibrated);assertEquals(version,result.version);assertEquals(5500L,error)
        }
    }
    @Test fun lostClockCommitIsNotReplayed()=runBlocking {
        var host=1800000000000000L; val base=host;var id=0L;var commits=0
        assertFailsWith<IOException> {
            NpBleTelemetry.synchronize(1,2,{++id},{host},{q->
                if(NpBleProtocol.u32(q,8)==1L){commits++;throw IOException("lost commit receipt")}
                val rx=(host-base)/1000;host+=10000;val b=ByteArray(64)
                listOf(0x3252504eL,1,0,NpBleProtocol.u32(q,12),0,rx,rx+1,1,0,0,0,0,1,1000,0).forEachIndexed{i,v->put(b,i*4,v)}
                NpBleProtocol.seal(b)
            },{})
        }
        assertEquals(1,commits)
    }
    private fun previewFrame(version:Int,id:Long,kind:Int,probe:Int,channel:Int,band:Int,first:Long,range:Long,data:ByteArray,offset:Int=0):ByteArray {
        val b=ByteArray(224);val select=probe.toLong() or ((kind or if(version==2) 64 else 0).toLong() shl 8) or (band.toLong() shl 12) or (channel.toLong() shl 16)
        listOf(if(version==1) 0x3156504eL else 0x3256504eL,id,123,15000,first and 0xffffffffL,first ushr 32,range,select).forEachIndexed{i,v->put(b,i*4,v)}
        val n=minOf(if(version==1) 160 else 192,data.size-offset);data.copyInto(b,32,offset,offset+n)
        if(version==1) put(b,192,if(band==0) 23400 else 28080)
        return b
    }
    @Test fun np2SuperSyncAnd12BitAdcDecodeMatchNativeLaneOrderAndPreserveMissingSamples() {
        val phase=137;val raw=ByteArray(3200);val sync=intArrayOf(0xd3,0x6a,0x59,0xc5,0x3e,0xa7,0x1d,0xe4,0xb2,0x4f,0x78,0x96)
        for(i in raw.indices){val pos=(i+phase)%1600;val tick=pos%100;val frame=pos/100;var bits=if(tick==0&&frame==0)7 else 0
            if(tick in 2..3)for(lane in 0..2){val bit=frame*6+lane*2+tick-2;bits=bits or (((sync[bit/8] ushr (bit%8)) and 1) shl lane)};raw[i]=bits.toByte()}
        val origin=NpBlePreview.Burst(2,9)
        for(offset in raw.indices step 192) origin.accept(previewFrame(2,9,0,1,0,0,0,(3200L shl 16) or offset.toLong(),raw,offset))
        assertEquals(phase,origin.phase())
        val values=IntArray(16){if(it==0)0 else if(it==1)4095 else 2048+it};val data=ByteArray(192)
        for(row in 0 until 16)for(bit in 0 until 12)data[row*12+bit]=((values[row] ushr (11-bit)) and 1).toByte()
        val burst=NpBlePreview.Burst(2,10);val first=(1600-phase+4).toLong()
        assertTrue(burst.accept(previewFrame(2,10,2,1,0,0,first,(192L shl 16) or 65527,data)))
        val decoded=burst.decode(phase)
        assertEquals(0.0,decoded[0].value);assertEquals(4095.0,decoded[1].value);assertTrue(decoded[3].value.isNaN());assertEquals(first+15*48000,decoded[15].tick)
        assertFailsWith<IllegalArgumentException>{burst.decode(phase+1)}
    }
    @Test fun np1OriginAndAPAndLfpPreviewRetainNative10BitCountsAndTimestamps() {
        val raw=ByteArray(1560)
        fun word(at:Int,line:Int,word:Int,value:Int){for(bit in 0..9){val p=at+word*10+bit;raw[p]=(raw[p].toInt() or (((value ushr (9-bit)) and 1) shl line)).toByte()}}
        for(f in 0 until 26){word(f*60,0,0,if(f%13==0)0x330 else 0xcf);word(f*60,7,2,0x11);word(f*60,7,3,f ushr 10);word(f*60,7,4,f and 1023)}
        val origin=NpBlePreview.Burst(1,9)
        for(offset in raw.indices step 160)origin.accept(previewFrame(1,9,0,1,0,0,0,(1560L shl 16) or offset.toLong(),raw,offset))
        assertEquals(0,origin.phase())
        for(band in 0..1){
            val data=ByteArray(160);val values=IntArray(16){if(it==0)0 else if(it==1)1023 else 512+it}
            for(row in 0 until 16)for(bit in 0..9)data[row*10+bit]=(((values[row] ushr (9-bit)) and 1) shl 1).toByte()
            val first=if(band==0)60L else 0L;val burst=NpBlePreview.Burst(1,10)
            assertTrue(burst.accept(previewFrame(1,10,2,1,0,band,first,(160L shl 16) or 65527,data)))
            val points=burst.decode(0)
            assertEquals(0.0,points[0].value);assertEquals(1023.0,points[1].value);assertTrue(points[3].value.isNaN())
            assertEquals(first+15*(if(band==0)23400L else 28080L),points[15].tick)
        }
    }
    @Test fun previewRejectsMissingCalibrationFragmentsAndMixedSessions() {
        val data=ByteArray(3200);val burst=NpBlePreview.Burst(2,9)
        assertFalse(burst.accept(previewFrame(2,9,0,1,0,0,0,3200L shl 16,data)))
        assertFailsWith<IllegalArgumentException>{burst.accept(previewFrame(2,9,0,1,0,0,0,(3200L shl 16) or 384,data,384))}
        assertFailsWith<IllegalArgumentException>{NpBlePreview.request(2,9,2,3,0)}
        assertFailsWith<IllegalArgumentException>{NpBlePreview.request(2,9,2,1,0,band=1)}
    }
    @Test fun simulatedPreviewUsesBARepliesAndPauseNeverSendsRecorderStartOrStop()=runBlocking {
        val raw=ByteArray(3200);val sync=intArrayOf(0xd3,0x6a,0x59,0xc5,0x3e,0xa7,0x1d,0xe4,0xb2,0x4f,0x78,0x96)
        for(i in raw.indices){val tick=i%100;val frame=i%1600/100;var bits=if(tick==0&&frame==0)7 else 0
            if(tick in 2..3)for(lane in 0..2){val bit=frame*6+lane*2+tick-2;bits=bits or (((sync[bit/8] ushr (bit%8)) and 1) shl lane)};raw[i]=bits.toByte()}
        fun frame(body:ByteArray,cmd:Int)=byteArrayOf(0xad.toByte(),cmd.toByte())+body+byteArrayOf(0xda.toByte(),0xda.toByte())
        val controls=mutableListOf<Long>();val previewKinds=mutableListOf<Long>();var published=NpDeviceUiState();lateinit var session:NpBleSession
        session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={bytes->
            val cmd=bytes[1].toInt() and 255;val q=if(cmd==0x82) null else bytes.copyOfRange(2,66)
            val magic=q?.let{NpBleProtocol.u32(it,0)}?:0;val id=q?.let{NpBleProtocol.u32(it,12)}?:1
            when(magic){
                0x3256504eL->{val kind=NpBleProtocol.u32(q!!,8);previewKinds.add(kind)
                    if(kind==0L)for(offset in raw.indices step 192)session.parser.push(frame(previewFrame(2,id,0,1,0,0,0,(3200L shl 16) or offset.toLong(),raw,offset),0xba))
                    if(kind==2L)session.parser.push(frame(previewFrame(2,id,2,1,0,0,4,(192L shl 16) or 65535,ByteArray(192)),0xba))
                }
                0x4343504eL->{val r=ByteArray(64);put(r,0,0x5243504e);put(r,4,1);put(r,8,2);put(r,12,id);session.parser.push(frame(NpBleProtocol.seal(r),0xb9))}
                else->{val op=q?.let{NpBleProtocol.u32(it,8)}?:0;controls.add(op);val r=ByteArray(64)
                    listOf(0x32524c4eL,2,op,id,0,3,0,3,15000,100,8192,0,500000000,17L or 128L,7).forEachIndexed{i,v->put(r,i*4,v)}
                    session.parser.push(frame(NpBleProtocol.seal(r),0xb9))
                }
            };true
        },publish={published=it})
        try{
            session.execute(NpCommand(NpAction.StartPreview))
            assertEquals("",published.failure);assertTrue(published.preview.enabled);assertEquals(16,published.preview.points.size)
            session.execute(NpCommand(NpAction.StopPreview))
            assertFalse(published.preview.enabled);assertEquals(3,published.status!!.state)
            assertTrue(controls.all{it==0L});assertEquals(listOf(0L,2L,3L),previewKinds)
        }finally{session.close()}
    }
}
