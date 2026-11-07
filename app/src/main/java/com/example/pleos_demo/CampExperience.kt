package com.example.pleos_demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

@Composable internal fun CampExperience(s:CampSession,start:()->Unit,apply:()->Unit,plan:()->Unit,edit:(Int)->Unit) {
    val mode=s.selected
    val selectedId=s.fields["idea.equipment"]
    val owned=s.equipmentIds.zip(s.equipment())
    val selectedEquipment=owned.firstOrNull { it.first==selectedId }?.second
    val ideas=matchingIdeas(mode,selectedEquipment?.let { listOf(it) }?:s.equipment())
    val chosen=ideas.firstOrNull { it.id==s.fields["idea.current"] }

    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            CampMode.visible.forEach { m->
                OutlinedButton(onClick={s.selectMode(m)},enabled=!s.busy,
                    modifier=Modifier.weight(1f).heightIn(min=172.dp),shape=RoundedCornerShape(24.dp),
                    colors=ButtonDefaults.outlinedButtonColors(containerColor=if(mode==m)Color(0xFFEAF2FF) else Color.White)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        ModeArt(m,Modifier.size(96.dp))
                        Text(if(mode==m)"✓ ${m.label}" else m.label,style=MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        if(chosen==null) Panel(Modifier.fillMaxWidth()) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                CampCompanion()
                Text("오늘 사용할 장비를 골라요",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
            }
            Caption("에너지 계획에 등록한 장비로 추천해요.")
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                FilterChip(selected=selectedEquipment==null,onClick={s.edit("idea.equipment","__all__")},
                    label={Text("전체 추천")},modifier=Modifier.heightIn(min=72.dp))
                owned.forEach { (id,equipment)->
                    OutlinedButton(onClick={s.edit("idea.equipment",id)},modifier=Modifier.width(180.dp).heightIn(min=152.dp),
                        shape=RoundedCornerShape(20.dp)) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) {
                            EquipmentArt(equipment.name,Modifier.size(90.dp))
                            Text((if(id==selectedId)"✓ " else "")+equipment.name)
                        }
                    }
                }
            }
            TextButton(onClick=plan,modifier=Modifier.heightIn(min=56.dp)) { Text("등록 장비 관리 · 에너지 계획") }
        }
        if(chosen==null) {
            if(ideas.isEmpty()) Panel(Modifier.fillMaxWidth()) {
                Text(if(owned.isEmpty())"먼저 사용할 장비를 등록해요" else "이 모드에 맞는 장비를 더해볼까요?",style=MaterialTheme.typography.titleLarge)
                Caption(when(mode) {
                    CampMode.COOK->"전자레인지로 팝콘, 에어프라이어로 치킨, 인덕션으로 따뜻한 라면을 만들 수 있어요."
                    CampMode.SLEEP->"전기장판을 등록하면 밤사이 소비와 귀가할 에너지를 함께 살펴봐요."
                    else->"전기포트로 차 한 잔, 프로젝터로 야외 영화관을 준비해 보세요."
                })
                PrimaryAction("에너지 계획에서 장비 등록",modifier=Modifier.fillMaxWidth(),action=plan)
            }
            ideas.forEach { idea->
                Panel(Modifier.fillMaxWidth()) {
                    EquipmentArt(idea.device,Modifier.size(150.dp))
                    Caption("${idea.device} 활용 · 계획용 ${idea.minutes}분")
                    Text(idea.title,style=MaterialTheme.typography.titleLarge)
                    Text(idea.subtitle)
                    val budget=s.ideaBudget(idea,idea.minutes)
                    Caption(if(budget==null)"사용 시간과 에너지 조건을 확인하면 예상 여유를 보여드려요."
                        else "이 활동을 포함한 계획 후 여유 ${budget.margin.fmt()} kWh")
                    PrimaryAction("이렇게 즐기기",!s.busy,Modifier.fillMaxWidth()) {
                        s.edit("idea.current",idea.id)
                    }
                }
            }
        } else {
            SecondaryAction("다른 추천 보기",modifier=Modifier.fillMaxWidth()) { s.edit("idea.current","") }
            IdeaPreparation(s,chosen,start,apply,plan,edit)
        }
        // Keep earlier progress and absolute deadlines accessible after upgrading.
        val legacyPrefix="guide.${mode.name}."
        if(s.fields.keys.any { it.startsWith(legacyPrefix) }) {
            Panel(Modifier.fillMaxWidth()) {
                Text("이전에 진행하던 가이드",style=MaterialTheme.typography.titleMedium)
                val guide=campingGuide(mode)
                val step=(s.fields[legacyPrefix+"step"]?.toIntOrNull()?:0).coerceIn(0,2)
                Caption(if(s.fields[legacyPrefix+"finished"]=="true")"완료 기록" else guide.steps[step].title)
                guide.steps.forEachIndexed { i,item->item.checks.forEachIndexed { j,label->
                    if(s.fields[legacyPrefix+"check.$i.$j"]=="true") Caption("✓ $label")
                } }
                if((s.fields[legacyPrefix+"deadline"]?.toLongOrNull()?:0)>0)
                    IdeaTimer(s,legacyPrefix,guide.timerMinutes,guide.timerLabel)
            }
        }
    }
}

