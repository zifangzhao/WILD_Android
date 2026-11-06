package com.wild.android.ble

import kotlinx.coroutines.*
import kotlin.test.*

/** Fixtures transcribed from the WILD-X V1/V2 console contracts, no hardware. */
class NpBleModesTest {
    private fun put(b:ByteArray,at:Int,v:Long)=NpBleProtocol.put(b,at,v)
    private fun status(version:Int=2,flags:Long=17L or 128L or 256L or 512L or 1024L or 8192L or 16384L or 1048576L,state:Int=if(version==1) 0 else 1,mask:Int=3,hz:Int=15000,id:Long=9,op:Long=0,lastId:Long=7,fault:Long=0):ByteArray {
        val b=ByteArray(64)
        listOf(if(version==1) 0x31524c4eL else 0x32524c4eL,version.toLong(),op,id,0,state.toLong(),fault,mask.toLong(),hz.toLong(),100,8192,0,500000000,flags,lastId).forEachIndexed{i,v->put(b,i*4,v)}
        return NpBleProtocol.seal(b)
    }
    private fun wire(body:ByteArray,command:Int=0xB9)=byteArrayOf(0xAD.toByte(),command.toByte())+body+byteArrayOf(0xDA.toByte(),0xDA.toByte())
    private fun s(version:Int=2)=NpBleProtocol.status(status(version))!!

