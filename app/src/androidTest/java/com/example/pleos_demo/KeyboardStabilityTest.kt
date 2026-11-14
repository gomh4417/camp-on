package com.example.pleos_demo

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KeyboardStabilityTest {
    @get:Rule val compose=createComposeRule()
    private val vehicle=object:VehicleSource {
        override suspend fun read()=VehicleReading()
        override suspend fun parking()=ParkingState(null,null)
        override suspend fun readControl(id:String):Double?=null
        override suspend fun writeControl(id:String,value:Double)=error("UI test must not actuate")
        override fun close(){}
    }
    @Test fun groupDialogKeepsWindowFocusAndDraftAcrossKeyboardResize() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_keyboard_${System.nanoTime()}"
        val session=CampSession(context,vehicle,name)
        var height by mutableIntStateOf(1100)
        compose.setContent { CampTheme {
            Box(Modifier.fillMaxWidth().height(height.dp)) { FigmaCampContent(session,startInJourney=true) }
        } }
        try {
            compose.onNodeWithText("그룹 연결",substring=false).performScrollTo().performClick()
            compose.onNodeWithText("공유 서버 주소").assertDoesNotExist()
            compose.onNodeWithText("새 그룹 만들기").assertIsSelected()
            compose.onNodeWithText("코드로 참여").performClick().assertIsSelected()
            compose.onNodeWithText("받은 초대 코드").assertExists()
            compose.onNodeWithText("새 그룹 만들기").performClick()
            val nickname=compose.onNode(hasSetTextAction() and hasText("내 닉네임"))
            nickname.performClick().performTextInput("캠핑")
            val dialogId=compose.onNode(isDialog()).fetchSemanticsNode().id
            // Includes intermediate animation sizes and repeated show/hide, not only end states.
            listOf(800,620,500,620,800,1100,500,1100).forEach { resized->
                compose.runOnIdle { height=resized }
                nickname.assertIsFocused().assertTextContains("캠핑")
                assertEquals("Dialog recreated while IME changed height",dialogId,compose.onNode(isDialog()).fetchSemanticsNode().id)
            }
            nickname.performTextInput(" 친구")
            nickname.assertTextContains("캠핑 친구")
            val code=compose.onNode(hasSetTextAction() and hasText("초대 코드 정하기"))
            code.performScrollTo().performClick().performTextInput("0427")
            code.assertTextContains("0427")
            nickname.performScrollTo().performClick().assertTextContains("캠핑 친구")
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }
    @Test fun equipmentInputKeepsFocusAndTextWhileHeightShrinks() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val name="test_equipment_keyboard_${System.nanoTime()}"
        val session=CampSession(context,vehicle,name)
        session.edit("scene.still","true")
        var height by mutableIntStateOf(1100)
        compose.setContent { CampTheme {
            Box(Modifier.fillMaxWidth().height(height.dp)) { FigmaCampContent(session,"edit",false) }
        } }
        try {
            val field=compose.onNodeWithContentDescription("장비 이름")
            field.performClick().performTextInput("전기포트")
            listOf(800,600,500,1100).forEach { resized->
                compose.runOnIdle { height=resized }
                field.assertIsFocused().assertTextContains("전기포트")
            }
            field.performTextInput(" 1")
            field.assertTextContains("전기포트 1")
        } finally { context.getSharedPreferences(name,0).edit().clear().commit() }
    }
}
