package com.example.pleos_demo

import org.junit.Assert.*
import org.junit.Test

class CampIdeasTest {
    @Test fun recommendationsUseOwnedEquipmentAndSelectedMode() {
        val equipment=listOf(Equipment("휴대용 전자 렌지",1400.0,.1),Equipment("AIR FRYER",1500.0,.5))
        assertEquals(listOf("popcorn","chicken"),matchingIdeas(CampMode.COOK,equipment).map { it.id })
        assertTrue(matchingIdeas(CampMode.SLEEP,equipment).isEmpty())
        assertTrue(matchingIdeas(CampMode.COOK,emptyList()).isEmpty())
    }

    @Test fun planningExtendsOnlySelectedDeviceAndIsIdempotent() {
        val equipment=listOf(Equipment("냉장고",60.0,8.0,.4),Equipment("에어프라이어",1500.0,.1))
        val planned=plannedIdeaEquipment(equipment,1,30)!!
        assertEquals(equipment[0],planned[0])
        assertEquals(.5,planned[1].hours,0.0)
        assertEquals(planned,plannedIdeaEquipment(planned,1,30))
        assertEquals(planned,plannedIdeaEquipment(planned,1,4))
        assertEquals(.942,Energy.equipmentKwh(planned)!!,1e-9)
    }

    @Test fun invalidInputsStayUnknownAndPeakPowerIgnoresDutyCycle() {
        val equipment=listOf(Equipment("냉장고",60.0,8.0,.4,2.0),Equipment("전자레인지",1400.0,.1))
        assertEquals(1520.0,simultaneousWatts(equipment)!!,0.0)
        assertNull(plannedIdeaEquipment(equipment,-1,30))
        assertNull(plannedIdeaEquipment(equipment,2,30))
        assertNull(plannedIdeaEquipment(equipment,1,0))
        assertNull(plannedIdeaEquipment(equipment,1,1441))
        assertNotNull(plannedIdeaEquipment(equipment,1,1440))
        assertNull(simultaneousWatts(listOf(Equipment("오류",Double.NaN,1.0))))
        assertNull(plannedIdeaEquipment(listOf(Equipment("오류",100.0,-1.0)),0,10))
    }
}
