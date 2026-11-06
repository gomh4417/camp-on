package com.example.pleos_demo

import org.junit.Assert.*
import org.junit.Test

class EnergyTest {
    private fun budget(capacity: Double?=80.0, soc:Double?=72.0, distance:Double?=120.0,
        efficiency:Double?=5.0, reserve:Double?=8.0, equipment:List<Equipment> = emptyList()) =
        Energy.calculate(capacity,soc,distance,efficiency,reserve,5.0,equipment)
    @Test fun normalBudget() {
        val b=budget()!!
        assertEquals(57.6,b.current,.001)
        assertEquals(24.0,b.home,.001)
        assertEquals(25.6,b.available,.001)
    }
    @Test fun returnShortfallSurvivesClamp() {
        val b=budget(soc=10.0)!!
        assertEquals(0.0,b.available,0.0)
        assertEquals(16.0,b.homeShortfall,.001)
        assertEquals(24.0,b.protectedShortfall,.001)
    }
    @Test fun reserveShortfallIsSeparate() {
        val b=budget(soc=35.0)!!
        assertEquals(0.0,b.homeShortfall,0.0)
        assertEquals(4.0,b.protectedShortfall,0.0)
    }
    @Test fun inverterLossOnlyOnEquipment() {
        val b=budget(equipment=listOf(Equipment("heater",1000.0,2.0,.5)))!!
        assertEquals(5+1/.9,b.consumption,.001)
    }
    @Test fun overBudget() {
        assertTrue(budget(equipment=listOf(Equipment("heater",2000.0,20.0,1.0)))!!.overBudget>0)
    }
    @Test fun rejectMissingInvalidAndUnits() {
        assertNull(budget(efficiency=0.0)); assertNull(budget(capacity=null))
        assertNull(budget(soc=101.0)); assertNull(budget(distance=-1.0))
        assertNull(budget(efficiency=Double.NaN)); assertNull(budget(reserve=Double.POSITIVE_INFINITY))
        assertNull(budget(equipment=listOf(Equipment("bad",100.0,1.0,50.0))))
    }
    @Test fun forecastStopsEquipmentAtItsDuration() {
        val points=Energy.forecast(100.0,50.0,4.0,1.0,listOf(Equipment("heater",900.0,1.0)))!!
        assertEquals(50.0,points.first().kwh,.001)
        assertEquals(48.0,points.first { it.hours==1.0 }.kwh,.001)
        assertEquals(45.0,points.last().kwh,.001)
        val b=Energy.calculate(100.0,50.0,0.0,5.0,0.0,4.0,listOf(Equipment("heater",900.0,1.0)))!!
        assertEquals(b.departure,points.last().kwh,.001)
    }
    @Test fun forecastRejectsUnknownAndClampsEmptyBattery() {
        assertNull(Energy.forecast(null,50.0,1.0,1.0,emptyList()))
        assertNull(Energy.forecast(100.0,101.0,1.0,1.0,emptyList()))
        assertNull(Energy.forecast(100.0,50.0,1.0,Double.NaN,emptyList()))
        assertEquals(0.0,Energy.forecast(10.0,10.0,4.0,2.0,emptyList())!!.last().kwh,0.0)
    }
    @Test fun preserveManualChange() {
        assertTrue(canRestore(22,22)); assertFalse(canRestore(26,22))
        assertFalse(canRestore(null,null)); assertFalse(canRestore(22,null))
    }
}
