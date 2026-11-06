package com.wild.android.ble

import kotlin.test.*
import kotlinx.coroutines.*

class NpMultiPreviewTest {
    private val selections=listOf(NpPreviewSelection(1,0),NpPreviewSelection(2,383))
    private val phases=listOf(0,137)
    private fun put(b:ByteArray,at:Int,v:Long)=NpBleProtocol.put(b,at,v)
    private fun word(b:ByteArray,at:Int,v:Int) {b[at]=v.toByte();b[at+1]=(v ushr 8).toByte()}
    private fun frame(sequence:Long,first:Long=(sequence-1)*40*48000,lanes:Int=2):ByteArray {
        val b=ByteArray(224)
        listOf(0x3256504eL,9,123,15000,first and 0xffffffffL,first ushr 32,(160L shl 16) or 40,(6L shl 8) or (lanes.toLong() shl 16),sequence,500).forEachIndexed { i,v ->put(b,i*4,v) }
        repeat(lanes) {i->word(b,40+i*2,NpMultiPreview.descriptor(selections[i]));word(b,48+i*2,NpMultiPreview.offset(selections[i],phases[i]))}
        word(b,52,1);word(b,54,418)
        repeat(40) {row -> repeat(lanes) {lane->word(b,60+(row*2+lane)*2,if(lane==0)-160+row*8 else 80+row*8)} }
        return NpBleProtocol.seal(b)
    }
    @Test fun np2RequestsMatchPcContractAndRefuseDuplicateSelections() {
        val q=NpMultiPreview.configure(9,123,selections,phases)
        assertTrue(NpBleProtocol.checked(q));assertEquals(6L,NpBleProtocol.u32(q,8));assertEquals(500L,NpBleProtocol.u32(q,16));assertEquals(2L,NpBleProtocol.u32(q,20))
        assertEquals(0,NpMultiPreview.descriptor(selections[0]));assertEquals(895,NpMultiPreview.descriptor(selections[1]))
        val stop=NpMultiPreview.feedback(9,123,2,7,true)
        assertEquals(7L,NpBleProtocol.u32(stop,8));assertEquals(0L,NpBleProtocol.u32(stop,24));assertEquals(1L,NpBleProtocol.u32(stop,28))
        assertFails {NpMultiPreview.configure(9,123,listOf(selections[0],selections[0]),phases)}
        assertFails {NpMultiPreview.configure(9,123,listOf(NpPreviewSelection(3,0)),listOf(0))}
    }
    @Test fun typedUnsupportedIsTheOnlyLegacyFallback() {
        val b=ByteArray(224);put(b,0,0x3256504e);put(b,4,9);put(b,28,129L shl 8)
        assertFalse(NpMultiPreview.supported(b,9))
        put(b,28,8L shl 8);assertFails {NpMultiPreview.supported(b,9)}
        put(b,8,123);put(b,12,15000);listOf(1,2,500,40,12,7).forEachIndexed { i,v->put(b,32+i*4,v.toLong()) };NpBleProtocol.seal(b)
        assertTrue(NpMultiPreview.supported(b,9));b[63]=1;assertFails {NpMultiPreview.supported(b,9)}
    }
    @Test fun reorderedPacketsOnlyPublishCheckedOriginalsAndDivideQ3Once() {
        val r=NpMultiPreview.Receiver(9,123,15000,selections,phases)
        assertTrue(r.accept(frame(2)).isEmpty());assertEquals(1,r.missing);assertEquals(0,r.acknowledged)
        val ready=r.accept(frame(1));assertEquals(listOf(1L,2L),ready.map {it.sequence});assertEquals(2,r.acknowledged);assertEquals(0,r.missing)
        assertEquals(-20.0,ready[0].traces[0][0].value);assertEquals(10.0,ready[0].traces[1][0].value)
        assertEquals(NpMultiPreview.offset(selections[1],137).toLong(),ready[0].traces[1][0].tick)
        assertTrue(r.accept(frame(1)).isEmpty())
        val bad=frame(3);bad[60]=12;assertTrue(r.accept(bad).isEmpty());assertEquals(3,r.missing)
        assertEquals(3,r.accept(frame(3)).single().sequence);assertEquals(0,r.missing)
    }
    @Test fun receiverRejectsChangedReplayMetadataSessionAndDiscontinuousTime() {
        val r=NpMultiPreview.Receiver(9,123,15000,selections,phases)
        r.accept(frame(2));val changed=frame(2);changed[60]=33;NpBleProtocol.seal(changed);assertFails {r.accept(changed)}
        val session=frame(1);put(session,8,124);NpBleProtocol.seal(session);assertFails {r.accept(session)}
        val alignment=frame(1);word(alignment,48,5);NpBleProtocol.seal(alignment);assertFails {r.accept(alignment)}
        val ordered=NpMultiPreview.Receiver(9,123,15000,selections,phases);ordered.accept(frame(1));assertFails {ordered.accept(frame(2,123))}
        assertFails {NpMultiPreview.Receiver(9,123,15000,selections,phases).accept(frame(65))}
        val stopped=frame(1);put(stopped,56,6);NpBleProtocol.seal(stopped);assertFails {r.accept(stopped)}
    }
    @Test fun inactiveLanesCannotHideData() {
        val r=NpMultiPreview.Receiver(9,123,15000,selections.take(1),phases.take(1));val b=frame(1,lanes=1)
        word(b,62,1);NpBleProtocol.seal(b);assertFails {r.accept(b)}
    }
    private fun compact():ByteArray {
        val b=ByteArray(224)
        listOf(0x3156504eL,9,123,15000,60,0,(128L shl 16) or 16,1L or (5L shl 8)).forEachIndexed {i,v->put(b,i*4,v)}
        for(lane in 0..3) {word(b,160+lane*2,lane);word(b,168+lane*2,if(lane==1)65527 else 65535);put(b,176+lane*4,listOf(0L,10L,60L,70L)[lane]);repeat(16) {row->word(b,32+lane*32+row*2,500+lane+row)} }
        put(b,192,23400);put(b,200,4);put(b,204,1);return NpBleProtocol.seal(b)
    }
    @Test fun np1FourChannelsKeepIndependentMasksOffsetsAndPolarity() {
        val b=compact();val f=NpMultiPreview.compact(b,9,123,15000,1,listOf(0,1,2,3),0,0)!!
        assertEquals(4,f.traces.size);assertEquals(12.0,f.traces[0][0].value);assertTrue(f.traces[1][3].value.isNaN());assertEquals(120L,f.traces[2][0].tick)
        val q=NpMultiPreview.compactRequest(9,5,1,listOf(0,1,2,3),0,123,0)
        assertEquals(4L,NpBleProtocol.u32(q,36));assertEquals(3L,NpBleProtocol.u32(q,48));assertTrue(NpBleProtocol.checked(q))
        b[32]=0;b[33]=4;NpBleProtocol.seal(b);assertFails {NpMultiPreview.compact(b,9,123,15000,1,listOf(0,1,2,3),0,0)}
        assertFails {NpMultiPreview.compactRequest(9,5,1,listOf(1,1),0,123,0)}
    }
    private fun wire(body:ByteArray,cmd:Int)=byteArrayOf(0xad.toByte(),cmd.toByte())+body+byteArrayOf(0xda.toByte(),0xda.toByte())
    private fun origin():ByteArray {
        val raw=ByteArray(3200);val sync=intArrayOf(0xd3,0x6a,0x59,0xc5,0x3e,0xa7,0x1d,0xe4,0xb2,0x4f,0x78,0x96)
        for(i in raw.indices)for(p in 1..2) {
            val pos=(i+if(p==1)0 else 137)%1600;val tick=pos%100;val f=pos/100;var bits=if(tick==0 && f==0)7 else 0
            if(tick in 2..3)for(lane in 0..2) {val bit=f*6+lane*2+tick-2;bits=bits or (((sync[bit/8] ushr (bit%8)) and 1) shl lane)}
            raw[i]=(raw[i].toInt() or (bits shl ((p-1)*3))).toByte()
        }
        return raw
    }
    private inner class Radio {
        var ui=NpDeviceUiState();var startCommands=0;var stopCommands=0;val previewKinds=mutableListOf<Long>();var configuredId=0L
        val controller=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={ bytes->receive(bytes) },publish={ui=it})
        fun preview(b:ByteArray) = controller.parser.push(wire(b,0xba))
        private fun receive(bytes:ByteArray):Boolean {
            val cmd=bytes[1].toInt() and 255;val q=if(cmd==0x82)null else bytes.copyOfRange(2,66);val id=q?.let {NpBleProtocol.u32(it,12)}?:1
            when(q?.let {NpBleProtocol.u32(it,0)}) {
                0x3256504eL -> {
                    val kind=NpBleProtocol.u32(q,8);previewKinds.add(kind)
                    when(kind) {
                        8L -> {val b=ByteArray(224);put(b,0,0x3256504e);put(b,4,id);put(b,8,123);put(b,12,15000);put(b,28,8L shl 8);listOf(1,2,500,40,12,7).forEachIndexed {i,v->put(b,32+i*4,v.toLong())};preview(NpBleProtocol.seal(b))}
                        0L -> {val raw=origin();for(at in raw.indices step 192) {
                            val b=ByteArray(224);listOf(0x3256504eL,id,123,15000,0,0,(3200L shl 16) or at.toLong(),1L or (64L shl 8)).forEachIndexed {i,v->put(b,i*4,v)}
                            raw.copyInto(b,32,at,minOf(at+192,raw.size));preview(b)
                        }}
                        6L -> {configuredId=id;val b=frame(1);put(b,4,id);preview(NpBleProtocol.seal(b))}
                    }
                }
                0x4343504eL -> {val r=ByteArray(64);put(r,0,0x5243504e);put(r,4,1);put(r,8,2);put(r,12,id);controller.parser.push(wire(NpBleProtocol.seal(r),0xb9))}
                else -> {
                    val op=q?.let {NpBleProtocol.u32(it,8)}?:0
                    if(op==2L)startCommands++;if(op==3L)stopCommands++
                    val r=ByteArray(64);listOf(0x32524c4eL,2,op,id,0,3,0,3,15000,100,8192,0,500000000,17L or 128L,7).forEachIndexed {i,v->put(r,i*4,v)}
                    controller.parser.push(wire(NpBleProtocol.seal(r),0xb9))
                }
            };return true
        }
    }
    @Test fun twoIndependentDeviceSessionsShowTwoTracesAndPauseDoesNotTouchRecording()=runBlocking {
        val a=Radio();val b=Radio()
        try {
            coroutineScope { listOf(a,b).map { radio->async {radio.controller.execute(NpCommand(NpAction.StartPreview,previewChannels=selections))} }.awaitAll() }
            for(radio in listOf(a,b)) {
                assertEquals("",radio.ui.failure);assertTrue(radio.ui.preview.enabled);assertEquals(2,radio.ui.preview.traces.size)
                assertEquals(-20.0,radio.ui.preview.traces[0].points[0].value);assertEquals(40,radio.ui.preview.points.size);assertTrue(radio.ui.preview.filtered)
            }
            a.controller.execute(NpCommand(NpAction.StopPreview));assertFalse(a.ui.preview.enabled);assertTrue(b.ui.preview.enabled)
            assertEquals(7L,a.previewKinds.last());assertEquals(3,a.ui.status!!.state)
            b.controller.execute(NpCommand(NpAction.StopPreview))
            assertEquals(0,a.startCommands+b.startCommands+a.stopCommands+b.stopCommands)
        }finally {a.controller.close();b.controller.close()}
    }
}
