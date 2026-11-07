// Creates an isolated three-person verification group; prints no bearer tokens.
import assert from "node:assert/strict";
const base=process.argv[2];
if(!base?.startsWith("https://"))throw Error("Pass the public HTTPS origin");
const api=async(path,body,token)=>{
 const r=await fetch(base+path,{method:body?"POST":"GET",headers:{"Content-Type":"application/json",...(token?{Authorization:"Bearer "+token}:{})},body:body?JSON.stringify(body):undefined});
 assert.ok(r.ok, "HTTP "+r.status);return r.json();
};
const a=await api("/api/groups",{name:"CAMP:ON 3인 캠핑",nickname:"PC",model:"아이오닉 5",destination:"솔향 캠핑장"});
const b=await api("/api/join",{code:a.code,nickname:"민지",model:"EV6"});
const c=await api("/api/join",{code:a.code,nickname:"준호",model:"EV3"});
await api("/api/memos",{text:"연동 확인 · B에서 얼음 2봉을 챙길게요."},b.token);
await api("/api/memos",{text:"연동 확인 · C에서 캠핑 준비 완료했어요."},c.token);
await api("/api/todos",{title:"얼음 2봉 챙기기",ownerVehicleId:b.vehicleId},a.token);
const state=await api("/api/state",undefined,a.token);
assert.equal(state.memberCount,3);assert.equal(state.memos.length,2);assert.equal(state.todos.length,1);
console.log(JSON.stringify({ok:true,code:a.code,url:base+"/?code="+a.code,participants:3,messages:2,todos:1}));
