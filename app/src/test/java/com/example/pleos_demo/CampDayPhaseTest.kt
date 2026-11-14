package com.example.pleos_demo

import org.junit.Assert.*
import org.junit.Test

class CampDayPhaseTest {
    @Test fun allBoundariesAndMidnight() {
        val cases=mapOf(0 to CampDayPhase.NIGHT,4 to CampDayPhase.NIGHT,5 to CampDayPhase.DAWN,10 to CampDayPhase.DAWN,
            11 to CampDayPhase.DAY,15 to CampDayPhase.DAY,16 to CampDayPhase.DUSK,18 to CampDayPhase.DUSK,
            19 to CampDayPhase.NIGHT,23 to CampDayPhase.NIGHT)
        cases.forEach { (hour,expected)->assertEquals(expected,CampDayPhase.atHour(hour)) }
    }
    @Test fun transitionsOnlyForAdjacentPhases() {
        CampDayPhase.entries.forEach { phase->
            assertNotNull(sceneTransition(phase,CampDayPhase.entries[(phase.ordinal+1)%4]))
            assertNull(sceneTransition(phase,phase))
            assertNull(sceneTransition(phase,CampDayPhase.entries[(phase.ordinal+2)%4]))
        }
    }
    @Test fun timezoneUsesLocalClock() {
        val instant=java.time.Instant.parse("2026-11-14T07:00:00Z").toEpochMilli()
        assertEquals(CampDayPhase.DUSK,CampDayPhase.at(instant,java.time.ZoneId.of("Asia/Seoul")))
        assertEquals(CampDayPhase.DAWN,CampDayPhase.at(instant,java.time.ZoneId.of("UTC")))
    }
}
