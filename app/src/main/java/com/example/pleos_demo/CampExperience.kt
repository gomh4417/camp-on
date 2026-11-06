package com.example.pleos_demo

import android.animation.ValueAnimator
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable internal fun CampExperience(s:CampSession,start:()->Unit,apply:()->Unit,plan:()->Unit,edit:(Int)->Unit) {
    val mode=s.selected
    val guide=campingGuide(mode)
    val prefix="guide.${mode.name}."
    val step=(s.fields[prefix+"step"]?.toIntOrNull()?:0).coerceIn(0,2)
    val finished=s.fields[prefix+"finished"]=="true"
    val still=s.fields["guide.still"]=="true" || !ValueAnimator.areAnimatorsEnabled()
    Column(verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            CampMode.visible.forEach { m->
                OutlinedButton(onClick={s.selectMode(m)},enabled=!s.busy,modifier=Modifier.weight(1f).heightIn(min=88.dp),
                    shape=RoundedCornerShape(20.dp),colors=ButtonDefaults.outlinedButtonColors(
                        containerColor=if(mode==m)Color(0xFFE9F1FF) else Color.White,contentColor=CampInk)) {
                    Column(horizontalAlignment=Alignment.CenterHorizontally) {
                        ModeArt(m,Modifier.size(52.dp))
                        Text(m.label,style=MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            Caption("CAMP:ON과 함께하는 캠핑")
            Text(guide.title,style=MaterialTheme.typography.headlineMedium)
            Caption(guide.promise)
            Crossfade(targetState=mode,animationSpec=tween(if(still)0 else 250),label="camping scene") { scene->
                StoryScene(scene,step,still)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                guide.steps.forEachIndexed { index,item->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                        .background(if(index==step)Color(0xFFE9F1FF) else CampBackground).padding(12.dp),
                        verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(if(index<step || finished)"✓" else "0${index+1}",color=CampAccent,style=MaterialTheme.typography.titleLarge)
                        Text(item.title,style=MaterialTheme.typography.labelLarge)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Caption(if(finished)"오늘의 ${mode.label} 가이드 완료" else "${step+1}/3 · 하나씩 준비해요")
                Spacer(Modifier.weight(1f))
                TextButton(onClick={s.edit("guide.still",(!still).toString())}) { Text(if(still)"그림 움직임 켜기" else "그림 움직임 끄기") }
            }
        }
        Panel(Modifier.fillMaxWidth()) {
            val item=guide.steps[step]
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
                ModeArt(mode,Modifier.size(64.dp))
                Column(Modifier.weight(1f)) {
                    Text(if(finished)"잘 마쳤어요!" else item.title,style=MaterialTheme.typography.titleLarge)
                    Text(if(finished)"다음 캠핑 시간으로 이어가 볼까요?" else item.instruction)
                }
            }
            if(!finished) {
                if(step==0) {
                    val added=s.equipmentIds.any { s.fields["name$it"]==guide.equipment.name }
                    SecondaryAction(if(added)"✓ ${guide.equipment.name} 계획에 포함됨" else "+ ${guide.equipment.name} 사용 계획에 담기",
                        !added,Modifier.fillMaxWidth()) {
                        if(s.equipmentIds.none { s.fields["name$it"]==guide.equipment.name }) s.addEquipment(guide.equipment)
                    }
                    Caption("${guide.equipment.watts.toInt()} W · ${guide.equipment.hours.fmt()}시간 가정값. 실제 제품에 맞게 수정할 수 있어요.")
                    TextButton(onClick=plan) { Text("다른 장비·사용 시간 고르기") }
                }
                if(step==1) {
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        PrimaryAction(if(s.active)"차량 환경 미리보기" else "캠핑 세션 시작",
                            if(s.active)s.canControl else s.canControl,Modifier.weight(1f),
                            if(s.active)apply else start)
                        SecondaryAction("세부 조절",modifier=Modifier.weight(1f)) { edit(if(mode==CampMode.SLEEP)1 else 0) }
                    }
                    Caption(if(s.active)"현재 차량: ${s.mode.label} · ${s.modeStatus}" else "차량 환경 적용은 캠핑 세션 시작 후 이용해요.")
                    GuideTimer(s,prefix,guide)
                }
                item.checks.forEachIndexed { index,label->
                    val key=prefix+"check.$step.$index"
                    val checked=s.fields[key]=="true"
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CampBackground)
                        .toggleable(checked,role=Role.Checkbox,onValueChange={s.edit(key,it.toString())})
                        .heightIn(min=64.dp).padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                        Checkbox(checked,null);Spacer(Modifier.width(12.dp));Text(label,Modifier.weight(1f))
                    }
                }
                val complete=item.checks.indices.all { s.fields[prefix+"check.$step.$it"]=="true" }
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    if(step>0) SecondaryAction("이전",modifier=Modifier.weight(1f)) { s.edit(prefix+"step",(step-1).toString()) }
                    PrimaryAction(if(step==2)"준비·정리 완료" else "확인하고 다음으로",complete,Modifier.weight(1f)) {
                        if(step<2)s.edit(prefix+"step",(step+1).toString()) else s.edit(prefix+"finished","true")
                    }
                }
            } else {
                if(mode==CampMode.SLEEP) PrimaryAction("취침 화면으로",modifier=Modifier.fillMaxWidth()) { s.updateMinimized(true) }
                PrimaryAction("${guide.next.label} 살펴보기",modifier=Modifier.fillMaxWidth()) { s.selectMode(guide.next) }
                TextButton(onClick={
                    s.fields.keys.filter { it.startsWith(prefix) }.toList().forEach { s.fields.remove(it) };s.save()
                }) { Text("이 가이드 다시 시작") }
            }
            Caption("확인 항목은 직접 점검한 기록이에요. 차량·외부 기기의 작동 여부를 대신 확인하지 않아요.")
        }
    }
}

