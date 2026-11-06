package com.example.pleos_demo

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun Double?.fmt()=this?.takeIf { it.isFinite() }?.let { String.format(Locale.KOREA,"%.1f",it) }?:"—"
private fun stamp(t:Long)=if(t<=0) "수신 대기" else SimpleDateFormat("HH:mm",Locale.KOREA).format(Date(t))
internal fun controlValue(c:VehicleControl,v:Double?):String=when {
    v==null->"확인 필요"
    c.choices.isNotEmpty()->c.choices[v]?:"확인 필요"
    c.kind in listOf(ControlKind.POWER,ControlKind.AC,ControlKind.RECIRCULATION,ControlKind.AUTO,ControlKind.DUAL,ControlKind.AIR_CLEANER,ControlKind.DEFROSTER)->if(v>0) "켜짐" else "꺼짐"
    c.kind==ControlKind.SEAT_TEMPERATURE->when { v<0->"통풍 ${(-v).toInt()}단";v>0->"열선 ${v.toInt()}단";else->"꺼짐" }
    c.kind==ControlKind.SEAT_POSITION->"${v.toInt()} · 위치"
    else->v.fmt()+c.kind.unit
}

@Composable fun CampApp() {
    val context=LocalContext.current
    val preferences=remember { context.getSharedPreferences("camp_on_preferences",0) }
    var demo by rememberSaveable { mutableStateOf(preferences.getBoolean("demo",false)) }
    key(demo) {
        val source=remember {
            if(demo) DemoVehicleSource(context) else runCatching<VehicleSource> { PleosVehicleSource(context) }.getOrElse {
                object:VehicleSource {
                    override suspend fun read():VehicleReading=error("차량 연결 실패")
                    override suspend fun parking()=ParkingState(null,null)
                    override suspend fun readControl(id:String):Double?=null
                    override suspend fun writeControl(id:String,value:Double) { error("차량 연결 실패") }
                    override fun close() {}
                }
            }
        }
        val s=remember { CampSession(context,source,if(demo) "camp_on_demo_v3" else "camp_on_v2") }
        DisposableEffect(s) { onDispose { s.save();source.close() } }
        CampTheme {
            CampContent(s) { enabled->
                preferences.edit().putBoolean("demo",enabled).apply();demo=enabled
            }
        }
    }
}
@Composable private fun CampContent(s:CampSession,switchSource:(Boolean)->Unit) {
    val scope=rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    var settings by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var editor by rememberSaveable { mutableIntStateOf(-1) }
    var confirm by rememberSaveable { mutableStateOf("") }
    var presetDialog by rememberSaveable { mutableStateOf(false) }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(s,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { while(true) { s.refresh();delay(5000) } }
        launch { while(true) { s.now=System.currentTimeMillis();s.refreshParking();delay(2000) } }
    } }
    BackHandler(s.minimized || page!=0) { if(s.minimized)s.updateMinimized(false) else page=0 }
    Surface(Modifier.fillMaxSize(),color=CampBackground) {
        BoxWithConstraints(Modifier.fillMaxSize().safeDrawingPadding()) {
            val wide=maxWidth>=1080.dp
            val padding=if(wide) 36.dp else 20.dp
            Column(Modifier.fillMaxSize().padding(horizontal=padding,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text("CAMP:ON",style=MaterialTheme.typography.headlineMedium)
                    if(wide) {
                        Spacer(Modifier.weight(1f))
                        Caption(if(s.parkingState.allowsControl) "P  주차 중" else if(s.parkingState.allows(ControlKind.TEMPERATURE)) "공조 조절 가능" else "차량 조작 잠김")
                        Caption(if(s.fresh && s.connected) "SDK 수신 중" else "연결 확인")
                    } else Spacer(Modifier.weight(1f))
                    Text("${s.soc.fmt()}%",style=MaterialTheme.typography.titleLarge)
                    TextButton(onClick={help=true},modifier=Modifier.heightIn(min=56.dp)) { Text("사용법") }
                    TextButton(onClick={settings=true},modifier=Modifier.heightIn(min=56.dp)) {
                        Text(if(s.isDemo) "시연 · 설정" else "Pleos · 설정")
                    }
                }
                if(!s.minimized) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        listOf("캠핑 현황","에너지 계획","종료·귀가").forEachIndexed { i,label->
                            FilterChip(selected=page==i,onClick={page=i},label={Text(label)},modifier=Modifier.heightIn(min=56.dp),
                                shape=RoundedCornerShape(14.dp))
                        }
                        if(s.refreshing || s.busy) CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp),strokeWidth=2.dp)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    WarningCard(s,{page=1;s.updateMinimized(false)},{page=2;s.updateMinimized(false)})
                    if(s.message.isNotBlank()) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text(s.message,Modifier.weight(1f),color=CampAccent)
                        TextButton(onClick={s.message=""}) { Text("닫기") }
                    }
                    if(s.minimized) SleepScreen(s) else when(page) {
                        0->Dashboard(s,wide,{confirm="start"},{editor=it},{presetDialog=true},{page=1},{page=3},{confirm="apply"})
                        1->PlanScreen(s,wide,{page=3})
                        2->DepartureScreen(s,wide,{confirm="end"},{confirm="manual"},{page=1})
                        3->EnergyDetail(s) { page=1 }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Caption(if(s.active) "${s.mode.name} · ${s.modeStatus}" else "캠핑 준비")
                    Caption("${s.reading.source.takeIf { s.reading.time>0 }?:"수신 대기"} · ${stamp(s.reading.time)}")
                }
            }
        }
    }
    if(help) PleosGuide { help=false }
    if(editor>=0) ControlEditor(s,editor) { editor=-1 }
    if(presetDialog) PresetDialog(s) { presetDialog=false }
    if(settings) SettingsDialog(s,{settings=false},switchSource) { scenario->
        (s.source as? DemoVehicleSource)?.scenario(scenario)
        scope.launch { s.refresh() }
    }
    if(confirm.isNotEmpty()) {
        val kind=confirm
        Modal(if(kind=="controls") "차량 설정 적용" else if(kind=="apply") "${s.selected.label} 모드 적용" else if(kind=="start") "캠핑을 시작할까요?" else if(kind=="manual") "직접 복원 확인" else "캠핑 종료·복원",
            {confirm=""}) {
            when(kind) {
                "apply","controls"->{
                    Caption("현재값 → 요청값")
                    s.plannedControls.filter { kind!="controls" || it.id in s.overrides }.forEach { c->
                        LabelValue(c.label,"${controlValue(c,s.current[c.id])} → ${controlValue(c,s.target(c))}")
                    }
                    Caption("변경 후 남은 소비 예상 ${s.previewBudget?.consumption.fmt()} kWh · 추정")
                    Caption("항목별 차량 응답을 확인한 뒤 결과를 표시합니다.")
                }
                "start"->{ Text("진입 전 차량 설정을 저장합니다.");Caption("종료 예정 ${s.fields["end"]}") }
                "manual"->Text("차량에서 남은 설정을 직접 복원했거나, 현재 설정 유지 여부를 확인했나요?")
                else->{
                    Text("앱에서 바꾼 설정을 진입 전 값으로 복원합니다.")
                    Caption("직접 바꾼 값은 유지하며, 실패한 항목은 다시 확인할 수 있습니다.")
                    s.written.keys.forEach { id->Caption("${s.reading.controls.find { it.id==id }?.label?:id} → ${s.initial[id].fmt()}") }
                }
            }
            PrimaryAction(if(kind=="apply" || kind=="controls") "확인하고 적용" else if(kind=="start") "캠핑 시작" else if(kind=="manual") "직접 확인 완료" else "종료·복원",
                enabled=if(kind=="manual") !s.busy else s.canControl && (kind !in listOf("apply","controls") || s.active),
                modifier=Modifier.fillMaxWidth()) {
                confirm=""
                scope.launch { when(kind) {
                    "apply"->s.applyMode()
                    "controls"->s.applyMode(onlyEdited=true)
                    "start"->s.start()
                    "manual"->s.acknowledgeManualRestore()
                    else->s.endAndRestore()
                } }
            }
        }
    }
}
@Composable private fun Split(wide:Boolean,left:@Composable ()->Unit,right:@Composable ()->Unit) {
    if(wide) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(24.dp)) {
        Column(Modifier.weight(1.65f),verticalArrangement=Arrangement.spacedBy(20.dp)) { left() }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(20.dp)) { right() }
    } else Column(verticalArrangement=Arrangement.spacedBy(20.dp)) { left();right() }
}
@Composable private fun Dashboard(s:CampSession,wide:Boolean,start:()->Unit,edit:(Int)->Unit,presets:()->Unit,plan:()->Unit,detail:()->Unit,apply:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Column(Modifier.weight(1f)) {
            Caption("오늘도, 편안한 캠핑")
            Text(if(s.active) s.mode.subtitle else "우리만의 쉼을 시작해요",style=MaterialTheme.typography.headlineLarge)
        }
        if(!s.active) PrimaryAction("캠핑 시작",s.canControl,action=start)
        else if(s.mode==CampMode.SLEEP) SecondaryAction("화면 최소화") { s.updateMinimized(true) }
    }
    Split(wide,left={
        CabinPanel(s,edit,presets)
        Panel(Modifier.fillMaxWidth()) {
            Caption("지금은 어떤 시간인가요?")
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                CampMode.visible.forEach { m->
                    OutlinedButton(onClick={s.selectMode(m)},enabled=!s.busy,
                        modifier=Modifier.weight(1f).heightIn(min=84.dp),shape=RoundedCornerShape(16.dp),
                        colors=ButtonDefaults.outlinedButtonColors(containerColor=if(s.selected==m) CampAccent else Color.Transparent,
                            contentColor=if(s.selected==m) CampBackground else CampInk)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            Text(m.label,style=MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${s.selected.label} 설정",style=MaterialTheme.typography.titleMedium)
                    Caption("적용 후 소비 예상 ${s.previewBudget?.consumption.fmt()} kWh")
                }
                PrimaryAction("모드 적용",s.active && s.canControl,action=apply)
            }
        }
    },right={
        Panel(Modifier.fillMaxWidth(),emphasis=true) {
            Caption("오늘 쓸 수 있는 여유")
            Text("계획 후 여유",style=MaterialTheme.typography.titleLarge)
            Row(verticalAlignment=Alignment.Bottom) {
                Text(s.budget?.margin.fmt(),style=MaterialTheme.typography.displayMedium)
                Text(" kWh",Modifier.padding(bottom=10.dp),color=CampMuted)
            }
            Allocation(s.budget)
            LabelValue("캠핑 가용",s.budget?.available.fmt()+" kWh")
            LabelValue("남은 사용 계획",s.budget?.consumption.fmt()+" kWh")
            HorizontalDivider(color=CampLine)
            LabelValue("종료 예정",s.fields["end"].orEmpty().takeLast(5))
            LabelValue("남은 시간",s.remainingHours.fmt()+" h")
            PrimaryAction("에너지 계획",modifier=Modifier.fillMaxWidth(),action=plan)
            TextButton(onClick=detail,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text("에너지 상세·변화 추이  ↗") }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("내가 저장한 쉼",style=MaterialTheme.typography.titleLarge)
            Caption(if(s.namedPresets.isEmpty()) "지금의 차량 설정을 이름과 함께 저장하세요." else "${s.namedPresets.size}개의 프리셋")
            s.namedPresets.take(3).forEach { p->
                OutlinedButton(onClick={s.loadPreset(p)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) { Text(p.name) }
            }
            SecondaryAction("프리셋 관리",modifier=Modifier.fillMaxWidth(),action=presets)
        }
    })
}
@Composable private fun CabinPanel(s:CampSession,edit:(Int)->Unit,presets:()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(CampSurface)) {
        CabinPlan(s,s.reading.controls.filter { it.kind in listOf(ControlKind.TEMPERATURE,ControlKind.SEAT_TEMPERATURE) },Modifier.fillMaxWidth())
        Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf(ControlKind.TEMPERATURE,ControlKind.LIGHT,ControlKind.SEAT_POSITION).forEachIndexed { index,kind->
                    val c=s.reading.controls.firstOrNull { it.kind==kind }
                    Card(onClick={edit(index)},modifier=Modifier.weight(1f).semantics { contentDescription=listOf("공조 조절","조명 조절","시트 조절")[index] },shape=RoundedCornerShape(18.dp),
                        colors=CardDefaults.cardColors(containerColor=CampBackground)) {
                        Box(Modifier.fillMaxWidth().height(104.dp)) {
                            Image(painterResource(if(index==2)R.drawable.cabin_flat_seat else R.drawable.cabin_flat_shell),null,
                                Modifier.fillMaxSize().padding(12.dp),contentScale=ContentScale.Fit)
                        }
                        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(listOf("공조 조절","조명 조절","시트 조절")[index],style=MaterialTheme.typography.titleMedium)
                            Caption(c?.let { control->controlValue(control,s.current[control.id].takeIf { s.fresh && s.reading.isFresh(control.id,s.now) }) }?:"미지원")
                        }
                    }
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Caption("공기와 빛도 내 취향으로 맞춰요")
                Spacer(Modifier.weight(1f))
                TextButton(onClick=presets,enabled=s.fresh && !s.busy) { Text("현재 설정 저장") }
            }
        }
    }
}
@Composable private fun PresetDialog(s:CampSession,dismiss:()->Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    Modal("나만의 프리셋",dismiss) {
        Caption("현재 반영된 차량 설정을 저장합니다. 불러온 뒤 적용을 눌러야 차량이 변경됩니다.")
        OutlinedTextField(name,{name=it.take(30)},label={Text("프리셋 이름")},singleLine=true,modifier=Modifier.fillMaxWidth())
        PrimaryAction("현재 설정 저장",name.isNotBlank() && s.fresh && !s.busy,Modifier.fillMaxWidth()) { s.namedPreset(name);name="" }
        s.namedPresets.toList().forEach { p->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={s.loadPreset(p);dismiss()},enabled=!s.busy,modifier=Modifier.weight(1f).heightIn(min=56.dp)) { Text("${p.name} · ${p.mode.label}") }
                TextButton(onClick={s.deletePreset(p)},enabled=!s.busy) { Text("삭제") }
            }
        }
    }
}
@Composable private fun PlanScreen(s:CampSession,wide:Boolean,detail:()->Unit) {
    Text("얼마나 더 쉬어볼까요?",style=MaterialTheme.typography.headlineLarge)
    Split(wide,left={
        Panel(Modifier.fillMaxWidth()) {
            Caption("01  캠핑 일정")
            Field(s,"end","종료 예정 · yyyy-MM-dd HH:mm",valid=s.endValid())
            LabelValue("남은 시간",s.remainingHours.fmt()+" h")
            Caption("현재 환경 기준 · ${s.mode.label} / ${s.modeStatus}")
        }
        Panel(Modifier.fillMaxWidth()) {
            Caption("02  외부 장비")
            s.equipmentIds.toList().forEach { id->
                Field(s,"name$id","장비 이름")
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Field(s,"watts$id","전력 · W",Modifier.weight(1f),valid=validNumber(s,"watts$id"))
                    Field(s,"hours$id","사용 · h",Modifier.weight(1f),valid=validNumber(s,"hours$id"))
                    Field(s,"quantity$id","수량",Modifier.weight(1f),valid=validNumber(s,"quantity$id",true))
                }
                if((s.number("duty$id")?:1.0)!=1.0) Field(s,"duty$id","사용률 · 0~1",valid=(s.number("duty$id")?:-1.0) in 0.0..1.0)
                TextButton(onClick={s.removeEquipment(id)}) { Text("장비 삭제") }
                HorizontalDivider(color=CampLine)
            }
            SecondaryAction("+ 장비 추가",modifier=Modifier.fillMaxWidth()) { s.addEquipment() }
            Caption("입력 시간은 앞으로 사용할 시간입니다. 종료 예정 시각까지의 소비만 합산합니다.")
        }
        Panel(Modifier.fillMaxWidth()) {
            Caption("03  귀가 조건")
            Field(s,"destination","귀가 목적지")
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Field(s,"distance","거리 · km",Modifier.weight(1f),valid=validNumber(s,"distance"))
                Field(s,"efficiency","전비 · km/kWh",Modifier.weight(1f),valid=validNumber(s,"efficiency",positive=true))
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Field(s,"capacity","용량 보정 · kWh (비우면 차량값)",Modifier.weight(1f),valid=s.fields["capacity"].isNullOrBlank() || validNumber(s,"capacity",positive=true))
                Field(s,"reserve","보호 예비량 · kWh",Modifier.weight(1f),valid=validNumber(s,"reserve"))
            }
            Caption("계산 용량 ${s.capacityKwh.fmt()} kWh · "+if(s.fields["capacity"].isNullOrBlank()) "차량 공칭 사용 가능 용량 / 신차 기준" else "사용자 입력")
            Field(s,"baseKw","기본 부하 · kW",valid=validNumber(s,"baseKw"))
        }
    },right={
        Panel(Modifier.fillMaxWidth(),emphasis=true) {
            Text("남은 소비 예상",style=MaterialTheme.typography.titleLarge)
            Text(s.budget?.consumption.fmt()+" kWh",style=MaterialTheme.typography.displaySmall)
            LabelValue("외부 장비",s.externalKwh.fmt()+" kWh")
            LabelValue("차량 공조",s.climateKwh.fmt()+" kWh")
            LabelValue("기본 부하",s.baseKwh.fmt()+" kWh")
            HorizontalDivider(color=CampLine)
            LabelValue("계획 후 여유",s.budget?.margin.fmt()+" kWh")
            Allocation(s.budget)
            PrimaryAction("계획 저장",s.budget!=null && s.endValid(),Modifier.fillMaxWidth()) { s.save();s.message="계획을 저장했습니다" }
            TextButton(onClick=detail,modifier=Modifier.heightIn(min=56.dp)) { Text("계산 근거·변화 추이") }
        }
        Panel(Modifier.fillMaxWidth()) {
            Text("계산 기준",style=MaterialTheme.typography.titleLarge)
            Caption("조회: 배터리 SOC와 현재 차량 설정")
            Caption("입력: 용량·거리·전비·장비·기본 부하")
            Caption("추정: 공조 소비와 종료 예상 잔여량")
            Caption("장비 W ÷ 1000 × 남은 h × 수량 ÷ 0.9")
            Caption("공조 계수는 실내·설정 온도 차이와 풍량을 반영한 계획용 가정입니다.")
            Caption("현재 잔량에 반영된 과거 소비는 다시 더하지 않습니다.")
        }
    })
}
private fun validNumber(s:CampSession,key:String,integer:Boolean=false,positive:Boolean=false):Boolean {
    val v=s.number(key)?:return false
    return v.isFinite() && v>=0 && (!positive || v>0) && (!integer || (v>=1 && v%1==0.0))
}
@Composable private fun Field(s:CampSession,key:String,label:String,modifier:Modifier=Modifier,valid:Boolean=true) {
    OutlinedTextField(s.fields[key].orEmpty(),{s.edit(key,it)},modifier.fillMaxWidth(),label={Text(label)},singleLine=true,
        isError=!valid,supportingText=if(!valid) ({Text("입력값 확인")}) else null,shape=RoundedCornerShape(12.dp))
}
@Composable private fun LabelValue(label:String,value:String) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp),verticalAlignment=Alignment.CenterVertically) {
        Text(label,Modifier.weight(1f),color=CampMuted)
        Text(value,style=MaterialTheme.typography.titleMedium)
    }
}
@Composable private fun Allocation(b:Budget?) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)).background(CampLine)) {
            if(b!=null && b.current>0) {
                val home=b.home.coerceAtMost(b.current)
                val reserve=b.reserve.coerceAtMost((b.current-home).coerceAtLeast(0.0))
                listOf(home to Color(0xFF71819A),reserve to Color(0xFF9AA3AF),b.available to CampAccent).forEach { (v,color)->
                    if(v>0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(color))
                }
            }
        }
        Caption("귀가 ${b?.home.fmt()}  ·  예비 ${b?.reserve.fmt()}  ·  캠핑 ${b?.available.fmt()} kWh")
    }
}
@Composable private fun WarningCard(s:CampSession,plan:()->Unit,departure:()->Unit) {
    val b=s.budget
    val title=when {
        !s.fresh->if(s.refreshing) "차량 정보를 불러오는 중" else "현재 에너지 판단 불가"
        b==null->"계산에 필요한 정보를 확인하세요"
        b.protectedShortfall>0->"현재 귀가 확보량 부족 · ${b.protectedShortfall.fmt()} kWh"
        b.overBudget>0->"남은 계획 초과 · ${b.overBudget.fmt()} kWh"
        b.margin<1.0->"캠핑 에너지 여유가 적습니다"
        else->null
    }
    if(title!=null) Column(Modifier.fillMaxWidth().background(Color(0xFFFFF3DF),RoundedCornerShape(18.dp)).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text(title,color=CampWarning,style=MaterialTheme.typography.titleMedium)
        if(!s.fresh) Caption("최근 수신 ${stamp(s.reading.time)} · 연결이 갱신되면 다시 계산합니다.")
        else if(b!=null) Caption("귀가 자체 부족 ${b.homeShortfall.fmt()} kWh · 계획 지속 시 부족 ${b.continuedShortfall.fmt()} kWh · ${stamp(s.now)} 갱신")
        else Caption("용량·귀가 조건·종료 시각·장비 입력 또는 차량 수신값이 필요합니다.")
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            TextButton(onClick=plan) { Text("계획 줄이기") }
            TextButton(onClick=departure) { Text("귀가·충전 확인") }
        }
    }
    if(!s.parkingState.allowsControl && s.active) Caption("일괄 설정과 시트 조절은 주차 후 이용해요.")
}
@Composable private fun EnergyDetail(s:CampSession,back:()->Unit) {
    Text("에너지의 흐름",style=MaterialTheme.typography.headlineLarge)
    Panel(Modifier.fillMaxWidth()) {
        LabelValue("현재 잔여 에너지",s.budget?.current.fmt()+" kWh")
        Allocation(s.budget)
        LabelValue("남은 계획",s.budget?.consumption.fmt()+" kWh")
        LabelValue("계획 후 여유",s.budget?.margin.fmt()+" kWh")
        BatteryChart(s)
        Caption("실선: 수신 기록 · 점선: 남은 계획 기반 예상 · 기록이 없는 구간은 연결하지 않습니다.")
        Caption(if(s.isDemo) "시연 데이터의 수신 기록입니다." else "SOC는 Pleos 조회값, 에너지와 미래 SOC는 추정값입니다.")
        LabelValue("조건 버전",s.conditionVersion.toString())
        SecondaryAction("사용 계획 수정",action=back)
    }
}
@Composable private fun BatteryChart(s:CampSession) {
    val points=s.history.toList()
    val end=s.endMillis
    val capacity=s.capacityKwh
    val predicted=if(capacity!=null && capacity>0) s.budget?.departure?.div(capacity)?.times(100) else null
    val first=points.firstOrNull()?.time?:s.now
    val last=maxOf(end?:s.now,s.now,first+1)
    Canvas(Modifier.fillMaxWidth().height(220.dp).semantics { contentDescription="배터리 수신 기록 ${points.size}개, 현재 ${s.soc.fmt()}%, 종료 예상 ${predicted.fmt()}%" }) {
        fun x(t:Long)=((t-first).toDouble()/(last-first)).toFloat()*size.width
        fun y(v:Double)=(1-v.coerceIn(0.0,100.0)/100).toFloat()*(size.height-20)+10
        listOf(0.0,25.0,50.0,75.0,100.0).forEach { v->drawLine(CampLine,Offset(0f,y(v)),Offset(size.width,y(v)),1f) }
        points.zipWithNext().forEach { (a,b)->
            if(b.time-a.time in 1..90_000 && a.source==b.source) drawLine(CampAccent,Offset(x(a.time),y(a.soc)),Offset(x(b.time),y(b.soc)),3f)
        }
        points.forEach { p->drawCircle(CampAccent,4f,Offset(x(p.time),y(p.soc))) }
        if(predicted!=null && end!=null && end>s.now && s.soc!=null) {
            drawLine(CampAccent,Offset(x(s.now),y(s.soc!!)),Offset(x(end),y(predicted)),3f,pathEffect=PathEffect.dashPathEffect(floatArrayOf(12f,10f)))
        }
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Caption("${stamp(first)} 기록 시작")
        Caption("현재 ${s.soc.fmt()}%")
        Caption("${stamp(end?:0)} 예상 ${predicted.fmt()}%")
    }
}
@Composable private fun SleepScreen(s:CampSession) {
    Column(Modifier.fillMaxWidth().padding(vertical=64.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Caption("SLEEP · 취침 중")
        Text(s.soc.fmt()+"%",style=MaterialTheme.typography.displayLarge)
        Text("편안한 밤 되세요",style=MaterialTheme.typography.headlineLarge)
        Caption("종료 예정 ${s.fields["end"]} · 최근 수신 ${stamp(s.reading.time)}")
        Caption(if(s.budget?.continuedShortfall==0.0) "계획 기준 귀가 에너지 확보 예상" else "귀가 에너지를 확인하세요")
        PrimaryAction("화면 열기") { s.updateMinimized(false) }
    }
}
@Composable private fun DepartureScreen(s:CampSession,wide:Boolean,end:()->Unit,manual:()->Unit,plan:()->Unit) {
    val context=LocalContext.current
    Text("돌아가는 길도 편안하게",style=MaterialTheme.typography.headlineLarge)
    Split(wide,left={
        Panel(Modifier.fillMaxWidth()) {
            Caption("01  캠핑 종료")
            Text("머물기 전의 설정으로",style=MaterialTheme.typography.headlineMedium)
            Caption("앱에서 변경한 설정만 복원합니다.")
            if(s.written.isEmpty()) Caption("복원이 필요한 설정이 없습니다.")
            s.written.toMap().forEach { (id,_)->
                LabelValue(s.reading.controls.find { it.id==id }?.label?:id,s.results[id]?:"복원 대기")
            }
            s.results.filterValues { it=="복원 확인" }.forEach { (id,result)->
                LabelValue(s.reading.controls.find { it.id==id }?.label?:id,result)
            }
            PrimaryAction(if(s.busy) "처리 중" else "종료·복원 확인",(s.active || s.written.isNotEmpty()) && s.canControl,Modifier.fillMaxWidth(),end)
            if(s.written.isNotEmpty()) TextButton(onClick=manual,enabled=!s.busy) { Text("차량에서 직접 복원 완료") }
        }
        Panel(Modifier.fillMaxWidth()) {
            Caption("02  출발 전 확인")
            listOf("도어·윈도우 닫힘","외부 조명 정리","V2L 장비 정리","주변과 적재물 확인").forEach { label->
                Row(Modifier.fillMaxWidth().heightIn(min=56.dp),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(s.checks[label]==true,{s.checkItem(label,it)})
                    Text(label,Modifier.weight(1f))
                }
            }
        }
    },right={
        Panel(Modifier.fillMaxWidth(),emphasis=true) {
            Caption("03  귀가 에너지")
            Text(s.fields["destination"].orEmpty().ifBlank { "목적지 미입력" },style=MaterialTheme.typography.headlineMedium)
            Caption("${s.fields["distance"]} km · ${s.fields["efficiency"]} km/kWh")
            LabelValue("종료 예상 잔여",s.budget?.departure.fmt()+" kWh")
            LabelValue("귀가 소요",s.budget?.home.fmt()+" kWh")
            LabelValue("도착 예상",s.budget?.arrival.fmt()+" kWh")
            LabelValue("보호 예비",s.budget?.reserve.fmt()+" kWh")
            HorizontalDivider(color=CampLine)
            LabelValue("추가 충전 필요",s.budget?.continuedShortfall.fmt()+" kWh")
            Caption("입력 조건에 따른 예상이며, 도달 가능성을 보장하지 않습니다.")
            SecondaryAction("목적지·계획 수정",modifier=Modifier.fillMaxWidth(),action=plan)
            PrimaryAction("CAMP FLOW 귀가 준비",s.budget!=null,Modifier.fillMaxWidth()) { s.prepareHandoff() }
        }
        if(s.handoff.isNotEmpty()) Panel(Modifier.fillMaxWidth()) {
            Text("귀가 준비 요약",style=MaterialTheme.typography.titleLarge)
            Caption(s.handoff)
            Caption(s.handoffStatus)
            SecondaryAction("요약 전달",modifier=Modifier.fillMaxWidth()) {
                val text=s.prepareHandoff()
                if(text!=null) {
                    val result=runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                        type="text/plain";putExtra(Intent.EXTRA_TEXT,text)
                    },"귀가 요약 전달")) }
                    s.handoffStatus=if(result.isSuccess) "공유 화면 요청 · 경로 설정 미확인" else "전달 실패 · 요약 보관됨"
                    s.save()
                }
            }
        }
    })
}
@Composable private fun SettingsDialog(s:CampSession,dismiss:()->Unit,switchSource:(Boolean)->Unit,scenario:(String)->Unit) {
    Modal("연결·시연 설정",dismiss) {
        Text(if(s.isDemo) "공모전 시연 모드" else "Pleos 차량 연결",style=MaterialTheme.typography.titleLarge)
        Caption("시연 설정과 실제 차량 세션은 별도로 저장됩니다.")
        if(s.isDemo) {
            listOf("일반","에너지 부족","일부 실패","주행 중","수신 지연","연결 끊김").forEach { value->
                OutlinedButton(onClick={scenario(value)},enabled=!s.busy,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)) {
                    Text(if((s.source as? DemoVehicleSource)?.scenario==value) "✓ $value" else value)
                }
            }
        }
        SecondaryAction(if(s.isDemo) "실제 Pleos 연결로 전환" else "시연 모드로 전환",
            !s.active && !s.busy && s.written.isEmpty(),Modifier.fillMaxWidth()) { dismiss();switchSource(!s.isDemo) }
        if(s.active || s.written.isNotEmpty()) Caption("캠핑 종료·복원 후 연결 모드를 전환할 수 있습니다.")
    }
}
@Composable private fun Modal(title:String,dismiss:()->Unit,content:@Composable ColumnScope.()->Unit) {
    Dialog(onDismissRequest=dismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.widthIn(max=760.dp).fillMaxWidth(.92f).fillMaxHeight(.9f),shape=RoundedCornerShape(24.dp),color=CampSurface) {
            Column(Modifier.padding(28.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Text(title,Modifier.weight(1f),style=MaterialTheme.typography.headlineSmall)
                    TextButton(onClick=dismiss,modifier=Modifier.heightIn(min=56.dp)) { Text("닫기") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp),content=content)
            }
        }
    }
}
