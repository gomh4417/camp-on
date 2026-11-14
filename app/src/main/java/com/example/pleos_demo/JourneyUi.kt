package com.example.pleos_demo

import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch
import org.json.JSONObject

@Composable private fun Avatar(id:String,modifier:Modifier=Modifier) {
    val drawable=when(id) { "princess"->R.drawable.mvp_profile_princess; "tralala"->R.drawable.mvp_profile_tralala;else->R.drawable.mvp_profile_tiramisu }
    Image(painterResource(drawable),null,modifier.size(56.dp).clip(CircleShape))
}
@Composable private fun VehicleRow(j:JourneySession,v:JSONObject,selected:Boolean=false,action:()->Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if(selected)Color.White else Color(0xFFF7F7F8))
        .border(if(selected)2.dp else 0.dp,if(selected)Color(0xFFCFDFFF) else Color.Transparent,RoundedCornerShape(16.dp))
        .clickable(onClick=action).padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        Avatar(v.optString("id"))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) {
            Text(v.label("name"),color=if(v.optString("id")==j.vehicleId)CampAccent else CampInk,style=MaterialTheme.typography.titleMedium)
            Caption(v.label("listStatus"))
            if(v.has("model"))Caption(v.label("model"))
            if(v.optString("source")=="참여자 직접 입력")Caption("입력 시각 · ${v.label("updatedAt")}")
        }
        Column(verticalArrangement=Arrangement.spacedBy(8.dp),horizontalAlignment=Alignment.End) {
            Text(if(v.optString("status")=="도착")"도착" else v.label("eta"))
            Caption(if(v.isNull("battery") || !v.has("battery"))"배터리 미입력" else "${v.label("battery")}% · ${v.label("source")}")
        }
    }
}

