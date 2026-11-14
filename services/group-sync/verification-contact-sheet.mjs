import {chromium} from "playwright";
import {readdir,readFile,writeFile} from "node:fs/promises";
import {resolve,join} from "node:path";
const dir=resolve("../../docs/verification/2026-11-14/android/figma-verification");
const files=(await readdir(dir)).filter(f=>f.endsWith(".png")).sort((a,b)=>parseInt(a)-parseInt(b));
const browser=await chromium.launch({channel:"chrome"});
const page=await browser.newPage({viewport:{width:1600,height:780},deviceScaleFactor:1});
for(let offset=0;offset<files.length;offset+=12) {
 const cards=await Promise.all(files.slice(offset,offset+12).map(async name=>{
  const data=(await readFile(join(dir,name))).toString("base64");
  return '<section><b>'+name+'</b><canvas width="400" height="288"></canvas><img hidden src="data:image/png;base64,'+data+'"></section>';
 }));
 await page.setContent('<style>*{box-sizing:border-box}body{margin:0;background:#dce3ee;font:14px Arial;display:grid;grid-template-columns:repeat(4,400px);align-content:start}section{height:322px;padding:0}b{display:block;height:30px;padding:7px}canvas{display:block}</style>'+cards.join(""));
 await page.locator("img").evaluateAll(imgs=>Promise.all(imgs.map(async img=>{
  await img.decode();const w=img.naturalWidth,h=img.naturalHeight;
  img.parentElement.querySelector("canvas").getContext("2d").drawImage(img,w*853/2560,h*90/1440,w*1682/2560,h*1210/1440,0,0,400,288);
 })));
 await page.screenshot({path:resolve(dir,"../contact-"+(offset/12+1)+".png"),fullPage:true});
}
await browser.close();
console.log("Contact sheets generated:",files.length);
