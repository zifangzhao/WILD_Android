package com.wild.android.ble

import java.security.MessageDigest
import kotlinx.coroutines.delay

data class NpCatalogRecord(val id:Long,val start:Long,val end:Long,val cookie:Long=0,val session:Long=0,val hz:Long=0,val mask:Long=0,val flags:Long=0) {
    val mib get() = (end-start)*512.0/1048576
    val label get() = when((flags ushr 8) and 255) { 3L -> if(flags and 0x80000L==0L) "Complete metadata*" else "Incomplete";4L->"Failed";1L->"Reserved";else->"Unverified" }
}
data class NpCatalogSnapshot(val version:Int,val capacity:Long,val frontier:Long,val records:List<NpCatalogRecord>,
    val fingerprint:String,val canDelete:Boolean,val warning:String="",val reportedAtMs:Long=System.currentTimeMillis())

/** Selected-session catalog client. Native V1 uncertainty must retire its link. */
internal class NpBleCatalog(
    private val readStatus:suspend ()->NpDeviceStatus,
    private val nextId:()->Long,
    private val exchange:suspend (Int,ByteArray,(ByteArray)->Boolean)->ByteArray,
    private val nativePage:suspend (Int)->ByteArray,
    private val nativeDelete:suspend ()->Long,
    private val mutate:suspend (ByteArray)->NpDeviceStatus,
    private val progress:(String)->Unit={},
) {
    private data class Info(val token:Long,val count:Long,val next:Long,val capacity:Long,val cid:Long,val flags:Long,val ordinal:Long,val start:Long,val end:Long,val cookie:Long)
    private data class Listing(val ui:NpCatalogSnapshot,val info:Info?=null,val deletion:ByteArray?=null)
    private var cached:Listing?=null
    private var boundUid:String?=null
    private fun u(b:ByteArray,at:Int)=NpBleProtocol.u32(b,at)
    private fun wide(b:ByteArray,at:Int)=u(b,at) or (u(b,at+4) shl 32)
    private fun align(n:Long)=(n+8191)/8192*8192
    private fun canRead(s:NpDeviceStatus)=s.stopped && s.capacitySectors>8192 && if(s.version==1) s.has(16) || s.has(0x01000000) && s.startSector==0L else s.has(0x400000L or 16) && s.state in listOf(1,5,7)
    private fun transactional(s:NpDeviceStatus)=s.version==1 && s.stopped && s.has(0x10000000L or 0x04000000L or 17)
    private suspend fun query(q:ByteArray):ByteArray=exchange(0xB9,q) { NpBleProtocol.matches(q,it) }.also { require(u(it,16)==0L) { "Catalog query rejected (${u(it,16)}). Refresh; nothing modified." } }
    private fun fingerprint(version:Int,s:NpDeviceStatus,pages:List<ByteArray>,records:List<NpCatalogRecord>,info:Info?):String {
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update("$version:${s.capacitySectors}:${s.startSector}:${s.flags and (if(version==1) 0x05000010L else 0x048f0010L)}:$info:$records".toByteArray())
        pages.forEach { digest.update(it) }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
    private suspend fun info():Info {
        val b=query(NpBleProtocol.request(2,9,nextId()))
        val i=Info(u(b,20),u(b,24),u(b,28),u(b,32),u(b,36),u(b,40),u(b,44),u(b,48),u(b,52),u(b,56))
        require(i.count<=64512 && i.capacity>8192 && i.next>=8192 && i.next%8192==0L && i.next<=align(i.capacity) && i.flags and 0x048f0000L.inv()==0L)
        require(i.flags and (262144L or 524288L)==0L || i.flags and 131072L!=0L)
        require(i.flags and 0x04000000L==0L || i.flags and (0x00800000L or 131072L)==(0x00800000L or 131072L))
        require(if(i.count==0L) i.ordinal==0xffffffffL && i.start==0L && i.end==0L && i.cookie==0L && i.next==8192L else i.ordinal==i.count-1 && i.start>=8192 && i.start%8192==0L && i.end>i.start+4 && i.end<=i.capacity && align(i.end)==i.next) { "Invalid catalog tail/geometry." }
        return i
    }
    private suspend fun readV2(s:NpDeviceStatus):Listing {
        val i=info();require(i.capacity==s.capacitySectors && i.next==s.startSector && i.flags==s.flags and 0x048f0000L) { "Card/catalog changed. Refresh." }
        val records=mutableListOf<NpCatalogRecord>();var start=8192L
        for(ordinal in 0 until i.count) {
            val b=query(NpBleProtocol.request(2,10,nextId(),i.token,i.count,ordinal))
            require(u(b,20)==i.token && u(b,24)==i.count && u(b,28)==ordinal) { "Catalog snapshot/ordinal changed." }
            val r=NpCatalogRecord(ordinal+1,u(b,32),u(b,36),u(b,40),u(b,44),u(b,48),u(b,52),u(b,56))
            val format=r.flags and 255;val state=(r.flags ushr 8) and 255;val last=ordinal==i.count-1;val metadata=r.flags and 0x40000L!=0L
            require(r.start==start && r.end>r.start+4 && r.end<=i.capacity && r.flags and 0xfff00000L==0L && format in listOf(0L,8L,11L,12L,13L,14L,15L,16L) && state in listOf(0L,1L,3L,4L) && (r.flags and 0x10000L!=0L)==last)
            require(!metadata || format!=0L && r.session!=0L && (if(format==8L) r.hz==0L && r.mask==0L else r.hz.toInt() in NpBleProtocol.rates && NpBleProtocol.profileTicks(r.mask.toInt())!=0L))
            if(format in 14L..16L && metadata) require(state!=0L && (state==3L)!=(r.flags and 0x80000L!=0L))
            if(r.flags and 0x20000L!=0L) require(last && metadata && state in listOf(3L,4L) && i.flags and 65536L.inv()==0L && (format !in 15L..16L || state==3L && r.flags and 0x80000L==0L))
            if(last) require(r.start==i.start && r.end==i.end && r.cookie==i.cookie)
            records.add(r);start=align(r.end)
            if(ordinal%32==0L) progress("Read ${ordinal+1}/${i.count} recording entries…")
        }
        require(start==i.next && i==info()) { "Catalog changed while listing. Refresh before modifying it." }
        val warning=if(i.flags and 0x048e0000L!=0L) "Degraded/recovery catalog; deletion disabled. " else ""
        return Listing(NpCatalogSnapshot(2,i.capacity,i.next,records,fingerprint(2,s,emptyList(),records,i),records.lastOrNull()?.flags?.and(0x20000L)!=0L && records.isNotEmpty(),warning+"*Metadata only; download verifies sample coverage/payload."),i)
    }
    private suspend fun readV1(s:NpDeviceStatus,inspect:Boolean):Listing {
        val fs2=s.has(0x04000000);val readOnly=s.has(0x01000000) && !s.has(16) && s.startSector==0L
        if(fs2) require(!readOnly && s.has(16) && s.startSector>=8192 && s.startSector%8192==0L && s.startSector<=s.capacitySectors)
        val records=mutableListOf<NpCatalogRecord>();val pages=mutableListOf<ByteArray>();var previous=8192L;var epoch=0L;var generation=0L;var total=0L;var missing=0;var ended=false;var warning=""
        for(page in 0 until if(fs2)70 else if(readOnly)16 else 1024) {
            val b=try { nativePage(page) } catch(error:Exception) { if(error is kotlinx.coroutines.CancellationException || !readOnly) throw error;warning="Readable prefix only; catalog read failed. Reconnect before retrying.";break }
            require(b.size==512)
            pages.add(b)
            if(fs2) {
                require(u(b,0)==0x47503253L && NpBleProtocol.checked(b) && u(b,4)==page.toLong() && u(b,504)==0L) { "Invalid filesystem2 page/CRC; no legacy projection used." }
                val count=u(b,24);val currentTotal=u(b,28);val first=page*58L;val more=u(b,36)
                require(wide(b,8)!=0L && wide(b,16)!=0L && currentTotal<=4032 && first<=currentTotal && count==minOf(58L,currentTotal-first) && u(b,32)==s.startSector && more==if(first+count<currentTotal)1L else 0L)
                require(if(currentTotal==0L) s.startSector==8192L else s.startSector!=8192L)
                if(page==0) {epoch=wide(b,8);generation=wide(b,16);total=currentTotal} else require(wide(b,8)==epoch && wide(b,16)==generation && currentTotal==total) { "Filesystem2 generation changed." }
                for(n in 0 until 58) {
                    val start=u(b,40+n*8);val end=u(b,44+n*8)
                    if(n>=count) {require(start==0L && end==0L);continue}
                    if(start==0L && end==0L) {missing++;continue}
                    require(start>=8192 && start%8192==0L && end%8192==0L && end>start && end<=s.startSector && end<=s.capacitySectors && start>=previous)
                    records.add(NpCatalogRecord(first+n+1,start,end));previous=end
                }
                if(more==0L) {ended=true;break}
            } else {
                val count=u(b,0)
                if(readOnly && count>63) {warning="Readable prefix only: invalid catalog count.";break};require(count<=63)
                for(n in 0 until count.toInt()) {
                    val start=align(previous);val end=u(b,4+n*4)
                    if(start>0xffffffffL || end<=start+4 || end>s.capacitySectors || u(b,256+n*4)!=end) {
                        if(readOnly) {warning="Readable prefix only: invalid extent; later starts were not inferred.";break} else error("Invalid catalog extent; nothing modified.")
                    }
                    records.add(NpCatalogRecord(records.size+1L,start,end));previous=end
                }
                if(warning.isNotBlank()) break
                if(count<63) {if(!readOnly) require(align(previous)==s.startSector);ended=true;break}
            }
            if(page%16==0) progress("Read ${records.size} recording entries…")
        }
        if(!ended && warning.isBlank()) {if(readOnly) warning="Readable prefix only: 16-page read-only limit." else error("Catalog exceeds supported page limit.")}
        if(missing>0) warning="$missing unavailable descriptors skipped; missing extents were not inferred. Delete disabled."
        if(readOnly) warning="Read-only catalog. $warning"
        val after=if(readOnly && !ended) s else readStatus()
        require(canRead(after) && after.capacitySectors==s.capacitySectors && after.startSector==s.startSector && after.flags and 0x05000010L==s.flags and 0x05000010L) { "Storage changed while listing." }
        var deletion:ByteArray?=null
        var canDelete=!readOnly && !fs2 && s.fault==0L && s.state in listOf(0,3) && s.has(17) && records.isNotEmpty()
        if(inspect && transactional(after) && missing==0 && records.isNotEmpty()) {
            try {
                val prior=deleteRead(deleteQuery(0));require(u(prior,12) !in 1L..2L && u(prior,16)<0xffffffffL)
                val nonce=u(prior,16)+1
                val inspected=deleteWait(deleteRead(deleteQuery(1,nonce)),1,nonce)
                val last=records.last()
                require(u(inspected,32)==s.capacitySectors && u(inspected,40)==s.startSector)
                deletion=inspected
                canDelete=u(inspected,28) and 3L==3L && u(inspected,36)>0 && last.id==u(inspected,44)+1 && last.start==u(inspected,48) && last.end<=u(inspected,52)
            } catch(error:Exception) {if(error is kotlinx.coroutines.CancellationException) throw error;warning+=" Delete inspection unavailable: ${error.message}";canDelete=false}
        }
        val snapshot=NpCatalogSnapshot(1,s.capacitySectors,s.startSector,records,fingerprint(1,s,pages,records,null),canDelete,warning)
        return Listing(snapshot,deletion=deletion)
    }
    private suspend fun read(inspect:Boolean):Listing {
        val s=readStatus();require(canRead(s)) { "Stop recording and mount a supported catalog first. NP2 listing needs firmware capability22." }
        return if(s.version==2) readV2(s) else readV1(s,inspect)
    }
    suspend fun refresh():NpCatalogSnapshot { cached=null;val listing=read(true);cached=listing;return listing.ui }
    fun invalidate() {cached=null}
    private fun deleteQuery(op:Long,nonce:Long=0)=ByteArray(8).also { NpBleProtocol.put(it,0,op);NpBleProtocol.put(it,4,nonce) }
    private suspend fun deleteRead(q:ByteArray):ByteArray {
        val b=exchange(0xA7,q) { r -> r.size==128 && NpBleProtocol.checked(r) && u(r,0)==0x3155444eL && u(r,4)==1L && (if(q.size==8) u(q,0)==0L || u(r,8)==u(q,0) && u(r,16)==u(q,4) else u(r,8)==2L && u(r,16)==u(q,16) && u(r,96)==u(q,96)) }
        require(u(b,8)<=2 && u(b,12)<=4 && u(b,28) and 15L.inv()==0L && (100 until 124).all { b[it]==0.toByte() })
        val uid=(80..88 step 4).joinToString("") { "%08X".format(u(b,it)) }
        require(uid!= "0".repeat(24) && uid!="F".repeat(24) && (boundUid==null || boundUid==uid)) { "Delete logger UID changed/unavailable." };boundUid=uid
        require((u(b,8)==0L)==(u(b,12)==0L) && (u(b,8)==0L || u(b,16)!=0L) && (u(b,12)!=3L || u(b,20)==0L))
        if(u(b,28) and 1L!=0L) require(u(b,32)>8192 && u(b,40) in 8192..u(b,32) && (64 until 80).any { b[it]!=0.toByte() } && (64 until 80).any { b[it]!=(-1).toByte() })
        if(u(b,28) and 2L!=0L) require(u(b,28) and 1L!=0L && u(b,36)!=0L && u(b,48)>=8192 && u(b,52)>u(b,48) && u(b,52)<=u(b,40))
        return b
    }
    private suspend fun deleteWait(first:ByteArray,op:Long,nonce:Long,confirmed:ByteArray?=null):ByteArray {
        var r=first;val deadline=System.nanoTime()+30_000_000_000L
        while(true) {
            require(u(r,8)==op && u(r,16)==nonce) { "Delete transaction changed; no replay." }
            if(confirmed!=null) require(u(r,24)==u(confirmed,24) && u(r,32)==u(confirmed,32) && (64 until 92).all { r[it]==confirmed[it] } && u(r,96)==u(confirmed,16)) { "Delete card/UID/epoch changed; no replay." }
            require(u(r,12)!=4L) { "Delete failed: ${u(r,20)}; inspect before another action." }
            if(u(r,12)==3L) {
                if(confirmed!=null) require(u(r,36)==u(confirmed,36)-1 && u(r,40) in 8192..u(confirmed,48))
                require(transactional(readStatus()));return r
            }
            require(u(r,12) in 1L..2L && System.nanoTime()<deadline) { "Delete outcome unknown; keep power connected, refresh. No replay." }
            delay(100);r=deleteRead(deleteQuery(0))
        }
    }
    suspend fun deleteLast(fingerprint:String):NpCatalogSnapshot {
        val confirmed=cached?:error("Refresh recordings before deleting.")
        require(confirmed.ui.canDelete && confirmed.ui.fingerprint==fingerprint) { "Delete confirmation is stale. Refresh and confirm again." }
        cached=null // Unknown mutation outcomes can never reuse this consent.
        val fresh=read(false)
        require(fresh.ui.fingerprint==confirmed.ui.fingerprint && fresh.ui.records.isNotEmpty()) { "Catalog changed after confirmation; nothing deleted." }
        val s=readStatus();require(canRead(s) && s.capacitySectors==fresh.ui.capacity && s.startSector==fresh.ui.frontier && s.has(17))
        val last=fresh.ui.records.last()
        if(s.version==2) {
            val i=requireNotNull(fresh.info);require(confirmed.ui.records.last().flags and 0x20000L!=0L && s.flags and 131072L==0L)
            val q=NpBleProtocol.request(2,11,nextId(),i.token,i.count,i.ordinal,last.start,last.end,last.cookie,0x4c44324e)
            val id=u(q,12);var ack=mutate(q);require(ack.lastMutationId==id)
            val deadline=System.nanoTime()+10_000_000_000L
            while(ack.busy && System.nanoTime()<deadline) {delay(200);ack=readStatus();require(ack.lastMutationId==id)}
            require(!ack.busy && ack.error==0L && ack.fault==0L && canRead(ack)) { "Delete completion unknown; no replay." }
        } else if(confirmed.deletion!=null) {
            require(transactional(s))
            val inspected=confirmed.deletion;val current=deleteRead(deleteQuery(0));require(current.contentEquals(inspected)) { "Inspected delete snapshot changed. Nothing deleted." }
            require(u(inspected,16)<0xffffffffL)
            val q=inspected.copyOf();NpBleProtocol.put(q,8,2);NpBleProtocol.put(q,16,u(inspected,16)+1);NpBleProtocol.put(q,96,u(inspected,16));NpBleProtocol.seal(q)
            deleteWait(deleteRead(q),2,u(q,16),inspected)
        } else {
            require(s.version==1 && !s.has(0x04000000) && !s.has(0x01000000) && s.fault==0L && s.state in listOf(0,3)) { "Filesystem2 needs transactional Delete Last support; legacy deletion requires a fault-free idle/completed recorder." }
            require(nativeDelete()==fresh.ui.records.size-1L) { "Native delete result unconfirmed; reconnect and refresh, no replay." }
        }
        val result=read(true)
        require(result.ui.capacity==fresh.ui.capacity && result.ui.records.size==fresh.ui.records.size-1 && (result.info==null || result.info.cid==fresh.info?.cid)) { "Deleted catalog result differs; no replay." }
        result.ui.records.forEachIndexed { i,r -> val old=fresh.ui.records[i];require(r.copy(flags=0)==old.copy(flags=0) && (r.flags xor old.flags) and (0x10000L or 0x20000L).inv()==0L) { "An earlier recording changed after deletion." } }
        cached=result;return result.ui
    }
}