@Composable internal fun JourneyScreen(j:JourneySession,s:CampSession,wide:Boolean,onArrived:()->Unit) {
    val scope=rememberCoroutineScope()
    val context=LocalContext.current
    var screen by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableStateOf(j.vehicleId) }
    var requestId by rememberSaveable { mutableStateOf("") }
    var connection by rememberSaveable { mutableStateOf(false) }
    var createGroup by rememberSaveable { mutableStateOf(true) }
    var composing by rememberSaveable { mutableStateOf(false) }
    var inviteCode by rememberSaveable { mutableStateOf("") }
    var connectionName by rememberSaveable { mutableStateOf("") }
    var vehicleModel by rememberSaveable { mutableStateOf("") }
    var groupTitle by rememberSaveable { mutableStateOf("") }
    var memo by rememberSaveable { mutableStateOf("") }
    val vehicle=j.vehicles.firstOrNull { it.optString("id")==selected }?:j.myVehicle
    BackHandler(screen!=0) { screen=0 }
    LaunchedEffect(j.tripStage,j.fresh) {
        if(j.tripStage=="arrived" && j.fresh) {
            j.finishTripPresentation()
            onArrived()
        }
    }
    val openMap:()->Unit={screen=3}
    if(screen!=0) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        FilledTonalIconButton(onClick={screen=0},modifier=Modifier.size(56.dp)) { Text("×") }
        Spacer(Modifier.width(20.dp))
        Text(if(screen==1)"그룹 차량" else if(screen==3)"위치·경로" else "그룹 브리핑",Modifier.weight(1f),style=MaterialTheme.typography.headlineMedium)
        if(screen==2) SecondaryAction("메모 남기기") { composing=true }
    }
    Caption(if(!j.connected)"그룹 연결 전" else if(j.fresh)"그룹 참여 중 · ${j.vehicles.size}대" else j.error.ifBlank { "그룹 정보를 불러오고 있어요" })
    if(j.message.isNotBlank()) Text(j.message,color=CampAccent)
    when(screen) {
        3->IntegratedJourneyMap(j,s,true) { screen=0 }
        0->{
            val left:@Composable ColumnScope.()->Unit={
                Row(Modifier.fillMaxWidth().padding(vertical=22.dp),verticalAlignment=Alignment.CenterVertically) {
                    Text(if(!j.connected)"함께 떠날 그룹을 연결해요" else if(!j.fresh)"그룹 연결을 확인해 주세요" else "${j.groupName}\n"+when { j.tripStage=="moving"->"함께 목적지로 이동해요"; j.vehicles.isEmpty()->"참여 차량을 기다리고 있어요"; j.vehicles.all { it.optString("status")=="도착" }->"모두 도착했어요"; j.vehicles.any { it.optString("status")=="이동 중" }->"이동 중인 차량이 있어요"; else->"출발 전, 소식을 나눠요" },Modifier.weight(1f),style=MaterialTheme.typography.headlineLarge)
                    Image(painterResource(R.drawable.mvp_camping),"캠핑 텐트",Modifier.size(112.dp))
                }
                Panel(Modifier.fillMaxWidth()) {
                    Caption("참여 차량 · ${j.vehicles.size}대")
                    j.vehicles.forEach { v->VehicleRow(j,v) { selected=v.optString("id");screen=1 } }
                    if(j.vehicles.isEmpty()) Caption("연결 후 차량 현황이 표시돼요")
                }
                Panel(Modifier.fillMaxWidth().clickable { screen=2 }) {
                    Row(Modifier.fillMaxWidth()) { Caption("그룹 브리핑");Spacer(Modifier.weight(1f));Caption("● · ·") }
                    Text(j.state?.label("headline")?:"그룹 소식을 기다리고 있어요",style=MaterialTheme.typography.titleLarge)
                    Caption(j.state?.label("relativeTime")?:"")
                }
            }
            if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(24.dp),content=left)
                Column(Modifier.weight(1.05f)) { IntegratedJourneyMap(j,s,false,openMap) }
            } else { Column(verticalArrangement=Arrangement.spacedBy(24.dp),content=left);IntegratedJourneyMap(j,s,false,openMap) }
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                PrimaryAction(if(j.connected)"그룹 연결 관리" else "그룹 연결") { connection=true }
                PrimaryAction(if(j.tripStage=="moving")"함께 이동 중" else "함께 출발하기",j.canStartTrip && !s.busy) { j.tripStartRequested=true }
                SecondaryAction("그룹 메시지",j.connected) { screen=2 }
            }
        }
        1->{
            val list:@Composable ()->Unit={Panel(Modifier.fillMaxWidth().heightIn(min=650.dp)) {
                j.vehicles.forEach { v->VehicleRow(j,v,vehicle?.optString("id")==v.optString("id")) { selected=v.optString("id") } }
            }}
            val detail:@Composable ()->Unit={
                Panel(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement=Arrangement.spacedBy(24.dp),verticalAlignment=Alignment.CenterVertically) {
                        Avatar(vehicle?.optString("id").orEmpty(),Modifier.size(80.dp))
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Caption("현재 상태");Text(vehicle?.label("status")?:"—",style=MaterialTheme.typography.headlineMedium) }
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) { Caption("현재 위치");Text(vehicle?.label("location")?:"—");Caption("예상 도착");Text(vehicle?.label("eta")?:"—") }
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Caption("남은 거리");Text(vehicle?.label("distance")?:"—");Caption("배터리");Text("${vehicle?.label("battery")?:"—"}%") }
                    }
                }
                JourneyTodos(j,vehicle?.optString("id")?:j.vehicleId,Modifier.heightIn(min=430.dp))
            }
            if(wide) Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(.7f)) { list() }
                Column(Modifier.weight(1.5f),verticalArrangement=Arrangement.spacedBy(24.dp)) { detail() }
            } else { list();detail() }
        }
        2->{
            val requests:@Composable ()->Unit={
                Panel(Modifier.fillMaxWidth().heightIn(min=210.dp)) {
                    Text("그룹 요약",style=MaterialTheme.typography.titleLarge)
                    Text(j.state?.label("summary")?:"소식을 기다리고 있어요")
                }
                Panel(Modifier.fillMaxWidth().heightIn(min=420.dp)) {
                    Text("요청 내용",style=MaterialTheme.typography.titleLarge)
                    j.requests.forEach { r->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(if(requestId==r.optString("id"))Color(0xFFEAF2FF) else Color(0xFFF7F7F8))
                            .clickable { requestId=r.optString("id") }.padding(18.dp),
                            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                            Avatar(r.optString("fromVehicleId"));Text(r.label("item"))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        SecondaryAction("TO-DO에 추가",j.editable && j.requests.any { it.optString("id")==requestId },Modifier.weight(1f)) { scope.launch { j.addRequest(requestId) } }
                        SecondaryAction("삭제하기",j.editable && j.requests.any { it.optString("id")==requestId },Modifier.weight(1f)) { scope.launch { if(j.removeRequest(requestId))requestId="" } }
                    }
                }
            }
            val logs:@Composable ()->Unit={Panel(Modifier.fillMaxWidth().heightIn(min=654.dp)) {
                Text("전체 메시지",style=MaterialTheme.typography.titleLarge)
                if(j.memos.isEmpty())Caption("아직 메시지가 없어요")
                j.memos.reversed().forEach { m->
                    Row(Modifier.fillMaxWidth().padding(vertical=20.dp),horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                        Avatar(m.optString("vehicleId"))
                        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) { Caption("${j.name(m.optString("vehicleId"))} · ${m.label("time")}");Text(m.label("text")) }
                    }
                    HorizontalDivider(color=CampLine)
                }
            }}
            if(wide) Row(horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                Column(Modifier.weight(1.25f),verticalArrangement=Arrangement.spacedBy(24.dp)) { requests() }
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)) { logs() }
            } else { requests();logs() }
        }
    }
    if(composing) AlertDialog(onDismissRequest={composing=false},title={Text("그룹 메모")},text={
        OutlinedTextField(memo,{memo=it.take(500)},label={Text("가는 길의 소식·구매 요청")})
    },confirmButton={TextButton(onClick={scope.launch { if(j.memo(memo)){memo="";composing=false} }},enabled=j.editable&&memo.isNotBlank()) { Text("보내기") }},
        dismissButton={TextButton(onClick={composing=false}) { Text("닫기") }})
    if(connection) AlertDialog(onDismissRequest={connection=false},
        modifier=Modifier.shadow(4.dp,RoundedCornerShape(28.dp)),containerColor=Color(0xFFFEFEFE),tonalElevation=0.dp,
        title={Text("그룹 연결")},text={
        Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(!j.connected && j.inviteCode.isNotBlank()) SecondaryAction("내 그룹 다시 연결",!j.busy && !j.refreshing) { scope.launch { if(j.resumeMembership())connection=false } }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp),verticalAlignment=Alignment.CenterVertically) {
                Button(onClick={createGroup=true},enabled=!j.busy,
                    colors=ButtonDefaults.buttonColors(containerColor=FBlue,contentColor=Color.White),
                    modifier=Modifier.heightIn(min=56.dp).semantics { this.selected=createGroup }) { Text("새 그룹 만들기") }
                TextButton(onClick={createGroup=false},enabled=!j.busy,
                    colors=ButtonDefaults.textButtonColors(contentColor=FBlue),
                    modifier=Modifier.heightIn(min=56.dp).semantics { this.selected=!createGroup }) { Text("코드로 참여") }
            }
            OutlinedTextField(inviteCode,{if(it.length<=4 && it.all { digit->digit in '0'..'9' })inviteCode=it},
                label={Text(if(createGroup)"초대 코드 정하기" else "받은 초대 코드")},
                supportingText={Text("숫자 4자리 · 예: 0427")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            OutlinedTextField(connectionName,{connectionName=it.take(30)},label={Text("내 닉네임")})
            OutlinedTextField(vehicleModel,{vehicleModel=it.take(60)},label={Text("내 차량명")})
            if(createGroup)OutlinedTextField(groupTitle,{groupTitle=it.take(60)},label={Text("새 그룹 이름")})
            if(j.connected) {
                Text("초대 코드 · ${j.inviteCode}")
                SecondaryAction("이 화면 연결 해제",!j.busy && !j.refreshing) { j.disconnect();connection=false }
            }
            Caption(j.message)
        }
    },confirmButton={
        var connecting by remember { mutableStateOf(false) }
        Button(onClick={
            connecting=true
            scope.launch {
                try {
                    // Let an in-flight poll finish instead of dropping the user's tap.
                    while(j.refreshing) kotlinx.coroutines.delay(50)
                    if(j.connect(j.serverUrl)) {
                        if(j.join(inviteCode,connectionName,vehicleModel,groupTitle,createGroup))connection=false
                    }
                } finally { connecting=false }
            }
        },enabled=!j.busy && !connecting && inviteCode.length==4 && connectionName.isNotBlank() && vehicleModel.isNotBlank(),modifier=Modifier.heightIn(min=64.dp)) {
            Text(if(connecting)"연결 중…" else if(createGroup)"내 차량으로 그룹 만들기" else "내 차량으로 참여")
        }
    })
}

