import test from "node:test";
import assert from "node:assert/strict";
import vm from "node:vm";
import {readFile} from "node:fs/promises";
const script=await readFile(new URL("../../app/src/main/assets/journey-simulation.js",import.meta.url),"utf8");
function harness(reduced=false){
 const elements=new Map(),layers=new Set(),markers=[],listeners={};let frame,fetches=0;
 const element=id=>{if(!elements.has(id))elements.set(id,{textContent:"",checked:false,disabled:false,setAttribute(){},append(){},querySelector:element});return elements.get(id);};
 const map={on(event,fn){listeners[event]=fn;},getCenter(){return {lat:37,lng:128};},hasLayer:l=>layers.has(l),removeLayer:l=>layers.delete(l),distance:(a,b)=>Math.hypot(a[0]-b[0],a[1]-b[1])*100000,fitBounds(){}};
 const layer=()=>({items:[],addTo(){layers.add(this);return this;},clearLayers(){this.items=[];}});
 const ctx={map,live:layer(),home:null,avatars:["avatar"],tell(){},console,AbortController,setTimeout,clearTimeout,performance:{now:()=>0},
  document:{hidden:false,createElement:()=>element("root-"+Math.random()),createTextNode:t=>({textContent:t}),addEventListener:(e,f)=>listeners[e]=f},
  matchMedia:()=>({matches:reduced,addEventListener(){}}),requestAnimationFrame:f=>{frame=f;return 1;},cancelAnimationFrame:()=>{frame=null;},
  fetch:async(url,options)=>{fetches++;assert.match(url,/route\/v1\/driving\/128,37;128.01,37.01/);assert.equal(options.method,undefined);return {ok:true,json:async()=>({code:"Ok",routes:[{geometry:{coordinates:[[128,37],[128.01,37.01]]}}]})};},
  L:{layerGroup:layer,control:()=>({addTo(){this.onAdd();}}),DomEvent:{disableClickPropagation(){},disableScrollPropagation(){}},divIcon:x=>x,latLngBounds:x=>x,polyline:()=>({addTo(){return this;}}),
   marker:(point,options)=>{const m={point,options,setLatLng(p){this.point=p;},bindTooltip(){return this;},addTo(l){l.items.push(this);return this;}};markers.push(m);return m;}}};
 ctx.window=ctx;vm.runInNewContext(script,ctx);
 return {ctx,e:id=>element("#trip-"+id),listeners,markers,fetches:()=>fetches,frame:()=>frame};
}
test("selected road route animates only actual members; cancellation and still mode",async()=>{
 const h=harness(true);
 assert.equal(h.e("start").disabled,true);
 h.ctx.updateTripMembers([{id:"a",name:"나"},{id:"b",name:"친구"}]);
 h.e("from").onclick();h.listeners.click({latlng:{lat:37,lng:128}});
 h.e("to").onclick();h.listeners.click({latlng:{lat:37.01,lng:128.01}});
 assert.equal(h.e("start").disabled,false);
 await h.e("start").onclick();
 assert.equal(h.fetches(),1);
 const pins=h.markers.filter(m=>m.options?.icon);
 assert.equal(pins.length,2);assert.equal(h.frame(),null);
 h.e("step").onclick();assert.ok(pins[0].point[0]>37);assert.ok(pins[1].point[0]>37);
 h.ctx.updateTripMembers([]);assert.equal(h.e("start").disabled,true);assert.equal(h.frame(),null);
 h.e("exit").onclick();assert.equal(h.ctx.tripSimulationActive,false);
});
test("request failure produces no fake route or participant pins",async()=>{
 const h=harness();h.ctx.fetch=async()=>({ok:false});
 h.ctx.updateTripMembers([{id:"a",name:"<script>"}]);
 h.e("from").onclick();h.listeners.click({latlng:{lat:37,lng:128}});
 h.e("to").onclick();h.listeners.click({latlng:{lat:37.01,lng:128.01}});
 await h.e("start").onclick();
 assert.equal(h.markers.filter(m=>m.options?.icon).length,0);
 assert.match(h.e("info").textContent,/경로를 찾지 못했어요/);
});
