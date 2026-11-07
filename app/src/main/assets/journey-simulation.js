// Local visual rehearsal only: no membership, vehicle-state or control API writes.
(function () {
    const layer=L.layerGroup();
    const controls=L.control({position:"topright"});
    let members=[],memberKey="",from=null,to=null,picking=null,route=null,requestId=0,abort=null;
    let markers=[],frameId=0,elapsed=0,lastTime=0,running=false,active=false;
    const reduced=matchMedia("(prefers-reduced-motion: reduce)");
    const root=document.createElement("section");
    root.className="trip-controls";root.setAttribute("aria-label","그룹 이동 시연");
    root.innerHTML='<strong>그룹 이동 시연</strong><p id="trip-info" role="status">그룹 연결 후 출발지와 목적지를 선택하세요.</p><div class="trip-grid"><button id="trip-from">출발지 선택</button><button id="trip-to">목적지 선택</button><button id="trip-center">지도 중심 선택</button><button id="trip-start">함께 이동</button><button id="trip-stop">일시 정지</button><button id="trip-exit">시연 끝내기</button></div><label><input id="trip-still" type="checkbox"> 정지 화면으로 보기</label><button id="trip-step">다음 위치 보기</button><small>선택한 위치로 도로 경로를 불러와요.<br>실제 위치·도착 상태·차량 설정은 바뀌지 않아요.</small>';
    controls.onAdd=()=>root;controls.addTo(map);
    L.DomEvent.disableClickPropagation(root);L.DomEvent.disableScrollPropagation(root);
    const el=id=>root.querySelector("#trip-"+id);
    el("still").checked=reduced.matches;
    function info(text){el("info").textContent=text;}
    function buttons(){
        el("start").disabled=!from||!to||!members.length||!!abort;
        el("start").textContent=running?"처음부터 다시":"함께 이동";
        el("stop").disabled=!active||el("still").checked||!!abort;
        el("stop").textContent=running?"일시 정지":"계속 이동";
        el("step").disabled=!route||!el("still").checked;
        el("center").disabled=!picking;
        el("from").textContent=from?"출발지 변경":"출발지 선택";
        el("to").textContent=to?"목적지 변경":"목적지 선택";
    }
    function stopFrame(){running=false;cancelAnimationFrame(frameId);frameId=0;buttons();}
    function clearRoute(){
        stopFrame();requestId++;if(abort)abort.abort();abort=null;
        route=null;markers=[];elapsed=0;layer.clearLayers();
    }
    function pointPin(point,label){
        if(point)L.marker(point).bindTooltip(document.createTextNode(label),{permanent:true}).addTo(layer);
    }
    function showPoints(){layer.clearLayers();pointPin(from,"시연 출발지");pointPin(to,"시연 목적지");}
    function enter(){
        active=true;window.tripSimulationActive=true;tell("이동 시연 · 실제 위치 아님");
        if(map.hasLayer(live))map.removeLayer(live);
        if(home&&map.hasLayer(home))map.removeLayer(home);
        layer.addTo(map);
    }
    function choose(point){
        if(!picking)return;clearRoute();enter();
        if(picking==="from")from=[point.lat,point.lng];else to=[point.lat,point.lng];
        picking=null;showPoints();info(from&&to?"참여자 "+members.length+"명 · 함께 이동을 눌러 주세요.":"다음 지점을 선택하세요.");buttons();
    }
    map.on("click",event=>choose(event.latlng));
    el("from").onclick=()=>{clearRoute();if(active)showPoints();picking="from";info("시연 출발지를 지도에서 누르거나 지도 중심을 선택하세요.");buttons();};
    el("to").onclick=()=>{clearRoute();if(active)showPoints();picking="to";info("시연 목적지를 지도에서 누르거나 지도 중심을 선택하세요.");buttons();};
    el("center").onclick=()=>choose(map.getCenter());
    function profile(v,index){
        const card=document.createElement("div");card.className="trip-profile";
        const img=document.createElement("img");img.src=avatars[index%avatars.length];img.alt="";
        const name=document.createElement("span");name.textContent=v.name;card.append(img,name);
        return L.divIcon({html:card,iconSize:[110,72],iconAnchor:[55,72+(index%3)*6],className:""});
    }
    function routePosition(points,lengths,distance){
        let low=1,high=lengths.length-1;
        while(low<high){const mid=(low+high)>>1;if(lengths[mid]<distance)low=mid+1;else high=mid;}
        const n=low,f=Math.max(0,Math.min(1,(distance-lengths[n-1])/Math.max(.001,lengths[n]-lengths[n-1])));
        return [points[n-1][0]+(points[n][0]-points[n-1][0])*f,points[n-1][1]+(points[n][1]-points[n-1][1])*f];
    }
    function draw(){
        if(!route)return;
        markers.forEach((m,i)=>{
            const progress=Math.max(0,Math.min(1,(elapsed-i*1200)/60000));
            m.setLatLng(routePosition(route.points,route.lengths,route.total*progress));
        });
        const done=elapsed>=60000+Math.max(0,markers.length-1)*1200;
        info(done?"이동 시연 완료 · 실제 도착은 변경하지 않았어요.":"이동 시연 · "+members.length+"명 · "+Math.min(100,Math.round(elapsed/600))+"%");
        return done;
    }
    function tick(now){
        if(!running)return;elapsed+=Math.min(100,now-lastTime);lastTime=now;
        if(draw()){stopFrame();return;}frameId=requestAnimationFrame(tick);
    }
    function play(){if(!route||el("still").checked)return;running=true;lastTime=performance.now();buttons();frameId=requestAnimationFrame(tick);}
    async function start(){
        if(!from||!to||!members.length)return;
        clearRoute();enter();showPoints();picking=null;
        const id=requestId;const controller=new AbortController();abort=controller;buttons();
        info("선택한 지점 사이의 도로 경로를 조회하고 있어요.");
        const timer=setTimeout(()=>controller.abort(),12000);
        try {
            const coordinates=[from,to].map(p=>p[1]+","+p[0]).join(";");
            const response=await fetch("https://router.project-osrm.org/route/v1/driving/"+coordinates+"?overview=full&geometries=geojson",{signal:controller.signal});
            if(!response.ok)throw Error("route");
            const data=await response.json();
            if(id!==requestId)return;
            const points=data.routes?.[0]?.geometry?.coordinates?.map(p=>[p[1],p[0]]);
            if(data.code!=="Ok"||!points||points.length<2||points.some(p=>!Number.isFinite(p[0])||!Number.isFinite(p[1])))throw Error("route");
            let total=0;const lengths=points.map((p,i)=>{if(i)total+=map.distance(points[i-1],p);return total;});
            if(total<1)throw Error("route");
            route={points,lengths,total};layer.clearLayers();
            L.polyline(points,{color:"#246beb",weight:6}).addTo(layer);
            pointPin(to,"시연 목적지");
            markers=members.map((v,i)=>L.marker(points[0],{icon:profile(v,i)}).addTo(layer));
            map.fitBounds(L.latLngBounds(points),{padding:[55,110],animate:false});
            draw();
            if(!el("still").checked)play();
            else info("정지 화면 · 다음 위치 보기로 이동 과정을 확인하세요.");
        } catch(e){if(id===requestId)info("경로를 찾지 못했어요. 도로 위 지점을 다시 선택해 주세요.");}
        finally{clearTimeout(timer);if(id===requestId){abort=null;buttons();}}
    }
    el("start").onclick=start;
    el("stop").onclick=()=>running?stopFrame():play();
    el("step").onclick=()=>{elapsed+=10000;draw();};
    el("still").onchange=()=>{stopFrame();buttons();if(route)info("정지 화면 · 다음 위치 보기로 확인하세요.");};
    reduced.addEventListener("change",()=>{if(reduced.matches){el("still").checked=true;stopFrame();buttons();}});
    function exit(){
        clearRoute();picking=null;active=false;window.tripSimulationActive=false;
        map.removeLayer(layer);live.addTo(map);if(home)home.addTo(map);
        info("이동 시연을 끝냈어요. 실제 위치 수신 화면입니다.");tell("위치 수신 대기");buttons();
    }
    el("exit").onclick=exit;
    document.addEventListener("visibilitychange",()=>{if(document.hidden)stopFrame();});
    window.updateTripMembers=items=>{
        const key=JSON.stringify(items);
        if(key===memberKey)return;
        const had=memberKey;memberKey=key;members=items;
        if(active){clearRoute();showPoints();info(items.length?"참여자가 바뀌었어요. 시연을 다시 시작해 주세요.":"그룹 연결을 확인해 주세요. 시연을 중지했어요.");}
        else if(!items.length)info("그룹 연결 후 출발지와 목적지를 선택하세요.");
        else if(!had)info("참여자 "+items.length+"명 · 출발지와 목적지를 선택하세요.");
        buttons();
    };
    buttons();
})();
