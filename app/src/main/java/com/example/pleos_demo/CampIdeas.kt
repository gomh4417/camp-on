package com.example.pleos_demo

internal data class CampIdea(
    val id:String,val mode:CampMode,val device:String,val aliases:List<String>,
    val title:String,val subtitle:String,val minutes:Int,val materials:String,val instructions:List<String>,val sourceUrl:String
) {
    fun matches(name:String)=aliases.any { name.lowercase().replace(" ","").contains(it) }
}
internal val campIdeas=listOf(
    CampIdea("popcorn",CampMode.COOK,"전자레인지",listOf("전자레인지","전자렌지","microwave"),
        "따끈한 팝콘 한 봉지","영화 보기 전, 간단한 캠핑 간식",4,"전자레인지용 팝콘 1봉",
        listOf("전자레인지용 제품을 한 봉지만 넣어요.","포장과 기기의 권장 출력·시간으로 조리해요. 아래 시간은 전력 계획용이에요.","곁에서 상태를 확인하고, 뜨거운 증기를 피해 개봉해요."),
        "https://image-us.samsung.com/SamsungUS/dacorca/products/cooking/microwaves/dcm24s/download/Dacor_Microwaves_24_Convection_Microwave_User_Manual.pdf"),
    CampIdea("chicken",CampMode.COOK,"에어프라이어",listOf("에어프라이어","에어프라이","airfryer"),
        "바삭한 캠핑 치킨","가져온 에어프라이어로 한 끼",30,"닭 조각, 소금·후추, 소량의 오일, 식품용 온도계",
        listOf("닭에 간을 하고 바스켓에 겹치지 않게 놓아요.","기기 설명서의 닭 조각 조리 설정을 사용해요. 30분은 예열을 포함한 전력 계획용 가정이에요.","가장 두꺼운 부분의 중심 온도 74°C 이상을 확인해요. 시간만으로 익었다고 판단하지 않아요."),
        "https://www.foodsafety.gov/food-safety-charts/safe-minimum-internal-temperatures"),
    CampIdea("ramen",CampMode.COOK,"인덕션",listOf("인덕션","전기레인지","induction"),
        "밖에서 끓이는 캠핑 라면","불 대신 전기로 만드는 따뜻한 국물",15,"라면, 물, 인덕션용 냄비",
        listOf("차 밖의 안정된 식탁에 인덕션용 냄비를 놓아요.","제품 포장의 물 양과 조리 시간을 따라 끓여요.","조리가 끝나면 기기 전원을 끄고 충분히 식혀요."),
        ""),
    CampIdea("tea",CampMode.RELAX,"전기포트",listOf("전기포트","전기주전자","kettle"),
        "노을 보며 따뜻한 차","차 한 잔을 위해 필요한 전력만",5,"물, 티백, 컵",
        listOf("포트의 최소·최대 수위 안으로 물을 넣어요.","포트로 물을 끓인 뒤 티백의 우림 안내를 따라요."),
        ""),
    CampIdea("cinema",CampMode.RELAX,"프로젝터",listOf("프로젝터","빔프로젝","projector"),
        "우리만의 캠핑 영화관","영화 한 편과 귀가 잔량을 함께 계획",120,"프로젝터, 스크린, 재생할 콘텐츠",
        listOf("안정된 곳에 프로젝터와 스크린을 설치해요.","빛이 스크린에 닿지 않도록 실내 조명을 미리 보고 조절해요.","다른 캠퍼에게 방해되지 않도록 음량을 맞춰요."),
        ""),
    CampIdea("blanket",CampMode.SLEEP,"전기장판",listOf("전기장판","전기매트","온열매트"),
        "전기장판과 포근한 밤","밤새 쓸 전력과 아침 귀가 여유 확인",480,"전기장판, 침구",
        listOf("장판을 접거나 겹치지 않고 펼쳐요.","제품의 취침 사용 지침과 자체 타이머를 설정해요.","앱은 장판 온도·전원을 제어하지 않아요. 기기에서 직접 확인해요."),
        "")
)
internal fun matchingIdeas(mode:CampMode,equipment:List<Equipment>)=
    campIdeas.filter { it.mode==mode && equipment.any { e->it.matches(e.name) } }

internal fun plannedIdeaEquipment(equipment:List<Equipment>,index:Int,minutes:Int):List<Equipment>? {
    if(index !in equipment.indices || minutes !in 1..1440 || Energy.equipmentKwh(equipment)==null) return null
    return equipment.mapIndexed { i,e->if(i==index)e.copy(hours=maxOf(e.hours,minutes/60.0)) else e }
}
internal fun simultaneousWatts(equipment:List<Equipment>):Double? =
    if(Energy.equipmentKwh(equipment)==null) null else equipment.sumOf { it.watts*it.quantity }.takeIf { it.isFinite() }
