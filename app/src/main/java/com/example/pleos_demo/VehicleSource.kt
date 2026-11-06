package com.example.pleos_demo

import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.AreaBounds
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.unit.TemperatureUnit
import kotlinx.coroutines.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class ControlKind(val title: String, val unit: String) {
    POWER("공조 전원",""), TEMPERATURE("목표 온도","°C"), FAN("풍량","단"),
    AC("A/C",""), RECIRCULATION("내기순환",""), LIGHT("조명","%"), SEAT_TEMPERATURE("시트 열선·통풍","단"), SEAT_POSITION("시트 등받이","")
}
data class VehicleControl(val id: String, val label: String, val kind: ControlKind,
    val min: Double, val max: Double, val step: Double = 1.0, val writable: Boolean)
data class VehicleReading(val soc: Double?=null, val charging: Boolean?=null,
    val cabin: Double?=null, val values: Map<String,Double> = emptyMap(),
    val controls: List<VehicleControl> = emptyList(), val errors: Map<String,String> = emptyMap(),
    val time: Long=System.currentTimeMillis(), val source: String="Pleos SDK",
    val observedAt: Map<String,Long> = emptyMap()) {
    fun isFresh(id: String, now: Long) = now - (observedAt[id] ?: time) in 0 until 30_000
}
data class ParkingState(val parked: Boolean?, val unrestricted: Boolean?, val time: Long=System.currentTimeMillis()) {
    val allowsControl get() = parked == true && unrestricted == true && System.currentTimeMillis()-time < 10_000
}
interface VehicleSource {
    suspend fun read(): VehicleReading
    suspend fun parking(): ParkingState
    suspend fun readControl(id: String): Double?
    suspend fun writeControl(id: String, value: Double)
    fun close()
}
private suspend fun <T> response(call: ((T)->Unit,(Exception)->Unit)->Unit): T = withTimeout(6000) {
    suspendCancellableCoroutine { continuation ->
        val ok: (T)->Unit = { if(continuation.isActive) continuation.resume(it) }
        val fail: (Exception)->Unit = { if(continuation.isActive) continuation.resumeWithException(it) }
        try { call(ok,fail) } catch(e: Exception) { fail(e) }
    }
}
private fun CapabilityConfig.readable() = accessType == AccessType.READ || accessType == AccessType.READ_WRITE
private fun CapabilityConfig.writable() = accessType == AccessType.WRITE || accessType == AccessType.READ_WRITE

