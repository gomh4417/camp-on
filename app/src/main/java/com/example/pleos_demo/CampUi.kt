package com.example.pleos_demo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Ink=Color(0xFFF2F4F8)
private val Soft=Color(0xFFABB4C3)
private val Accent=Color(0xFFC3D3EA)
private val Face=FontFamily(Font(R.font.pretendard_regular,FontWeight.Normal),Font(R.font.pretendard_medium,FontWeight.Medium),Font(R.font.pretendard_extrabold,FontWeight.ExtraBold))
private fun style(size:Int,weight:FontWeight=FontWeight.Normal)=TextStyle(fontFamily=Face,fontWeight=weight,fontSize=size.sp)
private val Type=Typography(
    displayLarge=style(80,FontWeight.ExtraBold),displayMedium=style(64,FontWeight.ExtraBold),displaySmall=style(48,FontWeight.ExtraBold),
    headlineLarge=style(36,FontWeight.ExtraBold),headlineMedium=style(30,FontWeight.ExtraBold),headlineSmall=style(26,FontWeight.ExtraBold),
    titleLarge=style(24,FontWeight.Medium),titleMedium=style(20,FontWeight.Medium),titleSmall=style(18,FontWeight.Medium),
    bodyLarge=style(20),bodyMedium=style(18),bodySmall=style(16),
    labelLarge=style(18,FontWeight.Medium),labelMedium=style(16,FontWeight.Medium),labelSmall=style(14,FontWeight.Medium))
private fun Double?.fmt()=this?.let { String.format(Locale.US,"%.1f",it) }?:"—"
private fun stamp(t:Long)=if(t==0L) "갱신 대기" else SimpleDateFormat("HH:mm:ss",Locale.KOREA).format(Date(t))
private fun value(c:VehicleControl,v:Double?):String=when {
    v==null->"확인 불가"
    c.kind in listOf(ControlKind.POWER,ControlKind.AC,ControlKind.RECIRCULATION)->if(v>0) "켜짐" else "꺼짐"
    else->v.fmt()+c.kind.unit
}

