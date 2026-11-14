package com.example.pleos_demo

import android.graphics.Bitmap
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDateTime

class CampUxTest {
    @get:Rule val compose=createComposeRule()
    private class VehicleFake:VehicleSource {
        var writes=0
        var values=mutableMapOf("temperature" to 22.0,"light" to 70.0,"cabin" to 0.0)
        val controls=listOf(
            VehicleControl("temperature","온도",ControlKind.TEMPERATURE,16.0,32.0,1.0,true),
            VehicleControl("light","조명 밝기",ControlKind.LIGHT,0.0,100.0,1.0,true),
            VehicleControl("cabin","실내등",ControlKind.CABIN_LIGHT,0.0,2.0,1.0,true,mapOf(0.0 to "OFF",1.0 to "ON",2.0 to "AUTO")))
        override suspend fun read()=VehicleReading(soc=75.0,cabin=21.0,values=values.toMap(),controls=controls)
        override suspend fun parking()=ParkingState(true,true)
        override suspend fun readControl(id:String)=values[id]
        override suspend fun writeControl(id:String,value:Double){writes++;values[id]=value}
        override fun close(){}
    }
    @Test fun journeyEntryDateEquipmentStartAndApply() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val name="test_ux_${System.nanoTime()}"
        val vehicle=VehicleFake()
        val s=CampSession(context,vehicle,name)
        s.edit("scene.still","true")
        s.edit("capacity","100")
        runBlocking { s.refresh() }
        var case by mutableIntStateOf(0)
        var panel by mutableStateOf("")
        var journey by mutableStateOf(true)
        compose.setContent { key(case) { CampTheme { FigmaCampContent(s,panel,journey) } } }
        fun capture(name:String) {
            compose.waitForIdle()
            val dir=File(context.filesDir,"figma-verification").apply { mkdirs() }
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            File(dir,name+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) };bitmap.recycle()
        }
        try {
            compose.onNodeWithText("함께 이동").assertExists()
            compose.onNodeWithText("함께 출발하기").assertIsNotEnabled()
            // Camping controls are tested independently; group departure is covered by map flow tests.
            compose.runOnIdle { journey=false;case++ }
            compose.onNodeWithText("OFF",useUnmergedTree=true).assertExists()
            assertEquals("OFF",cabinLightStatus("OFF",70.0))
            assertEquals("ON · 70%",cabinLightStatus("ON",70.0))
            assertEquals("AUTO",cabinLightStatus("AUTO",70.0))
            assertEquals("상태 확인 필요",cabinLightStatus(null,70.0))
            val bounds=listOf("실내 온도 영역","실내 조명 영역","시트 영역","외부 전원 영역").map {
                compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
            }
            bounds.forEach { assertEquals(bounds.first().width,it.width,1f) }
            capture("41-ux-home")
            compose.runOnIdle { journey=false;panel="equipment";case++ }
            compose.onNodeWithText("인덕션").performClick()
            compose.onAllNodesWithText("인덕션").assertCountEquals(1)
            compose.runOnIdle { assertEquals(1,s.equipmentIds.count { s.fields["name$it"]=="인덕션" }) }
            capture("42-ux-equipment")
            compose.runOnIdle {
                s.edit("end",LocalDateTime.now().minusHours(1).format(CampSession.format))
                panel="start";case++
            }
            compose.onAllNodesWithText("캠핑 시작").filter(hasClickAction()).onFirst().assertIsNotEnabled()
            compose.onNodeWithText("종료 시간 설정").performClick()
            compose.onNodeWithContentDescription("종료 날짜 선택").performClick()
            capture("43-ux-calendar")
            val tomorrow=LocalDateTime.now().toLocalDate().plusDays(1)
            if(tomorrow.month!=LocalDateTime.now().plusHours(8).month)
                compose.onNodeWithContentDescription("다음 달").performScrollTo().performClick()
            compose.onNodeWithContentDescription("${tomorrow} 선택").performScrollTo().performClick()
            compose.onNodeWithContentDescription("종료 시각 선택").performScrollTo().performClick()
            compose.onNodeWithContentDescription("시 늘리기").performScrollTo().performClick()
            compose.onNodeWithContentDescription("시 늘리기").performScrollTo().performClick()
            capture("44-ux-time")
            compose.onNodeWithText("선택 적용").performClick()
            compose.onNodeWithText("현재 차량 설정을 저장합니다.").assertExists()
            compose.onAllNodesWithText("캠핑 시작").filter(hasClickAction()).onFirst().assertIsEnabled().performClick()
            compose.waitUntil(10000) { s.active }
            assertEquals(0,vehicle.writes)
            capture("45-ux-started")
            compose.onNodeWithText("요리 모드").performClick()
            compose.onNodeWithText("확인하고 적용을 눌러야 차량에 전송돼요.").performScrollTo()
            compose.onNodeWithText("확인하고 적용").assertIsEnabled()
            capture("46-ux-mode")
            compose.onNodeWithText("확인하고 적용").performClick()
            compose.waitUntil(15000) { vehicle.writes>0 && !s.busy }
            assertTrue(s.modeStatus=="적용 완료" || s.modeStatus=="차량값 확인 완료")
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }
}
