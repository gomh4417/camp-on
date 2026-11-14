import assert from "node:assert/strict";
import {chromium} from "playwright";
const origin=process.argv[2];
assert.match(origin,/^https:\/\/[a-z0-9.-]+$/);
const health=await fetch(origin+"/api/health");assert.equal(health.status,200);
const timing=await (await fetch(origin+"/camping/timing.json")).json();
assert.equal(Object.keys(timing).length,4);
assert.ok(Object.values(timing).every(v=>v.durationMs===10000));
const browser=await chromium.launch({channel:"chrome"});
try {
 const page=await browser.newPage({viewport:{width:390,height:844},timezoneId:"Asia/Seoul",reducedMotion:"reduce"});
 await page.goto(origin,{waitUntil:"networkidle"});
 await page.evaluate(()=>document.fonts.ready);
 await page.locator("camp-scene canvas").waitFor({state:"visible"});
 assert.equal(await page.evaluate(()=>document.fonts.check('16px "Asta Sans"')),true);
 assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
 await page.screenshot({path:"../../docs/verification/2026-11-14/mobile/public-live-390.png",fullPage:true});
 console.log("Public HTTPS page, font, GIF timing, still image and 390px layout verified; no participants created.");
} finally { await browser.close(); }
