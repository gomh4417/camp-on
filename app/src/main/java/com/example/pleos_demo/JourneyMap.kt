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
    val prefs=remember { context.getSharedPreferences("camp_on_map",0) }
    var demoStart by remember { mutableLongStateOf(prefs.getLong("demoStart",0)) }
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
    LaunchedEffect(loaded,demoStart) { if(loaded)map.evaluateJavascript("window.startDemo($demoStart)",null) }
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
        Caption(if(demoStart>0)"강릉 경로 시연 · 차량 실제 위치와 별개" else "위치 수신 차량만 표시 · 집 경로는 저장 후 표시")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            SecondaryAction(if(expanded)"지도 줄이기" else "위치·경로 크게 보기",modifier=Modifier.weight(1f),action=onExpand)
            SecondaryAction(if(demoStart>0)"시연 끝내기" else "3대 이동 시연",loaded,Modifier.weight(1f)) {
                demoStart=if(demoStart>0)0 else System.currentTimeMillis()
                prefs.edit().putLong("demoStart",demoStart).apply()
            }
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
const avatars=${JSONArray(avatars)}, live=L.layerGroup().addTo(map), demo=L.layerGroup().addTo(map);
const icon=i=>L.divIcon({html:'<img class="avatar" src="'+avatars[i]+'">',iconSize:[54,54],iconAnchor:[27,27],className:''});
let start=0,run=0,routes=[],home=null;
const status=L.control({position:'bottomleft'});
status.onAdd=()=>{const el=L.DomUtil.create('div','note');el.id='status';el.textContent='위치 수신 대기';return el};status.addTo(map);
const tell=t=>document.getElementById('status').textContent=t;
window.updateVehicles=items=>{live.clearLayers();items.forEach(v=>L.marker([v.lat,v.lon],{icon:icon(v.index)}).bindTooltip(document.createTextNode(v.name)).addTo(live));if(!start)tell(items.length?'위치 수신 '+items.length+'대':'위치 수신 대기')};
window.homeRoute=g=>{if(home)map.removeLayer(home);home=L.geoJSON(g,{style:{color:'#246beb',weight:5}}).addTo(map);if(!start)map.fitBounds(home.getBounds(),{padding:[35,35]})};
const reduced=matchMedia('(prefers-reduced-motion: reduce)').matches;
window.startDemo=async value=>{
 start=value;const token=++run;demo.clearLayers();if(!start){tell('위치 수신 대기');return}
 tell('시연 경로를 불러오는 중');
 try{
 if(!routes.length){
 const from=[[128.866,37.752],[128.886,37.755],[128.915,37.775]],to=[128.945,37.797];
 routes=await Promise.all(from.map(async p=>{
 const response=await fetch('https://router.project-osrm.org/route/v1/driving/'+p.join(',')+';'+to.join(',')+'?overview=full&geometries=geojson');
 if(!response.ok)throw Error('route');
 const json=await response.json();if(json.code!=='Ok')throw Error('route');
 return json.routes[0].geometry.coordinates.map(c=>[c[1],c[0]]);
 }));
 }
 if(token!==run)return;
 const markers=routes.map((r,i)=>{L.polyline(r,{color:['#246beb','#f28f67','#c39969'][i],weight:5}).addTo(demo);return L.marker(r[0],{icon:icon(i)}).addTo(demo)});
 map.fitBounds(L.latLngBounds(routes.flat()),{padding:[45,45]});
 const lengths=routes.map(r=>{let total=0;return r.map((p,i)=>{if(i)total+=map.distance(r[i-1],p);return total})});
 let last=0;
 function frame(now){
 if(token!==run)return;
 if(!reduced || now-last>1000){
 last=now;
 routes.forEach((r,i)=>{
 const progress=Math.max(0,Math.min(1,(Date.now()-start)/(120000+i*25000)));
 const dist=lengths[i],target=dist[dist.length-1]*progress;
 let n=dist.findIndex(d=>d>=target);if(n<1)n=1;
 const f=(target-dist[n-1])/Math.max(.001,dist[n]-dist[n-1]);
 markers[i].setLatLng([r[n-1][0]+(r[n][0]-r[n-1][0])*f,r[n-1][1]+(r[n][1]-r[n-1][1])*f]);
 });
 tell(Date.now()-start>=170000?'시연 차량 3대 도착':'3대 이동 시연 · 실제 GPS 아님');
 }
 if(Date.now()-start<170000)requestAnimationFrame(frame);
 }
 requestAnimationFrame(frame);
 }catch(e){if(token===run)tell('경로 연결 실패 · 시연을 다시 시작해 주세요')}
};
window.mapReady=true;
</script></body></html>"""
