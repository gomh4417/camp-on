package com.example.pleos_demo

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.time.LocalDateTime

/** Deterministic visual fixtures only. Production never receives these readings. */
class FigmaScreensTest {
    @get:Rule val compose=createComposeRule()
    private class VisualVehicle:VehicleSource {
        var soc=75.0
        val values=mutableMapOf("temperature" to 22.0,"light" to 70.0,"auto" to 1.0,"fan" to 2.0,"cabin" to 1.0)
        val controls=listOf(
            VehicleControl("temperature","설정 온도",ControlKind.TEMPERATURE,16.0,32.0,1.0,true),
            VehicleControl("light","밝기",ControlKind.LIGHT,0.0,100.0,1.0,true),
            VehicleControl("auto","자동 공조",ControlKind.AUTO,0.0,1.0,1.0,true),
            VehicleControl("fan","바람 세기",ControlKind.FAN,1.0,7.0,1.0,true),
            VehicleControl("cabin","실내등",ControlKind.CABIN_LIGHT,0.0,2.0,1.0,true,mapOf(0.0 to "OFF",1.0 to "ON",2.0 to "AUTO")))
        override suspend fun read()=VehicleReading(soc=soc,charging=false,cabin=21.0,values=values.toMap(),controls=controls)
        override suspend fun parking()=ParkingState(true,true)
        override suspend fun readControl(id:String)=values[id]
        override suspend fun writeControl(id:String,value:Double){values[id]=value}
        override fun close(){}
    }
    @Test fun captureEveryDesignedScreenAndControlVariant() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="test_visual_${System.nanoTime()}"
        val vehicle=VisualVehicle()
        val s=CampSession(context,vehicle,name)
        s.edit("end",LocalDateTime.now().plusHours(12).format(CampSession.format))
        s.edit("capacity","100");s.edit("distance","120");s.edit("efficiency","4");s.edit("reserve","11")
        s.edit("scene.still","true")
        kotlinx.coroutines.runBlocking { s.refresh();s.refreshParking() }
        var case by mutableIntStateOf(0)
        var panel by mutableStateOf("")
        compose.setContent { key(case) { CampTheme { FigmaCampContent(s,panel,startInJourney=false) } } }
        val dir=File(context.filesDir,"figma-verification").apply { mkdirs() }
        fun capture(id:String,p:String="",change:()->Unit={}) {
            compose.runOnIdle { vehicle.soc=if(p=="shortfall")20.0 else 75.0;change();kotlinx.coroutines.runBlocking { s.refresh() };panel=if(p.startsWith("lamp-"))"equipment" else if(p=="edit")"options" else p;case++ }
            compose.waitForIdle()
            if(p=="edit") { compose.onNodeWithText("수정").performClick();compose.waitForIdle() }
            if(p.startsWith("lamp-")) {
                compose.onNodeWithText("외부 조명").performClick()
                if(p=="lamp-edit")compose.onNodeWithText("수정").performClick()
                if(p=="lamp-delete")compose.onNodeWithText("삭제").performClick()
                compose.waitForIdle()
            }
            Thread.sleep(700)
            compose.onAllNodesWithText("CAMP:ON",substring=true).onFirst().assertExists()
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            assertNotNull("Screenshot $id",bitmap)
            File(dir,"$id.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        try {
            capture("01-home-cook"){s.selectMode(CampMode.COOK)}
            capture("02-home-relax"){s.selectMode(CampMode.RELAX)}
            capture("03-home-sleep"){s.selectMode(CampMode.SLEEP)}
            listOf("setup","time","destination","places","plan","equipment","options","edit","delete","shortfall","detail",
                "seat","mode","start","departure","menu","settings").forEachIndexed { i,p->capture("${i+4}-$p",p) }
            capture("21-climate-auto","climate"){vehicle.values["auto"]=1.0;vehicle.values["temperature"]=22.0}
            capture("22-climate-manual","climate"){vehicle.values["auto"]=0.0}
            capture("23-climate-fan3","climate"){vehicle.values["fan"]=3.0}
            capture("24-climate-21","climate"){vehicle.values["temperature"]=21.0}
            capture("25-climate-23","climate"){vehicle.values["temperature"]=23.0}
            capture("26-climate-fan1","climate"){vehicle.values["fan"]=1.0}
            capture("27-light-on","light"){vehicle.values["light"]=70.0;vehicle.values["cabin"]=1.0}
            capture("28-light-auto","light"){vehicle.values["cabin"]=2.0}
            capture("29-light-off","light"){vehicle.values["cabin"]=0.0}
            capture("30-plan-first-off","plan"){s.edit("enabled${s.equipmentIds.first()}","false")}
            capture("31-plan-all-off","plan"){s.equipmentIds.forEach { s.edit("enabled$it","false") }}
            capture("32-place-edit","placeEdit")
            compose.runOnIdle { panel="plan";case++ }
            compose.waitForIdle()
            compose.onNodeWithText("+   장비 추가").performClick()
            compose.onNodeWithText("가져온 장비").assertExists()
            compose.onNodeWithContentDescription("닫기").performClick()
            compose.onNodeWithText("캠핑의 밤").assertExists()
            // Exercise the actual edit/add/delete navigation and persisted plan switch.
            compose.runOnIdle { panel="equipment";case++ }
            compose.onNodeWithText("기타 장비").performClick()
            compose.onNodeWithText("수정 저장").assertIsNotEnabled()
            compose.onNodeWithContentDescription("장비 이름").performTextInput("테스트 전기포트")
            compose.onNodeWithContentDescription("소비전력 · W").performTextInput("1000")
            compose.onNodeWithContentDescription("앞으로 사용 · 시간").performTextReplacement("0.25")
            compose.onNodeWithText("수정 저장").performClick()
            compose.runOnIdle { assertTrue(s.equipmentIds.any { s.fields["name$it"]=="테스트 전기포트" }) }
            compose.onNodeWithText("테스트 전기포트").performClick()
            compose.onNodeWithText("삭제").performClick()
            compose.onNodeWithText("취소").performClick()
            compose.runOnIdle { assertTrue(s.equipmentIds.any { s.fields["name$it"]=="테스트 전기포트" }) }
            compose.onNodeWithText("테스트 전기포트").performClick()
            compose.onNodeWithText("삭제").performClick()
            compose.onNodeWithText("삭제").performClick()
            compose.runOnIdle { assertFalse(s.equipmentIds.any { s.fields["name$it"]=="테스트 전기포트" }) }
            compose.onNodeWithText("장비 관리 완료").performClick()
            compose.onNodeWithContentDescription("장비 사용 계획 · 냉장고").performClick()
            compose.runOnIdle { assertEquals("true",s.fields["enabled${s.equipmentIds.first()}"]) }
            compose.onNodeWithContentDescription("닫기").performClick()
            compose.onNodeWithContentDescription("온도 올리기").performClick()
            compose.waitUntil(5000) { vehicle.values["temperature"]==24.0 }
            capture("33-plan-second-off","plan"){vehicle.values["temperature"]=22.0}
            capture("34-lamp-options","lamp-options")
            capture("35-lamp-edit","lamp-edit")
            capture("36-lamp-delete","lamp-delete")
            capture("37-equipment-fridge-removed","equipment"){s.removeEquipment(s.equipmentIds.first())}
            capture("38-plan-fridge-removed","plan")
            capture("39-equipment-lamp-removed","equipment"){
                s.addEquipment(Equipment("냉장고",60.0,6.0))
                s.equipmentIds.firstOrNull { s.fields["name$it"]=="외부 조명" }?.let { s.removeEquipment(it) }
            }
            capture("40-plan-lamp-removed","plan")
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }
}
