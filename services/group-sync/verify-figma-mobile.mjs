import {chromium} from "playwright";
import {spawn} from "node:child_process";
import {mkdtemp,mkdir,writeFile,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join,resolve} from "node:path";
import assert from "node:assert/strict";
const out=resolve("../../docs/verification/2026-11-14/mobile");
await mkdir(out,{recursive:true});
const temp=await mkdtemp(join(tmpdir(),"camp-figma-"));
const server=spawn(process.execPath,["companion.mjs"],{env:{...process.env,PORT:"8794",COMPANION_DATA_FILE:join(temp,"data.json")},stdio:"pipe"});
const base="http://127.0.0.1:8794";
let browser;
const evidence=[],errors=[];
try {
    for(let i=0;i<60;i++){try{if((await fetch(base+"/api/health")).ok)break;}catch{}await new Promise(r=>setTimeout(r,100));}
    browser=await chromium.launch({channel:"chrome",headless:true});
    const contexts=await Promise.all([1440,390,320].map(width=>browser.newContext({viewport:{width,height:900},locale:"ko-KR",timezoneId:"Asia/Seoul",reducedMotion:"reduce"})));
    const pages=await Promise.all(contexts.map(c=>c.newPage()));
    for(const page of pages)page.on("pageerror",e=>errors.push(e.message));
    async function shot(page,name){
        await page.evaluate(()=>document.fonts.ready);
        await page.waitForTimeout(200);
        assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth+1),"Horizontal overflow: "+name);
        await page.screenshot({path:join(out,name+".png"),fullPage:true});
        evidence.push({screen:name,viewport:page.viewportSize(),overflow:false});
    }
    const [a,b,c]=pages;
    await a.goto(base);await a.locator("camp-scene canvas:not([hidden])").waitFor();
    await shot(a,"01-desktop-landing");
    await a.getByRole("button",{name:"새 그룹 만들기",exact:true}).click();
    await a.locator("#nickname").fill("진행자");await a.locator("#groupName").fill("함께 가는 캠핑");
    await a.locator("#destination").fill("강릉 캠핑장");await a.locator("#code").fill("0427");
    await a.locator("#model").fill("아이오닉 5");await a.locator("#battery").fill("75");
    await shot(a,"02-desktop-create");
    await a.getByRole("button",{name:"그룹 만들기",exact:true}).click();
    await a.getByRole("heading",{name:"참여 차량 · 1대"}).waitFor();
    await shot(a,"03-desktop-one-member");
    for(const [page,name,model] of [[b,"지민","EV6"],[c,"서연","아이오닉 6"]]){
        await page.goto(base+"/?code=0427");await page.locator("#nickname").fill(name);await page.locator("#model").fill(model);
        await shot(page,"04-join-"+page.viewportSize().width);
        await page.getByRole("button",{name:"차량 등록하고 연결",exact:true}).click();
        await page.getByRole("heading",{name:/참여 차량/}).waitFor();
    }
    await a.getByRole("heading",{name:"참여 차량 · 3대"}).waitFor();
    for(const page of pages){
        const w=page.viewportSize().width;
        await shot(page,"05-home-"+w);
        await page.locator('[data-tab="settings"]').click();
        await shot(page,"06-settings-"+w);
        if(page===a){await page.locator("#schedule").fill("2026-11-15");await page.locator("#arrivalTime").fill("14:30");await page.getByRole("button",{name:"설정 저장",exact:true}).click();}
        await page.locator('[data-tab="todos"]').click();
        await shot(page,"07-todos-empty-"+w);
        await page.locator('[data-tab="chat"]').click();
        await shot(page,"08-chat-empty-"+w);
    }
    await b.locator("#message").fill("휴게소에서 얼음 2봉 부탁해요.");
    await b.getByRole("button",{name:"메시지 보내기",exact:true}).click();
    await a.locator("#messages").getByText("휴게소에서 얼음 2봉 부탁해요.",{exact:true}).waitFor();
    await shot(a,"09-synced-message");
    await c.locator('[data-tab="todos"]').click();await c.locator("#todoTitle").fill("텐트 팩 8개");
    await c.getByRole("button",{name:"공동 To-do에 추가",exact:true}).click();
    await a.locator('[data-tab="todos"]').click();await a.getByRole("heading",{name:"텐트 팩 8개",exact:true}).waitFor();
    await a.getByRole("button",{name:"텐트 팩 8개 완료 전환",exact:true}).click();
    await c.locator(".todo.done").waitFor();await shot(c,"10-synced-todo-320");
    const phaseCases=[
        ["2026-11-14T10:59:50+09:00","2026-11-14T10:59:59+09:00","01_dawn","01_dawn_to_day","02_day"],
        ["2026-11-14T15:59:50+09:00","2026-11-14T15:59:59+09:00","02_day","02_day_to_dusk","03_dusk"],
        ["2026-11-14T18:59:50+09:00","2026-11-14T18:59:59+09:00","03_dusk","03_dusk_to_night","04_night"],
        ["2026-11-15T04:59:50+09:00","2026-11-15T04:59:59+09:00","04_night","04_night_to_dawn","01_dawn"]
    ];
    for(const [start,boundary,from,transition,to] of phaseCases){
        const ctx=await browser.newContext({viewport:{width:390,height:844},timezoneId:"Asia/Seoul",reducedMotion:"no-preference"});
        const page=await ctx.newPage();
        await page.clock.install({time:new Date(start)});
        await page.goto(base);await page.locator('camp-scene img[src$="'+from+'_loop.gif"]').waitFor();
        await page.clock.pauseAt(new Date(boundary));await page.clock.runFor(1200);
        await page.locator('camp-scene img[src$="'+transition+'.gif"]').waitFor();
        await page.clock.runFor(10010);
        await page.locator('camp-scene img[src$="'+to+'_loop.gif"]').waitFor();
        await page.clock.runFor(12000);
        assert.ok((await page.locator("camp-scene img").getAttribute("src")).endsWith(to+"_loop.gif"));
        evidence.push({check:transition+" once then "+to+" loop",passed:true});
        await ctx.close();
    }
    assert.deepEqual(errors,[]);
    evidence.push({check:"three independent sessions, 0427, message and todo sync, date/time at 320px, reduced motion",passed:true});
    await writeFile(join(out,"results.json"),JSON.stringify({evidence,errors},null,2));
    console.log("PASS",evidence.length,"screens/checks",out);
} finally {await browser?.close();server.kill();await rm(temp,{recursive:true,force:true});}
