import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import {readFile} from "node:fs/promises";
const script=await readFile(new URL("../../app/src/main/assets/journey-simulation.js",import.meta.url),"utf8");
function harness(reduced=false){
 const elements=new Map(),layers=new Set(),markers=[],listeners={};let frame,fetches=0,now=0;
 const element=id=>{if(!elements.has(id))elements.set(id,{textContent:"",checked:false,disabled:false,setAttribute(){},append(){},querySelector:element});return elements.get(id);};
 const map={on(event,fn){listeners[event]=fn;},getCenter(){return {lat:37,lng:128};},hasLayer:l=>layers.has(l),removeLayer:l=>layers.delete(l),distance:(a,b)=>Math.hypot(a[0]-b[0],a[1]-b[1])*100000,fitBounds(){}};
 const layer=()=>({items:[],addTo(){layers.add(this);return this;},clearLayers(){this.items=[];}});
 const ctx={map,live:layer(),home:null,avatars:["avatar"],tell(){},console,AbortController,setTimeout,clearTimeout,performance:{now:()=>now},
  document:{hidden:false,createElement:()=>element("root-"+Math.random()),createTextNode:t=>({textContent:t}),addEventListener:(e,f)=>listeners[e]=f},
  matchMedia:()=>({matches:reduced,addEventListener(){}}),requestAnimationFrame:f=>{frame=f;return 1;},cancelAnimationFrame:()=>{frame=null;},
  fetch:async(url,options)=>{fetches++;assert.match(url,/route\/v1\/driving\/128,37;128.01,37.01/);assert.equal(options.method,undefined);return {ok:true,json:async()=>({code:"Ok",routes:[{geometry:{coordinates:[[128,37],[128.01,37.01]]}}]})};},
  L:{layerGroup:layer,control:()=>({addTo(){this.onAdd();}}),DomEvent:{disableClickPropagation(){},disableScrollPropagation(){}},divIcon:x=>x,latLngBounds:x=>x,polyline:()=>({addTo(){return this;}}),
   marker:(point,options)=>{const m={point,options,setLatLng(p){this.point=p;},bindTooltip(){return this;},addTo(l){l.items.push(this);return this;}};markers.push(m);return m;}}};
 ctx.window=ctx;vm.runInNewContext(script,ctx);
 return {ctx,e:id=>element("#trip-"+id),listeners,markers,fetches:()=>fetches,frame:()=>frame,
  advance(ms){for(let i=0;i<ms;i+=100){now+=100;const f=frame;frame=null;if(f)f(now);}}};
}
async function ready(h){
 h.ctx.updateTripMembers([{id:"a",name:"나"},{id:"b",name:"친구"}]);
 h.e("from").onclick();h.listeners.click({latlng:{lat:37,lng:128}});
 h.e("to").onclick();h.listeners.click({latlng:{lat:37.01,lng:128.01}});
 await h.e("confirm").onclick();
}
test("route setup enables separate departure; duplicate start cannot reset; all pins must arrive",async()=>{
 const h=harness();assert.equal(h.e("confirm").disabled,true);assert.equal(h.ctx.startGroupTrip(),false);
 await ready(h);assert.equal(h.ctx.groupTripSnapshot().stage,"ready");assert.equal(h.frame(),null);
 assert.equal(h.fetches(),1);assert.equal(h.markers.filter(m=>m.options?.icon).length,2);
 assert.equal(h.ctx.startGroupTrip(),true);h.advance(30000);
 const elapsed=h.ctx.groupTripSnapshot().elapsed;assert.equal(h.ctx.startGroupTrip(),false);assert.equal(h.ctx.groupTripSnapshot().elapsed,elapsed);
 h.advance(30000);assert.equal(h.ctx.groupTripSnapshot().stage,"moving");
 h.advance(1200);assert.equal(h.ctx.groupTripSnapshot().stage,"arrived");
 assert.ok(h.markers.filter(m=>m.options?.icon).every(m=>m.point[0]===37.01));
});
test("map recreation and group messages retain route and progress; changed members invalidate readiness",async()=>{
 const h=harness();await ready(h);h.ctx.startGroupTrip();h.advance(25000);
 const saved=JSON.parse(JSON.stringify(h.ctx.groupTripSnapshot()));
 const next=harness();next.ctx.restoreGroupTrip(saved);next.ctx.updateTripMembers(saved.members);
 assert.equal(next.ctx.groupTripSnapshot().elapsed,25000);assert.equal(next.ctx.groupTripSnapshot().stage,"moving");
 next.advance(36200);assert.equal(next.ctx.groupTripSnapshot().stage,"arrived");
 next.ctx.updateTripMembers([]);assert.equal(next.ctx.groupTripSnapshot().stage,"editing");assert.equal(next.ctx.startGroupTrip(),false);
});
test("reduced motion has stationary pins, keeps timed completion and background pauses",async()=>{
 const h=harness(true);await ready(h);h.ctx.startGroupTrip();h.advance(30000);
 assert.ok(h.markers.filter(m=>m.options?.icon).every(m=>m.point[0]===37));
 h.ctx.document.hidden=true;h.listeners.visibilitychange();h.advance(10000);assert.equal(h.ctx.groupTripSnapshot().elapsed,30000);
 h.ctx.document.hidden=false;h.listeners.visibilitychange();h.advance(31200);assert.equal(h.ctx.groupTripSnapshot().stage,"arrived");
});
test("request failure cannot enable departure or fabricate route",async()=>{
 const h=harness();h.ctx.fetch=async()=>({ok:false});await ready(h);
 assert.equal(h.ctx.groupTripSnapshot().stage,"editing");assert.equal(h.ctx.startGroupTrip(),false);
 assert.equal(h.markers.filter(m=>m.options?.icon).length,0);assert.match(h.e("info").textContent,/경로를 찾지 못했어요/);
});
test("changing a selection discards a late route response",async()=>{
 const h=harness();let resolve;h.ctx.fetch=()=>new Promise(r=>resolve=r);
 const pending=ready(h);h.e("from").onclick();
 resolve({ok:true,json:async()=>({code:"Ok",routes:[{geometry:{coordinates:[[128,37],[128.01,37.01]]}}]})});
 await pending;assert.equal(h.ctx.groupTripSnapshot().route,null);assert.equal(h.ctx.startGroupTrip(),false);
});
