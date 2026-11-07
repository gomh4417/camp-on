// Read-only production health check. Fixture participants belong only in isolated tests.
import assert from "node:assert/strict";
const base=process.argv[2];
if(!base?.startsWith("https://"))throw Error("Pass the public HTTPS origin");
const response=await fetch(base+"/api/health");
assert.ok(response.ok);
assert.equal((await response.json()).ok,true);
console.log("HTTPS health OK; no groups, participants or messages created.");