/** Same original character, cropped to its face; no alternate car or plug illustration. */
@Composable private fun CampCompanion() {
    val bitmap=ImageBitmap.imageResource(R.drawable.camp_story_cook)
    Canvas(Modifier.size(80.dp).clip(CircleShape).semantics { contentDescription="캠핑 곰 안내 캐릭터" }) {
        drawImage(bitmap,srcOffset=IntOffset(710,350),srcSize=IntSize(270,270),
            dstSize=IntSize(size.width.toInt(),size.height.toInt()))
    }
}

@Composable private fun IdeaPreparation(s:CampSession,idea:CampIdea,start:()->Unit,apply:()->Unit,plan:()->Unit,edit:(Int)->Unit) {
    val uriHandler=LocalUriHandler.current
    val prefix="idea.${idea.id}."
    val input=s.fields[prefix+"minutes"]?:idea.minutes.toString()
    val minutes=input.toIntOrNull()
    val budget=minutes?.let { s.ideaBudget(idea,it) }
    val prepared=minutes!=null && s.ideaPrepared(idea,minutes)
    val equipment=s.equipment().getOrNull(s.ideaEquipmentIndex(idea))
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            EquipmentArt(idea.device,Modifier.size(180.dp))
            Column(Modifier.weight(1f)) {
                Caption(idea.device)
                Text(idea.title,style=MaterialTheme.typography.headlineSmall)
            }
        }
        Text("준비물",style=MaterialTheme.typography.titleMedium)
        Text(idea.materials)
        idea.instructions.forEachIndexed { i,instruction->
            Row(Modifier.fillMaxWidth().background(CampBackground,RoundedCornerShape(18.dp)).padding(16.dp),
                verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                EquipmentArt(idea.device,Modifier.size(72.dp))
                Column(Modifier.weight(1f)) { Caption("0${i+1}");Text(instruction) }
            }
        }
        if(idea.sourceUrl.isNotBlank()) TextButton(onClick={
            runCatching { uriHandler.openUri(idea.sourceUrl) }.onFailure { s.message="안내를 열 수 없어요. 기기 설명서를 확인해 주세요." }
        },modifier=Modifier.heightIn(min=56.dp)) { Text("조리 안내 출처 보기") }
        OutlinedTextField(input,{s.edit(prefix+"minutes",it)},label={Text("예상 사용 시간 · 분")},
            supportingText={Text("전력 계획용 시간이에요. 실제 조리는 제품 설명서를 따라요.")},
            isError=minutes==null || minutes !in 1..1440,singleLine=true,modifier=Modifier.fillMaxWidth())
        Caption("등록 소비전력 ${equipment?.watts.fmt()} W × ${equipment?.quantity.fmt()}대")
        Caption("모든 등록 장비 동시 사용 시 ${simultaneousWatts(s.equipment()).fmt()} W · 시작 순간 전력 별도 확인")
        Caption("차량·V2L 어댑터·멀티탭의 허용 전력 중 가장 낮은 한도와 비교해 주세요.")
        Text("귀가 에너지까지 함께",style=MaterialTheme.typography.titleMedium)
        Caption("현재 수신된 차량 환경 기준 · 설정 변경 후 소비는 차량 미리보기에서 확인해요.")
        Text(if(budget==null)"계산 조건 확인 필요" else "계획 후 여유 ${budget.margin.fmt()} kWh",
            style=MaterialTheme.typography.titleLarge,color=CampAccent)
        if(budget!=null && budget.continuedShortfall>0)
            Caption("귀가·예비 포함 ${budget.continuedShortfall.fmt()} kWh 부족 · 사용 계획을 줄여 주세요.")
        PrimaryAction(if(prepared)"✓ 실행 계획 반영됨" else "실행",
            !s.busy && !prepared && budget!=null && budget.continuedShortfall==0.0,Modifier.fillMaxWidth()) {
            minutes?.let { if(s.prepareIdea(idea,it)) { if(s.active)apply() else start() } }
        }
        if(prepared) TextButton(onClick={s.edit("idea.prepared","")},enabled=!s.busy,
            modifier=Modifier.heightIn(min=56.dp)) { Text("새 활동 실행") }
        TextButton(onClick=plan,modifier=Modifier.heightIn(min=56.dp)) { Text("장비·시간 수정 · 에너지 계획") }
    }
    Panel(Modifier.fillMaxWidth()) {
        Text("차는 쉴 공간으로 준비",style=MaterialTheme.typography.titleLarge)
        Caption("선택한 ${idea.mode.label}의 공조·조명·시트 변경값을 확인하고 적용해요.")
        PrimaryAction(if(s.active)"차량 설정 미리보기" else "캠핑 세션 시작",
            prepared && s.canControl,Modifier.fillMaxWidth(),if(s.active)apply else start)
        if(!s.canControl) Caption("주차·차량 연결 상태를 확인하면 차량 준비를 진행할 수 있어요.")
        Caption(if(s.active)"차량: ${s.mode.label} · ${s.modeStatus}" else "세션 시작은 현재 설정을 저장하며, 차량 설정은 이후 별도로 적용해요.")
        if(s.results.isNotEmpty()) s.results.toMap().forEach { (id,result)->
            Caption("${s.reading.controls.find { it.id==id }?.label?:id} · $result")
        }
        SecondaryAction("차량 세부 조절",modifier=Modifier.fillMaxWidth()) { edit(0) }
        Text("직접 준비할 것",style=MaterialTheme.typography.titleMedium)
        listOf("기기 명판과 V2L 허용 전력 확인","V2L 연결·전원과 기기 설치 상태 확인").forEachIndexed { i,label->
            val key=prefix+"check.$i"
            val checked=s.fields[key]=="true"
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CampBackground)
                .toggleable(checked,role=Role.Checkbox,onValueChange={s.edit(key,it.toString())})
                .heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked,null);Spacer(Modifier.width(8.dp));Text(label,Modifier.weight(1f))
            }
        }
        Caption("체크는 직접 확인한 기록이에요. 앱에서 V2L이나 외부 기기를 켜지는 않아요.")
        IdeaTimer(s,prefix,minutes?.takeIf { it in 1..1440 }?:idea.minutes,"사용 시간 알림")
    }
}

@Composable private fun IdeaTimer(s:CampSession,prefix:String,minutes:Int,label:String) {
    val deadline=s.fields[prefix+"deadline"]?.toLongOrNull()?:0L
    val seconds=guideSecondsRemaining(deadline,s.now)
    Text(label,style=MaterialTheme.typography.titleMedium)
    Text(if(deadline==0L)"${minutes}분" else if(seconds==0L)"시간이 되었어요" else "%02d:%02d".format(seconds/60,seconds%60),
        style=MaterialTheme.typography.headlineMedium,color=CampAccent)
    if(deadline==0L) SecondaryAction("타이머 시작",modifier=Modifier.fillMaxWidth()) {
        if((s.fields[prefix+"deadline"]?.toLongOrNull()?:0L)==0L) {
            s.edit(prefix+"duration",minutes.toString())
            s.edit(prefix+"deadline",(System.currentTimeMillis()+minutes*60_000L).toString())
        }
    } else SecondaryAction(if(seconds==0L)"확인·초기화" else "타이머 취소",modifier=Modifier.fillMaxWidth()) {
        s.edit(prefix+"deadline","0")
    }
    Caption("화면에서 시간을 안내해요. 종료돼도 기기는 자동으로 꺼지지 않아요.")
}
