package com.example.pleos_demo

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Requires the isolated companion fixture started by verify-group-departure.mjs. */
class GroupDepartureTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun messagesAndMapDepartureReachCampingWithoutVehicleWrites() {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val context=instrumentation.targetContext
        val prefix="group_flow_${System.nanoTime()}"
        val host=JourneySession(context,prefix+"host")
        val phone=JourneySession(context,prefix+"phone")
        val third=JourneySession(context,prefix+"third")
        val source=object:VehicleSource {
            override suspend fun read()=VehicleReading()
            override suspend fun parking()=ParkingState(null,null)
            override suspend fun readControl(id:String):Double?=null
            override suspend fun writeControl(id:String,value:Double)=error("Group flow must not actuate")
            override fun close(){}
        }
        val session=CampSession(context,source,prefix+"camp")
        var arrived by mutableStateOf(false)
        fun capture(name:String) {
            compose.waitForIdle()
            instrumentation.waitForIdleSync()
            android.os.SystemClock.sleep(400) // Wait for the native/WebView frame to reach SurfaceFlinger.
            val dir=File(context.filesDir,"figma-verification").apply { mkdirs() }
            val bitmap=instrumentation.uiAutomation.takeScreenshot()
            File(dir,name+".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        try {
            runBlocking {
                listOf(host,phone,third).forEach { assertTrue(it.connect("http://10.0.2.2:18789")) }
                assertTrue(host.join("0427","대표","아이오닉 5","함께 캠핑",true))
                assertTrue(phone.join("0427","친구","EV3","",false))
                assertTrue(third.join("0427","동행","EV9","",false))
                assertTrue(phone.memo("중간에 휴게소에 들러요"))
                host.refresh()
            }
            compose.setContent { CampTheme {
                LaunchedEffect(Unit) { while(true) { host.refresh();delay(1000) } }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    if(arrived)Text("캠핑 준비")
                    else JourneyScreen(host,session,true) { arrived=true }
                }
            } }
            compose.onNodeWithText("함께 출발하기").assertIsNotEnabled()
            compose.onNodeWithText("그룹 메시지").performScrollTo().performClick()
            compose.onNodeWithText("전체 메시지").assertExists()
            compose.onAllNodesWithText("중간에 휴게소에 들러요",substring=true).onLast().assertExists()
            capture("47-group-messages")
            compose.onNodeWithText("×").performClick()
            fun findWeb(view:View):WebView? {
                if(view is WebView)return view
                if(view is ViewGroup)for(i in 0 until view.childCount)findWeb(view.getChildAt(i))?.let { return it }
                return null
            }
            var web:WebView?=null
            compose.waitUntil(15000) {
                instrumentation.runOnMainSync { web=findWeb(compose.activity.window.decorView) }
                web!=null
            }
            val ready=AtomicReference(false)
            compose.waitUntil(20000) {
                instrumentation.runOnMainSync { web!!.evaluateJavascript("!!window.mapReady") { ready.set(it=="true") } }
                ready.get()
            }
            // mapReady precedes onPageFinished; wait for the native restore/member synchronization too.
            compose.waitUntil(15000) {
                runCatching { JSONObject(host.tripSnapshot).getJSONArray("members").length()==3 }.getOrDefault(false)
            }
            fun js(value:String) { instrumentation.runOnMainSync { web!!.evaluateJavascript(value,null) } }
            val members=JSONArray(host.vehicles.map { JSONObject().put("id",it.optString("id")).put("name",it.label("name")) })
            val snapshot=JSONObject("""{"from":[37.77,128.9],"to":[37.78,128.91],"route":{"points":[[37.77,128.9],[37.78,128.91]],"lengths":[0,1400],"total":1400},"elapsed":0,"stage":"ready"}""").put("members",members)
            js("window.restoreGroupTrip($snapshot)")
            try { compose.waitUntil(5000) { host.canStartTrip } }
            catch(e:Exception) {
                capture("49-group-flow-failure")
                throw AssertionError("stage=${host.tripStage}, fresh=${host.fresh}, connected=${host.connected}, error=${host.error}, snapshot=${host.tripSnapshot}",e)
            }
            compose.onNodeWithText("함께 출발하기").performScrollTo().assertIsEnabled().performClick()
            compose.waitUntil(5000) { host.tripStage=="moving" }
            capture("48-group-moving")
            assertFalse(arrived)
            // Finish the same JS clock near the final participant; exercise WebView -> Compose arrival.
            js("window.restoreGroupTrip(Object.assign(window.groupTripSnapshot(),{elapsed:62300}))")
            compose.waitUntil(5000) { arrived }
            compose.onNodeWithText("캠핑 준비").assertExists()
            assertFalse(session.active)
            assertTrue(host.vehicles.all { it.optString("status")=="출발 대기" })
            assertEquals("친",profileInitial(" 친구"))
            assertEquals("😀",profileInitial("😀동행"))
            // The completed outbound geometry survives a new JourneySession instance.
            val restored=JourneySession(context,prefix+"host")
            runBlocking { assertTrue(restored.resumeMembership()) }
            assertTrue(restored.beginReturnTrip())
            val reverse=JSONObject(restored.tripSnapshot)
            assertEquals(37.78,reverse.getJSONArray("from").getDouble(0),.000001)
            assertEquals(37.77,reverse.getJSONArray("to").getDouble(0),.000001)
            assertEquals(0.0,reverse.getJSONObject("route").getJSONArray("lengths").getDouble(0),.000001)
            assertFalse(restored.beginReturnTrip()) // A second end tap must not restart the route.
            compose.runOnIdle { assertTrue(host.beginReturnTrip());arrived=false }
            compose.waitForIdle()
            val returning=AtomicReference(false)
            compose.waitUntil(20000) {
                instrumentation.runOnMainSync {
                    web=findWeb(compose.activity.window.decorView)
                    web?.evaluateJavascript("!!window.groupTripSnapshot && window.groupTripSnapshot().returning") { returning.set(it=="true") }
                }
                returning.get()
            }
            js("window.restoreGroupTrip(Object.assign(window.groupTripSnapshot(),{elapsed:30000}))")
            compose.waitUntil(5000) { host.tripReturning && JSONObject(host.tripSnapshot).optDouble("elapsed")>=30000 }
            capture("50-group-return")
            js("window.restoreGroupTrip(Object.assign(window.groupTripSnapshot(),{elapsed:62300}))")
            compose.waitUntil(5000) { host.tripStage=="returned" }
            assertFalse(arrived) // Return arrival must not reopen camping.
            compose.onNodeWithText("출발지에 도착했어요",substring=true).assertExists()
            assertTrue(host.vehicles.all { it.optString("status")=="출발 대기" })
        } finally {
            listOf("host","phone","third","camp").forEach { context.getSharedPreferences(prefix+it,0).edit().clear().commit() }
        }
    }
}