@Composable fun CampApp() {
    val context=LocalContext.current
    val source=remember { runCatching<VehicleSource> { PleosVehicleSource(context) }.getOrElse { failure->
        object:VehicleSource {
            override suspend fun read():VehicleReading=throw IllegalStateException("차량 연결 실패",failure)
            override suspend fun parking()=ParkingState(null,null)
            override suspend fun readControl(id:String):Double?=null
            override suspend fun writeControl(id:String,value:Double) { error("차량 연결 실패") }
            override fun close() {}
        }
    } }
    val s=remember { CampSession(context,source) }
    val scope=rememberCoroutineScope()
    var page by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(source) { onDispose { source.close() } }
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(s,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
        launch { while(true) { s.refresh();delay(5000) } }
        launch { while(true) { s.now=System.currentTimeMillis();s.refreshParking();delay(2000) } }
    } }
    MaterialTheme(typography=Type,colorScheme=darkColorScheme(primary=Accent,onPrimary=Color(0xFF141C28),background=Color(0xFF090C12),surface=Color(0xFF181D27),onSurface=Ink,onBackground=Ink,outline=Color(0xFF505969),secondary=Accent)) {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF222B3B),Color(0xFF0B0E15),Color(0xFF141822))))) {
            if(s.active && s.mode==CampMode.SLEEP) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.5f)))
            Row(Modifier.fillMaxSize().safeDrawingPadding().padding(28.dp),horizontalArrangement=Arrangement.spacedBy(28.dp)) {
                Column(Modifier.width(180.dp).fillMaxHeight(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                    Text("CAMP:ON",style=MaterialTheme.typography.headlineSmall,color=Ink)
                    Text("YOUR SPACE\nYOUR PACE",style=MaterialTheme.typography.labelSmall,color=Soft)
                    Spacer(Modifier.height(32.dp))
                    listOf("캠핑 홈","모드 설정","에너지 계획","철수 · 귀가").forEachIndexed { i,label->
                        FilledTonalButton(onClick={page=i},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp),
                            colors=ButtonDefaults.filledTonalButtonColors(containerColor=if(page==i) Color.White.copy(alpha=.16f) else Color.Transparent,contentColor=if(page==i) Ink else Soft),
                            shape=RoundedCornerShape(18.dp)) { Text(label) }
                    }
                    Spacer(Modifier.weight(1f))
                    Text(if(s.parkingState.allowsControl) "P · 주차 중" else "조작 잠김",color=Ink,style=MaterialTheme.typography.titleMedium)
                    Text(if(s.connected && s.fresh) "차량 연결됨" else if(s.refreshing) "차량 연결 중" else "연결 확인",color=Soft)
                    Text(stamp(s.reading.time),style=MaterialTheme.typography.bodySmall,color=Soft)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(listOf("머무는 순간, 캠핑이 되다","나에게 맞는 캠핑 환경","오늘의 에너지를 계획하세요","다음 여정을 위한 준비")[page],style=MaterialTheme.typography.headlineLarge,color=Ink)
                            Text(if(s.active) "${s.mode.label} ${s.mode.name}  ·  종료 ${s.fields["end"]}" else "차를 세우고, 나만의 시간을 시작하세요",color=Soft)
                        }
                        if(s.busy || s.refreshing) CircularProgressIndicator(Modifier.size(26.dp),strokeWidth=2.dp)
                    }
                    if(s.message.isNotBlank()) Note(s.message)
                    if(s.connectionError.isNotBlank()) Note(s.connectionError)
                    when(page) {
                        0->Home(s,{scope.launch { s.start() }},{s.selected=it;page=1},{page=2})
                        1->Modes(s) { scope.launch { s.applyMode() } }
                        2->EnergyScreen(s)
                        3->Leave(s,{scope.launch { s.restore() }})
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable private fun Glass(modifier:Modifier=Modifier,content:@Composable ColumnScope.()->Unit) {
    Column(modifier.background(Brush.verticalGradient(listOf(Color.White.copy(alpha=.085f),Color.White.copy(alpha=.025f))),RoundedCornerShape(28.dp))
        .border(1.dp,Color.White.copy(alpha=.12f),RoundedCornerShape(28.dp)).padding(26.dp),verticalArrangement=Arrangement.spacedBy(16.dp),content=content)
}
@Composable private fun Note(text:String) { Text(text,color=Soft,style=MaterialTheme.typography.bodyMedium) }
@Composable private fun Action(text:String,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,modifier=Modifier.heightIn(min=56.dp),shape=RoundedCornerShape(16.dp)) { Text(text) }
}
@Composable private fun Metric(label:String,value:String) {
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) { Text(label,color=Soft);Text(value,color=Ink,style=MaterialTheme.typography.headlineSmall) }
}
@Composable private fun Home(s:CampSession,start:()->Unit,select:(CampMode)->Unit,energy:()->Unit) {
    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Glass(Modifier.weight(1f)) {
            Text("BATTERY",color=Soft,style=MaterialTheme.typography.labelMedium)
            Row(verticalAlignment=Alignment.Bottom) { Text(s.soc.fmt(),style=MaterialTheme.typography.displayLarge,color=Ink);Text(" %",style=MaterialTheme.typography.headlineSmall,color=Soft) }
            Text(when(s.reading.charging.takeIf { s.fresh }) { true->"충전 중";false->"충전 연결 없음";null->"충전 상태 확인 불가" },color=Soft)
            BudgetBar(s.budget)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Metric("귀가",s.budget?.home.fmt()+" kWh");Metric("예비",s.budget?.reserve.fmt()+" kWh")
            }
        }
        Glass(Modifier.weight(1f)) {
            Text("CAMP ENERGY",color=Soft,style=MaterialTheme.typography.labelMedium)
            Text(s.budget?.available.fmt(),style=MaterialTheme.typography.displayLarge,color=Ink)
            Text("kWh · 캠핑 가용량",color=Soft)
            Metric("계획 소비",s.budget?.consumption.fmt()+" kWh")
            Action("에너지 계획",onClick=energy)
        }
    }
    Warnings(s)
    if(!s.active) Action("캠핑 시작",s.canControl && s.written.isEmpty(),start)
    Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        CampMode.entries.forEach { m->
            Glass(Modifier.weight(1f)) {
                Text(m.name,style=MaterialTheme.typography.titleLarge,color=Ink)
                Text(m.label,color=Soft)
                Text(m.subtitle,style=MaterialTheme.typography.bodySmall,color=Soft)
                OutlinedButton(onClick={select(m)},modifier=Modifier.heightIn(min=52.dp)) { Text("설정") }
            }
        }
    }
    Glass(Modifier.fillMaxWidth()) {
        Text("차량 상태",style=MaterialTheme.typography.titleLarge,color=Ink)
        Text("실내 온도  ${s.reading.cabin.takeIf { s.fresh }.fmt()} °C",color=Ink)
        s.reading.controls.forEach { c->Text("${c.label}  ·  ${value(c,s.current[c.id].takeIf { s.fresh })}",color=Soft) }
        if(s.reading.controls.isEmpty()) Note("차량 기능을 불러오는 중입니다")
    }
}

