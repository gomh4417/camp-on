package com.example.pleos_demo

import android.content.Context
import android.util.Log
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.round

enum class CampMode(val label:String,val subtitle:String,val temperature:Double,val light:Int,val fan:Int,val kw:Double) {
    COOK("요리","함께 만드는 저녁",23.0,80,2,0.65),
    RELAX("휴식","아무것도 하지 않아도 좋은 시간",23.0,40,2,0.50),
    SLEEP("취침","편안한 밤, 내일의 여유",22.0,0,1,0.40),
    MOVIE("영화","이전 프리셋",22.0,10,1,0.55);
    companion object { val visible = listOf(COOK, RELAX, SLEEP) }
}
data class Preset(val temperature:Double,val light:Int,val fan:Int=2,val power:Boolean=true,
    val ac:Boolean=true,val recirculate:Boolean=false,val seat:Int=35,val seatTemperature:Int=0)
data class NamedPreset(val name:String,val mode:CampMode,val preset:Preset,val values:Map<String,Double>)
data class BatteryPoint(val time:Long,val soc:Double,val source:String)
fun canRestore(current:Number?,lastWritten:Number?) = current!=null && lastWritten!=null &&
    current.toDouble().isFinite() && lastWritten.toDouble().isFinite() && abs(current.toDouble()-lastWritten.toDouble())<0.05

