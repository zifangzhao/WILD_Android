package com.wild.android.ble

import org.json.JSONObject
import java.security.MessageDigest

data class NpProbeMap(val version: Int, val connector: Int, val model: Int, val source: String,
    val sourceSha256: String, val payload: ByteArray) {
    val id get() = payload.copyOfRange(8,40).joinToString("") { "%02x".format(it) }
    val crc get() = NpBleProtocol.crc(payload,payload.size)
}
data class NpProbeReceipt(val connector: Int, val error: Int, val transferState: Int, val received: Long,
    val portState: Int, val verification: Int, val crc: Long, val id: String, val reportedAtMs: Long = System.currentTimeMillis()) {
    fun matches(map: NpProbeMap) = connector==map.connector && error==0 && crc==map.crc && id==map.id
    fun applied(map: NpProbeMap): Boolean {
        if (!matches(map) || portState!=3) return false
        return if (map.version==1) verification and (if (map.model==2) 3 else 4)==(if (map.model==2) 3 else 4)
        else verification==1 || (verification and 15==2 && verification ushr 4 in 1..14) || (verification and 15==3 && verification ushr 4 in 1..15)
    }
    val description get() = when(portState) { 0 -> "No map"; 1 -> "Receiving"; 2 -> "Staged — applies at next probe preparation"; 3 -> "Applied receipt — verify matching ID/CRC"; 4 -> "Application failed"; else -> "Unknown state" }
    val degraded get() = verification and 15 in 2..3 && verification ushr 4 != 0
}

