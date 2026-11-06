import test from "node:test";
import assert from "node:assert/strict";
import { spawn } from "node:child_process";
import { once } from "node:events";
import { mkdtemp, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

test("MVP_C API: shared todos, duplicate request, failed lookup and restart", async () => {
  const dir = await mkdtemp(join(tmpdir(), "camp-on-group-test-"));
  const endpoint = "http://127.0.0.1:18787";
  let child;
  const stop = async () => {
    if (child && child.exitCode === null) {
      const exited = once(child, "exit");
      child.kill(); await exited;
    }
  };
  const start = async () => {
    child = spawn(process.execPath, [fileURLToPath(new URL("./server.mjs", import.meta.url))], {
      env: { ...process.env, PORT: "18787", GROUP_DATA_FILE: join(dir, "state.json"), OPENAI_API_KEY: "" },
      stdio: ["ignore", "pipe", "pipe"]
    });
    await new Promise((resolve, reject) => {
      const timeout = setTimeout(() => reject(new Error("server startup timeout")), 5000);
      child.stdout.once("data", () => { clearTimeout(timeout); resolve(); });
      child.once("error", e => { clearTimeout(timeout); reject(e); });
      child.once("exit", code => { clearTimeout(timeout); reject(new Error("server exited " + code)); });
    });
  };
  const api = async (path, method = "GET", body) => {
    const response = await fetch(endpoint + path, { method,
      headers: { "Content-Type": "application/json" }, body: body === undefined ? undefined : JSON.stringify(body) });
    return { status: response.status, data: await response.json() };
  };
  try {
    await start();
    assert.equal((await api("/api/state")).data.vehicles.length, 3);
    const first = await api("/api/requests/request-1/todo", "POST");
    const second = await api("/api/requests/request-1/todo", "POST");
    assert.equal(second.data.todos.length, first.data.todos.length);
    await api("/api/todos/shopping", "PATCH", { done: true });
    assert.equal((await api("/api/state")).data.todos.find(t => t.id === "shopping").done, true);
    assert.equal((await api("/api/todos/missing", "PATCH", { done: true })).status, 404);
    const memo = await api("/api/memos", "POST", { vehicleId: "tralala", text: "휴게소에 들러요" });
    assert.equal(memo.status, 201);
    assert.equal(memo.data.state.memos.at(-1).text, "휴게소에 들러요");
    await api("/api/vehicles", "POST", { vehicles: [{ id: "princess", status: "도착", distance: "0km" }] });
    await stop(); await start();
    const restored = (await api("/api/state")).data;
    assert.equal(restored.todos.find(t => t.id === "shopping").done, true);
    assert.equal(restored.vehicles.find(v => v.id === "princess").status, "도착");
  } finally { await stop(); await rm(dir, { recursive: true, force: true }); }
});
