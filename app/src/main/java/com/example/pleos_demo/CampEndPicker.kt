package com.example.pleos_demo

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.*
import java.time.format.DateTimeFormatter

/** Figma date/time fields expand in-place, without a platform dialog or losing the caller. */
@Composable internal fun CampEndPicker(value:String,now:Long,modifier:Modifier,onChange:(String)->Unit) {
    val today=Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val selected=runCatching { LocalDateTime.parse(value,CampSession.format) }.getOrDefault(today.plusHours(8))
    var expanded by rememberSaveable { mutableStateOf("") }
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(selected).toString()) }
    val month=YearMonth.parse(monthText)
    fun update(date:LocalDateTime) { onChange(date.format(CampSession.format)) }
    Column(modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
        Text("날짜",fontSize=24.sp,fontWeight=FontWeight.Medium)
        PickerButton(selected.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일")),Modifier.fillMaxWidth().height(82.dp),
            description="종료 날짜 선택") { expanded=if(expanded=="date")"" else "date" }
        if(expanded=="date") {
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                PickerButton("‹",Modifier.size(72.dp),description="이전 달") { monthText=month.minusMonths(1).toString() }
                Text("${month.year}년 ${month.monthValue}월",Modifier.weight(1f),fontSize=28.sp,textAlign=TextAlign.Center)
                PickerButton("›",Modifier.size(72.dp),description="다음 달") { monthText=month.plusMonths(1).toString() }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                listOf("월","화","수","목","금","토","일").forEach { Text(it,Modifier.weight(1f),fontSize=22.sp,textAlign=TextAlign.Center,color=FMuted) }
            }
            val offset=month.atDay(1).dayOfWeek.value-1
            (0 until (offset+month.lengthOfMonth()+6)/7).forEach { week->
                Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    (0..6).forEach { day->
                        val number=week*7+day-offset+1
                        if(number in 1..month.lengthOfMonth()) {
                            val date=month.atDay(number)
                            PickerButton(number.toString(),Modifier.weight(1f).height(64.dp),selected=date==selected.toLocalDate(),
                                enabled=!date.isBefore(today.toLocalDate()),description="${date} 선택") { update(selected.with(date));expanded="" }
                        } else Spacer(Modifier.weight(1f).height(64.dp))
                    }
                }
            }
        }
        Text("종료 시각",fontSize=24.sp,fontWeight=FontWeight.Medium)
        PickerButton(selected.format(DateTimeFormatter.ofPattern("HH : mm")),Modifier.fillMaxWidth().height(118.dp),
            description="종료 시각 선택") { expanded=if(expanded=="time")"" else "time" }
        if(expanded=="time") {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(20.dp)) {
                TimeStepper("시",selected.hour,Modifier.weight(1f),
                    onUp={update(selected.withHour((selected.hour+1)%24))},
                    onDown={update(selected.withHour((selected.hour+23)%24))})
                TimeStepper("분",selected.minute,Modifier.weight(1f),
                    onUp={update(selected.withMinute((selected.minute+1)%60))},
                    onDown={update(selected.withMinute((selected.minute+59)%60))})
            }
            PickerButton("시각 선택 완료",Modifier.fillMaxWidth().height(68.dp)){expanded=""}
        }
        val minutes=Duration.between(today,selected).toMinutes()
        Text(if(minutes>0)"남은 시간  ${minutes/60}시간 ${minutes%60}분" else "현재 이후 날짜·시각을 선택해 주세요",
            fontSize=26.sp,color=FBlue)
    }
}
@Composable private fun TimeStepper(label:String,value:Int,modifier:Modifier,onUp:()->Unit,onDown:()->Unit) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        PickerButton("+",Modifier.fillMaxWidth().height(68.dp),description="${label} 늘리기",onClick=onUp)
        Text(value.toString().padStart(2,'0')+" "+label,fontSize=40.sp,color=FBlue)
        PickerButton("−",Modifier.fillMaxWidth().height(68.dp),description="${label} 줄이기",onClick=onDown)
    }
}
@Composable private fun PickerButton(label:String,modifier:Modifier,selected:Boolean=false,enabled:Boolean=true,
    description:String=label,onClick:()->Unit) {
    Box(modifier.background(if(selected)FBlue else FPale,RoundedCornerShape(18.dp))
        .clickable(enabled,role=Role.Button,onClick=onClick).semantics { contentDescription=description;if(!enabled)disabled() },
        contentAlignment=Alignment.Center) {
        Text(label,fontSize=26.sp,fontWeight=FontWeight.Medium,color=(if(selected)Color.White else FBlue).copy(alpha=if(enabled)1f else .35f))
    }
}
