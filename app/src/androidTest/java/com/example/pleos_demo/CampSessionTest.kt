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
        var delayed=false
        var unrestricted:Boolean?=null
        val controls=listOf(
            VehicleControl("temperature","온도",ControlKind.TEMPERATURE,16.0,32.0,.5,true),
            VehicleControl("light","조명",ControlKind.LIGHT,0.0,100.0,1.0,true))
        override suspend fun read():VehicleReading {
            check(!disconnected) { "연결 끊김" }
            return VehicleReading(soc=80.0,charging=false,values=values.toMap(),controls=controls)
        }
        override suspend fun parking()=ParkingState(parked,unrestricted?:parked)
        override suspend fun readControl(id:String)=values[id]
        override suspend fun writeControl(id:String,value:Double) {
            writes++
            delay(30)
            if(id=="light" && failLight) error("조명 응답 실패")
            if(!delayed) values[id]=value
        }
        override fun close() {}
    }

    @Test fun guideProgressAndTimerSurviveRestartWithoutVehicleWrites() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_guide_${System.nanoTime()}"
        val vehicle=VehicleFake()
        try {
            val s=CampSession(context,vehicle,name)
            val deadline=System.currentTimeMillis()+900_000
            s.edit("guide.COOK.step","1")
            s.edit("guide.COOK.check.0.0","true")
            s.edit("guide.COOK.deadline",deadline.toString())
            s.selectMode(CampMode.SLEEP)
            val resumed=CampSession(context,vehicle,name)
            assertEquals("1",resumed.fields["guide.COOK.step"])
            assertEquals("true",resumed.fields["guide.COOK.check.0.0"])
            assertEquals(deadline.toString(),resumed.fields["guide.COOK.deadline"])
            assertEquals(CampMode.SLEEP,resumed.selected)
            assertEquals(0,vehicle.writes)
            resumed.edit("guide.COOK.deadline","0")
            assertEquals("0",CampSession(context,vehicle,name).fields["guide.COOK.deadline"])
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }

    @Test fun ideaPreparationPersistsWithoutActuationOrDuplicateTimeExtension()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_idea_${System.nanoTime()}"
        val vehicle=VehicleFake()
        try {
            val s=CampSession(context,vehicle,name)
            s.edit("capacity","100")
            s.addEquipment(Equipment("전자레인지",1400.0,.01))
            s.refresh()
            val idea=campIdeas.first { it.id=="popcorn" }
            assertTrue(s.prepareIdea(idea,4))
            delay(20)
            s.start()
            assertTrue(s.active)
            assertTrue(s.ideaPrepared(idea,4))
            val id=s.equipmentIds[s.ideaEquipmentIndex(idea)]
            val editedAt=s.edited["hours$id"]
            val hours=s.fields["hours$id"]
            s.now+=1_000
            assertTrue(s.prepareIdea(idea,4))
            assertEquals(editedAt,s.edited["hours$id"])
            assertEquals(hours,s.fields["hours$id"])
            assertEquals(0,vehicle.writes)
            val resumed=CampSession(context,vehicle,name)
            assertTrue(resumed.ideaPrepared(idea,4))
            assertEquals("popcorn",resumed.fields["idea.current"])
            resumed.edit("hours$id","1.0")
            assertFalse(resumed.ideaPrepared(idea,4))
            assertNull(resumed.ideaBudget(idea,4)) // No fresh observation after restart.
            resumed.refresh()
            assertNotNull(resumed.ideaBudget(idea,4))
            resumed.now+=31_000
            assertNull(resumed.ideaBudget(idea,4))
            assertFalse(resumed.prepareIdea(idea,4))
            assertEquals(0,vehicle.writes)
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }

    @Test fun ideaPreparationRejectsShortfallInvalidTimeAndMissingEquipment()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_idea_boundary_${System.nanoTime()}"
        val vehicle=VehicleFake()
        try {
            val s=CampSession(context,vehicle,name)
            s.edit("capacity","100")
            s.refresh()
            val idea=campIdeas.first { it.id=="chicken" }
            assertNull(s.ideaBudget(idea,30))
            s.addEquipment(Equipment("에어프라이어",1500.0,.5))
            assertNull(s.ideaBudget(idea,0))
            assertNull(s.ideaBudget(idea,1441))
            assertNull(s.ideaBudget(idea,1440)) // Beyond the session deadline.
            s.edit("distance","10000")
            assertFalse(s.prepareIdea(idea,30))
            assertNull(s.fields["idea.prepared"])
            assertEquals(0,vehicle.writes)
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
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

    @Test fun individualControlDoesNotApplyUneditedPresetValues()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_individual_${System.nanoTime()}"
        val vehicle=VehicleFake().apply { failLight=false }
        try {
            val s=CampSession(context,vehicle,name)
            s.start()
            s.setTarget(vehicle.controls.first(),25.0)
            s.applyMode(onlyEdited=true)
            assertEquals(1,vehicle.writes)
            assertTrue(s.overrides.isEmpty())
            s.applyMode(onlyEdited=true)
            assertEquals(1,vehicle.writes)
            assertEquals(25.0,vehicle.values.getValue("temperature"),0.0)
            assertEquals(30.0,vehicle.values.getValue("light"),0.0)
            s.restore()
            assertEquals(24.0,vehicle.values.getValue("temperature"),0.0)
            assertTrue(s.written.isEmpty())
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
        Unit
    }

    @Test fun delayedDirectResponseSurvivesRestartWithoutDuplicateOrEarlyRestore()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_delayed_${System.nanoTime()}"
        val vehicle=VehicleFake().apply { delayed=true }
        try {
            val s=CampSession(context,vehicle,name)
            s.refresh()
            val temperature=vehicle.controls.first()
            s.applyDirect(temperature,25.0)
            assertFalse(s.active)
            assertTrue(s.awaiting(temperature.id))
            assertEquals(24.0,s.current.getValue(temperature.id),0.0)
            s.applyDirect(temperature,26.0)
            assertEquals(1,vehicle.writes)
            val restarted=CampSession(context,vehicle,name)
            restarted.refresh()
            restarted.restore()
            assertTrue(restarted.awaiting(temperature.id))
            assertEquals(1,vehicle.writes)
            assertEquals(25.0,restarted.written.getValue(temperature.id),0.0)
            vehicle.values[temperature.id]=25.0
            restarted.refresh()
            assertFalse(restarted.awaiting(temperature.id))
            assertEquals("요청값 수신 확인",restarted.results[temperature.id])
            restarted.restore()
            assertEquals("복원 반영 대기",restarted.results[temperature.id])
            restarted.restore()
            assertEquals(2,vehicle.writes)
            val restoring=CampSession(context,vehicle,name)
            vehicle.values[temperature.id]=24.0
            restoring.refresh()
            assertTrue(restoring.written.isEmpty())
            assertEquals("복원 확인",restoring.results[temperature.id])
            assertEquals(2,vehicle.writes)
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
        Unit
    }

    @Test fun drivingClimateRequiresPlatformPermission()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_driving_${System.nanoTime()}"
        val vehicle=VehicleFake().apply { parked=false;unrestricted=true }
        try {
            val s=CampSession(context,vehicle,name)
            s.refresh()
            assertFalse(s.canControl)
            assertTrue(s.canAdjust(vehicle.controls.first()))
            assertFalse(s.canAdjust(vehicle.controls.last()))
            s.applyDirect(vehicle.controls.first(),25.0)
            assertEquals(1,vehicle.writes)
            vehicle.unrestricted=false
            s.refresh()
            s.applyDirect(vehicle.controls.first(),26.0)
            assertEquals(1,vehicle.writes)
            assertFalse(ParkingState(null,true).allows(ControlKind.TEMPERATURE))
            assertFalse(ParkingState(false,null).allows(ControlKind.TEMPERATURE))
            assertFalse(ParkingState(false,true,System.currentTimeMillis()-11_000).allows(ControlKind.TEMPERATURE))
            assertFalse(ParkingState(false,true).allows(ControlKind.SEAT_POSITION))
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