@Composable private fun StoryScene(mode:CampMode,step:Int,still:Boolean) {
    val drawable=when(mode) { CampMode.COOK->R.drawable.camp_story_cook;CampMode.SLEEP->R.drawable.camp_story_sleep;else->R.drawable.camp_story_relax }
    Box(Modifier.fillMaxWidth().height(300.dp).clip(RoundedCornerShape(22.dp))
        .background(if(mode==CampMode.SLEEP)Color(0xFFEEF0FA) else Color(0xFFFFF8EE))) {
        Image(painterResource(drawable),when(mode) {
            CampMode.COOK->"전기차 곁 야외 식탁에서 요리하는 캠핑 곰"
            CampMode.SLEEP->"전기차 안 침낭에서 쉬는 캠핑 곰"
            else->"전기차 곁 의자에서 음료와 함께 쉬는 캠핑 곰"
        },Modifier.fillMaxSize().padding(10.dp),contentScale=ContentScale.Fit)
        if(!still && step==1) SceneMotion(mode)
        Surface(Modifier.align(Alignment.TopStart).padding(12.dp),shape=RoundedCornerShape(12.dp),color=Color.White) {
            Text(listOf("차와 함께 준비","캠핑을 즐기는 중","다음 쉼을 준비")[step],Modifier.padding(horizontal=14.dp,vertical=8.dp),
                style=MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable private fun SceneMotion(mode:CampMode) {
    val transition=rememberInfiniteTransition(label="gentle camping feedback")
    val phase by transition.animateFloat(0f,1f,infiniteRepeatable(tween(2600,easing=LinearEasing)),label="camp effect")
    Canvas(Modifier.fillMaxSize()) {
        repeat(3) { index->
            val p=(phase+index/3f)%1f
            if(mode==CampMode.SLEEP) {
                val x=size.width*(.72f+index*.055f);val y=size.height*.23f
                val alpha=.15f+.45f*(sin(p*Math.PI).toFloat())
                drawCircle(CampAccent.copy(alpha=alpha),3.dp.toPx(),Offset(x,y-index*8.dp.toPx()))
            } else {
                val x=size.width*.5f+index*12.dp.toPx()+sin(p*6.28f)*4.dp.toPx()
                val y=size.height*(.43f-p*.18f)
                drawLine(Color(0xFFB9B3A8).copy(alpha=(1-p)*.6f),Offset(x,y),Offset(x+3.dp.toPx(),y-12.dp.toPx()),3.dp.toPx(),StrokeCap.Round)
            }
        }
    }
}
@Composable private fun GuideTimer(s:CampSession,prefix:String,guide:CampGuide) {
    val deadline=s.fields[prefix+"deadline"]?.toLongOrNull()?:0L
    val seconds=guideSecondsRemaining(deadline,s.now)
    val duration=(s.fields[prefix+"duration"]?.toLongOrNull()?:guide.timerMinutes.toLong())*60
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CampBackground).padding(20.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(guide.timerLabel,style=MaterialTheme.typography.titleMedium)
        Text(if(deadline==0L)"${guide.timerMinutes}분" else if(seconds==0L)"시간이 되었어요" else "%02d:%02d".format(seconds/60,seconds%60),
            style=MaterialTheme.typography.headlineLarge,color=CampAccent)
        LinearProgressIndicator(progress={if(deadline==0L)0f else (1-seconds.toFloat()/duration.coerceAtLeast(1)).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth())
        if(deadline==0L) PrimaryAction("타이머 시작") {
            if((s.fields[prefix+"deadline"]?.toLongOrNull()?:0L)==0L) {
                s.edit(prefix+"duration",guide.timerMinutes.toString())
                s.edit(prefix+"deadline",(System.currentTimeMillis()+guide.timerMinutes*60_000L).toString())
            }
        } else SecondaryAction(if(seconds==0L)"확인·초기화" else "타이머 취소") { s.edit(prefix+"deadline","0") }
        Caption("앱 화면에서 시간을 안내해요. 타이머가 끝나도 기기는 꺼지지 않아요.")
    }
}