class PleosVehicleSource(context: Context) : VehicleSource {
    private val vehicle=Vehicle(context.applicationContext).also { it.initialize() }
    private val gate=ParkingGate(context.applicationContext)
    private data class Binding(val control: VehicleControl, val read: suspend ()->Double?, val write: suspend (Double)->Unit)
    private val bindings=linkedMapOf<String,Binding>()
    private var discovered=false
    private var discoveredAt=0L
    private var cabinArea: SeatArea?=null
    private val capabilityErrors=linkedMapOf<String,String>()
    private fun areaName(area: SeatArea) = area.values.joinToString("+") { it.name }
    private fun areaLabel(area: SeatArea) = areaName(area).replace("ROW_1", "앞좌석").replace("ROW_2", "뒷좌석").replace("LEFT", "왼쪽").replace("RIGHT", "오른쪽").replace("CENTER", "가운데").replace("_", " ")
    private fun bind(kind:ControlKind,area:SeatArea,config:CapabilityConfig,min:Double,max:Double,step:Double=1.0,
                     read:suspend ()->Double?,write:suspend (Double)->Unit) {
        if(!config.readable() || !min.isFinite() || !max.isFinite() || min>max) return
        val id=kind.name+":"+areaName(area)
        bindings[id]=Binding(VehicleControl(id,kind.title+" · "+areaLabel(area),kind,min,max,step,config.writable()),read,write)
    }
    private suspend fun discover() {
        bindings.clear(); capabilityErrors.clear()
        val hvac=vehicle.getHVAC()
        suspend fun attempt(name:String,block:suspend ()->Unit) {
            try { block() } catch(e:TimeoutCancellationException) { capabilityErrors[name]="응답 시간 초과" }
            catch(e:CancellationException) { throw e }
            catch(e:Exception) { capabilityErrors[name]=e.message ?: "연결 오류" }
        }
        attempt("온도") {
            val capability=response<Triple<CapabilityConfig,Map<SeatArea,AreaBounds<Float>>,Double>> { ok,fail ->
                hvac.checkHvacTemperatureCapabilities(onComplete={ c,areas,config ->
                    ok(Triple(c,areas,config?.incrementCelsius?.toDouble() ?: 0.5))
                },onFailed=fail)
            }
            capability.second.forEach { (area,bounds) ->
                bind(ControlKind.TEMPERATURE,area,capability.first,bounds.minValue.toDouble(),bounds.maxValue.toDouble(),
                    capability.third.takeIf { it>0 } ?: 0.5,
                    read={ response<Float?> { ok,fail -> hvac.getHvacTemperature(area,TemperatureUnit.CELSIUS,ok,fail) }?.toDouble() },
                    write={ value -> response<Unit> { ok,fail -> hvac.setHvacTemperature(area,value.toFloat(),TemperatureUnit.CELSIUS,{ _,_->ok(Unit) },fail) } })
            }
        }
        attempt("풍량") {
            val (config,areas)=response<Pair<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>> { ok,fail ->
                hvac.checkHvacFanSpeedCapabilities(onComplete={ c,a->ok(c to a) },onFailed=fail)
            }
            areas.forEach { (area,bounds) ->
                bind(ControlKind.FAN,area,config,bounds.minValue.toDouble(),bounds.maxValue.toDouble(),
                    read={ response<Int?> { ok,fail -> hvac.getHvacFanSpeed(area,ok,fail) }?.toDouble() },
                    write={ value -> response<Unit> { ok,fail -> hvac.setHvacFanSpeed(area,value.toInt(),{ _,_->ok(Unit) },fail) } })
            }
        }
        suspend fun switches(kind:ControlKind,
            capabilities: ((CapabilityConfig,List<SeatArea>)->Unit,(Exception)->Unit)->Unit,
            read: (SeatArea,(Boolean?)->Unit,(Exception)->Unit)->Unit,
            write: (SeatArea,Boolean,(SeatArea,Boolean?)->Unit,(Exception)->Unit)->Unit) {
            attempt(kind.title) {
                val (config,areas)=response<Pair<CapabilityConfig,List<SeatArea>>> { ok,fail -> capabilities({ c,a->ok(c to a) },fail) }
                areas.forEach { area ->
                    bind(kind,area,config,0.0,1.0,
                        read={ response<Boolean?> { ok,fail -> read(area,ok,fail) }?.let { if(it) 1.0 else 0.0 } },
                        write={ value -> response<Unit> { ok,fail -> write(area,value==1.0,{ _,_->ok(Unit) },fail) } })
                }
            }
        }
        switches(ControlKind.POWER,{ ok,fail->hvac.checkHvacPowerCapabilities(ok,fail) },hvac::getHvacPowerState,hvac::setHvacPower)
        switches(ControlKind.AC,{ ok,fail->hvac.checkHvacAcCapabilities(ok,fail) },hvac::getHvacAcState,hvac::setHvacAc)
        switches(ControlKind.RECIRCULATION,{ ok,fail->hvac.checkHvacRecirculationCapabilities(ok,fail) },hvac::getHvacRecirculationState,hvac::setHvacRecirculation)
        attempt("조명") {
            val light=vehicle.getLight()
            val (config,areas)=response<Pair<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>> { ok,fail ->
                light.checkAmbientLightBrightnessCapabilities({ c,a->ok(c to a) },fail)
            }
            areas.forEach { (area,bounds) ->
                bind(ControlKind.LIGHT,area,config,bounds.minValue.toDouble(),bounds.maxValue.toDouble(),
                    read={ response<Int?> { ok,fail -> light.getAmbientLightBrightness(area,ok,fail) }?.toDouble() },
                    write={ value -> response<Unit> { ok,fail -> light.setAmbientLightBrightness(area,value.toInt(),{ _,_->ok(Unit) },fail) } })
            }
        }
        attempt("시트 열선·통풍") {
            val seat=vehicle.getSeat()
            val (config,areas)=response<Pair<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>> { ok,fail ->
                seat.checkSeatTemperatureCapabilities({ c,a->ok(c to a) },fail)
            }
            areas.forEach { (area,bounds) ->
                bind(ControlKind.SEAT_TEMPERATURE,area,config,bounds.minValue.toDouble(),bounds.maxValue.toDouble(),
                    read={ response<Int?> { ok,fail->seat.getHvacSeatTemperature(area,ok,fail) }?.toDouble() },
                    write={ value->response<Unit> { ok,fail->seat.setHvacSeatTemperature(area,value.toInt(),{ _,_->ok(Unit) },fail) } })
            }
        }
        // SDK exposes movement commands, not an absolute position setter. Do not simulate a rest position.
        attempt("시트 위치") {
            val seat=vehicle.getSeat()
            val (config,areas)=response<Pair<CapabilityConfig,Map<SeatArea,AreaBounds<Int>>>> { ok,fail ->
                seat.checkSeatBackrestAnglePositionCapabilities({ c,a->ok(c to a) },fail)
            }
            areas.forEach { (area,bounds) ->
                if(config.readable()) {
                    val id=ControlKind.SEAT_POSITION.name+":"+areaName(area)
                    bindings[id]=Binding(VehicleControl(id,"시트 등받이 · "+areaLabel(area),ControlKind.SEAT_POSITION,
                        bounds.minValue.toDouble(),bounds.maxValue.toDouble(),writable=false),
                        read={ response<Int?> { ok,fail->seat.getCurrentSeatBackrestAnglePosition(area,ok,fail) }?.toDouble() },
                        write={ error("위치 복원 미지원") })
                }
            }
        }
        attempt("실내 온도") {
            val (config,areas)=response<Pair<CapabilityConfig,List<SeatArea>>> { ok,fail ->
                hvac.checkHvacTemperatureCurrentCapabilities({ c,a->ok(c to a) },fail)
            }
            cabinArea=areas.firstOrNull().takeIf { config.readable() }
        }
        discovered=bindings.isNotEmpty()
        discoveredAt=System.currentTimeMillis()
    }
    override suspend fun read(): VehicleReading {
        if(!discovered || System.currentTimeMillis()-discoveredAt>60_000) discover()
        return coroutineScope {
            val errors=capabilityErrors.toMutableMap()
            val observedAt=mutableMapOf<String,Long>()
            suspend fun <T> readValue(name:String,block:suspend ()->T):T? =
                try { block().also { if(it!=null) observedAt[name]=System.currentTimeMillis() } } catch(e:TimeoutCancellationException) { errors[name]="응답 시간 초과";null }
                catch(e:CancellationException) { throw e }
                catch(e:Exception) { errors[name]=e.message ?: "조회 실패";null }
            val soc=async {
                readValue("배터리") {
                    val battery=vehicle.getEvBattery()
                    val c=response<CapabilityConfig> { ok,fail->battery.checkEvBatteryStateOfChargeCapabilities(ok,fail) }
                    check(c.readable()) { "배터리 읽기 미지원" }
                    response<Int?> { ok,fail->battery.getEvBatteryStateOfCharge(ok,fail) }?.takeIf { it in 0..100 }?.toDouble()
                }
            }
            val charging=async {
                readValue("충전") {
                    val battery=vehicle.getEvBattery()
                    val c=response<CapabilityConfig> { ok,fail->battery.checkEvChargeSwitchCapabilities(ok,fail) }
                    check(c.readable()) { "충전 상태 읽기 미지원" }
                    response<Boolean?> { ok,fail->battery.getEvChargeSwitchState(ok,fail) }
                }
            }
            val cabin=async { cabinArea?.let { area ->
                readValue("실내 온도") { response<Float?> { ok,fail->vehicle.getHVAC().getHvacTemperatureCurrent(area,TemperatureUnit.CELSIUS,ok,fail) }?.toDouble() }
            } }
            val values=bindings.values.map { b->async { b.control.id to readValue(b.control.id) { b.read() } } }.awaitAll()
                .mapNotNull { (id,value)->value?.takeIf { it.isFinite() }?.let { id to it } }.toMap()
            VehicleReading(soc.await(),charging.await(),cabin.await(),values,bindings.values.map { it.control },errors,observedAt=observedAt)
        }
    }
    override suspend fun parking() = gate.read()
    override suspend fun readControl(id:String) = bindings[id]?.read?.invoke()
    override suspend fun writeControl(id:String,value:Double) {
        check(parking().allowsControl) { "주차 상태에서 조작할 수 있습니다" }
        val binding=bindings[id] ?: error("지원하지 않는 기능")
        check(binding.control.writable) { "읽기 전용 기능" }
        require(value.isFinite() && value in binding.control.min..binding.control.max) { "지원 범위를 벗어난 값" }
        val steps=(value-binding.control.min)/binding.control.step
        require(kotlin.math.abs(steps-kotlin.math.round(steps))<0.001) { "지원 증분 확인 필요" }
        binding.write(value)
    }
    override fun close() { gate.close(); vehicle.release() }
}
