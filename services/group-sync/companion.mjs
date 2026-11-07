import { createServer } from "node:http";
import { readFile, writeFile, rename, mkdir } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { randomBytes, randomUUID, createHash } from "node:crypto";

const root=dirname(fileURLToPath(import.meta.url));
const file=process.env.COMPANION_DATA_FILE || join(root,"data","companion-live.json");
const port=Number(process.env.PORT || 8788);

let db={groups:{},sessions:{}};
try { db=JSON.parse(await readFile(file,"utf8")); }
catch(e) { if(e.code!=="ENOENT") throw e; } // Never replace an unreadable store.
let queue=Promise.resolve();
const limits=new Map();
const hash=x=>createHash("sha256").update(x).digest("hex");
const clean=(x,n=100)=>typeof x==="string"?x.trim().slice(0,n):"";
const fail=(status,message)=>{throw Object.assign(new Error(message),{status});};
const persist=async()=>{
    await mkdir(dirname(file),{recursive:true});
    await writeFile(file+".tmp",JSON.stringify(db),"utf8");
    await rename(file+".tmp",file);
};
const json=(res,status,payload)=>{
    res.writeHead(status,{"Content-Type":"application/json; charset=utf-8","Cache-Control":"no-store",
        "Access-Control-Allow-Origin":"*","Access-Control-Allow-Headers":"Content-Type, Authorization, Idempotency-Key",
        "Access-Control-Allow-Methods":"GET, POST, PATCH, DELETE, OPTIONS","X-Content-Type-Options":"nosniff"});
    res.end(JSON.stringify(payload));
};
async function input(req) {
    let bytes=0;const parts=[];
    for await(const c of req) { bytes+=c.length;if(bytes>16384)fail(413,"입력이 너무 길어요");parts.push(c); }
    try { return parts.length?JSON.parse(Buffer.concat(parts)):{}; } catch {fail(400,"입력 형식을 확인해 주세요");}
}
function publicState(g) {
    return {...g.state,vehicles:g.state.vehicles.filter(v=>v.joined),ownerVehicleId:g.ownerVehicleId || "princess",memberCount:g.state.vehicles.filter(v=>v.joined).length};
}
function inviteCode(value) {
    if(typeof value!=="string" || value.length!==4 || /[^0-9]/.test(value))fail(400,"초대 코드는 숫자 4자리로 입력해 주세요");
    return value;
}
function newGroup(name,destination,code) {
    return {code,dedup:[],state:{
        revision:1,group:{id:randomUUID(),name,destination,schedule:"",arrivalTime:""},
        vehicles:[],
        todos:[],memos:[],requests:[],headline:"그룹을 만들었어요",summary:"초대 코드로 동행을 초대해 주세요",
        relativeTime:"방금 전",updatedAt:new Date().toISOString()
    }};
}
function profile(data) {
    const name=clean(data.nickname,30),model=clean(data.model,60);
    if(!name || !model)fail(400,"닉네임과 차량 이름을 입력해 주세요");
    const battery=data.battery==="" || data.battery==null?null:Number(data.battery);
    if(battery!==null && (!Number.isFinite(battery)||battery<0||battery>100))fail(400,"배터리는 0~100% 또는 미입력으로 남겨 주세요");
    return {name,model,battery,source:"참여자 직접 입력",updatedAt:new Date().toISOString()};
}
function issue(g,vehicleId,nickname) {
    const token=randomBytes(32).toString("base64url");
    db.sessions[hash(token)]={code:g.code,vehicleId,nickname,createdAt:Date.now()};
    return {token,vehicleId,code:g.code,state:publicState(g)};
}
function brief(g,headline) {
    const pending=g.state.todos.filter(t=>!t.done).length;
    const latest=g.state.memos.at(-1);
    g.state.headline=headline;
    g.state.summary=(latest?latest.sender+": "+latest.text:"공유된 메시지가 없어요")+" · 남은 할 일 "+pending+"개";
    g.state.relativeTime="방금 전";g.state.updatedAt=new Date().toISOString();g.state.revision++;
}
async function handle(req,res) {
    if(req.method==="OPTIONS")return json(res,204,{});
    const url=new URL(req.url,"http://localhost");
    if(url.pathname==="/api/health")return json(res,200,{ok:true});
    if(!url.pathname.startsWith("/api/")) {
        const paths={"/":"index.html","/app.js":"app.js","/styles.css":"styles.css","/art.svg":"art.svg"};
        const path=paths[url.pathname];
        if(!path || req.method!=="GET")return json(res,404,{error:"찾을 수 없어요"});
        const body=await readFile(join(root,"mobile",path));
        res.writeHead(200,{"Content-Type":path.endsWith(".html")?"text/html; charset=utf-8":path.endsWith(".js")?"text/javascript; charset=utf-8":path.endsWith(".svg")?"image/svg+xml":"text/css; charset=utf-8"});
        return res.end(body);
    }
    const key=req.headers["cf-connecting-ip"] || req.socket.remoteAddress;
    const now=Date.now(),rate=limits.get(key);
    if(!rate || now-rate.time>60000)limits.set(key,{time:now,count:1});
    else if(++rate.count>240)fail(429,"잠시 후 다시 시도해 주세요");
    if(limits.size>10000)for(const [k,r] of limits)if(now-r.time>60000)limits.delete(k);
    const data=req.method==="GET"?{}:await input(req);
    const enrollmentKey=req.headers["idempotency-key"]?hash(String(req.headers["idempotency-key"])):null;
    if(req.method==="POST" && url.pathname==="/api/groups") {
        const code=inviteCode(data.code);
        const name=clean(data.name,60),nickname=clean(data.nickname,30);
        if(!name || !nickname)fail(400,"그룹 이름과 닉네임을 입력해 주세요");
        const existing=enrollmentKey && Object.values(db.groups).find(g=>g.enrollmentKey===enrollmentKey);
        if(existing){
            if(existing.code!==code)fail(409,"다른 초대 코드로 다시 시도해 주세요");
            const result=issue(existing,existing.ownerVehicleId,nickname);await persist();return json(res,200,result);
        }
        if(db.groups[code])fail(409,"이미 사용 중인 초대 코드예요. 다른 숫자 4자리를 정해 주세요");
        if(Object.keys(db.groups).length>=100)fail(429,"그룹 생성 한도에 도달했어요");
        const g=newGroup(name,clean(data.destination)||"목적지 미정",code);g.enrollmentKey=enrollmentKey;db.groups[g.code]=g;
        const v={id:randomUUID(),...profile(data),joined:true,status:"출발 대기",listStatus:"출발 대기",location:null,eta:null,distance:null};
        g.state.vehicles.push(v);g.ownerVehicleId=v.id;
        const result=issue(g,v.id,nickname);await persist();return json(res,201,result);
    }
    if(req.method==="POST" && url.pathname==="/api/join") {
        const g=db.groups[inviteCode(data.code)];
        if(!g)fail(404,"초대 코드를 확인해 주세요");
        if(data.display)fail(400,"닉네임과 차량명으로 참여해 주세요");
        if(g.state.vehicles.filter(v=>v.joined).length>=30)fail(400,"그룹 차량은 30대까지 참여할 수 있어요");
        if(Object.keys(db.sessions).length>=10000)fail(429,"연결 수 한도에 도달했어요");
        const nickname=clean(data.nickname,30);
        if(!nickname)fail(400,"이름을 입력해 주세요");
        const previous=enrollmentKey && g.registrations?.[enrollmentKey];
        if(previous){const result=issue(g,previous,nickname);await persist();return json(res,200,result);}
        let id=null;
        if(!data.display) {
            const v={id:randomUUID(),...profile(data),joined:true,status:"출발 대기",listStatus:"출발 대기",location:null,eta:null,distance:null};
            g.state.vehicles.push(v);id=v.id;
            if(enrollmentKey){g.registrations??={};g.registrations[enrollmentKey]=id;}
            brief(g,nickname+"님이 참여했어요");
        } else if(data.display!==true)fail(400,"연결 형식 확인");
        const result=issue(g,id,nickname);await persist();return json(res,200,result);
    }
    const token=(req.headers.authorization||"").replace(/^Bearer /,"");
    const session=db.sessions[hash(token)];
    if(!session)fail(401,"그룹에 먼저 참여해 주세요");
    const g=db.groups[session.code],state=g.state;
    if(req.method==="GET" && url.pathname==="/api/state")return json(res,200,publicState(g));
    const idempotency=req.headers["idempotency-key"];
    if(idempotency && g.dedup.includes(session.vehicleId+":"+idempotency))return json(res,200,publicState(g));
    let changed=false;
    if(req.method==="PATCH" && url.pathname==="/api/group") {
        if(session.vehicleId!==(g.ownerVehicleId || "princess"))fail(403,"그룹을 만든 사람이 일정을 변경할 수 있어요");
        for(const k of ["name","destination","schedule","arrivalTime"]) {
            if(k in data) {const value=clean(data[k]);if(!value && ["name","destination"].includes(k))fail(400,"이름과 목적지를 확인해 주세요");state.group[k]=value;}
        }
        brief(g,"그룹 일정을 변경했어요");changed=true;
    } else if(req.method==="POST" && url.pathname==="/api/memos") {
        const text=clean(data.text,500);if(!text)fail(400,"메시지를 입력해 주세요");
        const sender=state.vehicles.find(v=>v.id===session.vehicleId)?.name || session.nickname || "차량 화면";
        state.memos.push({id:randomUUID(),vehicleId:session.vehicleId,sender,text,time:"방금 전",createdAt:new Date().toISOString()});
        state.memos=state.memos.slice(-100);brief(g,sender+"님의 새 메시지");changed=true;
    } else if(req.method==="POST" && url.pathname==="/api/todos") {
        const title=clean(data.title,120);if(!title || !state.vehicles.some(v=>v.joined && v.id===data.ownerVehicleId))fail(400,"할 일과 담당 차량을 확인해 주세요");
        if(state.todos.length>=100)fail(400,"할 일은 100개까지 등록할 수 있어요");
        state.todos.push({id:randomUUID(),title,ownerVehicleId:data.ownerVehicleId,done:false,status:"TODO",createdBy:session.vehicleId});
        brief(g,"새 할 일: "+title);changed=true;
    } else if(url.pathname.startsWith("/api/todos/") && ["PATCH","DELETE"].includes(req.method)) {
        const id=decodeURIComponent(url.pathname.slice(11)),item=state.todos.find(t=>t.id===id);
        if(!item)fail(404,"할 일을 찾을 수 없어요");
        if(req.method==="DELETE")state.todos=state.todos.filter(t=>t.id!==id);
        else {
            if("title" in data) {const title=clean(data.title,120);if(!title)fail(400,"할 일을 입력해 주세요");item.title=title;}
            if("ownerVehicleId" in data) {if(!state.vehicles.some(v=>v.joined && v.id===data.ownerVehicleId))fail(400,"담당 차량 확인");item.ownerVehicleId=data.ownerVehicleId;}
            if("done" in data) {if(typeof data.done!=="boolean")fail(400,"완료 상태 확인");item.done=data.done;item.status=data.done?"DONE":"TODO";}
            if("status" in data) {if(!["TODO","DOING","DONE"].includes(data.status))fail(400,"진행 상태 확인");item.status=data.status;item.done=data.status==="DONE";}
        }
        brief(g,"할 일을 업데이트했어요");changed=true;
    } else if(req.method==="POST" && url.pathname==="/api/vehicles") {
        const v=state.vehicles.find(v=>v.id===session.vehicleId);
        if(!v)fail(403,"차량 참여자만 이동 상태를 공유할 수 있어요");
        if("model" in data)Object.assign(v,profile({...data,nickname:v.name}));
        const incoming=data.vehicles?.find(v=>v.id===session.vehicleId) || data;
        if(!["출발 대기","이동 중","잠시 정차","도착"].includes(incoming.status))fail(400,"이동 상태를 확인해 주세요");
        v.status=incoming.status;v.listStatus=incoming.status;v.location=clean(incoming.location)||null;
        v.eta=clean(incoming.eta,30)||null;v.updatedAt=new Date().toISOString();v.source="참여자 직접 입력";
        brief(g,v.name+" · "+v.status);changed=true;
    }
    if(!changed)fail(404,"요청을 찾을 수 없어요");
    if(idempotency)g.dedup=[...g.dedup,(session.vehicleId+":"+idempotency).slice(0,200)].slice(-300);
    await persist();return json(res,200,publicState(g));
}
const server=createServer((req,res)=>{
    // One serialized transaction per request prevents lost updates across three clients and restart.
    queue=queue.then(async()=>{
        const before=structuredClone(db);
        try {await handle(req,res);}
        catch(e) {db=before;if(!res.headersSent)json(res,e.status||500,{error:e.status?e.message:"저장하지 못했어요. 다시 확인해 주세요"});}
    });
});
server.listen(port,"127.0.0.1",()=>console.log("Companion ready: http://127.0.0.1:"+port));
