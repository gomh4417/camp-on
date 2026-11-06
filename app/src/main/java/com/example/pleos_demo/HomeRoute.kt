package com.example.pleos_demo

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

internal data class Place(val name:String,val lat:Double,val lon:Double)
internal object RouteLookup {
    @Suppress("DEPRECATION")
    suspend fun places(context:Context,address:String):List<Place> = withContext(Dispatchers.IO) {
        check(Geocoder.isPresent()) { "주소 검색을 사용할 수 없어요" }
        Geocoder(context,Locale.KOREA).getFromLocationName(address,5).orEmpty().map {
            Place(it.getAddressLine(0)?:address,it.latitude,it.longitude)
        }
    }
    suspend fun route(from:Place,to:Place):JSONObject = withContext(Dispatchers.IO) {
        val url="https://router.project-osrm.org/route/v1/driving/${from.lon},${from.lat};${to.lon},${to.lat}?overview=full&geometries=geojson"
        val connection=URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout=10000;connection.readTimeout=15000
            connection.setRequestProperty("User-Agent","CAMP-ON/1.0")
            check(connection.responseCode==200) { "경로 서버에 연결하지 못했어요" }
            val response=JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            check(response.optString("code")=="Ok") { "자동차 경로를 찾지 못했어요" }
            response.getJSONArray("routes").getJSONObject(0).also {
                check(it.getDouble("distance").isFinite() && it.getDouble("distance")>=0)
            }
        } finally { connection.disconnect() }
    }
}
@Composable internal fun HomeRouteSettings(s:CampSession) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var draft by rememberSaveable { mutableStateOf(s.fields["homeAddress"].orEmpty()) }
    var candidates by remember { mutableStateOf<List<Place>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf("") }
    var manual by rememberSaveable { mutableStateOf(false) }
    Text("집까지, 알아서 계산해요",style=MaterialTheme.typography.titleLarge)
    Caption("집 주소를 한 번 저장하세요. 캠핑장에서 집까지 자동차 경로로 계산해요.")
    OutlinedTextField(draft,{draft=it},label={Text("집 주소 검색")},singleLine=true,modifier=Modifier.fillMaxWidth())
    SecondaryAction(if(searching)"검색 중" else "주소 찾기",!searching && draft.isNotBlank()) {
        scope.launch {
            searching=true;feedback="";candidates=emptyList()
            try { candidates=RouteLookup.places(context,draft);if(candidates.isEmpty())feedback="주소를 더 자세히 입력해 주세요" }
            catch(e:CancellationException) { throw e }
            catch(_:Exception) { feedback="주소 검색 연결을 확인해 주세요" }
            finally { searching=false }
        }
    }
    candidates.forEach { place->
        TextButton(onClick={
            s.edit("homeAddress",place.name);s.edit("homeLat",place.lat.toString());s.edit("homeLon",place.lon.toString())
            s.edit("destination","집");s.edit("routeKey","");candidates=emptyList();draft=place.name
        }) { Text("집으로 저장 · ${place.name}") }
    }
    s.fields["homeAddress"]?.takeIf { it.isNotBlank() }?.let { Caption("저장한 집 · $it") }
    OutlinedTextField(s.fields["campSite"].orEmpty(),{s.edit("campSite",it)},label={Text("출발할 캠핑장 · 주소 또는 이름")},
        modifier=Modifier.fillMaxWidth(),singleLine=true)
    Caption(s.fields["routeStatus"]?:"집을 저장하면 귀가 전력이 자동으로 계산돼요")
    if(feedback.isNotBlank()) Caption(feedback)
    Text("귀가 예상 ${s.budget?.home.fmt()} kWh · ${s.number("distance").fmt()} km",style=MaterialTheme.typography.titleMedium)
    Caption("전비 ${s.fields["efficiency"]} km/kWh 기준 추정 · 도로·날씨에 따라 달라져요")
    Caption("주소 검색과 OSRM 경로 조회에 입력 위치를 사용해요.")
    TextButton(onClick={manual=!manual}) { Text(if(manual)"상세 설정 접기" else "전비·직접 거리 설정") }
    if(manual) {
        OutlinedTextField(s.fields["efficiency"].orEmpty(),{s.edit("efficiency",it)},label={Text("평소 전비 · km/kWh")},singleLine=true)
        OutlinedTextField(s.fields["distance"].orEmpty(),{
            s.edit("distance",it);s.edit("routeStatus","직접 입력한 거리");s.edit("routeManual","true")
        },label={Text("직접 입력 · km")},singleLine=true)
        SecondaryAction("자동 경로 다시 계산",!s.fields["homeLat"].isNullOrBlank()) {
            s.edit("routeManual","false");s.edit("routeKey","")
        }
    }
}

/** Runs independently of the plan screen so saved home routes stay current after campsite changes. */
@Composable internal fun HomeRouteSync(s:CampSession) {
    val context=LocalContext.current
    val homeLat=s.fields["homeLat"];val homeLon=s.fields["homeLon"]
    val origin=s.fields["campSite"].orEmpty().trim()
    val key="$origin|$homeLat|$homeLon"
    val savedKey=s.fields["routeKey"]
    val manual=s.fields["routeManual"]=="true"
    LaunchedEffect(key,savedKey,manual) {
        if(manual || homeLat==null || homeLon==null || origin.isBlank() || savedKey==key) return@LaunchedEffect
        s.edit("distance","");s.edit("routeStatus","집까지 경로를 계산하고 있어요")
        try {
            kotlinx.coroutines.delay(900)
            val places=RouteLookup.places(context,origin)
            check(places.size==1) { "캠핑장 주소를 상세히 입력해 주세요" }
            val from=places.single()
            val home=Place(s.fields["homeAddress"].orEmpty(),homeLat.toDouble(),homeLon.toDouble())
            val route=RouteLookup.route(from,home)
            s.edit("distance",(route.getDouble("distance")/1000).toString())
            s.edit("routeGeometry",route.getJSONObject("geometry").toString())
            s.edit("routeAt",System.currentTimeMillis().toString())
            s.edit("routeStatus","OSRM 자동차 경로 · "+java.text.SimpleDateFormat("MM/dd HH:mm",Locale.KOREA).format(java.util.Date()))
            s.edit("routeKey",key)
        } catch(e:CancellationException) { throw e }
        catch(e:Exception) { s.edit("routeStatus",e.message?.takeIf { it=="캠핑장 주소를 상세히 입력해 주세요" }?:"경로 조회 실패 · 주소를 확인하거나 거리를 직접 입력하세요") }
    }
}