@Composable private fun JourneyMap(openOriginal:()->Unit) {
    val context=LocalContext.current
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val map=remember { WebView(context).apply {
        layoutParams=android.view.ViewGroup.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT,android.view.ViewGroup.LayoutParams.MATCH_PARENT)
        settings.javaScriptEnabled=true
        settings.allowFileAccess=false
        settings.allowContentAccess=false
        webViewClient=object:WebViewClient() {
            override fun onPageFinished(view:WebView,url:String) { loading=false }
            override fun onReceivedError(view:WebView,request:android.webkit.WebResourceRequest,error:android.webkit.WebResourceError) {
                if(request.isForMainFrame) { failed=true;loading=false }
            }
            override fun shouldOverrideUrlLoading(view:WebView,request:android.webkit.WebResourceRequest):Boolean =
                request.url.scheme!="https" || request.url.host!="www.openstreetmap.org"
        }
        loadUrl("https://www.openstreetmap.org/export/embed.html?bbox=128.82%2C37.72%2C128.96%2C37.84&layer=mapnik")
    }}
    LaunchedEffect(map,loading) {
        if(loading) { kotlinx.coroutines.delay(15000);if(loading){failed=true;loading=false} }
    }
    DisposableEffect(map) { onDispose { map.stopLoading();map.destroy() } }
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(700.dp).clip(RoundedCornerShape(24.dp)).background(Color.White)) {
            AndroidView(factory={map},modifier=Modifier.fillMaxSize())
            if(loading || failed) Column(Modifier.align(Alignment.Center).background(Color.White).padding(24.dp),
                horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(16.dp)) {
                if(loading)CircularProgressIndicator()
                Text(if(failed)"지도를 불러오지 못했어요" else "강릉 지도를 불러오고 있어요")
                if(failed)SecondaryAction("다시 불러오기") { failed=false;loading=true;map.reload() }
            }
        }
        Caption("강릉 주변 지도 · 차량 실시간 위치는 원본 지도에서 확인")
        SecondaryAction("차량 위치·경로 열기",modifier=Modifier.fillMaxWidth(),action=openOriginal)
    }
}
@Composable private fun JourneyTodos(j:JourneySession,owner:String,modifier:Modifier=Modifier) {
    val scope=rememberCoroutineScope()
    Panel(modifier.fillMaxWidth()) {
        Text("TO-DO",style=MaterialTheme.typography.titleLarge)
        val todos=j.todos.filter { it.optString("ownerVehicleId")==owner }
        if(todos.isEmpty())Caption("등록된 할 일이 없어요")
        todos.forEach { t->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFF7F7F8)).heightIn(min=72.dp)
                .toggleable(t.optBoolean("done"),enabled=j.editable,role=Role.Checkbox,onValueChange={done->scope.launch { j.completeTodo(t.optString("id"),done) }})
                .padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(t.optBoolean("done"),null,enabled=j.editable)
                Text(t.label("title"),Modifier.weight(1f))
            }
        }
    }
}
@Composable internal fun JourneyCarryover(j:JourneySession,onJourney:()->Unit) {
    if(j.camping) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(j.destination,Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            TextButton(onClick=onJourney) { Text("그룹 현황") }
        }
        if(!j.fresh)Caption("그룹 연결 확인 필요 · 할 일 변경 잠김")
        JourneyTodos(j,j.vehicleId)
    }
}
