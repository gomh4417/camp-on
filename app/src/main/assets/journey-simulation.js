// Local route animation; never writes membership, observed location or vehicle controls.
(function () {
    const layer=L.layerGroup(),controls=L.control({position:"topright"});
    let members=[],memberKey="",from=null,to=null,picking=null,route=null,requestId=0,abort=null;
    let markers=[],frameId=0,elapsed=0,lastTime=0,stage="editing",active=false,returning=false;
    const reduced=matchMedia("(prefers-reduced-motion: reduce)");
    const root=document.createElement("section");
    root.className="trip-controls";root.setAttribute("aria-label","그룹 이동");
    root.innerHTML='<strong>그룹 이동</strong><p id="trip-info" role="status">그룹 연결 후 출발지와 목적지를 선택하세요.</p><div class="trip-grid"><button id="trip-from">출발지 선택</button><button id="trip-to">목적지 선택</button><button id="trip-center">지도 중심 선택</button></div><button id="trip-confirm">설정 완료</button>';
    controls.onAdd=()=>root;controls.addTo(map);
    L.DomEvent.disableClickPropagation(root);L.DomEvent.disableScrollPropagation(root);
    const el=id=>root.querySelector("#trip-"+id);
    function info(text){el("info").textContent=text;}
    function buttons(){
        el("confirm").disabled=!from||!to||!members.length||!!abort||stage==="moving"||stage==="ready";
        el("confirm").textContent=abort?"경로 확인 중…":stage==="ready"?"설정 완료됨":"설정 완료";
        el("center").disabled=!picking;
        el("from").disabled=el("to").disabled=stage==="moving";
        el("from").textContent=from?"출발지 변경":"출발지 선택";
        el("to").textContent=to?"목적지 변경":"목적지 선택";
    }
    function stopFrame(){cancelAnimationFrame(frameId);frameId=0;}
    function clearRoute(){
        stopFrame();requestId++;if(abort)abort.abort();abort=null;
        route=null;markers=[];elapsed=0;stage="editing";returning=false;layer.clearLayers();
    }
    function pointPin(point,label){
        if(point)L.marker(point).bindTooltip(document.createTextNode(label),{permanent:true}).addTo(layer);
    }
    function showPoints(){layer.clearLayers();pointPin(from,"출발지");pointPin(to,"목적지");}
    function enter(){
        active=true;window.tripSimulationActive=true;tell("그룹 이동");
        if(map.hasLayer(live))map.removeLayer(live);
        if(home&&map.hasLayer(home))map.removeLayer(home);
        layer.addTo(map);
    }
    function choose(point){
        if(!picking||stage==="moving")return;clearRoute();enter();
        if(picking==="from")from=[point.lat,point.lng];else to=[point.lat,point.lng];
        picking=null;showPoints();info(from&&to?"설정 완료를 눌러 경로를 확인하세요.":"다음 지점을 선택하세요.");buttons();
    }
    map.on("click",event=>choose(event.latlng));
    el("from").onclick=()=>{if(stage==="moving")return;clearRoute();if(active)showPoints();picking="from";info("출발지를 지도에서 누르거나 지도 중심을 선택하세요.");buttons();};
    el("to").onclick=()=>{if(stage==="moving")return;clearRoute();if(active)showPoints();picking="to";info("목적지를 지도에서 누르거나 지도 중심을 선택하세요.");buttons();};
    el("center").onclick=()=>choose(map.getCenter());
    function profile(v,index){
        const card=document.createElement("div");card.className="trip-profile";
        const initial=document.createElement("b");initial.className="trip-initial";initial.textContent=Array.from(v.name.trim())[0]||"?";
        const name=document.createElement("span");name.textContent=v.name;card.append(initial,name);
        return L.divIcon({html:card,iconSize:[110,72],iconAnchor:[55,72+(index%3)*6],className:""});
    }
    function routePosition(points,lengths,distance){
        let low=1,high=lengths.length-1;
        while(low<high){const mid=(low+high)>>1;if(lengths[mid]<distance)low=mid+1;else high=mid;}
        const n=low,f=Math.max(0,Math.min(1,(distance-lengths[n-1])/Math.max(.001,lengths[n]-lengths[n-1])));
        return [points[n-1][0]+(points[n][0]-points[n-1][0])*f,points[n-1][1]+(points[n][1]-points[n-1][1])*f];
    }
    function draw(){
        if(!route)return false;
        markers.forEach((m,i)=>{
            const progress=Math.max(0,Math.min(1,(elapsed-i*1200)/60000));
            // Reduced motion keeps stationary pins until each participant reaches the end.
            m.setLatLng(routePosition(route.points,route.lengths,route.total*(reduced.matches?(progress===1?1:0):progress)));
        });
        const done=markers.length>0&&elapsed>=60000+(markers.length-1)*1200;
        info(stage==="ready"?"경로 설정 완료 · 함께 출발하기를 눌러 주세요.":done?(returning?"모두 출발지에 도착했어요.":"모두 도착했어요. 캠핑을 준비해요."):(returning?"출발지로 돌아가는 중 · ":"함께 이동 중 · ")+members.length+"명 · "+Math.min(100,Math.round(elapsed/(60000+Math.max(0,markers.length-1)*1200)*100))+"%");
        return done;
    }
    function tick(now){
        if(stage!=="moving")return;elapsed+=Math.min(100,Math.max(0,now-lastTime));lastTime=now;
        if(draw()){stage=returning?"returned":"arrived";stopFrame();buttons();return;}
        frameId=requestAnimationFrame(tick);
    }
    function play(){if(stage!=="moving"||frameId||document.hidden)return;lastTime=performance.now();frameId=requestAnimationFrame(tick);}
    function renderRoute(){
        enter();layer.clearLayers();L.polyline(route.points,{color:"#2468ee",weight:6}).addTo(layer);
        pointPin(to,"목적지");markers=members.map((v,i)=>L.marker(route.points[0],{icon:profile(v,i)}).addTo(layer));
        map.fitBounds(L.latLngBounds(route.points),{padding:[55,110],animate:false});draw();buttons();
    }
    async function configure(){
        if(!from||!to||!members.length||abort||stage==="moving")return;
        clearRoute();enter();showPoints();picking=null;
        const id=requestId,controller=new AbortController();abort=controller;buttons();
        info("선택한 지점 사이의 도로 경로를 확인하고 있어요.");
        const timer=setTimeout(()=>controller.abort(),12000);
        try {
            const coordinates=[from,to].map(p=>p[1]+","+p[0]).join(";");
            const response=await fetch("https://router.project-osrm.org/route/v1/driving/"+coordinates+"?overview=full&geometries=geojson",{signal:controller.signal});
            if(!response.ok)throw Error("route");
            const data=await response.json();if(id!==requestId)return;
            const points=data.routes?.[0]?.geometry?.coordinates?.map(p=>[p[1],p[0]]);
            if(data.code!=="Ok"||!points||points.length<2||points.some(p=>!Number.isFinite(p[0])||!Number.isFinite(p[1])))throw Error("route");
            let total=0;const lengths=points.map((p,i)=>{if(i)total+=map.distance(points[i-1],p);return total;});
            if(total<1)throw Error("route");
            route={points,lengths,total};stage="ready";renderRoute();
        } catch(e){if(id===requestId)info("경로를 찾지 못했어요. 도로 위 지점을 다시 선택해 주세요.");}
        finally{clearTimeout(timer);if(id===requestId){abort=null;buttons();}}
    }
    el("confirm").onclick=configure;
    window.startGroupTrip=()=>{
        if(stage!=="ready"||!route||!members.length)return false;
        stage="moving";elapsed=0;draw();buttons();play();return true;
    };
    // Snapshot survives map expansion and opening group messages; separate from observed vehicle state.
    window.groupTripSnapshot=()=>({from,to,route,elapsed,stage,members,returning});
    window.restoreGroupTrip=s=>{
        if(!s||!Array.isArray(s.members))return;
        stopFrame();returning=s.returning===true;
        members=s.members;memberKey=JSON.stringify(members);from=s.from;to=s.to;route=s.route;elapsed=Number(s.elapsed)||0;
        stage=route&&["ready","moving","arrived","returned"].includes(s.stage)?s.stage:"editing";
        if(route){renderRoute();play();}else if(from||to){enter();showPoints();buttons();}
    };
    document.addEventListener("visibilitychange",()=>{if(document.hidden)stopFrame();else play();});
    window.updateTripMembers=items=>{
        const key=JSON.stringify(items);if(key===memberKey)return;
        memberKey=key;members=items;
        if(active){clearRoute();showPoints();info(items.length?"참여자가 바뀌었어요. 경로 설정을 다시 완료해 주세요.":"그룹 연결을 확인해 주세요.");}
        else info(items.length?"참여자 "+items.length+"명 · 출발지와 목적지를 선택하세요.":"그룹 연결 후 출발지와 목적지를 선택하세요.");
        buttons();
    };
    buttons();
})();
