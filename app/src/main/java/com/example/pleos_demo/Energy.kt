package com.example.pleos_demo

data class Equipment(val name: String, val watts: Double, val hours: Double, val duty: Double)
data class Budget(val current: Double, val home: Double, val reserve: Double, val available: Double,
    val homeShortfall: Double, val protectedShortfall: Double, val consumption: Double) {
    val overBudget get() = (consumption - available).coerceAtLeast(0.0)
}
object Energy {
    fun calculate(capacity: Double?, soc: Double?, distance: Double?, efficiency: Double?,
        reserve: Double?, vehicleKwh: Double?, equipment: List<Equipment>, inverterEfficiency: Double = 0.9): Budget? {
        val values = listOf(capacity, soc, distance, efficiency, reserve, vehicleKwh)
        if (values.any { it == null || !it.isFinite() || it < 0 }) return null
        val c = capacity!!; val s = soc!!; val d = distance!!; val e = efficiency!!; val r = reserve!!; val v = vehicleKwh!!
        if (c <= 0 || s > 100 || e <= 0 || !inverterEfficiency.isFinite() || inverterEfficiency <= 0 || inverterEfficiency > 1) return null
        if (equipment.any { !it.watts.isFinite() || !it.hours.isFinite() || !it.duty.isFinite() ||
                    it.watts < 0 || it.hours < 0 || it.duty !in 0.0..1.0 }) return null
        val current = c * s / 100
        val home = d / e
        val consumption = v + equipment.sumOf { it.watts / 1000 * it.hours * it.duty } / inverterEfficiency
        if (!current.isFinite() || !home.isFinite() || !consumption.isFinite()) return null
        return Budget(current, home, r, (current-home-r).coerceAtLeast(0.0),
            (home-current).coerceAtLeast(0.0), (home+r-current).coerceAtLeast(0.0), consumption)
    }
}
