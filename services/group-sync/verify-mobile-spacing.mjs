import {chromium} from "playwright";
import {spawn} from "node:child_process";
import {once} from "node:events";
import {mkdtemp,mkdir,writeFile,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join,resolve} from "node:path";
import assert from "node:assert/strict";
const temp=await mkdtemp(join(tmpdir(),"camp-spacing-"));
const out=resolve("../../docs/verification/2026-11-14/mobile-spacing");
await mkdir(out,{recursive:true});
const server=spawn(process.execPath,["companion.mjs"],{env:{...process.env,PORT:"18796",COMPANION_DATA_FILE:join(temp,"data.json")},stdio:"pipe"});
let browser;const checks=[],errors=[];
try {
 await Promise.race([once(server.stdout,"data"),once(server,"exit").then(()=>{throw Error("fixture failed");})]);
 browser=await chromium.launch({channel:"chrome"});
 async function check(page,name){
  await page.evaluate(()=>document.fonts.ready);
  const result=await page.evaluate(()=>{
   const issues=[];
   for(const parent of document.querySelectorAll("form,.vehicle .grow,.todo .grow,.bubble")){
    const visible=[...parent.children].filter(e=>getComputedStyle(e).display!=="none"&&e.getBoundingClientRect().height>0);
    for(let i=1;i<visible.length;i++){
     const a=visible[i-1].getBoundingClientRect(),b=visible[i].getBoundingClientRect();
     if(b.top>=a.top&&b.top-a.bottom<6)issues.push(parent.tagName+" "+visible[i].tagName+" gap "+(b.top-a.bottom));
    }
   }
   return {issues,overflow:document.documentElement.scrollWidth>innerWidth+1};
  });
  assert.deepEqual(result.issues,[],name);assert.equal(result.overflow,false,name);
  assert.equal(await page.locator(".scene-toggle").count(),0);
  await page.screenshot({path:join(out,name+".png"),fullPage:true});checks.push({name,...result});
 }
 const pages=[];
 for(const width of [320,390,430]){
  const ctx=await browser.newContext({viewport:{width,height:844},locale:"ko-KR",reducedMotion:"reduce"});
  const page=await ctx.newPage();pages.push(page);page.on("pageerror",e=>errors.push(e.message));
  await page.goto("http://127.0.0.1:18796/");
  await page.getByRole("button",{name:pages.length===1?"새 그룹 만들기":"초대 코드로 참여",exact:true}).click();
  await page.locator("#nickname").fill(width===320?"민지":width===390?"서연":"준호");
  assert.equal(await page.locator("#profile-initial").textContent(),width===320?"민":width===390?"서":"준");
  await page.locator("#code").fill("0427");await page.locator("#model").fill("아이오닉 5");
  await check(page,"join-"+width);
  await page.locator("#entry-form .primary").click();
  await page.getByRole("heading",{name:/참여 차량/}).waitFor();
 }
 for(const page of pages){
  const width=page.viewportSize().width;
  await page.getByRole("heading",{name:"참여 차량 · 3대"}).waitFor();
  assert.deepEqual(await page.locator(".vehicle .avatar").allTextContents(),["민","서","준"]);
  await check(page,"home-"+width);
  await page.locator('[data-tab="settings"]').click();await check(page,"settings-"+width);
  await page.locator('[data-tab="todos"]').click();await page.locator("#todoTitle").fill("얼음 "+width);
  await page.locator("#todo-form .primary").click();await page.getByRole("heading",{name:"얼음 "+width,exact:true}).waitFor();
  await page.locator(".todo details summary").first().click();await check(page,"todos-"+width);
  await page.locator('[data-tab="chat"]').click();await page.locator("#message").fill("휴게소에서 만나요 "+width);
  await page.locator("#message-form .primary").click();await page.locator(".bubble").first().waitFor();await check(page,"chat-"+width);
 }
 assert.deepEqual(errors,[]);
 await writeFile(join(out,"results.json"),JSON.stringify({checks,errors},null,2));
 console.log("PASS",checks.length,"mobile spacing/profile screens, three independent participants");
} finally {
 await browser?.close();const exited=once(server,"exit");server.kill();await exited;await rm(temp,{recursive:true,force:true});
}