/** Vendor routing is bundled, never guessed from bank/channel arithmetic for NP2. */
internal object NpProbeConfiguration {
    const val MaxFileBytes=1024*1024
    const val VendorSha="85c1236e512be518f6706256c24a214fab7ff238e993149e242fd5ad69ba167f"
    private val gains=listOf(50,125,250,500,1000,1500,2000,3000)
    private data class Channel(val channel:Int,val site:Int?,val shank:Int=0,val standby:Boolean=false,
        val reference:String="External",val apGain:Int=1000,val lfpGain:Int=50,val highPass:Boolean=true)
    fun importMap(bytes:ByteArray,source:String,connector:Int,routingJson:String):NpProbeMap {
        require(bytes.isNotEmpty() && bytes.size<=MaxFileBytes && connector in 1..3) { "Choose a configuration up to 1 MiB and connector P1–P3." }
        val text=Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString().trim().removePrefix("\uFEFF")
        val channels=mutableListOf<Channel>(); var bank=0; val model:Int; val version:Int
        if (text.startsWith("(")) {
            require(bytes.size<=131072) { "IMRO exceeds 128 KiB." }
            val entries=Regex("\\(([^()]*)\\)").findAll(text).toList(); var end=0
            for (entry in entries) { require(text.substring(end,entry.range.first).isBlank()) { "Unexpected text outside IMRO rows." };end=entry.range.last+1 }
            require(entries.size==385 && text.substring(end).isBlank()) { "IMRO needs a header and exactly 384 channel rows." }
            val header=entries[0].groupValues[1].split(',').map { it.trim() }
            require(header.size==2 && header[1]=="384") { "IMRO header must specify 384 channels." }
            model=when(header[0]) { "0","NP1000","NP1001" -> 2; "2013","NP2013" -> 4; else -> error("Unsupported probe '${header[0]}'. Device programming supports NP1 or NP2013 four-shank gain100, not legacy NP2 gain80/single-shank models.") }
            version=if(model==4) 2 else 1; var referenceBank:Int?=null
            entries.drop(1).forEach { row ->
                val v=row.groupValues[1].trim().split(Regex("\\s+")).map { require(it.matches(Regex("[0-9]+"))) { "IMRO fields must be unsigned integers." };it.toInt() }
                require(v.size==if(version==1) 6 else 5)
                val ch=v[0];require(ch in 0..383)
                if (version==1) {
                    require(v[1] in 0..2 && ch+384*v[1]<960 && v[2] in 0..4 && v[5] in 0..1)
                    if (v[2]>=2) { val b=v[2]-2;require(referenceBank==null || referenceBank==b) { "NP1 has one shared internal-reference bank." };referenceBank=b }
                    channels.add(Channel(ch,if(ch==191) null else ch+384*v[1],standby=ch==191,reference=when(v[2]) {0->"External";1->"Tip";else->"Internal"},apGain=v[3],lfpGain=v[4],highPass=v[5]==1))
                } else {
                    require(v[1] in 0..3 && v[4] in 0..1279 && v[2]==v[4]/384)
                    val reference=when(v[3]) {0->"External";1->"Ground";in 2..5->"Tip${v[3]-2}";else->error("Unsupported NP2013 on-shank reference.")}
                    channels.add(Channel(ch,v[4],v[1],reference=reference,apGain=100,lfpGain=100,highPass=false))
                }
            }; bank=referenceBank?:0
        } else {
            val doc=JSONObject(text)
            fun integer(o:JSONObject,key:String,default:Int?=null):Int {
                if(!o.has(key) && default!=null) return default
                val value=o.get(key);require(value is Number && value.toString().matches(Regex("-?[0-9]+"))) { "$key must be an integer, not a string/fraction." };return value.toString().toInt()
            }
            fun boolean(o:JSONObject,key:String,default:Boolean):Boolean {
                if(!o.has(key)) return default
                return (o.get(key) as? Boolean)?:error("$key must be a boolean.")
            }
            require(integer(doc,"SchemaVersion",1)==1 && doc.optString("Numbering","zero-based")=="zero-based" && doc.optString("HardwareState","NotApplied")=="NotApplied") { "Unsupported config schema, numbering or hardware-state assertion." }
            model=when(doc.getString("Profile")) {"Np1"->2;"Nric1384"->1;"Np2Multi"->4;else->error("Unsupported device probe profile.")};version=if(model==4) 2 else 1
            val mapping=if(version==2) "imec-np2-sha256:$VendorSha" else if(model==2) "imec-np1-phaseiii-v2-zero-based-v1" else "nric1384-fixed-input-v1"
            require(doc.getString("MappingId")==mapping) { "Configuration uses another vendor routing catalog." }
            if(version==2) require(doc.optString("ModelRevision","") in listOf("","2013","NP2013")) { "Only NP2013 fixed-gain100 is programmable on V2." }
            bank=integer(doc,"InternalReferenceBank",0)
            val rows=doc.getJSONArray("Channels");require(rows.length()==384)
            for(i in 0 until rows.length()) { val c=rows.getJSONObject(i)
                channels.add(Channel(integer(c,"Channel"),if(c.isNull("Site")) null else integer(c,"Site"),integer(c,"Shank",0),boolean(c,"Standby",false),c.optString("Reference","External"),integer(c,"ApGain",1000),integer(c,"LfpGain",50),boolean(c,"HighPass",true)))
            }
        }
        require(channels.map { it.channel }.sorted()==(0..383).toList()) { "Exactly one row for every channel 0–383 is required." }
        require(bank in 0..2 && (model!=1 || bank==0))
        val bytesOut=ByteArray(if(version==1) 1576 else 876)
        (if(version==1) "NPB1" else "N2B1").toByteArray().copyInto(bytesOut)
        bytesOut[4]=model.toByte();bytesOut[5]=(connector-1).toByte();bytesOut[6]=(if(version==1)bank else 0).toByte()
        val routing=if(version==2) JSONObject(routingJson).also { require(it.getString("sha256")==VendorSha) }.getJSONArray("shanks") else null
        var tip:Int?=null
        fun bit(shank:Int,n:Int) { val at=40+shank*161+n/8;bytesOut[at]=(bytesOut[at].toInt() or (1 shl (7-n%8))).toByte() }
        val occupied=mutableSetOf<Pair<Int,Int>>()
        for(c in channels) {
            if(version==1) {
                require(c.shank==0 && c.apGain in gains && c.lfpGain in gains)
                require(if(c.site==null) model==2 && c.standby else c.site in 0 until (if(model==2) 960 else 384) && c.site%384==c.channel && (model!=2 || c.site !in listOf(191,575,959))) { "Channel ${c.channel}: invalid/reserved NP1 or NRIC site." }
                if(model==1) require(c.site==c.channel)
                val refs=if(model==2) listOf("External","Tip","Internal") else listOf("All","Parity");require(c.reference in refs)
                val at=40+c.channel*4;val site=c.site?:65535;bytesOut[at]=site.toByte();bytesOut[at+1]=(site ushr 8).toByte()
                bytesOut[at+2]=(gains.indexOf(c.apGain) or (gains.indexOf(c.lfpGain) shl 3) or (if(c.highPass)64 else 0) or (if(c.standby)128 else 0)).toByte();bytesOut[at+3]=refs.indexOf(c.reference).toByte()
            } else {
                require(c.shank in 0..3 && c.site!=null && c.site in 0..1279 && !c.standby && c.apGain==100 && c.lfpGain==100 && !c.highPass) { "NP2013 needs all 384 connected channels, gain100, full band." }
                require(routing!!.getJSONArray(c.shank).getInt(c.site)==c.channel && occupied.add(c.shank to c.site)) { "Site/channel routing conflicts with the vendor map." }
                bit(c.shank,if(c.site and 1!=0)642-c.site/2 else 645+c.site/2)
                val ref=when(c.reference) {
                    "Ground"->1
                    "External"->{bit(c.shank,2);bit(c.shank,1285);4}
                    "Tip","Tip0","Tip1","Tip2","Tip3"->{val target=if(c.reference=="Tip") c.shank else c.reference.last()-'0';require(tip==null || tip==target) { "Only one shared Tip-reference shank can be connected." };tip=target;2}
                    else->error("Unsupported NP2013 reference.")
                }
                val at=684+(c.channel and 1)*96+(383-c.channel)/4
                bytesOut[at]=(bytesOut[at].toInt() or (ref shl (if(c.channel and 2!=0)4 else 0))).toByte()
            }
        }
        tip?.let { bit(it,643);bit(it,644) }
        // Canonical physical request identity, including connector. Exact
        // imported-file identity is retained separately as sourceSha256.
        MessageDigest.getInstance("SHA-256").digest(bytesOut).copyInto(bytesOut,8)
        val sourceHash=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        return NpProbeMap(version,connector,model,source.take(160),sourceHash,bytesOut)
    }
}

