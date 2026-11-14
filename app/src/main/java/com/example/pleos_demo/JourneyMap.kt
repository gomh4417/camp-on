package com.example.pleos_demo

import android.graphics.BitmapFactory
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

@Composable internal fun IntegratedJourneyMap(j:JourneySession,s:CampSession,expanded:Boolean,onExpand:()->Unit) {
    val context=LocalContext.current
    var loaded by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val avatars=remember {
        listOf(R.drawable.mvp_profile_princess,R.drawable.mvp_profile_tralala,R.drawable.mvp_profile_tiramisu).map { id->
            val bitmap=BitmapFactory.decodeResource(context.resources,id)
            val bytes=ByteArrayOutputStream().use { out->bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);out.toByteArray() }
            bitmap.recycle()
            "data:image/png;base64,"+Base64.encodeToString(bytes,Base64.NO_WRAP)
        }
    }
    val html=remember { mapHtml(avatars,context.assets.open("journey-simulation.js").bufferedReader().use { it.readText() }) }
    val map=remember { WebView(context).apply {
        layoutParams=android.view.ViewGroup.LayoutParams(-1,-1)
        settings.javaScriptEnabled=true;settings.allowFileAccess=false;settings.allowContentAccess=false
        webViewClient=object:WebViewClient() {
            override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest)=true
            override fun onPageFinished(view:WebView,url:String) {
                view.evaluateJavascript("typeof L !== 'undefined' && !!window.mapReady") { result->
                    loaded=result=="true";failed=!loaded
                }
            }
            override fun onReceivedError(view:WebView,request:android.webkit.WebResourceRequest,error:android.webkit.WebResourceError) {
                if(request.isForMainFrame) failed=true
            }
        }
        loadDataWithBaseURL("https://camp-on.local/",html,"text/html","UTF-8",null)
    } }
    DisposableEffect(map) { onDispose { map.stopLoading();map.destroy() } }
    LaunchedEffect(loaded,map) {
        if(loaded) {
            map.evaluateJavascript("window.restoreGroupTrip(${j.tripSnapshot})",null)
            while(true) {
                map.evaluateJavascript("window.groupTripSnapshot()") { value ->
                    if(value!=null && value!="null")j.updateTripSnapshot(value)
                }
                kotlinx.coroutines.delay(200)
            }
        }
    }
    LaunchedEffect(loaded,j.tripStartRequested) {
        if(loaded && j.tripStartRequested) {
            j.tripStartRequested=false
            map.evaluateJavascript("window.startGroupTrip()",null)
        }
    }
    LaunchedEffect(loaded,j.receivedAt,j.fresh,s.fields["routeGeometry"]) {
        if(loaded) {
            val members=JSONArray()
            if(j.fresh)j.vehicles.forEach { v->members.put(JSONObject().put("id",v.optString("id")).put("name",v.label("name"))) }
            map.evaluateJavascript("window.updateTripMembers($members)",null)
            val positions=JSONArray()
            if(j.fresh) j.vehicles.forEachIndexed { index,v->
                val lat=v.optDouble("latitude");val lon=v.optDouble("longitude")
                val time=v.optLong("locationTimestamp")
                if(lat in -90.0..90.0 && lon in -180.0..180.0 && System.currentTimeMillis()-time in 0..30_000)
                    positions.put(JSONObject().put("lat",lat).put("lon",lon).put("name",v.label("name")).put("index",index%3))
            }
            map.evaluateJavascript("window.updateVehicles($positions)",null)
            val geometry=runCatching { JSONObject(s.fields["routeGeometry"].orEmpty()) }.getOrNull()
            if(geometry!=null && s.fields["routeManual"]!="true")
                map.evaluateJavascript("window.homeRoute($geometry)",null)
        }
    }
    LaunchedEffect(map) { kotlinx.coroutines.delay(15000);if(!loaded)failed=true }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(if(expanded)850.dp else 700.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)) {
            AndroidView(factory={map},modifier=Modifier.fillMaxSize())
            if(!loaded) Column(Modifier.align(Alignment.Center).background(Color.White).padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
                if(!failed)CircularProgressIndicator()
                Text(if(failed)"지도 연결을 확인해 주세요" else "지도를 불러오고 있어요")
                if(failed)SecondaryAction("다시 불러오기") {
                    failed=false;loaded=false;map.loadDataWithBaseURL("https://camp-on.local/",html,"text/html","UTF-8",null)
                }
            }
        }

        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SecondaryAction(if(expanded)"지도 줄이기" else "위치·경로 크게 보기",modifier=Modifier.weight(1f),action=onExpand)

        }
    }
}

