import {chromium} from "playwright";
import {spawn} from "node:child_process";
import {join} from "node:path";
const browser=await chromium.launch({channel:"chrome",headless:true});
const page=await browser.newPage({viewport:{width:1440,height:1000}});
await page.goto("http://127.0.0.1:8088/");
await page.waitForTimeout(1500);
console.log(await page.locator("body").innerText());
await page.screenshot({path:"../../docs/verification/2026-11-14/pleos-console.png",fullPage:true});
if(process.argv.includes("--verify-sdk")) {
    const child=spawn(join(process.env.LOCALAPPDATA,"Android/Sdk/platform-tools/adb.exe"),["-s","emulator-5554","shell","am","instrument","--user","10","-w","-e","class","com.example.pleos_demo.PleosSdkVerificationTest","com.example.pleos_demo.test/androidx.test.runner.AndroidJUnitRunner"],{stdio:"inherit"});
    const code=await new Promise(resolve=>child.on("exit",resolve));
    if(code!==0)process.exitCode=code;
}
await browser.close();
