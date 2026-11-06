package com.wild.android.ble

import android.app.Application
import java.io.File
import org.json.JSONObject
import org.json.JSONArray
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],application=Application::class)
class NpProbeConfigurationTest {
    private val routing get()=File("src/main/assets/np2-routing.json").readText()
    private fun fixture(name:String)=javaClass.getResourceAsStream("/neuropixels/$name.imro")!!.use {it.readBytes()}
    @Test fun imroPayloadsMatchTheExistingConsoleCompilerOutsideItsOpaqueJsonIdentity() {
        val expected=mapOf("np1" to "09fd8b2447d58d5f32ec0e97fcdca3c9eab16a699788d7ef9fd6620b81a46587","np2-p1" to "1057863a285b26bea023c2d22f11e23269c2c9e032f5359d530e6edcb3a938e8","np2-p3" to "24301e62f3d989d1ccb758d7c61847b67c23d243ab525688d1f834acfa264a16")
        expected.forEach { (name,hash) ->
            val map=NpProbeConfiguration.importMap(fixture(name),"$name.imro",if(name.endsWith("p3"))3 else 1,routing)
            assertEquals(hash,map.id);assertEquals(if(name=="np1")1576 else 876,map.payload.size)
            val another=NpProbeConfiguration.importMap(fixture(name),"renamed.imro",map.connector,routing)
            assertContentEquals(map.payload,another.payload)
        }
    }
    @Test fun rejectsMalformedDuplicateUnsupportedAndIncorrectlyRoutedImro() {
        val base=fixture("np2-p1").toString(Charsets.UTF_8)
        for(text in listOf(base.replaceFirst("(2013,384)","(24,384)"),base+"junk",base.replaceFirst("(0 ","(1 "),base.replaceFirst(Regex("\\(0 [^)]*\\)"),"(0 0 0 0 1279)"))) {
            assertFails {NpProbeConfiguration.importMap(text.toByteArray(),"bad.imro",1,routing)}
        }
        assertFails {NpProbeConfiguration.importMap(ByteArray(1048577),"large",1,routing)}
    }
    private fun np1Json():JSONObject {
        val rows=JSONArray()
        for(ch in 0..383) rows.put(JSONObject().put("Channel",ch).put("Site",if(ch==191)JSONObject.NULL else ch).put("Shank",0).put("Standby",ch==191).put("Reference","External").put("ApGain",1000).put("LfpGain",50).put("HighPass",true))
        return JSONObject().put("Profile","Np1").put("MappingId","imec-np1-phaseiii-v2-zero-based-v1").put("Channels",rows)
    }
    @Test fun nativeJsonKeepsStandbyAndRejectsFractionalSiteOrForeignMapping() {
        val json=np1Json();val map=NpProbeConfiguration.importMap(json.toString().toByteArray(),"map.json",2,routing)
        assertEquals(2,map.connector);assertEquals(65535,(map.payload[804].toInt() and 255) or ((map.payload[805].toInt() and 255) shl 8))
        json.getJSONArray("Channels").getJSONObject(0).put("Site",0.5)
        assertFails {NpProbeConfiguration.importMap(json.toString().toByteArray(),"bad.json",1,routing)}
        assertFails {NpProbeConfiguration.importMap(np1Json().put("MappingId","other").toString().toByteArray(),"bad.json",1,routing)}
    }
}
