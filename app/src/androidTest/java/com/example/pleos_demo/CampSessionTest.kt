package com.example.pleos_demo

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class CampSessionTest {
    private class VehicleFake:VehicleSource {
        val values=mutableMapOf("temperature" to 24.0,"light" to 30.0)
        var writes=0
        var failLight=true
        var parked=true
        var disconnected=false
        val controls=listOf(
            VehicleControl("temperature","온도",ControlKind.TEMPERATURE,16.0,32.0,.5,true),
            VehicleControl("light","조명",ControlKind.LIGHT,0.0,100.0,1.0,true))
        override suspend fun read():VehicleReading {
            check(!disconnected) { "연결 끊김" }
            return VehicleReading(soc=80.0,charging=false,values=values.toMap(),controls=controls)
        }
        override suspend fun parking()=ParkingState(parked,parked)
        override suspend fun readControl(id:String)=values[id]
        override suspend fun writeControl(id:String,value:Double) {
            writes++
            delay(30)
            if(id=="light" && failLight) error("조명 응답 실패")
            values[id]=value
        }
        override fun close() {}
    }

    @Test fun partialDuplicateRestartAndManualRestore()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_camp_${System.nanoTime()}"
        val vehicle=VehicleFake()
        try {
            val s=CampSession(context,vehicle,name)
            s.start()
            assertTrue(s.active)
            s.selected=CampMode.COOK
            val first=launch(start=CoroutineStart.UNDISPATCHED) { s.applyMode() }
            s.applyMode()
            first.join()
            assertEquals(2,vehicle.writes)
            assertEquals("적용 확인",s.results["temperature"])
            assertTrue(s.results.getValue("light").startsWith("실패"))
            assertEquals(2,s.written.size) // Failed IPC remains unresolved until read-back.
            val restored=CampSession(context,vehicle,name)
            assertTrue(restored.active)
            assertEquals(CampMode.RELAX,restored.mode) // A partial application does not change the confirmed mode.
            restored.refresh()
            vehicle.values["temperature"]=26.0
            restored.restore()
            assertEquals(26.0,vehicle.values["temperature"]!!,0.0)
            assertEquals("수동 변경 유지",restored.results["temperature"])
            vehicle.values["temperature"]=23.0
            restored.restore()
            assertEquals(24.0,vehicle.values["temperature"]!!,0.0)
            assertFalse(restored.written.containsKey("temperature"))
            restored.acknowledgeManualRestore()
            assertTrue(restored.written.isEmpty())
            vehicle.disconnected=true
            restored.refresh()
            assertNull(restored.soc)
            assertFalse(restored.canControl)
            vehicle.disconnected=false
            restored.refresh()
            restored.now+=31_000
            assertFalse(restored.canControl)
            assertNull(restored.soc)
            restored.addEquipment()
            val id=restored.equipmentIds.last()
            restored.edit("watts$id","300")
            restored.selected=CampMode.MOVIE
            restored.savePreset(Preset(25.0,15))
            val again=CampSession(context,vehicle,name)
            assertEquals(3,again.equipment().size)
            assertEquals(300.0,again.equipment().last().watts,0.0)
            assertEquals(Preset(25.0,15),again.presets[CampMode.MOVIE])
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
        Unit
    }

    @Test fun unknownParkingBlocksWrites()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_parking_${System.nanoTime()}"
        val vehicle=VehicleFake()
        vehicle.parked=false
        val s=CampSession(context,vehicle,name)
        s.start()
        assertFalse(s.active)
        s.applyMode()
        assertEquals(0,vehicle.writes)
        context.getSharedPreferences(name,0).edit().clear().commit()
        Unit
    }
}
