package com.example.pleos_demo

import android.content.Context
import androidx.compose.runtime.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.round

enum class CampMode(val label:String,val subtitle:String,val temperature:Double,val light:Int,val fan:Int,val kw:Double) {
    COOK("요리","야외의 작은 주방",23.0,80,2,0.8),
    RELAX("휴식","잠시, 아무것도 하지 않기",22.0,45,2,0.6),
    MOVIE("영화","우리만의 조용한 극장",22.0,10,1,0.7),
    SLEEP("취침","고요한 밤을 준비해요",21.0,5,1,0.5)
}
data class Preset(val temperature:Double,val light:Int,val fan:Int=2,val power:Boolean=true,val ac:Boolean=true,val recirculate:Boolean=false)
fun canRestore(current: Number?,lastWritten: Number?) =
    current!=null && lastWritten!=null && abs(current.toDouble()-lastWritten.toDouble())<0.05

class CampSession(context:Context,val source:VehicleSource,storageName:String="camp_on_v2") {
    private val prefs=context.getSharedPreferences(storageName,Context.MODE_PRIVATE)
    private val saved=runCatching { JSONObject(prefs.getString("session","{}")!!) }.getOrDefault(JSONObject())
    private val mutex=Mutex()
    val fields=mutableStateMapOf<String,String>()
    val edited=mutableStateMapOf<String,Long>()
    val equipmentIds=mutableStateListOf<String>()
    val presets=mutableStateMapOf<CampMode,Preset>()
    val initial=mutableStateMapOf<String,Double>()
    val written=mutableStateMapOf<String,Double>()
    val results=mutableStateMapOf<String,String>()
    val checks=mutableStateMapOf<String,Boolean>()
    var active by mutableStateOf(saved.optBoolean("active"))
    var mode by mutableStateOf(runCatching { CampMode.valueOf(saved.optString("mode","RELAX")) }.getOrDefault(CampMode.RELAX))
    var selected by mutableStateOf(mode)
    var reading by mutableStateOf(VehicleReading(time=0))
    var parkingState by mutableStateOf(ParkingState(null,null,0))
    var now by mutableLongStateOf(System.currentTimeMillis())
    var busy by mutableStateOf(false)
    var refreshing by mutableStateOf(false)
    var message by mutableStateOf("")
    var connectionError by mutableStateOf("")
    val createdAt=saved.optLong("createdAt",now)
    var estimateTime by mutableLongStateOf(saved.optLong("estimateTime",0))
    val fresh get()=reading.time>0 && now-reading.time<30_000
    val canControl get()=fresh && parkingState.allowsControl && !busy && !refreshing
    val soc get()=reading.soc.takeIf { fresh }
    val current get()=reading.values
    val budget get()=Energy.calculate(number("capacity"),soc,number("distance"),number("efficiency"),number("reserve"),number("vehicle"),equipment())
    val connected get()=reading.time>0 && connectionError.isEmpty()
    init {
        val defaults=mapOf("capacity" to "80","distance" to "120","efficiency" to "5","reserve" to "8",
            "vehicle" to "5","name" to "전기 조리기","watts" to "800","hours" to "1.5","duty" to "0.6",
            "end" to LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
        defaults.forEach { (k,v)->fields[k]=v }
        saved.optJSONObject("fields")?.let { o->o.keys().forEach { fields[it]=o.getString(it) } }
        saved.optJSONObject("edited")?.let { o->o.keys().forEach { edited[it]=o.getLong(it) } }
        val ids=saved.optJSONArray("equipmentIds")
        if(ids==null) equipmentIds.add("") else for(i in 0 until ids.length()) equipmentIds.add(ids.getString(i))
        CampMode.entries.forEach { m->
            val p=saved.optJSONObject("presets")?.optJSONObject(m.name)
            presets[m]=Preset(p?.optDouble("temperature",m.temperature)?:m.temperature,p?.optInt("light",m.light)?:m.light,
                p?.optInt("fan",m.fan)?:m.fan,p?.optBoolean("power",true)?:true,p?.optBoolean("ac",true)?:true,p?.optBoolean("recirculate",false)?:false)
        }
        listOf("initial" to initial,"written" to written).forEach { (key,map)->
            saved.optJSONObject(key)?.let { o->o.keys().forEach { map[it]=o.getDouble(it) } }
        }
        saved.optJSONObject("results")?.let { o->o.keys().forEach { results[it]=o.getString(it).replace("요청 중","응답 확인 필요") } }
        saved.optJSONObject("checks")?.let { o->o.keys().forEach { checks[it]=o.getBoolean(it) } }
    }
    fun number(key:String)=fields[key]?.toDoubleOrNull()
    fun equipment()=equipmentIds.map { id->Equipment(fields["name$id"].orEmpty(),number("watts$id")?:Double.NaN,number("hours$id")?:Double.NaN,number("duty$id")?:Double.NaN) }
    fun addEquipment() {
        val id="_"+System.nanoTime(); equipmentIds.add(id)
        mapOf("name" to "새 장비","watts" to "100","hours" to "1","duty" to "1").forEach { (k,v)->fields[k+id]=v;edited[k+id]=now };save()
    }
    fun removeEquipment(id:String) { equipmentIds.remove(id);save() }
    fun endValid()=runCatching { LocalDateTime.parse(fields["end"],DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")).isAfter(LocalDateTime.now()) }.getOrDefault(false)
    fun edit(key:String,value:String) { fields[key]=value;edited[key]=now;if(key=="end") updateEnergy();save() }
    fun savePreset(p:Preset) { presets[selected]=p;save() }
    fun checkItem(label:String,checked:Boolean) { checks[label]=checked;save() }
    fun target(control:VehicleControl,p:Preset=presets.getValue(selected)):Double {
        val value=when(control.kind) {
            ControlKind.POWER->if(p.power) 1.0 else 0.0
            ControlKind.TEMPERATURE->p.temperature
            ControlKind.FAN->p.fan.toDouble()
            ControlKind.AC->if(p.ac) 1.0 else 0.0
            ControlKind.RECIRCULATION->if(p.recirculate) 1.0 else 0.0
            ControlKind.LIGHT->control.min+(control.max-control.min)*p.light/100.0
        }
        val step=control.step.takeIf { it>0 && it.isFinite() }?:1.0
        return (control.min+round((value-control.min)/step)*step).coerceIn(control.min,control.max)
    }
    suspend fun refreshParking() {
        parkingState=try { source.parking() } catch(e:CancellationException) { throw e } catch(_:Exception) { ParkingState(null,null) }
    }
    suspend fun refresh() {
        if(busy || refreshing) return
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
        catch(e:Exception) { connectionError=e.message?:"차량 연결 실패";reading=VehicleReading(time=0) }
        now=System.currentTimeMillis()
    }
    suspend fun start() {
        if(active || busy || written.isNotEmpty()) return
        busy=true
        try { mutex.withLock {
            readLatest()
            if(!parkingState.allowsControl || !fresh) { message="주차 상태와 차량 연결을 확인하세요";return@withLock }
            if(!endValid()) { message="종료 시각을 설정하세요";return@withLock }
            initial.clear();initial.putAll(current);results.clear();checks.clear()
            active=true;message="캠핑을 시작했습니다";save()
        } } finally { busy=false }
    }
    private suspend fun applyOne(c:VehicleControl,value:Double) {
        if(!c.writable) { results[c.id]="읽기 전용";return }
        if(!source.parking().allowsControl) { results[c.id]="주차 상태 확인 필요";return }
        val before=source.readControl(c.id)
        if(before==null) { results[c.id]="현재 값 확인 불가";return }
        if(canRestore(before,value)) { results[c.id]="설정 유지";return }
        if(!initial.containsKey(c.id)) initial[c.id]=before
        // Persist intent before IPC so a killed process does not lose an uncertain write.
        written[c.id]=value;results[c.id]="요청 중";save()
        source.writeControl(c.id,value)
        repeat(8) {
            val actual=source.readControl(c.id)
            if(canRestore(actual,value)) { results[c.id]="적용 확인";save();return }
            delay(250)
        }
        error("응답 값이 설정값과 다릅니다")
    }
    suspend fun applyMode() {
        if(!active || !canControl) return
        busy=true
        val selectedMode=selected
        val preset=presets.getValue(selectedMode)
        try { mutex.withLock {
            readLatest()
            if(!parkingState.allowsControl || !fresh) { message="주차 상태와 연결을 확인하세요";return@withLock }
            val controls=reading.controls.sortedBy { if(it.kind==ControlKind.POWER && !preset.power) 99 else it.kind.ordinal }
            results.clear()
            for(c in controls) {
                try { applyOne(c,target(c,preset)) }
                catch(e:TimeoutCancellationException) { results[c.id]="응답 시간 초과" }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { results[c.id]="실패 · ${e.message?: "연결 오류"}" }
                save()
            }
            mode=selectedMode;updateEnergy()
            message=if(controls.isNotEmpty() && results.values.all { it=="적용 확인" || it=="설정 유지" }) "모드 적용 완료" else "일부 항목을 적용하지 못했습니다"
            readLatest();save()
        } } finally { busy=false;save() }
    }
    suspend fun restore() {
        if(!canControl || written.isEmpty()) return
        busy=true
        try { mutex.withLock {
            for((id,value) in written.toMap().entries.reversed()) {
                try {
                    check(source.parking().allowsControl) { "주차 상태 확인 필요" }
                    val actual=source.readControl(id)
                    if(!canRestore(actual,value)) { results[id]="수동 변경 유지";continue }
                    val original=initial[id]?:error("시작 값 없음")
                    results[id]="복원 요청 중";save()
                    source.writeControl(id,original)
                    var confirmed=false
                    repeat(8) { if(!confirmed) { confirmed=canRestore(source.readControl(id),original);if(!confirmed) delay(250) } }
                    check(confirmed) { "복원 응답 확인 필요" }
                    written.remove(id);results[id]="복원 확인"
                } catch(e:TimeoutCancellationException) { results[id]="복원 응답 시간 초과" }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { results[id]="복원 실패 · ${e.message}" }
                save()
            }
            readLatest();message=if(written.isEmpty()) "설정 복원 완료" else "남은 항목을 확인하세요";save()
        } } finally { busy=false;save() }
    }
    fun acknowledgeManualRestore() { if(busy)return;written.keys.toList().forEach { results[it]="직접 확인 완료" };written.clear();save() }
    fun finish() { if(busy || checks.values.count { it }<5)return;active=false;message="캠핑을 마쳤습니다";save() }
    private fun updateEnergy() {
        if(edited.containsKey("vehicle"))return
        val end=runCatching { LocalDateTime.parse(fields["end"],DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) }.getOrNull()?:return
        val hours=java.time.Duration.between(LocalDateTime.now(),end).toMinutes().coerceAtLeast(0)/60.0
        fields["vehicle"]=String.format(java.util.Locale.US,"%.2f",hours*mode.kw);estimateTime=System.currentTimeMillis()
    }
    fun save() {
        val ps=JSONObject()
        presets.forEach { (m,p)->ps.put(m.name,JSONObject().put("temperature",p.temperature).put("light",p.light).put("fan",p.fan).put("power",p.power).put("ac",p.ac).put("recirculate",p.recirculate)) }
        val o=JSONObject().put("active",active).put("mode",mode.name).put("createdAt",createdAt).put("estimateTime",estimateTime)
            .put("fields",JSONObject(fields.toMap())).put("edited",JSONObject(edited.toMap())).put("equipmentIds",JSONArray(equipmentIds.toList()))
            .put("initial",JSONObject(initial.toMap())).put("written",JSONObject(written.toMap())).put("results",JSONObject(results.toMap()))
            .put("checks",JSONObject(checks.toMap())).put("presets",ps)
        prefs.edit().putString("session",o.toString()).apply()
    }
}