class CampSession(context:Context,val source:VehicleSource,storageName:String="camp_on_v2") {
    private val prefs=context.getSharedPreferences(storageName,Context.MODE_PRIVATE)
    private val saved=runCatching { JSONObject(prefs.getString("session","{}")!!) }.getOrDefault(JSONObject())
    private val mutex=Mutex()
    val fields=mutableStateMapOf<String,String>()
    val edited=mutableStateMapOf<String,Long>()
    val equipmentIds=mutableStateListOf<String>()
    val presets=mutableStateMapOf<CampMode,Preset>()
    val namedPresets=mutableStateListOf<NamedPreset>()
    val initial=mutableStateMapOf<String,Double>()
    val written=mutableStateMapOf<String,Double>()
    val results=mutableStateMapOf<String,String>()
    val checks=mutableStateMapOf<String,Boolean>()
    val overrides=mutableStateMapOf<String,Double>()
    val history=mutableStateListOf<BatteryPoint>()
    var active by mutableStateOf(saved.optBoolean("active"))
    var mode by mutableStateOf(parseMode(saved.optString("mode","RELAX")))
    var selected by mutableStateOf(parseMode(saved.optString("selected",mode.name)))
    var minimized by mutableStateOf(saved.optBoolean("minimized"))
    var modeStatus by mutableStateOf(saved.optString("modeStatus","적용 전"))
    var reading by mutableStateOf(VehicleReading(time=0))
    var parkingState by mutableStateOf(ParkingState(null,null,0))
    var now by mutableLongStateOf(System.currentTimeMillis())
    var busy by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var message by mutableStateOf("")
    var connectionError by mutableStateOf("")
    var handoff by mutableStateOf(saved.optString("handoff"))
    var handoffStatus by mutableStateOf(saved.optString("handoffStatus"))
    var conditionVersion by mutableLongStateOf(saved.optLong("conditionVersion",0))
    var createdAt by mutableLongStateOf(saved.optLong("createdAt",now))
    val isDemo get()=source is DemoVehicleSource
    val fresh get()=reading.time>0 && now-reading.time in 0 until 30_000
    val canControl get()=fresh && connected && parkingState.allowsControl && !busy && !refreshing
    val capacityKwh get()=if(fields["capacity"].isNullOrBlank()) reading.nominalCapacityKwh.takeIf { fresh && reading.isFresh("차량 공칭 용량",now) } else number("capacity")
    val soc get()=reading.soc.takeIf { fresh && reading.isFresh("배터리",now) }
    val current get()=reading.values
    val connected get()=reading.time>0 && connectionError.isEmpty()
    val endMillis get()=runCatching { LocalDateTime.parse(fields["end"],format).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
    val remainingHours get()=endMillis?.let { ((it-now)/3_600_000.0).coerceAtLeast(0.0) }
    val baseKwh get()=number("baseKw")?.takeIf { it.isFinite() && it>=0 }?.let { kw->remainingHours?.let { kw*it } }
    // Estimate from confirmed read-back, never from a merely selected mode.
    val climateKw get()=climateEstimate(current)
    private fun climateEstimate(values:Map<String,Double>):Double? {
        if(!fresh) return null
        val powers=reading.controls.filter { it.kind==ControlKind.POWER }
        if(powers.isEmpty()) return number("climateKw")?.takeIf { it.isFinite() && it>=0 }
        if(powers.any { values[it.id]==null || !reading.isFresh(it.id,now) }) return null
        if(powers.all { values[it.id]==0.0 }) return 0.0
        val temperatures=reading.controls.filter { it.kind==ControlKind.TEMPERATURE }
        val fans=reading.controls.filter { it.kind==ControlKind.FAN }
        if((temperatures+fans).any { values[it.id]==null || !reading.isFresh(it.id,now) }) return null
        val temperature=temperatures.mapNotNull { values[it.id] }.average().takeIf { it.isFinite() } ?: return null
        val fan=fans.mapNotNull { values[it.id] }.maxOrNull() ?: return null
        val cabin=reading.cabin?.takeIf { reading.isFresh("실내 온도",now) } ?: return null
        return 0.24 + abs(cabin-temperature)*0.08 + fan*0.05
    }
    val climateKwh get()=climateKw?.let { kw->remainingHours?.let { kw*it } }
    val externalKwh get()=Energy.equipmentKwh(remainingEquipment())?.div(.9)
    val budget get()=calculateBudget(climateKwh)
    val previewBudget get():Budget? {
        val planned=current.toMutableMap()
        plannedControls.forEach { planned[it.id]=target(it) }
        val kw=climateEstimate(planned) ?: return null
        return calculateBudget(remainingHours?.times(kw))
    }
    private fun calculateBudget(climate:Double?):Budget? {
        val base=baseKwh ?: return null
        return Energy.calculate(capacityKwh,soc,number("distance"),number("efficiency"),number("reserve"),
            climate?.plus(base),remainingEquipment())
    }
    init {
        val defaults=mapOf("capacity" to if(isDemo) "110.3" else "", "distance" to "150","efficiency" to "5","reserve" to "11",
            "baseKw" to ".075","climateKw" to ".5","destination" to "집","name" to "냉장고",
            "watts" to "60","hours" to "6","quantity" to "1","duty" to "1",
            "end" to LocalDateTime.now().plusHours(8).format(format))
        fields.putAll(defaults)
        saved.optJSONObject("fields")?.let { o->o.keys().forEach { fields[it]=o.getString(it) } }
        saved.optJSONObject("edited")?.let { o->o.keys().forEach { edited[it]=o.getLong(it) } }
        val ids=saved.optJSONArray("equipmentIds")
        if(ids==null) {
            equipmentIds.add(""); equipmentIds.add("_light")
            fields.putAll(mapOf("name_light" to "외부 조명","watts_light" to "30","hours_light" to "6","quantity_light" to "1","duty_light" to "1"))
        } else for(i in 0 until ids.length()) equipmentIds.add(ids.getString(i))
        CampMode.entries.forEach { m->presets[m]=decodePreset(saved.optJSONObject("presets")?.optJSONObject(m.name),m) }
        listOf("initial" to initial,"written" to written,"overrides" to overrides).forEach { (key,map)->
            saved.optJSONObject(key)?.let { o->o.keys().forEach { keyName->o.optDouble(keyName).takeIf { it.isFinite() }?.let { map[keyName]=it } } }
        }
        saved.optJSONObject("results")?.let { o->o.keys().forEach { results[it]=o.getString(it).replace("요청 중","응답 확인 필요") } }
        saved.optJSONObject("checks")?.let { o->o.keys().forEach { checks[it]=o.getBoolean(it) } }
        saved.optJSONArray("namedPresets")?.let { a->for(i in 0 until a.length()) {
            val o=a.getJSONObject(i);val m=parseMode(o.optString("mode"))
            val v=o.optJSONObject("values")?:JSONObject()
            namedPresets.add(NamedPreset(o.getString("name"),m,decodePreset(o.optJSONObject("preset"),m),
                v.keys().asSequence().mapNotNull { k->v.optDouble(k).takeIf { it.isFinite() }?.let { k to it } }.toMap()))
        } }
        saved.optJSONArray("history")?.let { a->for(i in 0 until a.length()) {
            val o=a.getJSONObject(i);val value=o.optDouble("soc")
            if(value in 0.0..100.0) history.add(BatteryPoint(o.getLong("time"),value,o.optString("source","Pleos SDK")))
        } }
        equipmentIds.forEach { id->if(!edited.containsKey("hours$id")) edited["hours$id"]=now }
        if(mode==CampMode.MOVIE) mode=CampMode.RELAX
        if(selected==CampMode.MOVIE) selected=CampMode.RELAX
    }
    fun number(key:String)=fields[key]?.toDoubleOrNull()
    fun equipment()=equipmentIds.map { id->Equipment(fields["name$id"].orEmpty(),number("watts$id")?:Double.NaN,
        number("hours$id")?:Double.NaN,
        if(fields.containsKey("duty$id")) number("duty$id")?:Double.NaN else 1.0,
        if(fields.containsKey("quantity$id")) number("quantity$id")?:Double.NaN else 1.0) }
    fun remainingEquipment():List<Equipment> {
        val hours=remainingHours ?: return listOf(Equipment("",Double.NaN,0.0))
        return equipmentIds.zip(equipment()).map { (id,e)->
            val elapsed=if(active) ((now-(edited["hours$id"]?:now))/3_600_000.0).coerceAtLeast(0.0) else 0.0
            e.copy(hours=if(e.hours.isFinite() && e.hours>=0) minOf((e.hours-elapsed).coerceAtLeast(0.0),hours) else Double.NaN)
        }
    }
    fun addEquipment() {
        val id="_"+System.nanoTime();equipmentIds.add(id)
        mapOf("name" to "새 장비","watts" to "100","hours" to "1","quantity" to "1","duty" to "1").forEach { (k,v)->fields[k+id]=v;edited[k+id]=now }
        changed();save()
    }
    fun removeEquipment(id:String) { equipmentIds.remove(id);changed();save() }
    fun endValid()=(endMillis?:0)>now
    fun edit(key:String,value:String) { fields[key]=value;edited[key]=now;changed();save() }
    fun selectMode(value:CampMode) { if(busy)return;selected=value;overrides.clear();save() }
    fun savePreset(p:Preset) { if(busy)return;presets[selected]=p;overrides.clear();changed();save() }
    fun setTarget(c:VehicleControl,value:Double) { if(busy)return;overrides[c.id]=normalized(c,value);results.remove(c.id);message="";changed();save() }
    fun checkItem(label:String,checked:Boolean) { checks[label]=checked;save() }
    fun updateMinimized(value:Boolean) { minimized=value;save() }
    fun namedPreset(name:String) {
        val clean=name.trim().take(30)
        if(clean.isEmpty() || !fresh || busy) return
        val supported=reading.controls.filter { it.writable && reading.isFresh(it.id,now) }
            .mapNotNull { c->current[c.id]?.let { c.id to it } }.toMap()
        if(supported.isEmpty()) { message="저장할 차량 설정이 없습니다";return }
        namedPresets.removeAll { it.name==clean }
        namedPresets.add(NamedPreset(clean,mode,presets.getValue(mode),supported));save();message="프리셋을 저장했습니다"
    }
    fun loadPreset(p:NamedPreset) { if(busy)return;selected=p.mode;presets[selected]=p.preset;overrides.clear();overrides.putAll(p.values);changed();save() }
    fun deletePreset(p:NamedPreset) { namedPresets.remove(p);save() }
    val plannedControls get()=reading.controls.filter { it.writable && (it.presetManaged || it.id in overrides) }
    fun target(c:VehicleControl,p:Preset=presets.getValue(selected)):Double=normalized(c,overrides[c.id]?:when(c.kind) {
        ControlKind.POWER->if(p.power) 1.0 else 0.0
        ControlKind.TEMPERATURE->p.temperature
        ControlKind.FAN->p.fan.toDouble()
        ControlKind.AC->if(p.ac) 1.0 else 0.0
        ControlKind.RECIRCULATION->if(p.recirculate) 1.0 else 0.0
        ControlKind.LIGHT->c.min+(c.max-c.min)*p.light/100.0
        ControlKind.SEAT_TEMPERATURE->p.seatTemperature.toDouble()
        ControlKind.SEAT_POSITION->if(c.presetManaged) p.seat.toDouble() else current[c.id]?:c.min
        else->current[c.id]?:c.min
    })
    private fun normalized(c:VehicleControl,v:Double):Double {
        if(c.choices.isNotEmpty()) return c.choices.keys.minBy { abs(it-v) }
        val step=c.step.takeIf { it.isFinite() && it>0 }?:1.0
        return (c.min+round((v-c.min)/step)*step).coerceIn(c.min,c.max)
    }
    suspend fun refreshParking() {
        parkingState=try { source.parking() } catch(e:CancellationException) { throw e } catch(_:Exception) { ParkingState(null,null) }
    }
    suspend fun refresh() {
        if(busy || refreshing)return
        refreshing=true
        try { mutex.withLock { readLatest() } } finally { refreshing=false }
    }
    private suspend fun readLatest() {
        refreshParking()
        try {
            reading=source.read()
            connectionError=if(reading.soc==null && reading.values.isEmpty()) "차량 정보를 가져오지 못했습니다" else ""
        } catch(e:TimeoutCancellationException) { connectionError="차량 응답 시간 초과";reading=VehicleReading(time=0) }
        catch(e:CancellationException) { throw e }
        catch(e:Exception) { connectionError="차량 연결을 확인하세요";reading=VehicleReading(time=0);Log.w("CampSession","Read failed",e) }
        now=System.currentTimeMillis();changed()
        soc?.let { value->
            val last=history.lastOrNull()
            if(last==null || now-last.time>=60_000 || abs(last.soc-value)>=.1) {
                history.add(BatteryPoint(reading.observedAt["배터리"]?:reading.time,value,reading.source))
                while(history.size>720) history.removeAt(0)
                save()
            }
        }
    }
    suspend fun start() {
        if(active || busy || written.isNotEmpty())return
        busy=true
        try { mutex.withLock {
            readLatest()
            if(!parkingState.allowsControl || !fresh) { message="주차 상태와 연결을 확인하세요";return@withLock }
            if(!endValid()) { message="종료 예정 시각을 설정하세요";return@withLock }
            initial.clear();initial.putAll(current.filterKeys { reading.isFresh(it,now) });results.clear();checks.clear()
            equipmentIds.forEach { edited["hours$it"]=now }
            createdAt=now;active=true;modeStatus="적용 전";message="캠핑을 시작했습니다";save()
        } } finally { busy=false }
    }
    private suspend fun persistBeforeWrite() {
        val json=serialize()
        check(withContext(Dispatchers.IO) { prefs.edit().putString("session",json).commit() }) { "변경 기록 저장 실패" }
    }
    private suspend fun applyOne(c:VehicleControl,value:Double) {
        check(source.parking().allowsControl) { "주차 상태 확인 필요" }
        val before=source.readControl(c.id)
        check(before!=null && before.isFinite()) { "현재 값 확인 불가" }
        if(canRestore(before,value)) { results[c.id]="설정 유지";return }
        if(!initial.containsKey(c.id)) initial[c.id]=before
        written[c.id]=value;results[c.id]="요청 중";persistBeforeWrite()
        source.writeControl(c.id,value)
        repeat(8) {
            if(canRestore(source.readControl(c.id),value)) { results[c.id]="적용 확인";save();return }
            delay(250)
        }
        error("차량 반영 확인 필요")
    }
    suspend fun applyMode(onlyEdited:Boolean=false) {
        if(!active || !canControl)return
        busy=true
        val selectedMode=selected
        val preset=presets.getValue(selectedMode)
        val targets=plannedControls.filter { !onlyEdited || it.id in overrides }.associate { it.id to target(it,preset) }
        if(targets.isEmpty()) { busy=false;message="변경할 항목을 선택하세요";return }
        try { mutex.withLock {
            readLatest()
            if(!parkingState.allowsControl || !fresh) { message="주차 상태와 연결을 확인하세요";return@withLock }
            val controls=reading.controls.filter { it.writable && targets.containsKey(it.id) }.sortedBy {
                when {
                    it.kind==ControlKind.POWER->if(targets[it.id]==0.0) 99 else -3
                    it.kind==ControlKind.AUTO->if(targets[it.id]==0.0) -2 else 98
                    it.kind==ControlKind.DUAL->-1
                    else->it.kind.ordinal
                }
            }
            controls.forEach { results[it.id]="대기" }
            for(c in controls) {
                try { applyOne(c,targets.getValue(c.id)) }
                catch(e:TimeoutCancellationException) { results[c.id]="실패 · 응답 시간 초과" }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { results[c.id]="실패 · "+(e.message?:"반영 확인 필요").take(80);Log.w("CampSession","Apply ${c.id}",e) }
                save()
            }
            readLatest()
            controls.filter { results[it.id] in listOf("적용 확인","설정 유지") }.forEach { c ->
                if(!canRestore(current[c.id],targets[c.id]?:Double.NaN)) results[c.id]="실패 · 최종 값 확인 필요"
            }
            val success=controls.isNotEmpty() && controls.all { results[it.id] in listOf("적용 확인","설정 유지") }
            controls.filter { results[it.id] in listOf("적용 확인","설정 유지") }.forEach { c->
                if(overrides[c.id]==targets[c.id]) overrides.remove(c.id)
            }
            if(success && !onlyEdited) mode=selectedMode
            modeStatus=if(success) { if(onlyEdited) "개별 설정 적용" else "적용 완료" } else "일부 적용 · 확인 필요"
            message=modeStatus
            save()
        } } finally { busy=false;save() }
    }
    suspend fun restore() {
        if(!canControl || written.isEmpty())return
        busy=true
        try { mutex.withLock {
            // Restore power ON first and OFF last; dependent climate writes need power.
            val pending=written.toMap().entries.sortedBy { (id,_)->
                if(reading.controls.find { it.id==id }?.kind==ControlKind.POWER) {
                    if(initial[id]==1.0) -1 else 99
                } else 0
            }
            for((id,value) in pending) {
                try {
                    check(source.parking().allowsControl) { "주차 상태 확인 필요" }
                    val actual=source.readControl(id)
                    if(actual==null) { results[id]="현재 값 확인 필요";continue }
                    val original=initial[id]?:error("시작 값 없음")
                    if(canRestore(actual,original)) { written.remove(id);results[id]="복원 확인";save();continue }
                    if(!canRestore(actual,value)) { results[id]="수동 변경 유지";continue }
                    results[id]="복원 요청 중";persistBeforeWrite()
                    source.writeControl(id,original)
                    var confirmed=false
                    repeat(8) { if(!confirmed) { confirmed=canRestore(source.readControl(id),original);if(!confirmed)delay(250) } }
                    check(confirmed) { "복원 응답 확인 필요" }
                    written.remove(id);results[id]="복원 확인"
                } catch(e:TimeoutCancellationException) { results[id]="복원 응답 시간 초과" }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { results[id]="복원 실패 · 확인 필요";Log.w("CampSession","Restore $id",e) }
                save()
            }
            readLatest();message=if(written.isEmpty()) "설정 복원 완료" else "남은 항목을 확인하세요";save()
        } } finally { busy=false;save() }
    }
    fun acknowledgeManualRestore() { if(busy)return;written.keys.toList().forEach { results[it]="직접 확인 완료" };written.clear();save() }
    suspend fun endAndRestore() {
        if(!active || !canControl)return
        if(written.isNotEmpty())restore()
        if(written.isEmpty())finish()
    }
    fun finish() {
        if(busy || written.isNotEmpty())return
        val remaining=remainingEquipment()
        equipmentIds.zip(remaining).forEach { (id,e)->fields["hours$id"]=e.hours.toString();edited["hours$id"]=now }
        active=false;minimized=false;modeStatus="종료";message="캠핑을 마쳤습니다";save()
    }
    fun prepareHandoff():String? {
        val b=budget ?: run { message="귀가 계산에 필요한 값을 확인하세요";return null }
        fun f(v:Double)=String.format(java.util.Locale.KOREA,"%.1f",v)
        handoff="""CAMP FLOW · 귀가 준비
목적지: ${fields["destination"]}
귀가 거리: ${fields["distance"]} km / 전비: ${fields["efficiency"]} km/kWh
종료 예정: ${fields["end"]}
출발 예상 잔여: ${f(b.departure)} kWh
귀가 소요: ${f(b.home)} kWh / 보호 예비: ${f(b.reserve)} kWh
도착 예상: ${f(b.arrival)} kWh / 추가 충전 필요: ${f(b.continuedShortfall)} kWh
최근 수신: ${java.util.Date(reading.time)} / 출처: ${reading.source}
조건 버전: $conditionVersion / 추정값, 경로 설정 미완료"""
        handoffStatus="요약 준비 완료 · 경로 설정 전";save();return handoff
    }
    fun changed() { conditionVersion++ }
    private fun serialize():String {
        val ps=JSONObject();presets.forEach { (m,p)->ps.put(m.name,encodePreset(p)) }
        val named=JSONArray();namedPresets.forEach { named.put(JSONObject().put("name",it.name).put("mode",it.mode.name).put("preset",encodePreset(it.preset)).put("values",JSONObject(it.values))) }
        val points=JSONArray();history.forEach { points.put(JSONObject().put("time",it.time).put("soc",it.soc).put("source",it.source)) }
        return JSONObject().put("active",active).put("mode",mode.name).put("selected",selected.name).put("createdAt",createdAt)
            .put("minimized",minimized).put("modeStatus",modeStatus).put("conditionVersion",conditionVersion)
            .put("fields",JSONObject(fields.toMap())).put("edited",JSONObject(edited.toMap())).put("equipmentIds",JSONArray(equipmentIds.toList()))
            .put("initial",JSONObject(initial.toMap())).put("written",JSONObject(written.toMap())).put("results",JSONObject(results.toMap()))
            .put("checks",JSONObject(checks.toMap())).put("presets",ps).put("namedPresets",named).put("overrides",JSONObject(overrides.toMap()))
            .put("history",points).put("handoff",handoff).put("handoffStatus",handoffStatus).toString()
    }
    fun save() { prefs.edit().putString("session",serialize()).apply() }
    companion object {
        val format:DateTimeFormatter=DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        private fun parseMode(v:String)=runCatching { CampMode.valueOf(v) }.getOrDefault(CampMode.RELAX)
        private fun decodePreset(p:JSONObject?,m:CampMode)=Preset(
            p?.optDouble("temperature",m.temperature)?:m.temperature,p?.optInt("light",m.light)?:m.light,
            p?.optInt("fan",m.fan)?:m.fan,p?.optBoolean("power",true)?:true,p?.optBoolean("ac",true)?:true,
            p?.optBoolean("recirculate",false)?:false,p?.optInt("seat",35)?:35,p?.optInt("seatTemperature",0)?:0)
        private fun encodePreset(p:Preset)=JSONObject().put("temperature",p.temperature).put("light",p.light).put("fan",p.fan)
            .put("power",p.power).put("ac",p.ac).put("recirculate",p.recirculate).put("seat",p.seat).put("seatTemperature",p.seatTemperature)
    }
}
