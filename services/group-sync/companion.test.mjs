import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp,rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";
test("three clients: invitation, authenticated briefing, idempotency, todo sync and restart",async()=>{
    const dir=await mkdtemp(join(tmpdir(),"campon-companion-"));let child;
    const start=async()=>{
        child=spawn(process.execPath,[fileURLToPath(new URL("./companion.mjs",import.meta.url))],
            {env:{...process.env,PORT:"18788",COMPANION_DATA_FILE:join(dir,"state.json")},stdio:["ignore","pipe","pipe"]});
        await Promise.race([once(child.stdout,"data"),once(child,"exit").then(()=>{throw Error("server exited");})]);
    };
    const stop=async()=>{if(child && child.exitCode===null){const done=once(child,"exit");child.kill();await done;}};
    const api=async(path,method="GET",body,token,key)=>{
        const res=await fetch("http://127.0.0.1:18788"+path,{method,headers:{"Content-Type":"application/json",...(token?{Authorization:"Bearer "+token}:{}),...(key?{"Idempotency-Key":key}:{})},body:body?JSON.stringify(body):undefined});
        return {status:res.status,data:await res.json()};
    };
    try {
        await start();
        assert.equal((await api("/api/state")).status,401);
        const a=(await api("/api/groups","POST",{name:"검증 그룹",nickname:"PC",model:"아이오닉 5",battery:80,destination:"캠핑장"})).data;
        assert.equal(a.state.vehicles.length,1);
        assert.equal(a.state.memos.length,0);
        assert.equal(a.state.todos.length,0);
        assert.equal(a.vehicleId,a.state.vehicles[0].id);
        assert.equal(a.state.vehicles[0].status,"출발 대기");
        assert.equal(a.state.vehicles[0].battery,80);
        assert.equal((await api("/api/join","POST",{code:a.code,nickname:"차량 화면",display:true})).status,400);
        assert.equal((await api("/api/state","GET",undefined,a.token)).data.memberCount,1);
        assert.equal((await api("/api/join","POST",{code:a.code,nickname:"실패",model:"EV",battery:101})).status,400);
        const b=(await api("/api/join","POST",{code:a.code,nickname:"민지",model:"EV6",battery:""})).data;
        const c=(await api("/api/join","POST",{code:a.code,nickname:"준호",model:"아이오닉 6",battery:65})).data;
        assert.equal((await api("/api/join","POST",{code:"WRONG",vehicleId:"tralala"})).status,404);
        assert.equal((await api("/api/state","GET",undefined,c.token)).data.memberCount,3);
        assert.equal(b.state.vehicles.find(v=>v.id===b.vehicleId).battery,null);
        assert.equal((await api("/api/state","GET",undefined,a.token)).data.vehicles[0].status,"출발 대기");
        const extra=(await api("/api/join","POST",{code:a.code,nickname:"네번째",model:"EV9"})).data;
        assert.equal(extra.state.vehicles.length,4);
        const payload={code:a.code,nickname:"재시도",model:"EV3"};
        const first=(await api("/api/join","POST",payload,undefined,"join-once")).data;
        const second=(await api("/api/join","POST",payload,undefined,"join-once")).data;
        assert.equal(first.vehicleId,second.vehicleId);
        assert.equal(second.state.vehicles.length,5);
        await Promise.all([
            api("/api/memos","POST",{text:"얼음 2봉 부탁해요",vehicleId:"princess"},b.token,"memo-b"),
            api("/api/memos","POST",{text:"캠핑장 도착했어요"},c.token,"memo-c")
        ]);
        await api("/api/memos","POST",{text:"얼음 2봉 부탁해요"},b.token,"memo-b");
        const shared=(await api("/api/state","GET",undefined,a.token)).data;
        assert.equal(shared.memos.length,2);
        assert.equal(shared.memos.find(m=>m.text.includes("얼음")).vehicleId,b.vehicleId);
        const todo=(await api("/api/todos","POST",{title:"얼음 2봉",ownerVehicleId:a.vehicleId},b.token,"todo-b")).data.todos[0];
        await api("/api/todos/"+todo.id,"PATCH",{status:"DOING"},a.token);
        assert.equal((await api("/api/state","GET",undefined,c.token)).data.todos[0].status,"DOING");
        await api("/api/todos/"+todo.id,"PATCH",{done:true},a.token);
        assert.equal((await api("/api/state","GET",undefined,b.token)).data.todos[0].done,true);
        assert.equal((await api("/api/todos/"+todo.id,"PATCH",{done:"false"},a.token)).status,400);
        assert.equal((await api("/api/group","PATCH",{destination:"다른 장소"},b.token)).status,403);
        await api("/api/group","PATCH",{destination:"솔향 캠핑장"},a.token);
        const other=(await api("/api/groups","POST",{name:"다른 그룹",nickname:"다른 사람",model:"EV3"})).data;
        assert.equal((await api("/api/state","GET",undefined,other.token)).data.memos.length,0);
        await stop();await start();
        const resumed=(await api("/api/state","GET",undefined,a.token)).data;
        assert.equal(resumed.memos.length,2);assert.equal(resumed.todos[0].done,true);
        assert.equal(resumed.group.destination,"솔향 캠핑장");
        await api("/api/todos/"+todo.id,"DELETE",undefined,a.token);
        assert.equal((await api("/api/state","GET",undefined,b.token)).data.todos.length,0);
    } finally {await stop();await rm(dir,{recursive:true,force:true});}
});