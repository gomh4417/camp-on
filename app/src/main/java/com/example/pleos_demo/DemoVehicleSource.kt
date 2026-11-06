package com.example.pleos_demo

import android.content.Context
import kotlinx.coroutines.delay

/** Explicit competition demo. Never used as a fallback for a failed Pleos connection. */
class DemoVehicleSource(context: Context) : VehicleSource {
    private val prefs = context.getSharedPreferences("camp_on_demo_vehicle", Context.MODE_PRIVATE)
    val controls = listOf(
        VehicleControl("demo_power", "공조 전원", ControlKind.POWER, 0.0, 1.0, writable = true),
        VehicleControl("demo_temperature", "설정 온도", ControlKind.TEMPERATURE, 16.0, 30.0, .5, true),
        VehicleControl("demo_fan", "풍량", ControlKind.FAN, 0.0, 8.0, writable = true),
        VehicleControl("demo_ac", "에어컨", ControlKind.AC, 0.0, 1.0, writable = true),
        VehicleControl("demo_recirculation", "내기순환", ControlKind.RECIRCULATION, 0.0, 1.0, writable = true),
        VehicleControl("demo_light", "실내 조명", ControlKind.LIGHT, 0.0, 100.0, 5.0, true),
        VehicleControl("demo_seat", "시트 등받이", ControlKind.SEAT_POSITION, 0.0, 100.0, 5.0, true),
        VehicleControl("demo_seat_temperature", "시트 열선·통풍", ControlKind.SEAT_TEMPERATURE, -3.0, 3.0, writable = true)
    )
    private val defaults = listOf(1.0, 25.0, 2.0, 1.0, 0.0, 60.0, 20.0, 0.0)
    private val values = controls.mapIndexed { i, c -> c.id to prefs.getFloat(c.id, defaults[i].toFloat()).toDouble() }.toMap().toMutableMap()
    var scenario: String = "일반"
        private set
    fun scenario(value: String) { scenario = value }
    override suspend fun read(): VehicleReading {
        delay(120)
        check(scenario != "연결 끊김") { "차량 연결을 확인하세요" }
        val time = System.currentTimeMillis() - if (scenario == "수신 지연") 60_000 else 0
        return VehicleReading(
            soc = if (scenario == "에너지 부족") 30.0 else 75.0, charging = false, cabin = 25.0,
            values = values.toMap(), controls = controls, time = time, source = "시연 데이터",
            observedAt = (controls.map { it.id } + listOf("배터리", "충전", "실내 온도")).associateWith { time }
        )
    }
    override suspend fun parking() = if (scenario == "연결 끊김") ParkingState(null, null)
        else ParkingState(scenario != "주행 중", scenario != "주행 중")
    override suspend fun readControl(id: String): Double? {
        check(scenario != "연결 끊김") { "연결 끊김" }
        return values[id]
    }
    override suspend fun writeControl(id: String, value: Double) {
        val c = controls.first { it.id == id }
        check(parking().allows(c.kind)) { "차량 조작 제한 또는 상태 확인 필요" }
        require(value.isFinite() && value in c.min..c.max)
        delay(200)
        check(!(scenario == "일부 실패" && c.kind == ControlKind.LIGHT)) { "조명 응답 없음" }
        values[id] = value
        prefs.edit().putFloat(id, value.toFloat()).apply()
    }
    override fun close() {}
}
