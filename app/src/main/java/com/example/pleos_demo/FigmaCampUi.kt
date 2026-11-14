package com.example.pleos_demo

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val FBackground=Color(0xFFF4F7FC)
private val FBlue=Color(0xFF2468EE)
private val FPale=Color(0xFFE6EEFF)
private val FInk=Color(0xFF182435)
private val FMuted=Color(0xFF7B8798)
private fun Modifier.slot(x:Int,y:Int,w:Int,h:Int)=offset(x.dp,y.dp).size(w.dp,h.dp)
@Composable private fun FText(value:String,x:Int,y:Int,w:Int,size:Int=24,color:Color=FInk,bold:Boolean=false,align:TextAlign=TextAlign.Start) {
    Text(value,Modifier.offset(x.dp,y.dp).width(w.dp),fontSize=size.sp,lineHeight=(size*1.25).sp,
        fontWeight=if(bold)FontWeight.ExtraBold else FontWeight.Normal,color=color,textAlign=align)
}
@Composable private fun FButton(label:String,x:Int,y:Int,w:Int,h:Int=68,primary:Boolean=false,enabled:Boolean=true,action:()->Unit) {
    Box(Modifier.slot(x,y,w,h).clip(RoundedCornerShape(18.dp))
        .background(if(primary)FBlue else FPale).clickable(enabled=enabled,role=Role.Button,onClick=action)
        .semantics { if(!enabled)disabled() },contentAlignment=Alignment.Center) {
        Text(label,color=(if(primary)Color.White else FBlue).copy(alpha=if(enabled)1f else .45f),
            fontSize=24.sp,lineHeight=30.sp,fontWeight=FontWeight.Medium,textAlign=TextAlign.Center,
            modifier=Modifier.padding(horizontal=12.dp))
    }
}
@Composable private fun FIcon(resource:Int,x:Int,y:Int,size:Int=35,tint:Color?=null) {
    Image(painterResource(resource),null,Modifier.slot(x,y,size,size),colorFilter=tint?.let { ColorFilter.tint(it) })
}
@Composable private fun FSurface(x:Int,y:Int,w:Int,h:Int,color:Color=Color.White,content:@Composable BoxScope.()->Unit={}) {
    Box(Modifier.slot(x,y,w,h).clip(RoundedCornerShape(28.dp)).background(color),content=content)
}
@Composable private fun FToggle(checked:Boolean,enabled:Boolean,x:Int,y:Int,label:String,change:(Boolean)->Unit) {
    Box(Modifier.slot(x,y,90,64).semantics { contentDescription=label;stateDescription=if(checked)"켜짐" else "꺼짐" }
        .toggleable(checked,enabled=enabled,role=Role.Switch,onValueChange=change),contentAlignment=Alignment.Center) {
        Box(Modifier.size(78.dp,44.dp).clip(RoundedCornerShape(22.dp))
            .background((if(checked)FBlue else Color(0xFFD3DAE5)).copy(alpha=if(enabled)1f else .45f))) {
            Box(Modifier.offset(if(checked)39.dp else 5.dp,5.dp).size(34.dp).background(Color.White,RoundedCornerShape(17.dp)))
        }
    }
}

/** Preserve the Figma 1600x900 composition within the app's own inset-safe window. */
@Composable private fun DesignViewport(content:@Composable ()->Unit) {
    val fontScale=LocalDensity.current.fontScale
    Layout(content={CompositionLocalProvider(LocalDensity provides Density(1f,fontScale)) {
        Box(Modifier.requiredSize(1600.dp,900.dp).clip(RoundedCornerShape(30.dp)).background(FBackground)) { content() }
    }},modifier=Modifier.fillMaxSize()) { children,constraints->
        val width=constraints.maxWidth;val height=constraints.maxHeight
        val scale=minOf(width/1600f,height/900f)
        val child=children.single().measure(Constraints.fixed(1600,900))
        layout(width,height) { child.placeWithLayer((width-1600)/2,(height-900)/2) { scaleX=scale;scaleY=scale } }
    }
}
internal fun CampSession.observed(kind:ControlKind):Pair<VehicleControl,Double?>? =
    reading.controls.firstOrNull { it.kind==kind }?.let { control -> control to current[control.id].takeIf { fresh && reading.isFresh(control.id,now) } }
private fun Double?.degree()=this?.let { if(it%1.0==0.0)it.toInt().toString() else it.fmt() }?:"—"