@Composable private fun Modes(s:CampSession,apply:()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) { CampMode.entries.forEach { m->
        FilterChip(selected=s.selected==m,onClick={s.selected=m},label={Text("${m.label} ${m.name}")},modifier=Modifier.heightIn(min=56.dp))
    } }
    val p=s.presets.getValue(s.selected)
    fun supported(kind:ControlKind)=s.reading.controls.any { it.kind==kind && it.writable }
    Row(horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Glass(Modifier.weight(1f)) {
            Text(s.selected.subtitle,style=MaterialTheme.typography.headlineSmall,color=Ink)
            Adjust("온도",p.temperature.fmt()+" °C",supported(ControlKind.TEMPERATURE),{s.savePreset(p.copy(temperature=(p.temperature-.5).coerceAtLeast(16.0)))},{s.savePreset(p.copy(temperature=(p.temperature+.5).coerceAtMost(32.0)))})
            Adjust("풍량",p.fan.toString()+"단",supported(ControlKind.FAN),{s.savePreset(p.copy(fan=(p.fan-1).coerceAtLeast(0)))},{s.savePreset(p.copy(fan=(p.fan+1).coerceAtMost(10)))})
            Adjust("조명",p.light.toString()+"%",supported(ControlKind.LIGHT),{s.savePreset(p.copy(light=(p.light-5).coerceAtLeast(0)))},{s.savePreset(p.copy(light=(p.light+5).coerceAtMost(100)))})
            Toggle("공조 전원",p.power,supported(ControlKind.POWER)) { s.savePreset(p.copy(power=it)) }
            Toggle("A/C",p.ac,supported(ControlKind.AC)) { s.savePreset(p.copy(ac=it)) }
            Toggle("내기순환",p.recirculate,supported(ControlKind.RECIRCULATION)) { s.savePreset(p.copy(recirculate=it)) }
            Note("설정은 자동 저장됩니다")
            Note("차량 소비 계획 · ${s.selected.kw} kW × 남은 시간")
            if(s.selected==CampMode.COOK) Note("야외 요리를 위한 환경 설정")
            if(s.selected==CampMode.SLEEP) Note("앱 화면을 어둡게 전환합니다")
        }
        Glass(Modifier.weight(1f)) {
            Text("변경할 차량 설정",style=MaterialTheme.typography.titleLarge,color=Ink)
            s.reading.controls.forEach { c->
                Text(c.label,color=Ink,style=MaterialTheme.typography.titleMedium)
                Note("${value(c,s.current[c.id])}  →  ${value(c,s.target(c))}")
                Note(s.results[c.id]?:if(c.writable) "적용 대기" else "읽기 전용")
            }
            if(s.reading.controls.isEmpty()) Note("연결된 제어 항목이 없습니다")
            Note("시트 · 창문 · V2L 제어는 지원하지 않습니다")
            Action(if(s.busy) "적용 중" else "모드 적용",s.active && s.canControl && s.reading.controls.any { it.writable }) { confirm=true }
            if(!s.active) Note("캠핑 홈에서 먼저 캠핑을 시작하세요")
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("${s.selected.label} 모드 적용")},
        text={Text("표시된 차량 설정을 변경합니다. 계속할까요?")},
        confirmButton={TextButton(onClick={confirm=false;apply()}) { Text("적용") }},
        dismissButton={TextButton(onClick={confirm=false}) { Text("취소") }})
}
@Composable private fun Adjust(label:String,value:String,enabled:Boolean,minus:()->Unit,plus:()->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(if(enabled) label else "$label · 미지원",Modifier.weight(1f),color=Soft)
        OutlinedButton(onClick=minus,enabled=enabled,modifier=Modifier.sizeIn(minWidth=56.dp,minHeight=56.dp)) { Text("−") }
        Text(value,color=Ink,style=MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick=plus,enabled=enabled,modifier=Modifier.sizeIn(minWidth=56.dp,minHeight=56.dp)) { Text("+") }
    }
}
@Composable private fun Toggle(label:String,checked:Boolean,enabled:Boolean=true,change:(Boolean)->Unit) {
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) { Text(if(enabled) label else "$label · 미지원",Modifier.weight(1f),color=Ink);Switch(checked,onCheckedChange=change,enabled=enabled) }
}
@Composable private fun Input(s:CampSession,key:String,label:String,modifier:Modifier=Modifier) {
    OutlinedTextField(s.fields[key].orEmpty(),{s.edit(key,it)},modifier=modifier,label={Text(label)},singleLine=true,shape=RoundedCornerShape(14.dp))
}
@Composable private fun EnergyScreen(s:CampSession) {
    Glass(Modifier.fillMaxWidth()) {
        Text("에너지 배분",style=MaterialTheme.typography.titleLarge,color=Ink)
        BudgetBar(s.budget)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Metric("현재",s.budget?.current.fmt()+" kWh");Metric("귀가",s.budget?.home.fmt()+" kWh")
            Metric("예비",s.budget?.reserve.fmt()+" kWh");Metric("캠핑",s.budget?.available.fmt()+" kWh")
        }
        Text("계획 소비 ${s.budget?.consumption.fmt()} / 가용 ${s.budget?.available.fmt()} kWh",color=Ink)
    }
    Warnings(s)
    Glass(Modifier.fillMaxWidth()) {
        Text("캠핑과 귀가",style=MaterialTheme.typography.titleLarge,color=Ink)
        Input(s,"end","캠핑 종료 · yyyy-MM-dd HH:mm",Modifier.fillMaxWidth())
        if(!s.endValid()) Note("미래의 종료 시각을 입력하세요")
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Input(s,"capacity","사용 가능 배터리 용량 · kWh",Modifier.weight(1f));Input(s,"distance","귀가 거리 · km",Modifier.weight(1f))
        }
        Row(horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            Input(s,"efficiency","전비 · km/kWh",Modifier.weight(1f));Input(s,"reserve","보호 예비량 · kWh",Modifier.weight(1f))
        }
        Input(s,"vehicle","차량 기능 소비 계획 · kWh",Modifier.fillMaxWidth())
        Note("입력한 용량·거리·전비로 계산합니다. 장비 소비는 별도로 합산합니다.")
    }
    s.equipmentIds.toList().forEach { id->
        Glass(Modifier.fillMaxWidth()) {
            Input(s,"name$id","장비 이름",Modifier.fillMaxWidth())
            Row(horizontalArrangement=Arrangement.spacedBy(14.dp)) {
                Input(s,"watts$id","소비전력 · W",Modifier.weight(1f))
                Input(s,"hours$id","사용시간 · h",Modifier.weight(1f))
                Input(s,"duty$id","사용률 · 0~1",Modifier.weight(1f))
            }
            TextButton(onClick={s.removeEquipment(id)}) { Text("장비 삭제") }
        }
    }
    Action("장비 추가",onClick={s.addEquipment()})
    Note("장비 전력 × 시간 × 사용률 ÷ 0.9 · 변환 효율 90% 적용")
}
@Composable private fun BudgetBar(b:Budget?) {
    Row(Modifier.fillMaxWidth().height(14.dp).background(Color.White.copy(alpha=.05f),RoundedCornerShape(7.dp))) {
        if(b!=null && b.current>0) {
            val home=b.home.coerceAtMost(b.current)
            val reserve=b.reserve.coerceAtMost((b.current-home).coerceAtLeast(0.0))
            listOf(home to .9f,reserve to .5f,b.available to .22f).forEach { (v,a)->
                if(v>0) Box(Modifier.weight(v.toFloat()).fillMaxHeight().background(Accent.copy(alpha=a)))
            }
        }
    }
}
@Composable private fun Warnings(s:CampSession) {
    val b=s.budget
    when {
        b==null->Note("계산에 필요한 차량 정보와 입력값을 확인하세요. 전비는 0보다 커야 합니다.")
        b.homeShortfall>0->Note("귀가 에너지 부족 · ${b.homeShortfall.fmt()} kWh 이상 충전을 검토하세요")
        b.protectedShortfall>0->Note("예비량 부족 · ${b.protectedShortfall.fmt()} kWh 추가 충전을 검토하세요")
    }
    if(b!=null && b.overBudget>0) Note("캠핑 계획 초과 · ${b.overBudget.fmt()} kWh. 장비 사용시간 또는 종료 시각을 조정하세요.")
}
@Composable private fun Leave(s:CampSession,restore:()->Unit) {
    var confirm by remember { mutableStateOf(false) }
    Glass(Modifier.fillMaxWidth()) {
        Text("PACK & LEAVE",style=MaterialTheme.typography.headlineSmall,color=Ink)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Metric("귀가 에너지",s.budget?.home.fmt()+" kWh");Metric("추가 충전",s.budget?.protectedShortfall.fmt()+" kWh")
        }
        Warnings(s)
    }
    Glass(Modifier.fillMaxWidth()) {
        Text("캠핑 전 설정으로",style=MaterialTheme.typography.titleLarge,color=Ink)
        s.written.toMap().forEach { (id,v)->
            val c=s.reading.controls.find { it.id==id }
            Text("${c?.label?:id} · ${v.fmt()} / 시작 ${s.initial[id].fmt()}",color=Ink)
            Note(s.results[id]?:"복원 대기")
        }
        if(s.written.isEmpty()) Note("복원할 설정이 없습니다")
        Action("설정 복원",s.canControl && s.written.isNotEmpty()) { confirm=true }
        if(s.written.isNotEmpty()) TextButton(onClick={s.acknowledgeManualRestore()},enabled=!s.busy) { Text("차량에서 직접 복원 완료") }
        Note("직접 바꾼 설정은 유지합니다")
    }
    Glass(Modifier.fillMaxWidth()) {
        Text("출발 전 확인",style=MaterialTheme.typography.titleLarge,color=Ink)
        listOf("도어 닫힘","윈도우 닫힘","조명 확인","V2L 장비 정리","주변과 적재물 확인").forEach { label->
            Toggle(label,s.checks[label]==true) { s.checkItem(label,it) }
        }
        Note("내비게이션은 차량에서 목적지를 선택하세요")
        Action("캠핑 종료",s.active && !s.busy && s.written.isEmpty() && s.checks.values.count { it }>=5) { s.finish() }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text("차량 설정 복원")},
        text={Text("앱에서 변경한 공조와 조명을 캠핑 시작 값으로 복원합니다.")},
        confirmButton={TextButton(onClick={confirm=false;restore()}) { Text("복원") }},
        dismissButton={TextButton(onClick={confirm=false}) { Text("취소") }})
}
