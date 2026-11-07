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
    val html=remember { mapHtml(avatars) }
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
    LaunchedEffect(loaded,j.receivedAt,s.fields["routeGeometry"]) {
        if(loaded) {
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
        Caption("위치 수신 차량만 표시 · 집 경로는 저장 후 표시")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SecondaryAction(if(expanded)"지도 줄이기" else "위치·경로 크게 보기",modifier=Modifier.weight(1f),action=onExpand)

        }
    }
}

private fun mapHtml(avatars:List<String>)="""<!doctype html>
<html lang="ko"><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<style>html,body,#map{margin:0;height:100%;font-family:sans-serif}.avatar{border:3px solid white;border-radius:50%;width:48px;height:48px;box-shadow:0 2px 6px #777}.note{background:white;border-radius:12px;padding:12px;color:#191f28;font-size:16px}</style>
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
window.updateVehicles=items=>{live.clearLayers();items.forEach(v=>L.marker([v.lat,v.lon],{icon:icon(v.index)}).bindTooltip(document.createTextNode(v.name)).addTo(live));tell(items.length?'위치 수신 '+items.length+'대':'위치 수신 대기')};
window.homeRoute=g=>{if(home)map.removeLayer(home);home=L.geoJSON(g,{style:{color:'#246beb',weight:5}}).addTo(map);map.fitBounds(home.getBounds(),{padding:[35,35]})};
window.mapReady=true;
</script></body></html>"""
