package com.example.pleos_demo

data class Equipment(val name: String, val watts: Double, val hours: Double, val duty: Double = 1.0, val quantity: Double = 1.0)
data class Budget(val current: Double, val home: Double, val reserve: Double, val available: Double,
    val homeShortfall: Double, val protectedShortfall: Double, val consumption: Double) {
    val overBudget get() = (consumption - available).coerceAtLeast(0.0)
    val margin get() = available - consumption
    val departure get() = (current - consumption).coerceAtLeast(0.0)
    val arrival get() = (departure - home).coerceAtLeast(0.0)
    val continuedShortfall get() = (home + reserve + consumption - current).coerceAtLeast(0.0)
}
data class ForecastPoint(val hours: Double, val kwh: Double)
object Energy {
    /** Equipment starts now and stops after its remaining duration; vehicle load stays constant. */
    fun forecast(capacity: Double?, soc: Double?, hours: Double?, vehicleKw: Double?,
        equipment: List<Equipment>): List<ForecastPoint>? {
        if (listOf(capacity,soc,hours,vehicleKw).any { it==null || !it.isFinite() || it<0 }) return null
        if (capacity!!<=0 || soc!!>100 || equipmentKwh(equipment)==null) return null
        val duration=hours!!
        val times=(listOf(0.0,duration)+equipment.map { it.hours.coerceIn(0.0,duration) }+
            (0..24).map { duration*it/24 }).distinct().sorted()
        return times.map { t->
            val consumed=vehicleKw!!*t+equipmentKwh(equipment,t)!!/.9
            if(!consumed.isFinite()) return null
            ForecastPoint(t,(capacity*soc/100-consumed).coerceAtLeast(0.0))
        }
    }
    fun equipmentKwh(equipment: List<Equipment>, remainingHours: Double = Double.MAX_VALUE): Double? {
        if (!remainingHours.isFinite() || remainingHours < 0) return null
        if (equipment.any { !it.watts.isFinite() || !it.hours.isFinite() || !it.duty.isFinite() ||
                    !it.quantity.isFinite() || it.watts < 0 || it.hours < 0 || it.duty !in 0.0..1.0 ||
                    it.quantity < 1 || it.quantity % 1.0 != 0.0 }) return null
        return equipment.sumOf { it.watts / 1000 * minOf(it.hours, remainingHours) * it.duty * it.quantity }
            .takeIf { it.isFinite() }
    }
    fun calculate(capacity: Double?, soc: Double?, distance: Double?, efficiency: Double?,
        reserve: Double?, vehicleKwh: Double?, equipment: List<Equipment>, inverterEfficiency: Double = 0.9): Budget? {
        val values = listOf(capacity, soc, distance, efficiency, reserve, vehicleKwh)
        if (values.any { it == null || !it.isFinite() || it < 0 }) return null
        val c = capacity!!; val s = soc!!; val d = distance!!; val e = efficiency!!; val r = reserve!!; val v = vehicleKwh!!
        if (c <= 0 || s > 100 || e <= 0 || !inverterEfficiency.isFinite() || inverterEfficiency <= 0 || inverterEfficiency > 1) return null
        val external = equipmentKwh(equipment) ?: return null
        val current = c * (s / 100)
        val home = d / e
        val consumption = v + external / inverterEfficiency
        if (!current.isFinite() || !home.isFinite() || !consumption.isFinite() || !(home+r+consumption).isFinite()) return null
        return Budget(current, home, r, (current-home-r).coerceAtLeast(0.0),
            (home-current).coerceAtLeast(0.0), (home+r-current).coerceAtLeast(0.0), consumption)
    }
}
