package com.example.pleos_demo

import org.junit.Assert.*
import org.junit.Test

class CampGuideTest {
    @Test fun timerExpiresWithoutNegativeTime() {
        assertEquals(0L,guideSecondsRemaining(0,1000))
        assertEquals(0L,guideSecondsRemaining(1000,1000))
        assertEquals(0L,guideSecondsRemaining(1000,2000))
        assertEquals(1L,guideSecondsRemaining(2000,1001))
    }
    @Test fun persistedDeadlineSurvivesReentry() {
        val deadline=1_000_000L
        assertEquals(60L,guideSecondsRemaining(deadline,940_000))
        assertEquals(20L,guideSecondsRemaining(deadline,980_000))
        assertEquals(0L,guideSecondsRemaining(deadline,1_020_000))
    }
    @Test fun guidesHaveActionableStagesAndValidEquipment() {
        CampMode.visible.forEach { mode->
            val guide=campingGuide(mode)
            assertEquals(3,guide.steps.size)
            assertTrue(guide.steps.all { it.checks.isNotEmpty() && it.instruction.isNotBlank() })
            assertTrue(guide.timerMinutes>0)
            assertNotNull(Energy.equipmentKwh(listOf(guide.equipment)))
        }
    }
}
