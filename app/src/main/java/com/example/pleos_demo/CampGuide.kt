package com.example.pleos_demo

internal data class CampGuideStep(val title:String,val instruction:String,val checks:List<String>)
internal data class CampGuide(val title:String,val promise:String,val steps:List<CampGuideStep>,
    val equipment:Equipment,val timerMinutes:Int,val timerLabel:String,val next:CampMode)

internal fun campingGuide(mode:CampMode)=when(mode) {
    CampMode.COOK->CampGuide("차의 에너지로, 따뜻한 한 끼","식사 준비부터 뒷정리까지 함께해요.",
        listOf(
            CampGuideStep("식사 준비","쓸 조리기기를 계획에 담고, 야외 식탁을 준비해요.",listOf("차 밖의 평평한 곳에 조리 공간 마련","차량·어댑터의 허용 전력과 연결 상태 확인")),
            CampGuideStep("맛있게 요리","조리 시간은 타이머에 맡기고, 차 안은 쉴 수 있게 맞춰요.",listOf("조리 중에는 기기 곁에 있기")),
            CampGuideStep("가볍게 정리","식사를 마쳤다면 전기기기를 끄고 다음 쉼을 준비해요.",listOf("조리기기 전원과 연결 해제 확인","식기·음식물·쓰레기 정리"))
        ),Equipment("인덕션",1200.0,.5),15,"조리 타이머",CampMode.RELAX)
    CampMode.SLEEP->CampGuide("차 안에서, 포근한 하룻밤","귀가할 에너지를 남기고 잠자리를 준비해요.",
        listOf(
            CampGuideStep("잠자리 준비","침구와 주변을 정돈하고, 아침까지의 사용 계획을 확인해요.",listOf("침구와 개인 물품 정리","캠핑 종료 시각·귀가 에너지 확인")),
            CampGuideStep("취침 환경","밝기와 온도를 미리 보고, 내게 맞는 취침 환경을 적용해요.",listOf("불필요한 외부 전기기기 끄기")),
            CampGuideStep("편안한 밤","문과 주변을 직접 확인한 뒤 화면을 쉬게 해주세요.",listOf("문·창문·주변 상태 직접 확인","귀가 예상 잔량 다시 확인"))
        ),Equipment("전기장판",60.0,8.0),5,"취침 준비 타이머",CampMode.RELAX)
    else->CampGuide("잠시 멈추고, 나만의 쉼","차 곁에서 쉬는 시간도 계획해요.",
        listOf(
            CampGuideStep("쉼터 만들기","의자와 조명을 준비하고, 필요한 장비만 계획에 담아요.",listOf("의자·테이블을 안정적으로 배치","산책 전이라면 전기기기 상태 확인")),
            CampGuideStep("느긋한 휴식","휴식 시간을 정하고, 차 안의 온도와 빛을 편하게 맞춰요.",listOf("음료와 필요한 물품 준비")),
            CampGuideStep("다음 시간 준비","쉬는 시간을 마무리하고, 잠자리와 남은 에너지를 살펴요.",listOf("사용하지 않는 장비 전원 끄기","남은 배터리와 캠핑 종료 시각 확인"))
        ),Equipment("LED 조명",20.0,4.0),20,"휴식 타이머",CampMode.SLEEP)
}

/** Persist deadlines, not ticking counters. A completed timer never actuates anything. */
internal fun guideSecondsRemaining(deadline:Long,now:Long):Long =
    if(deadline<=0 || deadline<=now) 0 else ((deadline-now)/1000+if((deadline-now)%1000>0)1 else 0)