internal object NpConfigWire {
    fun request(version:Int,op:Int,port:Int,id:Long,offset:Int=0,length:Int=0,crc:Long=0,data:ByteArray?=null):ByteArray {
        require(version in 1..2 && op in 0..(if(version==2)5 else 4) && port in 0..2 && id in 1..0xffffffffL)
        require(if(op==2) data!=null && data.size in 1..32 && data.size==length else data==null)
        val b=ByteArray(64);"NPC$version".toByteArray().copyInto(b);b[4]=version.toByte();b[5]=op.toByte();b[6]=port.toByte()
        NpBleProtocol.put(b,8,id);NpBleProtocol.put(b,12,offset.toLong());NpBleProtocol.put(b,16,length.toLong());NpBleProtocol.put(b,20,crc);data?.copyInto(b,24)
        return NpBleProtocol.seal(b)
    }
    fun matches(q:ByteArray,r:ByteArray)=r.size==64 && NpBleProtocol.checked(r) && r.copyOfRange(0,4).contentEquals("NPR${q[4]}".toByteArray()) && r[4]==q[4] && r[5]==q[5] && NpBleProtocol.u32(r,8)==NpBleProtocol.u32(q,8) && NpBleProtocol.u32(r,16)==if(q[4].toInt()==1)1576L else 876L
    fun parse(q:ByteArray,r:ByteArray):NpProbeReceipt {
        require(matches(q,r)) { "Configuration receipt family/op/ID/CRC mismatch." }
        val port=q[6].toInt();val v=q[4].toInt()
        require(r[6].toInt() and 255<=10 && r[7].toInt() and 255<=4 && r[20].toInt() and 255<=4)
        if(v==2 && q[5].toInt()!=5 && r[20].toInt()!=0) require(r[22].toInt()==port && r[23].toInt()==4)
        return NpProbeReceipt(port+1,r[6].toInt() and 255,r[7].toInt() and 255,NpBleProtocol.u32(r,12),r[20].toInt() and 255,r[21].toInt() and 255,NpBleProtocol.u32(r,24),r.copyOfRange(28,60).joinToString("") { "%02x".format(it) })
    }
}
