package com.wild.android.ble

import kotlinx.coroutines.*
import java.io.IOException
import kotlin.test.*

class NpAdvancedBleTest {
    private fun put(b:ByteArray,at:Int,v:Long)=NpBleProtocol.put(b,at,v)
    private fun wire(b:ByteArray,cmd:Int=0xB9)=byteArrayOf(0xad.toByte(),cmd.toByte())+b+byteArrayOf(0xda.toByte(),0xda.toByte())
    private fun state(version:Int=2,mask:Int=7,hz:Int=20000,frontier:Long=8192,flags:Long=17L or 128L or 512L or 8192L or 16384L or 1048576L)=NpDeviceStatus(version,0,9,0,if(version==1)0 else 1,0,mask,hz,0,frontier,0,1000000,flags,7)
    private fun status(q:ByteArray?,s:NpDeviceStatus):ByteArray {
        val r=ByteArray(64)
        listOf(if(s.version==1)0x31524c4eL else 0x32524c4eL,s.version.toLong(),q?.let{NpBleProtocol.u32(it,8)}?:0,q?.let{NpBleProtocol.u32(it,12)}?:1,0,s.state.toLong(),0,s.probeMask.toLong(),s.sampleRate.toLong(),0,s.startSector,0,s.capacitySectors,s.flags,s.lastMutationId).forEachIndexed{i,v->put(r,i*4,v)}
        return NpBleProtocol.seal(r)
    }
    @Test fun lostProbeCommitIsRecoveredByReadOnlyReceiptWithoutReplayingUpload()=runBlocking {
        for(v in 1..2) {
            val payload=ByteArray(if(v==1)1576 else 876);(if(v==1)"NPB1" else "N2B1").toByteArray().copyInto(payload);payload[4]=(if(v==1)2 else 4).toByte();payload[8]=99
            val map=NpProbeMap(v,1,payload[4].toInt(),"fixture","hash",payload)
            var received=0L;var staged=false;var commits=0;var begins=0;var ui=NpDeviceUiState();lateinit var session:NpBleSession
            session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={bytes->
                val cmd=bytes[1].toInt() and 255;val q=if(cmd==0x82)null else bytes.copyOfRange(2,66)
                if(cmd==0xB8) {
                    val op=q!![5].toInt();val r=ByteArray(64);"NPR$v".toByteArray().copyInto(r);r[4]=v.toByte();r[5]=op.toByte();put(r,8,NpBleProtocol.u32(q,8));put(r,16,payload.size.toLong())
                    if(op==5) {put(r,24,7);put(r,28,4)} else {
                        if(op==1){begins++;received=0};if(op==2)received=NpBleProtocol.u32(q,12)+NpBleProtocol.u32(q,16)
                        if(op==3){commits++;staged=true;received=0}
                        r[7]=(if(staged)2 else if(received>0)1 else 0).toByte();put(r,12,received);r[20]=(if(staged)2 else 0).toByte()
                        if(v==2)r[23]=4
                        if(staged){put(r,24,map.crc);payload.copyInto(r,28,8,40)}
                    }
                    if(op==3)false else {session.parser.push(wire(NpBleProtocol.seal(r),0xB8));true}
                } else {session.parser.push(wire(status(q,state(v))));true}
            },publish={ui=it})
            try {
                session.execute(NpCommand(NpAction.StageProbeMap,probeMap=map));assertEquals(1,commits);assertTrue(ui.failure.contains("not fully sent"))
                session.execute(NpCommand(NpAction.ReadProbeReceipts));assertTrue(ui.probeReceipts.single().matches(map))
                session.execute(NpCommand(NpAction.StageProbeMap,probeMap=map));assertEquals("",ui.failure);assertEquals(1,commits);assertEquals(1,begins)
            }finally{session.close()}
        }
    }
    @Test fun closedLoopWireAndQualificationGatesMatchTheConsole() {
        val caps=NpClosedLoopStatus(0,1,0,0,20000,2,7,64,511,0)
        val settings=NpClosedLoopSettings(listOf(NpDetector(2,0),NpDetector(2,1,7f,false)))
        NpClosedLoop.validate(state(),caps,settings)
        val q=NpClosedLoop.request(1,33,20000,settings)
        assertEquals(2L,NpBleProtocol.u32(q,32));assertEquals(1L,NpBleProtocol.u32(q,52));assertEquals(0x00000602L,NpBleProtocol.u32(q,36));assertEquals(7f.toRawBits().toLong(),NpBleProtocol.u32(q,48))
        assertFails {NpClosedLoop.validate(state(mask=3),caps,settings)}
        assertFails {NpClosedLoop.validate(state(),caps,settings.copy(detectors=listOf(NpDetector(1,0),NpDetector(2,1))))}
        assertFails {NpClosedLoop.validate(state(),caps.copy(filterMask=0),settings)}
        assertFails {NpClosedLoop.request(1,33,20000,settings.copy(detectors=listOf(NpDetector(gain=Float.NaN))))}
        NpClosedLoop.validate(state(1,mask=3,hz=15000),caps,NpClosedLoopSettings(listOf(NpDetector(1,100))))
    }
    @Test fun uncertainClosedLoopSetupBlocksStartUntilReadbackAndNeverReplaysConfigure()=runBlocking {
        var crc=0L;var configured=false;var configs=0;var starts=0;var ui=NpDeviceUiState();lateinit var session:NpBleSession
        session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={bytes->
            val cmd=bytes[1].toInt() and 255;val q=if(cmd==0x82)null else bytes.copyOfRange(2,66)
            if(q!=null && NpBleProtocol.u32(q,0)==0x4343504eL) {
                val op=NpBleProtocol.u32(q,8)
                if(op==1L){configs++;configured=true;crc=NpBleProtocol.u32(q,60);false}else{
                    val r=ByteArray(64);listOf(0x5243504eL,1,op,NpBleProtocol.u32(q,12),0,if(configured && op==2L)3L else 1L,crc,0,20000,0,2,7,64,511,0).forEachIndexed{i,v->put(r,i*4,v)};session.parser.push(wire(NpBleProtocol.seal(r)));true
                }
            }else {if(q?.let{NpBleProtocol.u32(it,8)}==2L)starts++;session.parser.push(wire(status(q,state())));true}
        },publish={ui=it})
        try {
            session.execute(NpCommand(NpAction.ConfigureClosedLoop,closedLoopSettings=NpClosedLoopSettings(listOf(NpDetector(2,0)))))
            assertTrue(ui.closedLoopUncertain);assertEquals(1,configs)
            session.start(10);assertEquals(0,starts);assertTrue(ui.failure.contains("uncertain"))
            session.execute(NpCommand(NpAction.ReadClosedLoop));assertFalse(ui.closedLoopUncertain);assertEquals(1,configs)
            assertTrue(ui.closedLoop!!.configured);assertFalse(ui.closedLoopCapabilities!!.configured)
        }finally{session.close()}
    }
    @Test fun transactionalDeleteFramesSurviveFragmentationAndTruncatedPreview() {
        val b=ByteArray(128);put(b,0,0x3155444e);put(b,4,1);put(b,8,2);put(b,16,4);NpBleProtocol.seal(b)
        assertEquals(131,NpBleProtocol.frame(0xA7,b).size)
        for(split in 0..132) {
            val got=mutableListOf<ByteArray>();val p=NpBleFrameParser{cmd,body->if(cmd==0xA7)got.add(body)};val frame=wire(b,0xA7)
            p.push(frame.copyOfRange(0,split));p.push(frame.copyOfRange(split,frame.size));assertContentEquals(b,got.single())
        }
        val got=mutableListOf<Int>();val p=NpBleFrameParser{cmd,_->got.add(cmd)}
        p.push(byteArrayOf(0xad.toByte(),0xba.toByte())+ByteArray(40));p.push(wire(b,0xA7));assertEquals(listOf(0xA7),got)
    }
    private inner class Card(val version:Int=2,val fs2:Boolean=false) {
        var records=mutableListOf(NpCatalogRecord(1,8192,16384),NpCatalogRecord(2,16384,24576));var cid=88L;var deletes=0;var lost=false
        var receipt=ByteArray(128)
        fun s()=state(version,frontier=records.lastOrNull()?.end?:8192,flags=17L or 128L or if(version==2)0x400000L else if(fs2)0x14000000L else 0L)
        fun ndu(op:Long,nonce:Long,inspected:Long=0):ByteArray {
            val r=ByteArray(128);put(r,0,0x3155444e);put(r,4,1);put(r,8,op);put(r,12,if(op==0L)0 else 3);put(r,16,nonce);put(r,24,1);put(r,28,if(records.isEmpty())1 else 3);put(r,32,s().capacitySectors);put(r,36,records.size.toLong());put(r,40,s().startSector)
            records.lastOrNull()?.let {put(r,44,it.id-1);put(r,48,it.start);put(r,52,it.end)}
            for(at in 64..88 step 4) put(r,at,(at+1).toLong());put(r,96,inspected)
            return NpBleProtocol.seal(r)
        }
        val client=NpBleCatalog({s()},{++sequence},{command,q,matches->
            if(command==0xA7) {
                if(receipt.all{it==0.toByte()})receipt=ndu(0,0)
                if(q.size==8 && NpBleProtocol.u32(q,0)==1L)receipt=ndu(1,NpBleProtocol.u32(q,4))
                if(q.size==128){deletes++;records.removeAt(records.lastIndex);receipt=ndu(2,NpBleProtocol.u32(q,16),NpBleProtocol.u32(q,96))}
                assertTrue(matches(receipt));receipt.copyOf()
            }else {
                val op=NpBleProtocol.u32(q,8);val r=ByteArray(64);put(r,0,0x32524c4e);put(r,4,2);put(r,8,op);put(r,12,NpBleProtocol.u32(q,12))
                if(op==9L){val last=records.lastOrNull();listOf(100L+records.size,records.size.toLong(),s().startSector,s().capacitySectors,cid,0,last?.id?.minus(1)?:0xffffffffL,last?.start?:0,last?.end?:0,if(last==null)0 else 99L+records.size).forEachIndexed{i,v->put(r,20+i*4,v)}}
                else {val ordinal=NpBleProtocol.u32(q,24).toInt();val entry=records[ordinal];val last=ordinal==records.lastIndex;listOf(100L+records.size,records.size.toLong(),ordinal.toLong(),entry.start,entry.end,100L+ordinal,10,15000,3,12L or (3L shl 8) or 0x40000L or if(last)0x30000L else 0L).forEachIndexed{i,v->put(r,20+i*4,v)}}
                NpBleProtocol.seal(r);assertTrue(matches(r));r
            }
        },{page->
            assertEquals(0,page);val r=ByteArray(512)
            if(fs2){put(r,0,0x47503253);put(r,8,1);put(r,16,1);put(r,24,records.size.toLong());put(r,28,records.size.toLong());put(r,32,s().startSector);records.forEachIndexed{i,e->put(r,40+i*8,e.start);put(r,44+i*8,e.end)};NpBleProtocol.seal(r)}
            else {put(r,0,records.size.toLong());records.forEachIndexed{i,e->put(r,4+i*4,e.end);put(r,256+i*4,e.end)};r}
        },{deletes++;records.removeAt(records.lastIndex);records.size.toLong()},{q->deletes++;records.removeAt(records.lastIndex);if(lost)throw IOException("delete ACK lost");s().copy(lastMutationId=NpBleProtocol.u32(q,12))})
        var sequence=100L
    }
    @Test fun listsAndDeletesLegacyV1Filesystem2V1AndV2WithEarlierEntriesIntact()=runBlocking {
        for(card in listOf(Card(1),Card(1,true),Card(2))) {
            val first=card.client.refresh();assertEquals(2,first.records.size);assertTrue(first.canDelete)
            val result=card.client.deleteLast(first.fingerprint);assertEquals(1,result.records.size);assertEquals(first.records.first(),result.records.first().copy(flags=first.records.first().flags));assertEquals(1,card.deletes)
        }
    }
    @Test fun changedCatalogOrCidRejectsDeleteBeforeMutation()=runBlocking {
        for(v in 1..2) {
            val card=Card(v);val first=card.client.refresh();if(v==1)card.records.removeAt(card.records.lastIndex) else card.cid++
            assertFails {card.client.deleteLast(first.fingerprint)};assertEquals(0,card.deletes)
        }
    }
    @Test fun lostDeleteAckInvalidatesConsentAndIsNeverReplayed()=runBlocking {
        val card=Card();val first=card.client.refresh();card.lost=true
        assertFails {card.client.deleteLast(first.fingerprint)};assertFails {card.client.deleteLast(first.fingerprint)};assertEquals(1,card.deletes)
    }
    @Test fun legacyDeleteRequiresBothReceiptsInEitherOrderWithoutASecondMutation()=runBlocking {
        for(parametersFirst in listOf(false,true)) {
            var count=2;var deletes=0;var ui=NpDeviceUiState();lateinit var session:NpBleSession
            session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={bytes->
                when(bytes[1].toInt() and 255) {
                    0x94->{val page=ByteArray(512);put(page,0,count.toLong());for(i in 0 until count){put(page,4+i*4,16384L+i*8192);put(page,256+i*4,16384L+i*8192)};session.parser.push(wire(page,0x94))}
                    0x95->{deletes++;count--;val result=ByteArray(4);put(result,0,count.toLong());val parameters=wire(ByteArray(512),0x90);val deleted=wire(result,0x95);session.parser.push(if(parametersFirst)parameters+deleted else deleted+parameters)}
                    else->{val q=if(bytes[1]==0x82.toByte())null else bytes.copyOfRange(2,66);session.parser.push(wire(status(q,state(1,frontier=8192L+count*8192,flags=17))))}
                };true
            },publish={ui=it})
            try {
                session.execute(NpCommand(NpAction.ReadCatalog));val catalog=assertNotNull(ui.catalog);assertTrue(catalog.canDelete)
                session.execute(NpCommand(NpAction.DeleteLast,catalogFingerprint=catalog.fingerprint));assertEquals("",ui.failure);assertEquals(1,ui.catalog!!.records.size);assertEquals(1,deletes)
            }finally{session.close()}
        }
    }
}
