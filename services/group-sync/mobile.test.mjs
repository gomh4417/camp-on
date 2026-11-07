import test from "node:test";
import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import vm from "node:vm";
test("mobile renders registration and dynamic participant screens without fixed roles",async()=>{
 const nodes=new Map();
 const node=s=>{if(!nodes.has(s))nodes.set(s,{innerHTML:"",textContent:"",hidden:false});return nodes.get(s);};
 const ctx=vm.createContext({document:{querySelector:node,addEventListener(){}},localStorage:{getItem(){return null;}},
 location:{search:"",pathname:"/",origin:"https://example.test"},URLSearchParams,setInterval(){},console});
 vm.runInContext(await readFile(new URL("./mobile/app.js",import.meta.url),"utf8"),ctx);
 vm.runInContext('entry="join";render()',ctx);
 assert.match(node("#screen").innerHTML,/name="nickname"/);
 assert.match(node("#screen").innerHTML,/name="model"/);
 assert.match(node("#screen").innerHTML,/min="0" max="100"/);
 assert.doesNotMatch(node("#screen").innerHTML,/CAR B|CAR C|name="vehicleId"/);
 vm.runInContext('session={vehicleId:"dynamic",code:"CODE"};state={revision:1,ownerVehicleId:"dynamic",group:{name:"캠핑",destination:"해변"},memberCount:1,vehicles:[{id:"dynamic",name:"민지",model:"EV6",status:"출발 대기",battery:null}],todos:[],memos:[]};tab="settings";render()',ctx);
 assert.match(node("#screen").innerHTML,/type="date"/);assert.match(node("#screen").innerHTML,/type="time"/);
 vm.runInContext('tab="todos";render()',ctx);
 assert.match(node("#screen").innerHTML,/value="dynamic"/);
 vm.runInContext('tab="home";render()',ctx);
 assert.match(node("#screen").innerHTML,/참여 차량 · 1대/);
 assert.match(node("#screen").innerHTML,/출발 대기/);
 assert.doesNotMatch(node("#screen").innerHTML,/undefined|이동하고 있어요/);
});
