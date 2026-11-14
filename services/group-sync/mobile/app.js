const $=s=>document.querySelector(s);
const e=s=>String(s??"").replace(/[&<>"']/g,c=>({"&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;","'":"&#39;"}[c]));
const vehicleOptions=()=>state.vehicles.map(v=>[v.id,v.name+" · "+(v.model||"차량")]);
const vehicleName=id=>state?.vehicles.find(v=>v.id===id)?.name || "차량 화면";
let session;
try{session=JSON.parse(localStorage.getItem("camp-on-mobile")||"null");}catch{session=null;}
let state=null,tab="home",entry="landing",filter="all",busy=false,online=false,pending=null;
const drafts={};
const params=new URLSearchParams(location.search);
if(params.has("code")) {const code=params.get("code");drafts.code=code.length===4 && !/[^0-9]/.test(code)?code:"";entry="join";}
const save=()=>localStorage.setItem("camp-on-mobile",JSON.stringify(session));
function notice(text=""){ $("#notice").textContent=text;$("#notice").hidden=!text; }
function field(name,label,type="text",value="",required=false) {
    return '<label for="'+name+'">'+e(label)+'</label><input id="'+name+'" name="'+name+'" type="'+type+'" value="'+e(drafts[name]??value)+'" '+(required?"required":"")+' maxlength="120" '+(type==="number"?'min="0" max="100" step="any" inputmode="decimal"':"")+'>';
}
function codeField(label) {
    return '<label for="code">'+e(label)+'</label><input id="code" name="code" type="text" inputmode="numeric" pattern="[0-9]{4}" minlength="4" maxlength="4" required placeholder="예: 0427" value="'+e(drafts.code??"")+'"><p class="muted">숫자 4자리 · 앞자리가 0이어도 괜찮아요.</p>';
}
function options(values,current){return values.map(([value,label])=>'<option value="'+e(value)+'" '+(value===current?"selected":"")+'>'+e(label)+'</option>').join("");}
function roleSelect(name,current){return '<label for="'+name+'">담당 차량</label><select id="'+name+'" name="'+name+'">'+options(vehicleOptions(),drafts[name]??current)+'</select>';}
function button(label,action,cls="primary"){return '<button type="button" class="'+cls+'" data-action="'+action+'">'+label+'</button>';}
function heading(kicker,title,subtitle=""){return '<div class="eyebrow">'+e(kicker)+'</div><h1>'+e(title)+'</h1>'+(subtitle?'<p class="muted">'+e(subtitle)+'</p>':"");}
function vehicles(){return state.vehicles.map((v,i)=>'<div class="vehicle"><div class="avatar">'+e(v.name.slice(0,1))+'</div><div class="grow"><h3>'+e(v.name)+(v.id===session.vehicleId?" · 나":"")+'</h3><div class="status">'+e(v.status)+" · "+e(v.model||"차량 이름 미입력")+'</div></div><span class="badge">'+e(v.battery==null?"배터리 미입력":v.battery+"% · 직접 입력 · "+(v.updatedAt?new Date(v.updatedAt).toLocaleString("ko-KR"):"시각 미상"))+'</span></div>').join("");}
function render(){
    $("#nav").innerHTML=session?[["home","⌂","시작"],["settings","⚑","설정"],["todos","✓","To-do"],["chat","▤","대화"]].map(([id,icon,label])=>'<button data-tab="'+id+'" '+(tab===id?'aria-current="page"':"")+'><b aria-hidden="true">'+icon+'</b>'+label+'</button>').join(""):"";
    $("#connection").textContent=session?(online?"● 그룹 동기화 중":"연결 확인 중"):"DRIVE TOGETHER";
    if(!session){
        if(entry==="landing")$("#screen").innerHTML='<camp-scene></camp-scene>'+heading("DRIVE TOGETHER","여정을 함께 시작해요","차량과 휴대폰에서 같은 그룹으로 만나세요.")+'<div class="stack">'+button("새 그룹 만들기","create")+button("초대 코드로 참여","join","secondary")+'</div><p class="disclaimer">닉네임과 차량 정보를 등록하면 그룹 차량 목록에 표시돼요.</p>';
        else $("#screen").innerHTML=button("‹ 돌아가기","landing","back")+heading("CAMP:ON",entry==="create"?"우리의 캠핑 그룹":"초대받은 그룹에 참여")+'<form id="entry-form" class="panel">'+field("nickname","닉네임","text","",true)+(entry==="create"?field("groupName","그룹 이름","text","우리의 캠핑",true)+field("destination","캠핑 목적지","text","")+codeField("초대 코드 정하기"):codeField("받은 초대 코드"))+field("model","차량 이름 · 예: 아이오닉 5","text","",true)+field("battery","현재 배터리 (%) · 모르면 비워두세요","number","")+'<p class="muted">입력한 차량 정보만 공유해요. 연결해도 이동 상태는 출발 대기로 유지돼요.</p><button class="primary" '+(busy?"disabled":"")+'>'+(entry==="create"?"그룹 만들기":"차량 등록하고 연결")+'</button></form>';
        return;
    }
    if(!state){$("#screen").innerHTML=heading("CAMP:ON","그룹을 불러오고 있어요")+'<div class="panel">'+button("다시 연결","refresh")+button("다른 그룹으로","logout","text-btn")+'</div>';return;}
    const group=state.group,me=state.vehicles.find(v=>v.id===session.vehicleId),done=state.todos.filter(t=>t.done).length;
    if(tab==="home")$("#screen").innerHTML='<camp-scene></camp-scene>'+heading("OUR JOURNEY",group.name,group.destination)+'<div class="panel"><div class="row"><h2 class="grow">참여 차량 · '+state.memberCount+'대</h2><span class="badge">'+e(vehicleName(session.vehicleId))+'</span></div>'+vehicles()+'</div><div class="panel"><div class="eyebrow">GROUP BRIEFING</div><h2>'+e(state.headline)+'</h2><p>'+e(state.summary)+'</p>'+button("그룹 대화 보기","chat","secondary")+'</div><div class="panel"><h2>함께 준비한 만큼</h2><div class="row"><span class="grow">공동 To-do</span><b>'+done+' / '+state.todos.length+'</b></div><div class="progress"><span style="width:'+(state.todos.length?done/state.todos.length*100:0)+'%"></span></div>'+button("준비물 확인하기","todos","text-btn")+'</div>';
    if(tab==="settings")$("#screen").innerHTML=heading(group.name,"그룹 설정")+'<form id="settings-form" class="panel"><h2>함께 갈 곳</h2>'+field("destination","목적지","text",group.destination,true)+'<div class="two"><div>'+field("schedule","출발일","date",group.schedule)+'</div><div>'+field("arrivalTime","도착 목표","time",group.arrivalTime)+'</div></div><p class="muted">그룹을 만든 사람이 일정을 변경할 수 있어요.</p><button class="primary" '+(session.vehicleId!==state.ownerVehicleId||busy?"disabled":"")+'>설정 저장</button></form><form id="vehicle-form" class="panel"><h2>내 이동 상태</h2><label for="status">현재 상태</label><select name="status" id="status">'+options(["출발 대기","이동 중","잠시 정차","도착"].map(s=>[s,s]),drafts.status??me.status)+'</select>'+field("model","차량 이름","text",me.model||"",true)+field("battery","배터리 (%) · 직접 입력","number",me.battery??"")+field("location","현재 위치 · 직접 입력","text",me.location||"")+field("eta","도착 예상 · 예: 20분 후","text",me.eta||"")+'<p class="muted">직접 입력한 상태를 공유해요. 실시간 차량 센서값이 아니에요.</p><button class="primary" '+(busy?"disabled":"")+'>내 상태 공유</button></form><div class="panel"><h2>동행 초대</h2><div class="code">'+e(session.code)+'</div><p class="muted">초대받은 사람은 닉네임과 차량 정보를 입력하고 연결하세요.</p>'+button("초대 링크 복사","invite","secondary")+'<details><summary>차량 앱 연결 안내</summary><p>차량의 함께 이동 → 그룹 연결에서 아래 주소와 초대 코드를 입력하세요. 차량 앱도 내 닉네임과 차량명으로 참여해요. 새 그룹 만들기를 선택하고 숫자 4자리 초대 코드를 정해 주세요.</p><p style="overflow-wrap:anywhere">'+e(location.origin)+'</p></details></div>'+button("이 기기에서 나가기","logout","text-btn");
    if(tab==="todos"){
        const items=state.todos.filter(t=>filter==="all"||filter==="mine"&&t.ownerVehicleId===session.vehicleId||filter==="pending"&&!t.done);
        $("#screen").innerHTML=heading(group.name,"공동 To-do")+'<div class="panel"><div class="row"><b class="grow">준비 진행률</b><b>'+done+' / '+state.todos.length+'</b></div><div class="progress"><span style="width:'+(state.todos.length?done/state.todos.length*100:0)+'%"></span></div></div><div class="filters">'+[["all","전체"],["mine","내 차량"],["pending","미완료"]].map(([v,label])=>'<button data-filter="'+v+'" class="'+(filter===v?"active":"")+'">'+label+'</button>').join("")+'</div>'+items.map(t=>'<article class="todo '+(t.done?"done":"")+'"><div class="row"><button class="check" data-todo="'+e(t.id)+'" aria-label="'+e(t.title)+' 완료 전환">'+(t.done?"✓":"○")+'</button><div class="grow"><h3>'+e(t.title)+'</h3><small>'+e(t.status==="DOING"?"진행 중":t.done?"완료":"준비할 것")+'</small></div><span class="badge">'+e(vehicleName(t.ownerVehicleId))+'</span></div><details><summary>수정</summary><form data-edit="'+e(t.id)+'"><label>할 일<input name="title" value="'+e(t.title)+'" maxlength="120" required></label><label>담당<select name="ownerVehicleId">'+options(vehicleOptions(),t.ownerVehicleId)+'</select></label><label>진행<select name="status">'+options([["TODO","할 일"],["DOING","진행 중"],["DONE","완료"]],t.status||"TODO")+'</select></label><div class="row"><button class="secondary grow">수정 저장</button><button type="button" class="text-btn delete" data-delete="'+e(t.id)+'">삭제</button></div></form></details></article>').join("")+(items.length?"":'<div class="empty">아직 할 일이 없어요. 준비물을 추가해 보세요.</div>')+'<form id="todo-form" class="panel"><h2>준비물 추가</h2>'+field("todoTitle","할 일 · 물품·수량","text","",true)+roleSelect("ownerVehicleId",session.vehicleId)+'<br><button class="primary" '+(busy?"disabled":"")+'>공동 To-do에 추가</button></form>';
    }
    if(tab==="chat")$("#screen").innerHTML=heading(group.name,"그룹 대화","소식과 부탁을 남기면 차량 브리핑에도 전달돼요.")+'<div class="panel"><h3>그룹 브리핑</h3><p>'+e(state.summary)+'</p></div><div id="messages">'+(state.memos.length?state.memos.map(m=>'<article class="bubble '+(m.vehicleId===session.vehicleId?"mine":"")+'"><small>'+e(m.sender)+' · '+new Date(m.createdAt).toLocaleTimeString("ko-KR",{hour:"2-digit",minute:"2-digit"})+'</small><p>'+e(m.text)+'</p><button class="text-btn" data-memo-todo="'+e(m.id)+'">+ 할 일로 가져오기</button></article>').join(""):'<div class="empty">첫 소식을 남겨 보세요.</div>')+'</div><form id="message-form" class="panel message-form"><label for="message">그룹에 메시지 남기기</label><textarea name="message" id="message" maxlength="500" required placeholder="예: 휴게소에서 얼음 2봉 부탁해요">'+e(drafts.message||"")+'</textarea><p class="muted">운전자는 정차 후 입력해 주세요.</p><button class="primary" '+(busy?"disabled":"")+'>메시지 보내기</button></form>';
}
async function api(path,method="GET",body){
    const signature=method+path+JSON.stringify(body);
    if(method!=="GET" && pending?.signature!==signature)pending={signature,key:crypto.randomUUID()};
    const response=await fetch(path,{method,headers:{"Content-Type":"application/json",...(session?{Authorization:"Bearer "+session.token}:{}),...(method!=="GET"?{"Idempotency-Key":pending.key}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(8000)});
    const data=await response.json();
    if(!response.ok)throw Object.assign(new Error(data.error||"연결을 확인해 주세요"),{status:response.status});
    if(method!=="GET")pending=null;
    return data;
}
async function refresh(){
    if(!session||busy)return;
    try{const next=await api("/api/state");const changed=state?.revision!==next.revision;state=next;online=true;
        $("#connection").textContent="● 그룹 동기화 중";
        if(changed&&!document.activeElement?.matches("input,textarea,select")&&!document.querySelector("details[open]"))render();
    }catch(err){online=false;if(err.status===401){session=null;state=null;localStorage.removeItem("camp-on-mobile");entry="landing";notice("이전 연결이 만료됐어요. 새 그룹을 만들거나 초대 코드로 참여하세요.");render();}else $("#connection").textContent="연결 확인 필요";}
}
async function mutate(path,method,body){
    if(busy)return false;busy=true;document.querySelectorAll("form button").forEach(b=>b.disabled=true);
    try{state=await api(path,method,body);online=true;notice("그룹에 반영했어요");return true;}
    catch(err){notice(err.message);return false;}
    finally{busy=false;}
}
document.addEventListener("input",ev=>{
    if(ev.target.name==="code" && (ev.target.value.length>4 || /[^0-9]/.test(ev.target.value)))ev.target.value=drafts.code??"";
    if(ev.target.name && !ev.target.closest("[data-edit]"))drafts[ev.target.name]=ev.target.value;
});
document.addEventListener("change",ev=>{if(ev.target.name&&!ev.target.closest("[data-edit]"))drafts[ev.target.name]=ev.target.value;});
document.addEventListener("submit",async ev=>{
    ev.preventDefault();if(busy)return;const form=ev.target,data=Object.fromEntries(new FormData(form));
    if(form.id==="entry-form"){
        if(typeof data.code!=="string" || data.code.length!==4 || /[^0-9]/.test(data.code)){notice("초대 코드는 숫자 4자리로 입력해 주세요");return;}
        busy=true;render();
        try{session=await api(entry==="create"?"/api/groups":"/api/join","POST",entry==="create"?{...data,name:data.groupName}:data);state=session.state;delete session.state;save();online=true;notice();Object.keys(drafts).forEach(k=>delete drafts[k]);history.replaceState(null,"",location.pathname);}
        catch(err){notice(err.message);}finally{busy=false;render();}return;
    }
    let ok=false;
    if(form.id==="settings-form")ok=await mutate("/api/group","PATCH",data);
    if(form.id==="vehicle-form")ok=await mutate("/api/vehicles","POST",data);
    if(form.id==="todo-form"){ok=await mutate("/api/todos","POST",{title:data.todoTitle,ownerVehicleId:data.ownerVehicleId});if(ok)delete drafts.todoTitle;}
    if(form.id==="message-form"){ok=await mutate("/api/memos","POST",{text:data.message});if(ok)delete drafts.message;}
    if(form.dataset.edit)ok=await mutate("/api/todos/"+encodeURIComponent(form.dataset.edit),"PATCH",data);
    render();
});
document.addEventListener("click",async ev=>{
    const b=ev.target.closest("button");if(!b||busy)return;
    if(b.dataset.tab){tab=b.dataset.tab;notice();render();window.scrollTo(0,0);}
    if(b.dataset.filter){filter=b.dataset.filter;render();}
    const a=b.dataset.action;
    if(["landing","create","join"].includes(a)){entry=a;render();}
    if(["chat","todos"].includes(a)){tab=a;render();window.scrollTo(0,0);}
    if(a==="logout"){session=null;state=null;localStorage.removeItem("camp-on-mobile");entry="landing";Object.keys(drafts).forEach(k=>delete drafts[k]);render();}
    if(a==="refresh")await refresh();
    if(a==="invite"){
        const link=location.origin+"/?code="+encodeURIComponent(session.code);
        try{await navigator.clipboard.writeText(link);notice("초대 링크를 복사했어요");}catch{notice(link);}
    }
    if(b.dataset.todo){const t=state.todos.find(t=>t.id===b.dataset.todo);await mutate("/api/todos/"+encodeURIComponent(t.id),"PATCH",{done:!t.done});render();}
    if(b.dataset.delete && confirm("이 할 일을 삭제할까요?")){await mutate("/api/todos/"+encodeURIComponent(b.dataset.delete),"DELETE");render();}
    if(b.dataset.memoTodo){drafts.todoTitle=state.memos.find(m=>m.id===b.dataset.memoTodo)?.text.slice(0,120)||"";tab="todos";render();$("#todoTitle").focus();}
});
render();refresh();setInterval(()=>{if(!document.hidden)refresh();},1500);
document.addEventListener("visibilitychange",()=>{if(!document.hidden)refresh();});