private fun mapHtml(avatars:List<String>,simulation:String)="""<!doctype html>
<html lang="ko"><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>html,body,#map{margin:0;height:100%;font-family:sans-serif}.avatar{border:3px solid white;border-radius:50%;width:48px;height:48px;box-shadow:0 2px 6px #777}.note{background:white;border-radius:12px;padding:12px;color:#191f28;font-size:16px}.trip-controls{background:#fff;border-radius:16px;padding:16px;width:320px;max-width:50vw;color:#26364b;box-sizing:border-box}.trip-controls p{font-size:15px;min-height:42px;margin:8px 0}.trip-controls button{min-height:56px;border:0;border-radius:12px;background:#eaf1ff;color:#215bb5;font-size:15px;padding:8px;cursor:pointer}.trip-controls button:disabled{opacity:.45}.trip-controls button:focus-visible{outline:3px solid #246beb}.trip-grid{display:grid;grid-template-columns:1fr 1fr;gap:8px}.trip-controls label{display:flex;align-items:center;min-height:56px;font-size:16px}.trip-controls input{width:24px;height:24px}.trip-controls small{display:block;margin-top:8px;font-size:12px}.trip-controls #trip-confirm{width:100%;margin-top:10px;background:#2468ee;color:white}.trip-controls{background:#fefefe;box-shadow:0 3px 12px #18243512}.trip-profile{display:flex;align-items:center;flex-direction:column}.trip-profile img{width:44px;height:44px;border:3px solid #246beb;border-radius:50%;background:white}.trip-profile span{background:white;color:#1e395e;border:1px solid #c7d9f5;border-radius:8px;padding:4px 8px;font-size:14px;max-width:110px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.trip-profile:after{content:"";border:7px solid transparent;border-top-color:#246beb}</style>
</head><body><div id="map"></div><script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<script>
const map=L.map('map',{zoomControl:true}).setView([37.77,128.9],12);
L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map);
const avatars=${JSONArray(avatars)}, live=L.layerGroup().addTo(map);
const icon=i=>L.divIcon({html:'<img class="avatar" src="'+avatars[i]+'">',iconSize:[54,54],iconAnchor:[27,27],className:''});
let home=null;
const status=L.control({position:'bottomleft'});
status.onAdd=()=>{const el=L.DomUtil.create('div','note');el.id='status';el.textContent='위치 수신 대기';return el};status.addTo(map);
const tell=t=>document.getElementById('status').textContent=t;
window.updateVehicles=items=>{live.clearLayers();items.forEach(v=>L.marker([v.lat,v.lon],{icon:icon(v.index)}).bindTooltip(document.createTextNode(v.name)).addTo(live));if(!window.tripSimulationActive)tell(items.length?'위치 수신 '+items.length+'대':'위치 수신 대기')};
let homeKey='';
window.homeRoute=g=>{const key=JSON.stringify(g);if(key===homeKey)return;homeKey=key;if(home)map.removeLayer(home);home=L.geoJSON(g,{style:{color:'#246beb',weight:5}});if(!window.tripSimulationActive){home.addTo(map);map.fitBounds(home.getBounds(),{padding:[35,35]})}};
$simulation
window.mapReady=true;
</script></body></html>"""
