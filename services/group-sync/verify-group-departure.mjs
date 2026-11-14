import {spawn} from "node:child_process";
import {once} from "node:events";
import {mkdtemp,rm} from "node:fs/promises";
import {tmpdir} from "node:os";
import {join} from "node:path";
const dir=await mkdtemp(join(tmpdir(),"campon-group-flow-"));
const server=spawn(process.execPath,["companion.mjs"],{env:{...process.env,PORT:"18789",COMPANION_DATA_FILE:join(dir,"state.json")},stdio:["ignore","pipe","pipe"]});
try {
 await Promise.race([once(server.stdout,"data"),once(server,"exit").then(()=>{throw Error("fixture failed");})]);
 const adb=join(process.env.LOCALAPPDATA,"Android/Sdk/platform-tools/adb.exe");
 const test=spawn(adb,["-s","emulator-5554","shell","am","instrument","--user","10","-w","-e","class",process.argv.includes("--group-only")?"com.example.pleos_demo.GroupDepartureTest":"com.example.pleos_demo.GroupDepartureTest,com.example.pleos_demo.KeyboardStabilityTest,com.example.pleos_demo.CampUxTest","com.example.pleos_demo.test/androidx.test.runner.AndroidJUnitRunner"],{stdio:["ignore","pipe","pipe"]});
 let output="";test.stdout.on("data",data=>{output+=data;process.stdout.write(data);});test.stderr.pipe(process.stderr);
 const [code]=await once(test,"exit");
 if(code!==0||!output.includes(process.argv.includes("--group-only")?"OK (1 test)":"OK (4 tests)"))throw Error("Android group flow verification failed");
} finally {
 const exited=once(server,"exit");server.kill();await exited;await rm(dir,{recursive:true,force:true});
}