@Composable internal fun FigmaCampContent(s:CampSession,initialPanel:String="") {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    val journey=remember { JourneySession(context) }
    var panel by rememberSaveable { mutableStateOf(initialPanel) }
    var editId by rememberSaveable { mutableStateOf(s.equipmentIds.firstOrNull()) }
    var equipmentName by rememberSaveable { mutableStateOf("") }
    var equipmentWatts by rememberSaveable { mutableStateOf("") }
    var equipmentHours by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableIntStateOf(-1) }
    var journeyPage by rememberSaveable { mutableStateOf(false) }
    var still by remember { mutableStateOf(s.fields["scene.still"]=="true") }
    var dateDraft by rememberSaveable { mutableStateOf("") }
    var placeDraft by rememberSaveable { mutableStateOf("") }
    var distanceDraft by rememberSaveable { mutableStateOf("") }
    var reserveDraft by rememberSaveable { mutableStateOf("") }
    var efficiencyDraft by rememberSaveable { mutableStateOf("") }
    HomeRouteSync(s)
    LaunchedEffect(s,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { while(true) { s.refresh();delay(5000) } }
        launch { while(true) { journey.refresh();delay(1500) } }
        launch { while(true) { s.now=System.currentTimeMillis();s.refreshParking();delay(1000) } }
    } }
    BackHandler(panel.isNotEmpty() || journeyPage) { if(panel.isNotEmpty())panel="" else journeyPage=false }
    fun openDate() { dateDraft=s.fields["end"].orEmpty();panel="time" }
    fun editEquipment(id:String?) {
        editId=id;equipmentName=if(id==null)"" else s.fields["name$id"].orEmpty()
        equipmentWatts=if(id==null)"" else s.fields["watts$id"].orEmpty()
        equipmentHours=if(id==null)"1" else s.fields["hours$id"].orEmpty();panel="edit"
    }
    val temp=s.observed(ControlKind.TEMPERATURE)
    val light=s.observed(ControlKind.LIGHT)
    val auto=s.observed(ControlKind.AUTO)
    val fan=s.observed(ControlKind.FAN)
    val cabin=s.reading.cabin.takeIf { s.fresh && s.reading.isFresh("실내 온도",s.now) }
    fun send(control:Pair<VehicleControl,Double?>?,value:Double) {
        control?.let { scope.launch { s.applyDirect(it.first,value) } }
    }
    fun stepTemp(direction:Int) { temp?.let { (c,v)->if(v!=null)send(temp,v+direction*c.step) } }
    val clock=Instant.ofEpochMilli(s.now).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
    val end=runCatching { LocalDateTime.parse(s.fields["end"],CampSession.format) }.getOrNull()
    val endDate=end?.toLocalDate()?.toString()?:"날짜 선택"
    val endTime=end?.format(DateTimeFormatter.ofPattern("HH : mm"))?:"시각 선택"
    val remaining=s.remainingHours?.let { "${it.toInt()}시간 ${((it%1)*60).toInt()}분" }?:"확인 필요"
    val b=s.budget
    val boundTemp=temp?.let { (c,v)->v!=null && s.canAdjust(c) }==true
    Surface(Modifier.fillMaxSize().safeDrawingPadding(),color=FBackground) {
        DesignViewport {
            Box(Modifier.slot(0,0,1600,82).background(Color.White))
            Box(Modifier.slot(28,10,210,64).clickable(role=Role.Button){panel="menu"}.semantics { contentDescription="CAMP:ON 메뉴" })
            FText("CAMP:ON",44,25,200,28,bold=true)
            FText(when(s.parkingState.parked){true->"P";false->"—";null->"—"},267,25,40,28,FBlue,true)
            Box(Modifier.slot(321,27,1,28).background(Color(0xFFDCE3EE)))
            FText(if(s.active)"캠핑 중" else "캠핑 준비",344,29,180,22,bold=true)
            FText("외부 —",1130,29,145,22,FMuted)
            FIcon(R.drawable.figma_battery,1290,26,30)
            FText(s.soc.degree()+"%",1330,27,100,24,bold=true)
            FText(clock,1470,29,100,22,bold=true)
            if(journeyPage) {
                Column(Modifier.slot(40,100,1520,770).verticalScroll(rememberScrollState())) {
                    SecondaryAction("‹ 캠핑으로") { journeyPage=false }
                    JourneyScreen(journey,s,true) { journeyPage=false;panel="setup" }
                }
            } else {
                FText("캠핑의 밤",48,115,600,36,bold=true)
                FText("차 안의 편안함을, 캠핑장까지",49,163,650,21,FMuted)
                FSurface(876,115,676,72,Color(0xFFE8EDF5)) {
                    CampMode.visible.forEachIndexed { i,mode->
                        val selected=s.selected==mode
                        Box(Modifier.slot(6+221*i,6,221,60).clip(RoundedCornerShape(16.dp))
                            .background(if(selected)FBlue else Color.Transparent)
                            .clickable(enabled=!s.busy,role=Role.Tab){s.selectMode(mode);panel="mode"}
                            .semantics { this.selected=selected }) {
                            FIcon(when(mode){CampMode.COOK->R.drawable.figma_vector_cook;CampMode.SLEEP->R.drawable.figma_vector_moon;else->R.drawable.figma_vector_sun},
                                31,18,25,if(selected)Color.White else FMuted)
                            FText(mode.label.removeSuffix(" 모드"),75,14,130,25,if(selected)Color.White else FMuted,true)
                        }
                    }
                }
                FSurface(40,226,960,376,FPale) {
                    CampScene(s.now,still,Modifier.fillMaxSize())
                    Box(Modifier.offset(24.dp,22.dp).height(46.dp).widthIn(min=224.dp,max=520.dp)
                        .background(Color.White,RoundedCornerShape(23.dp)).padding(horizontal=18.dp,vertical=9.dp)) {
                        Text(if(s.active)"●  ${s.mode.label} · ${s.modeStatus}" else "●  캠핑 준비",
                            color=FBlue,fontSize=20.sp,lineHeight=25.sp,fontWeight=FontWeight.Medium)
                    }
                }
                FSurface(1024,226,536,376) {
                    FText("오늘의 에너지 여유",28,26,480,22,FMuted)
                    FText(b?.margin.fmt(),28,71,330,68,bold=true)
                    FText("kWh",if(kotlin.math.abs(b?.margin?:0.0)>=100)263 else 188,112,130,26,FMuted)
                    Box(Modifier.slot(28,171,480,12).clip(RoundedCornerShape(6.dp)).background(Color(0xFFE5ECF6))) {
                        if(b!=null && b.current>0)Row(Modifier.fillMaxSize()) {
                            val protected=((b.home+b.reserve)/b.current).toFloat().coerceIn(0f,1f)
                            if(protected>0)Box(Modifier.fillMaxWidth(protected).fillMaxHeight().background(Color(0xFF96A8C1)))
                            Box(Modifier.weight(1f).fillMaxHeight().background(FBlue))
                        }
                    }
                    FText(when { b==null->"차량·계획 정보 확인 필요";b.continuedShortfall>0->"귀가·예비 전력 확인 필요";else->"귀가·예비 전력 확보 예상" },28,204,480,22,bold=true)
                    FButton("에너지 계획 설정  →",28,277,480,70,true) { panel=if(s.fields["plan.ready"]=="true")"plan" else "setup" }
                }
                FSurface(40,626,1520,200) {
                    Box(Modifier.slot(0,0,340,200).clickable(role=Role.Button){panel="climate"}.semantics { contentDescription="실내 공조 설정" })
                    FText("실내 온도",28,25,300,23,bold=true)
                    FText(temp?.second.degree()+"°",28,70,220,70,bold=true)
                    FText(if(auto?.second==1.0)"자동 공조" else if(auto?.second==0.0)"수동 공조" else "차량값 확인 필요",30,154,300,19,FMuted)
                    FArrowButton(R.drawable.figma_vector_up,"온도 올리기",356,30,boundTemp && temp!!.second!!<temp.first.max){stepTemp(1)}
                    FArrowButton(R.drawable.figma_vector_down,"온도 내리기",356,108,boundTemp && temp!!.second!!>temp.first.min){stepTemp(-1)}
                    listOf(490,832,1162).forEach { Box(Modifier.slot(it,30,1,140).background(Color(0xFFDCE3EE))) }
                    val tiles=listOf(Triple("실내 조명",R.drawable.figma_sun,"light"),Triple("시트",R.drawable.figma_seat,"seat"),Triple("외부 전원",R.drawable.figma_vector_plug,"plan"))
                    tiles.forEachIndexed { i,(label,icon,target)->
                        Box(Modifier.slot(518+i*330,0,314,200).clickable(role=Role.Button){panel=target}) {
                            FIcon(icon,21,28)
                            FText(label,21,89,260,25,bold=true)
                            val value=when(i){0->light?.second?.let { "${it.toInt()}%" }?:"수신 대기";1->"좌석별 조절";else->"${s.equipmentIds.size}개 등록"}
                            FText(value,21,134,260,22,FBlue)
                        }
                    }
                }
                FText("외부 — · 실내 ${cabin.degree()}°",51,855,520,21,FMuted)
                Box(Modifier.slot(560,836,500,56).clickable(role=Role.Button){still=!still;s.edit("scene.still",still.toString())}) {
                    FText(if(still)"정지 화면 · 움직임 켜기" else "정지 화면으로 보기",0,17,500,19,FMuted,align=TextAlign.Center)
                }
                FButton(if(s.active)"캠핑 종료  →" else "캠핑 시작  →",1320,836,240,56,enabled=!s.busy){panel=if(s.active)"departure" else "start"}
            }
            if(panel.isNotEmpty()) {
                Box(Modifier.slot(0,82,1600,818).background(FInk.copy(alpha=.20f)).clickable(enabled=!s.busy){panel=""})
                val widePanel=panel=="detail"
                Box(Modifier.slot(if(widePanel)600 else 750,82,if(widePanel)1000 else 850,818).background(Color.White)) {
                    val titles=mapOf("setup" to ("에너지 계획 설정" to "종료 시간과 다음 목적지에 맞춰 전력을 계획해요"),
                        "plan" to ("에너지 계획" to "종료 시간까지, 여유 있게 사용하세요"),"time" to ("캠핑 종료 시간" to "머무를 날짜와 시각을 확인하세요"),
                        "destination" to ("종료 후 목적지" to "다음 목적지까지 이동할 전력과 예비 전력을 확보해요"),
                        "places" to ("종료 후 목적지 선택" to "캠핑 다음에 이동할 장소를 선택하세요"),
                        "placeEdit" to ("목적지 정보 입력" to "이동할 장소와 경로 정보를 입력하세요"),
                        "equipment" to ("가져온 장비" to "사용 중인 장비를 확인하고 추가·수정·삭제하세요"),
                        "options" to ("가져온 장비" to "사용 중인 장비를 확인하고 추가·수정·삭제하세요"),
                        "edit" to ((if(editId==null)"장비 추가" else "${s.fields["name$editId"]} 수정") to "장비 이름과 소비전력을 확인한 뒤 저장하세요"),
                        "delete" to ("${s.fields["name$editId"].orEmpty()}를 삭제할까요?" to "가져온 장비 목록과 에너지 계획에서 제외돼요"),
                        "shortfall" to ("사용 계획을 조정해 주세요" to "현재 계획으로는 귀가·예비 전력이 부족해요"),
                        "detail" to ("에너지 사용 현황" to "현재 배터리와 앞으로의 사용량을 함께 확인하세요"),
                        "climate" to ("실내 공조" to "차 안에서 편안하게 머무르세요"),
                        "light" to ("실내 조명" to "캠핑 분위기에 맞춰 밝기를 조절하세요"),
                        "seat" to ("시트" to "좌석을 선택하고 편안한 자세로 조절하세요"),
                        "mode" to (s.selected.label to "변경값을 확인한 뒤 차량에 적용하세요"),
                        "start" to ("캠핑 시작" to "진입 전 차량 설정을 저장해요"),
                        "departure" to ("캠핑 종료·귀가" to "앱이 바꾼 값만 확인하고 복원해요"),
                        "menu" to ("CAMP:ON" to "차 안의 편안함을, 캠핑장까지"),
                        "settings" to ("차량 연결" to "Pleos SDK 관측값과 지원 기능"))
                    val title=titles[panel]?:("CAMP:ON" to "")
                    FText(title.first,36,30,if(widePanel)850 else 705,34,bold=true)
                    FText(title.second,36,83,if(widePanel)928 else 778,21,FMuted)
                    Box(Modifier.slot(if(widePanel)912 else 762,20,56,56).clickable(enabled=!s.busy,role=Role.Button){panel=""}.semantics { contentDescription="닫기" },contentAlignment=Alignment.Center) { Image(painterResource(R.drawable.figma_close),null,Modifier.size(24.dp)) }
                    when(panel) {
                        "setup"->{
                            FText("종료 예정",36,150,600,24,bold=true)
                            FButton(endDate,36,190,778,64){openDate()}
                            FButton(endTime,36,274,778,88){openDate()}
                            FText("남은 시간  $remaining",36,380,778,26,FBlue,true)
                            FText("캠핑 종료 후 목적지",36,446,700,23,bold=true)
                            FButton("${s.fields["destination"]}  ·  ${s.number("distance").degree()} km  ›",36,488,778,72){panel="destination"}
                            FText("귀가 ${b?.home.fmt()} kWh  +  예비 ${s.number("reserve").fmt()} kWh",36,588,778,23,FMuted)
                            FButton("계획 수립",36,718,778,68,true,s.endValid()){s.edit("plan.ready","true");panel="plan"}
                        }
                        "time"->{
                            val draft=runCatching { LocalDateTime.parse(dateDraft,CampSession.format) }.getOrDefault(LocalDateTime.now().plusHours(8))
                            FText("날짜",36,160,778,24,bold=true)
                            FButton(draft.toLocalDate().toString(),36,208,778,82) {
                                DatePickerDialog(context,{_,y,m,d->dateDraft=LocalDateTime.of(y,m+1,d,draft.hour,draft.minute).format(CampSession.format)},draft.year,draft.monthValue-1,draft.dayOfMonth).show()
                            }
                            FText("종료 시각",36,340,778,24,bold=true)
                            FButton(draft.format(DateTimeFormatter.ofPattern("HH : mm")),36,386,778,118) {
                                TimePickerDialog(context,{_,h,m->dateDraft=draft.withHour(h).withMinute(m).format(CampSession.format)},draft.hour,draft.minute,true).show()
                            }
                            FText(if(draft.isAfter(LocalDateTime.now()))"남은 시간  ${java.time.Duration.between(LocalDateTime.now(),draft).toHours()}시간 ${java.time.Duration.between(LocalDateTime.now(),draft).toMinutes()%60}분" else "현재 이후 시각을 선택하세요",36,550,778,24,FBlue)
                            FButton("선택 적용",36,718,778,68,true,draft.isAfter(LocalDateTime.now())){s.edit("end",dateDraft.ifBlank { draft.format(CampSession.format) });panel="setup"}
                        }
                        "destination"->{
                            FText("선택한 목적지",36,150,778,22,FMuted)
                            FButton("${s.fields["destination"]}  ·  ${s.number("distance").degree()} km ›",36,194,778,76){
                                placeDraft=s.fields["destination"].orEmpty();distanceDraft=s.fields["distance"].orEmpty()
                                reserveDraft=s.fields["reserve"].orEmpty();efficiencyDraft=s.fields["efficiency"].orEmpty();panel="places"
                            }
                            FText("귀가 필요 전력",36,326,450,25,bold=true);FText(b?.home.fmt()+" kWh",575,318,240,34,bold=true)
                            FText("예비 전력",36,416,450,25,bold=true);FText(s.number("reserve").fmt()+" kWh",575,408,240,34,bold=true)
                            FText("귀가·예비 전력 이하로 내려가면 알려드려요.",36,540,778,22,FMuted)
                            FButton("에너지 사용 현황  →",36,610,340,56){panel="detail"}
                            FButton("목적지 적용",36,718,778,68,true){panel="setup"}
                        }
                        "places"->{
                            FSurface(36,180,778,176,FPale) {
                                Box(Modifier.fillMaxSize().border(2.dp,FBlue,RoundedCornerShape(18.dp)).clickable(role=Role.Button){panel="placeEdit"})
                                FText(s.fields["destination"].orEmpty().ifBlank { "목적지 입력" },28,28,710,30,bold=true)
                                FText("${s.number("distance").degree()} km  ·  필요 전력 ${b?.home.fmt()} kWh",28,91,710,24,FMuted)
                            }
                            FText("선택한 목적지까지의 이동 거리로 필요 전력을 계산해요.",36,400,778,22,FMuted)
                            FButton("목적지 선택",36,718,778,68,true){panel="destination"}
                        }
                        "placeEdit"->{
                            Column(Modifier.slot(36,150,778,510).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                FInput(placeDraft,{placeDraft=it},"목적지")
                                FInput(distanceDraft,{distanceDraft=it},"이동 거리 · km",true)
                                FInput(efficiencyDraft,{efficiencyDraft=it},"전비 · km/kWh",true)
                                FInput(reserveDraft,{reserveDraft=it},"예비 전력 · kWh",true)
                                Caption("직접 입력한 거리·전비로 필요 전력을 계산해요.")
                            }
                            val valid=placeDraft.isNotBlank() && distanceDraft.toDoubleOrNull()?.let { it.isFinite() && it>=0 }==true &&
                                efficiencyDraft.toDoubleOrNull()?.let { it.isFinite() && it>0 }==true &&
                                reserveDraft.toDoubleOrNull()?.let { it.isFinite() && it>=0 }==true
                            FButton("목적지 선택",36,718,778,68,true,valid){
                                s.edit("destination",placeDraft);s.edit("distance",distanceDraft);s.edit("efficiency",efficiencyDraft);s.edit("reserve",reserveDraft);s.edit("routeManual","true");panel="destination"
                            }
                        }
                        "plan"->{
                            FSurface(36,132,778,76,FBackground){FText("${s.fields["end"]} 종료  ·  종료 후 ${s.fields["destination"]} (${s.fields["distance"]} km)",22,23,734,23,FMuted)}
                            FText("외부 장비",36,225,600,25,bold=true)
                            Column(Modifier.slot(24,266,802,192).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                                if(s.equipmentIds.isEmpty())Caption("가져온 장비를 추가해 주세요.")
                                s.equipmentIds.toList().forEach { id->
                                    val on=s.fields["enabled$id"]!="false"
                                    Box(Modifier.fillMaxWidth().height(88.dp).clip(RoundedCornerShape(18.dp)).background(if(on)FPale else FBackground)
                                        .border(if(on)2.dp else 0.dp,if(on)FBlue else Color.Transparent,RoundedCornerShape(18.dp))) {
                                        FIcon(if(s.fields["name$id"].orEmpty().contains("냉장"))R.drawable.figma_vector_fridge else if(s.fields["name$id"].orEmpty().contains("조명"))R.drawable.figma_vector_lamp else R.drawable.figma_vector_plug,14,24,40)
                                        FText(s.fields["name$id"].orEmpty(),84,12,480,25,bold=true)
                                        FText("${s.number("watts$id").degree()} W · ${if(on)"사용 계획" else "사용 안 함"}",84,50,490,20,if(on)FBlue else FMuted)
                                        FButton("···",630,14,60,58){editId=id;panel="options"}
                                        FToggle(on,true,704,12,"장비 사용 계획 · ${s.fields["name$id"]}") {s.edit("enabled$id",it.toString())}
                                    }
                                }
                            }
                            FButton("+   장비 추가",36,478,778,68){panel="equipment"}
                            Box(Modifier.slot(36,550,778,1).background(Color(0xFFDCE3EE)))
                            FText("예상 사용",36,574,350,22,FMuted);FText(b?.consumption.fmt()+" kWh",574,567,240,28,bold=true,align=TextAlign.End)
                            FText("계획 후 여유",36,624,350,22,FMuted);FText(b?.margin.fmt()+" kWh",560,612,254,36,FBlue,true,TextAlign.End)
                            FButton("계획 설정",36,666,379,56){panel="setup"};FButton("사용 현황",435,666,379,56){panel="detail"}
                            FButton("계획 적용",36,738,778,56,true,b!=null && s.endValid()){
                                if(b!!.continuedShortfall>0)panel="shortfall" else {s.save();s.message="사용 계획 저장 · 외부 전원은 장비에서 확인하세요";panel=""}
                            }
                        }
                        "equipment","options"->{
                            Column(Modifier.slot(36,150,778,520).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                                s.equipmentIds.toList().chunked(2).forEach { pair->
                                    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                                        pair.forEach { id->
                                            Box(Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(18.dp)).background(FPale)
                                                .border(2.dp,FBlue,RoundedCornerShape(18.dp)).clickable(role=Role.Button){editId=id;panel="options"}) {
                                                FIcon(if(s.fields["name$id"].orEmpty().contains("냉장"))R.drawable.figma_vector_fridge else if(s.fields["name$id"].orEmpty().contains("조명"))R.drawable.figma_vector_lamp else R.drawable.figma_vector_plug,22,28,35)
                                                FText(s.fields["name$id"].orEmpty(),86,20,240,25,bold=true)
                                                FText("${s.number("watts$id").degree()} W",86,69,250,20,FMuted)
                                                FText(if(s.fields["enabled$id"]=="false")"사용 안 함" else "● 사용 계획",86,102,250,19,FBlue)
                                                FText("···",318,16,40,24,FBlue)
                                            }
                                        }
                                        if(pair.size==1)Spacer(Modifier.weight(1f))
                                    }
                                }
                                Row(horizontalArrangement=Arrangement.spacedBy(20.dp)){
                                    Box(Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(18.dp)).background(FBackground).clickable(role=Role.Button){
                                        val existing=s.equipmentIds.firstOrNull { s.fields["name$it"]=="인덕션" }
                                        if(existing!=null)editEquipment(existing) else s.addEquipment(Equipment("인덕션",1200.0,.5))
                                    }) {
                                        FIcon(R.drawable.figma_vector_cook,22,28,35)
                                        FText("인덕션",86,20,250,25,bold=true);FText("1,200 W",86,69,250,20,FMuted)
                                        FText(if(s.equipmentIds.any { s.fields["name$it"]=="인덕션" })"등록됨 · 수정" else "미등록",86,102,250,19,FMuted)
                                    }
                                    Box(Modifier.weight(1f).height(136.dp).clip(RoundedCornerShape(18.dp)).background(FBackground).clickable(role=Role.Button){editEquipment(null)}) {
                                        FIcon(R.drawable.figma_vector_plug,22,28,35)
                                        FText("기타 장비",86,20,250,25,bold=true);FText("직접 입력",86,69,250,20,FMuted);FText("새 장비",86,102,250,19,FMuted)
                                    }
                                }
                            }
                            if(panel=="options") {
                                val right=s.equipmentIds.indexOf(editId)%2==1
                                FSurface(if(right)550 else 170,210,278,174) {
                                    Box(Modifier.fillMaxSize().border(1.dp,Color(0xFFE8EDF5),RoundedCornerShape(18.dp)))
                                    Box(Modifier.slot(0,6,278,80).clickable(role=Role.Button){editEquipment(editId)}){FText("수정",0,25,278,24,FBlue,align=TextAlign.Center)}
                                    Box(Modifier.slot(0,86,278,80).clickable(role=Role.Button){panel="delete"}){FText("삭제",0,25,278,24,FBlue,align=TextAlign.Center)}
                                }
                            }
                            FButton("장비 관리 완료",36,718,778,68,true){panel="plan"}
                        }
                        "edit"->{
                            Column(Modifier.slot(36,150,778,520).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(26.dp)) {
                                FInput(equipmentName,{equipmentName=it},"장비 이름")
                                FInput(equipmentWatts,{equipmentWatts=it},"소비전력 · W",true)
                                FInput(equipmentHours,{equipmentHours=it},"앞으로 사용 · 시간",true)
                                Caption("제품에 표기된 정격 소비전력을 기준으로 입력하세요.")
                            }
                            val watts=equipmentWatts.toDoubleOrNull();val hours=equipmentHours.toDoubleOrNull()
                            val valid=equipmentName.isNotBlank() && watts!=null && watts.isFinite() && watts>=0 && hours!=null && hours.isFinite() && hours>=0
                            FButton("수정 저장",36,718,778,68,true,valid){
                                val id=editId
                                if(id==null)s.addEquipment(Equipment(equipmentName,watts!!,hours!!))
                                else {s.edit("name$id",equipmentName);s.edit("watts$id",equipmentWatts);s.edit("hours$id",equipmentHours);s.edit("equipmentSource$id","사용자 입력")}
                                panel="equipment"
                            }
                        }
                        "delete"->{
                            FSurface(36,190,778,180,FBackground){FText(s.fields["name$editId"].orEmpty(),28,28,710,30,bold=true);FText("필요하면 장비 추가에서 다시 등록할 수 있어요.",28,86,710,24,FMuted)}
                            FButton("취소",36,718,379,68){panel="equipment"}
                            FButton("삭제",435,718,379,68,true){editId?.let { s.removeEquipment(it) };panel="equipment"}
                        }
                        "shortfall"->{
                            FText("${b?.continuedShortfall.fmt()} kWh 부족",36,170,778,56,FBlue,true)
                            FText("장비 사용 시간과 소비전력을 줄여 주세요.",36,274,778,25,bold=true)
                            FButton("장비 사용 계획 수정",36,354,778,76,true){panel="plan"}
                            FButton("캠핑 종료 시간 앞당기기",36,452,778,76){openDate()}
                            FText("귀가 필요 ${b?.home.fmt()} · 예비 ${b?.reserve.fmt()} kWh",36,600,778,23,FMuted)
                        }
                        "detail"->{
                            FText("현재 배터리",36,144,400,22,FMuted);FText(s.soc.degree()+"%",36,184,400,64,bold=true)
                            FText("캠핑 예상 사용",536,144,400,22,FMuted);FText(b?.consumption.fmt()+" kWh",536,184,400,48,FBlue,true)
                            FigmaForecast(s,Modifier.slot(36,300,928,380))
                            FButton("확인",36,718,928,68,true){panel="plan"}
                        }
                        "climate"->{
                            FText(temp?.second.degree()+"°",250,189,350,124,bold=true,align=TextAlign.Center)
                            FButton("‹",120,212,84,110,enabled=boundTemp && temp!!.second!!>temp.first.min){stepTemp(-1)}
                            FButton("›",646,212,84,110,enabled=boundTemp && temp!!.second!!<temp.first.max){stepTemp(1)}
                            FText("현재 실내 ${cabin.degree()}° · 외부 —",36,365,778,24,FMuted)
                            FButton("자동 공조  ${auto?.second?.let { if(it>0)"ON" else "OFF" }?:"미지원"}",36,444,778,76,auto?.second==1.0,auto?.let { s.canAdjust(it.first) }==true){send(auto,if(auto?.second==1.0)0.0 else 1.0)}
                            FButton("바람 세기  ·  ${fan?.second.degree()}단",36,546,778,76,enabled=auto?.second==0.0 && fan?.let { s.canAdjust(it.first) }==true){
                                fan?.let { (c,v)->if(v!=null)send(fan,if(v>=c.max)c.min else v+c.step) }
                            }
                            FText(s.message,36,642,778,20,FMuted)
                            FButton("적용",36,718,778,68,true){panel=""}
                            Box(Modifier.slot(36,658,778,56).clickable(role=Role.Button){advanced=0}) { FText("상세 공조 · 좌석별 설정  →",0,14,778,20,FMuted) }
                        }
                        "light"->{
                            val power=s.observed(ControlKind.CABIN_LIGHT)
                            val value=light?.second
                            var draft by remember(value) { mutableStateOf<Float?>(null) }
                            FIcon(R.drawable.figma_light,345,155,150)
                            FText(value?.let { "${it.toInt()}%" }?:"—",259,331,332,64,FBlue,true,TextAlign.Center)
                            FSurface(36,454,501,108,Color(0xFFF8F9FB)){
                                FText("실내 조명",31,37,300,26,bold=true)
                                val on=power?.second?.let { power.first.choices[it]?.uppercase() !in setOf("OFF","꺼짐") }?:false
                                FToggle(on,power?.let { s.canAdjust(it.first) }==true,369,22,"실내 조명 전원"){
                                    val choices=power?.first?.choices.orEmpty()
                                    val target=choices.entries.firstOrNull { e->if(it)e.value=="ON" || e.value=="켜짐" else e.value=="OFF" || e.value=="꺼짐" }?.key
                                    if(target!=null)send(power,target) else s.message="지원 전원값을 상세 조명에서 선택하세요"
                                }
                            }
                            val autoValue=power?.first?.choices?.entries?.firstOrNull { it.value=="AUTO" || it.value=="자동" }?.key
                            FButton("AUTO",566,454,248,108,primary=autoValue!=null && power.second==autoValue,
                                enabled=autoValue!=null && power?.let { s.canAdjust(it.first) }==true){send(power,autoValue!!)}
                            FSurface(36,612,778,150,Color(0xFFF8F9FB)){
                                FText("밝기 조절",28,23,340,24,bold=true)
                                FText(if(draft!=null)"요청 ${draft!!.toInt()}%" else value?.let { "${it.toInt()}%" }?:"미지원",540,23,206,25,bold=true,align=TextAlign.End)
                                Slider(value=draft?:value?.toFloat()?:0f,onValueChange={draft=it},
                                    onValueChangeFinished={draft?.let { send(light,it.toDouble()) };draft=null},
                                    valueRange=light?.first?.let { it.min.toFloat()..it.max.toFloat() }?:0f..100f,
                                    enabled=light?.let { s.canAdjust(it.first) && it.first.max>it.first.min }==true,
                                    modifier=Modifier.slot(28,70,720,64).semantics { contentDescription="실내 조명 밝기" })
                            }
                            Box(Modifier.slot(36,770,778,48).clickable(role=Role.Button){advanced=1}){FText(s.message.ifBlank { "상세 조명 설정  →" },0,5,778,18,FMuted)}
                        }
                        "seat"->{
                            Column(Modifier.slot(36,140,778,560).verticalScroll(rememberScrollState())){
                                CabinPlan(s,s.reading.controls.filter { it.kind==ControlKind.SEAT_POSITION || it.kind==ControlKind.SEAT_TEMPERATURE },Modifier.fillMaxWidth())
                            }
                            FButton("좌석별 상세 조절",36,718,778,68,true){advanced=2}
                        }
                        "mode"->{
                            Column(Modifier.slot(36,140,778,530).verticalScroll(rememberScrollState())){ModeApplyPreview(s)}
                            FText(s.message,36,674,778,20,FMuted)
                            FButton("확인하고 적용",36,718,778,68,true,s.canControl && s.endValid()){
                                scope.launch { if(!s.active)s.start();if(s.active)s.applyMode();if(s.modeStatus=="적용 완료")panel="" }
                            }
                        }
                        "start"->{
                            FText("현재 차량 설정을 보관합니다.",36,180,778,32,bold=true)
                            FText("종료 예정 ${s.fields["end"]}",36,248,778,24,FMuted)
                            FText("시작만으로 차량 설정이 바뀌지 않아요.",36,320,778,24,FMuted)
                            FButton("종료 시간 설정",36,430,778,72){openDate()}
                            FText(s.message,36,620,778,22,FMuted)
                            FButton("캠핑 시작",36,718,778,68,true,s.canControl && s.endValid()){scope.launch{s.start();if(s.active)panel=""}}
                        }
                        "departure"->{
                            Column(Modifier.slot(36,150,778,500).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)){
                                Text("앱에서 바꾼 값만 진입 전으로 복원합니다.",fontSize=28.sp,fontWeight=FontWeight.Medium)
                                if(s.written.isEmpty())Caption("복원이 필요한 설정이 없습니다.")
                                s.written.toMap().forEach { (id,_)->Text("${s.reading.controls.find { it.id==id }?.label?:id} · ${s.results[id]?:"복원 대기"}",fontSize=22.sp) }
                                listOf("도어·윈도우 닫힘","외부 조명 정리","V2L 장비 정리","주변과 적재물 확인").forEach { label->
                                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.checks[label]==true,{s.checkItem(label,it)});Text(label,fontSize=24.sp)}
                                }
                                Caption("귀가 ${b?.home.fmt()} kWh · 추가 필요 ${b?.continuedShortfall.fmt()} kWh")
                            }
                            FButton("귀가 조건 확인",36,638,778,64){panel="destination"}
                            FButton("종료·복원",36,718,778,68,true,s.canControl && (s.active || s.written.isNotEmpty())){
                                scope.launch{s.endAndRestore();if(!s.active && s.written.isEmpty())panel=""}
                            }
                        }
                        "menu"->{
                            FButton("함께 이동 · 그룹 브리핑",36,170,778,84,true){journeyPage=true;panel=""}
                            FButton("캠핑 화면",36,280,778,84){journeyPage=false;panel=""}
                            FButton("Pleos 연결·지원 기능",36,390,778,84){panel="settings"}
                            FButton(if(still)"장면 움직임 켜기" else "장면 정지 화면으로 보기",36,500,778,84){still=!still;s.edit("scene.still",still.toString())}
                            FButton("캠핑 종료·귀가",36,610,778,84){panel="departure"}
                        }
                        "settings"->{
                            Column(Modifier.slot(36,150,778,520).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(18.dp)) {
                                Text(if(s.connected)"Pleos SDK 수신 중" else "차량 연결 확인 필요",fontSize=32.sp,fontWeight=FontWeight.Medium)
                                Caption("지원 제어 ${s.reading.controls.size}개 · ${s.reading.source}")
                                Caption("SDK 수신값만 표시합니다. 미지원·미수신값은 —로 표시합니다.")
                                Caption("주차 ${s.parkingState.parked} · 조작 허용 ${s.parkingState.unrestricted}")
                                s.reading.errors.forEach { (name,error)->Caption("$name · $error") }
                                FInput(s.fields["capacity"].orEmpty(),{s.edit("capacity",it)},"사용 가능 배터리 용량 보정 · kWh",true)
                                Caption("배터리 소비와 종료 잔량은 계획용 추정값입니다.")
                            }
                            FButton("상태 다시 확인",36,718,778,68,true,!s.busy && !s.refreshing){scope.launch{s.refresh()}}
                        }
                    }
                }
            }
        }
    }
    if(advanced>=0)ControlEditor(s,advanced){advanced=-1}
}
@Composable private fun FInput(value:String,change:(String)->Unit,label:String,numeric:Boolean=false) {
    Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
        Text(label,fontSize=24.sp,fontWeight=FontWeight.Medium,color=FInk)
        TextField(value,change,Modifier.fillMaxWidth().heightIn(min=76.dp).semantics { contentDescription=label },singleLine=true,
            textStyle=LocalTextStyle.current.copy(fontSize=28.sp,fontWeight=FontWeight.Medium),
            shape=RoundedCornerShape(18.dp),
            colors=TextFieldDefaults.colors(focusedContainerColor=FBackground,unfocusedContainerColor=FBackground,
                focusedIndicatorColor=Color.Transparent,unfocusedIndicatorColor=Color.Transparent),
            keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=if(numeric)androidx.compose.ui.text.input.KeyboardType.Decimal else androidx.compose.ui.text.input.KeyboardType.Text))
    }
}
@Composable private fun FArrowButton(resource:Int,label:String,x:Int,y:Int,enabled:Boolean,click:()->Unit) {
    Box(Modifier.slot(x,y,90,64).clip(RoundedCornerShape(18.dp)).background(FPale)
        .clickable(enabled=enabled,role=Role.Button,onClick=click).semantics { contentDescription=label },
        contentAlignment=Alignment.Center) {
        Image(painterResource(resource),null,Modifier.size(30.dp),alpha=if(enabled)1f else .4f)
    }
}
@Composable private fun FigmaForecast(s:CampSession,modifier:Modifier) {
    val capacity=s.capacityKwh
    val kw=s.climateKw?.let { climate->s.number("baseKw")?.let { it+climate } }
    val points=Energy.forecast(capacity,s.soc,s.remainingHours,kw,s.remainingEquipment())
    Box(modifier) {
        Box(Modifier.slot(0,8,928,1).background(Color(0xFFE5ECF6)))
        if(points==null || capacity==null || !s.endValid()) {
            FText("배터리·사용 계획을 확인하면 예상 그래프가 나타나요",0,90,928,23,FMuted)
        } else {
            val protected=s.budget?.let { (it.home+it.reserve)/capacity*100 }
            val first=points.first().kwh/capacity*100
            val last=points.last().kwh/capacity*100
            val top=kotlin.math.ceil(first/5)*5
            val bottom=(kotlin.math.floor(minOf(last,protected?:last)/5)*5).coerceAtMost(top-10).coerceAtLeast(0.0)
            val range=(top-bottom).coerceAtLeast(1.0)
            val duration=s.remainingHours?:0.0
            listOf(top,(top+bottom)/2,bottom).forEachIndexed { i,v-> FText("${v.degree()}%",0,42+i*90,70,19,FMuted) }
            Canvas(Modifier.slot(44,55,838,180).semantics { contentDescription="배터리 ${first.fmt()}퍼센트, 종료 예상 ${last.fmt()}퍼센트. 귀가 예비 ${protected.fmt()}퍼센트" }) {
                fun x(h:Double)=(h/duration.coerceAtLeast(.001)*size.width).toFloat()
                fun y(v:Double)=((top-v)/range*size.height).toFloat().coerceIn(0f,size.height)
                listOf(top,(top+bottom)/2,bottom).forEach { drawLine(Color(0xFFE5ECF6),androidx.compose.ui.geometry.Offset(0f,y(it)),androidx.compose.ui.geometry.Offset(size.width,y(it)),1f) }
                protected?.let { drawLine(FBlue.copy(alpha=.45f),androidx.compose.ui.geometry.Offset(0f,y(it)),androidx.compose.ui.geometry.Offset(size.width,y(it)),3f,
                    pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f,8f))) }
                points.zipWithNext().forEach { (a,b)->drawLine(FBlue,androidx.compose.ui.geometry.Offset(x(a.hours),y(a.kwh/capacity*100)),androidx.compose.ui.geometry.Offset(x(b.hours),y(b.kwh/capacity*100)),5f) }
            }
            FText("지금",44,285,300,20,FMuted)
            FText(s.fields["end"].orEmpty(),500,285,382,20,FMuted,align=TextAlign.End)
            FText("— 예상 잔량    ┄ 귀가·예비 확보량",44,340,838,20,FBlue)
        }
    }
}