    @Test fun allModeDestinationsAreFamilySpecificAndRequireStoppedFaultFreeState() {
        for(version in 1..2) for(destination in 1..if(version==1) 4 else 3) {
            val q=NpBleProtocol.mode(s(version),10,destination)
            assertEquals(if(version==1) 7L else 5L,NpBleProtocol.u32(q,8)); assertEquals(destination.toLong(),NpBleProtocol.u32(q,16)); assertTrue(NpBleProtocol.checked(q))
        }
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.mode(s(2),10,4)}
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.mode(s(2).copy(state=3,flags=s().flags or 4),10,2)}
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.mode(s(1).copy(fault=4),10,2)}
        assertFalse(s(2).copy(state=255).canSwitchMode)
    }
    @Test fun scheduledRequestsCarryFixedUtcAtTheCorrectFamilyOffsets() {
        val now=1800000000000L; val utc=now+60000
        for(version in 1..2) {
            val q=NpBleProtocol.scheduled(s(version),10,10,true,utc,now)
            val offset=if(version==1) 44 else 28
            assertEquals(if(version==1) 6L else 4L,NpBleProtocol.u32(q,8))
            assertEquals(utc*1000,NpBleProtocol.u32(q,offset) or (NpBleProtocol.u32(q,offset+4) shl 32))
            assertTrue(NpBleProtocol.checked(q))
            assertFailsWith<IllegalArgumentException>{NpBleProtocol.scheduled(s(version),10,10,true,now+4000,now)}
            assertFailsWith<IllegalArgumentException>{NpBleProtocol.scheduled(s(version).copy(flags=s(version).flags and 1024L.inv()),10,10,true,utc,now)}
        }
    }
    @Test fun storageCommandsGuardConfirmedCardAndUseNativeFormatAndRecoveryKeys() {
        val v1=s(1); val v2=s(2)
        assertEquals(8L,NpBleProtocol.u32(NpBleProtocol.storage(v1,10,NpAction.Mount,v1.capacitySectors),8))
        val legacy=NpBleProtocol.storage(v1,10,NpAction.Format,v1.capacitySectors)
        assertEquals(0x46524d54L,NpBleProtocol.u32(legacy,36))
        val fs2=NpBleProtocol.storage(v1.copy(flags=v1.flags or 0x02000000),10,NpAction.Format,v1.capacitySectors,epoch=0x123456789abcdefL)
        assertEquals(2L,NpBleProtocol.u32(fs2,4)); assertEquals(2L,NpBleProtocol.u32(fs2,16)); assertEquals(0x89abcdefL,NpBleProtocol.u32(fs2,44))
        assertTrue(NpBleProtocol.matches(fs2,status(version=1,id=10,op=5)))
        assertFalse(NpBleProtocol.matches(fs2,status(version=2,id=10,op=5)))
        assertEquals(0x3850534eL,NpBleProtocol.u32(NpBleProtocol.storage(v2,10,NpAction.Format,v2.capacitySectors),16))
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.storage(v2,10,NpAction.Format,v2.capacitySectors+1)}
        val recover=v2.copy(flags=v2.flags or 32768L or 131072L or 524288L or 0x200000L)
        assertTrue(recover.canRecover)
        assertEquals(8L,NpBleProtocol.u32(NpBleProtocol.storage(recover,10,NpAction.Recover,recover.capacitySectors),8))
        assertFalse(recover.copy(flags=recover.flags and 0x200000L.inv()).canRecover)
        val offer=v2.copy(flags=v2.flags or 0x04820000L)
        assertTrue(offer.canOverwrite)
        val rewind=NpBleProtocol.storage(offer,10,NpAction.Overwrite,offer.capacitySectors,offer.startSector)
        assertEquals(12L,NpBleProtocol.u32(rewind,8)); assertEquals(0x574f324eL,NpBleProtocol.u32(rewind,16))
        assertEquals(offer.startSector,NpBleProtocol.u32(rewind,20))
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.storage(offer,10,NpAction.Overwrite,offer.capacitySectors,offer.startSector+8192)}
    }
    @Test fun mediaProfilesReserveNativeTypedChunksAndCannotEraseNeuralSelection() {
        for(profile in listOf(NpMediaProfile(40,false),NpMediaProfile(0,true),NpMediaProfile(50,true))) {
            val q=NpBleProtocol.mediaCommand(profile)
            assertEquals(35,q.size); assertEquals(0x33,q[1].toInt()); assertEquals(profile.cameraHz.toLong(),NpBleProtocol.u32(q,10)); assertEquals(if(profile.microphone) 200000L else 0L,NpBleProtocol.u32(q,18))
            val params=ByteArray(512)
            params[12]=if(profile.cameraHz>0) 64 else 0; params[13]=if(profile.cameraHz>0) 1 else 0; params[16]=if(profile.microphone) 1 else 0
            put(params,48,profile.cameraHz.toLong()); put(params,56,if(profile.microphone) 200000 else 0)
            assertEquals(profile,NpBleProtocol.mediaProfile(params))
            val media=NpBleProtocol.status(status(1,flags=s(1).flags or 0x600000,mask=0,hz=0))!!
            assertTrue(media.isMedia); assertTrue(media.canStart)
            assertFailsWith<IllegalArgumentException>{NpBleProtocol.start(media,10,10,true)}
            val start=NpBleProtocol.start(media,10,10,true,profile)
            assertEquals(0L,NpBleProtocol.u32(start,16)); assertTrue(NpBleProtocol.u32(start,28)>NpBleProtocol.u32(start,24))
            assertTrue(NpBleProtocol.supportsProfile(media,1,15000),"A media device must still offer neural profiles")
        }
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.mediaCommand(NpMediaProfile(0,false))}
        assertFailsWith<IllegalArgumentException>{NpBleProtocol.mediaCommand(NpMediaProfile(51,true))}
    }
    @Test fun nativeParameterFramesKeepEmbeddedDelimitersAndEveryFragmentBoundary() {
        val body=ByteArray(512){it.toByte()}; body[12]=0x3e; body[20]=0xad.toByte()
        val frame=wire(body,0x90)
        for(split in 0..frame.size) {
            val received=mutableListOf<ByteArray>(); val parser=NpBleFrameParser{cmd,b->if(cmd==0x90) received.add(b)}
            parser.push(frame.copyOfRange(0,split)); parser.push(frame.copyOfRange(split,frame.size))
            assertContentEquals(body,received.single())
        }
    }
    @Test fun simulatedFormatAndHandoffAreSingleMutationsWithFreshStatusAndDisconnect()=runBlocking {
        val sent=mutableListOf<Long>(); var last=7L; var retired=false; var published=NpDeviceUiState()
        lateinit var session:NpBleSession
        session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={frame->
            val command=frame[1].toInt() and 255
            val q=if(command==0x82) null else frame.copyOfRange(2,66)
            val op=q?.let{NpBleProtocol.u32(it,8)}?:0; val id=q?.let{NpBleProtocol.u32(it,12)}?:1
            if(op!=0L){sent.add(op);last=id}
            session.parser.push(wire(status(1,flags=s(1).flags or 0x06000000,id=id,op=op,lastId=last)));true
        },publish={published=it},retire={retired=true})
        try {
            session.refresh(); session.execute(NpCommand(NpAction.Format,expectedCapacity=500000000))
            assertEquals("",published.failure); assertEquals(listOf(5L),sent)
            session.execute(NpCommand(NpAction.UsbDownload))
            assertEquals(listOf(5L,7L),sent); assertTrue(retired)
        }finally{session.close()}
    }
    @Test fun partialNativeSendRetiresConnectionAndIsNeverReplayed()=runBlocking {
        var retired=false; var native=0; var published=NpDeviceUiState(); lateinit var session:NpBleSession
        session=NpBleSession(CoroutineScope(SupervisorJob()+Dispatchers.Unconfined),write={frame->
            val cmd=frame[1].toInt() and 255
            if(cmd==0x33){native++;false}else{
                val q=if(cmd==0x82) null else frame.copyOfRange(2,66)
                session.parser.push(wire(status(1,flags=s(1).flags or 0x200000,id=q?.let{NpBleProtocol.u32(it,12)}?:1)));true
            }
        },publish={published=it},retire={retired=true})
        try {session.execute(NpCommand(NpAction.ApplyMedia,cameraHz=40)); assertTrue(retired); assertEquals(1,native); assertTrue(published.failure.contains("not fully sent"))}finally{session.close()}
    }
    @Test fun imuAssemblyRejectsChangingSnapshotTokens()=runBlocking {
        var id=0L
        assertFailsWith<IllegalArgumentException> {
            NpBleTelemetry.imu({++id}){q->
                val r=ByteArray(64); put(r,0,0x5253504e); put(r,4,1); put(r,8,NpBleProtocol.u32(q,8)); put(r,12,NpBleProtocol.u32(q,12)); put(r,20,id); put(r,24,NpBleProtocol.u32(q,20)); put(r,28,140); NpBleProtocol.seal(r)
            }
        }
        Unit
    }
}
