import {spawnSync} from "node:child_process";
import {writeFile} from "node:fs/promises";
import {join} from "node:path";
const name=process.argv[2];if(!/^[a-z0-9-]+$/.test(name||""))throw Error("Pass a capture name");
const result=spawnSync(join(process.env.LOCALAPPDATA,"Android/Sdk/platform-tools/adb.exe"),["-s","emulator-5554","exec-out","screencap","-p"],{maxBuffer:20*1024*1024});
if(result.status!==0)throw Error(result.stderr.toString());
await writeFile("../../docs/verification/2026-11-14/android/live-"+name+".png",result.stdout);
console.log("Captured live SDK app:",name);
