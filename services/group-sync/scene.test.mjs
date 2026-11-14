import {test} from "node:test";
import assert from "node:assert/strict";
import {readFile} from "node:fs/promises";
import {gifDuration} from "./gif-duration.mjs";
test("all supplied transitions have finite frame-derived durations",async()=>{
    for(const name of ["01_dawn_to_day","02_day_to_dusk","03_dusk_to_night","04_night_to_dawn"]) {
        const result=gifDuration(await readFile(new URL("../../app/src/main/assets/camping/transitions/"+name+".gif",import.meta.url)));
        assert.ok(result.frames>1);assert.ok(result.durationMs>0&&result.durationMs<60000);
        console.log(name,JSON.stringify(result));
    }
});
test("reject malformed GIF without looping forever",()=>{
    assert.throws(()=>gifDuration(Buffer.from("invalid")));
    assert.throws(()=>gifDuration(Buffer.from("GIF89a00000000000000000000")));
});
