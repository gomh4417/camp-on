import { createServer } from "node:http";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = dirname(fileURLToPath(import.meta.url));
const dataFile = process.env.GROUP_DATA_FILE || join(root, "data", "state.json");
const port = Number(process.env.PORT || 8787);
const clients = new Set();

const defaultState = {
  revision: 1,
  group: {
    id: "gangneung-camping",
    name: "강릉 캠핑 그룹",
    destination: "솔향강릉카라반캠핑장"
  },
  vehicles: [
    { id: "princess", name: "공주등장", status: "이동 중", listStatus: "이동 중", location: "강릉", eta: "17분 후", distance: "9.2km", battery: 71 },
    { id: "tralala", name: "트랄랄라", status: "출발 대기", listStatus: "출발 대기", location: "강릉IC 인근", eta: "16분 후", distance: "10.7km", battery: 71 },
    { id: "tiramisu", name: "티라미수케", status: "도착", listStatus: "도착 · 솔향강릉카라반캠핑장", location: "솔향강릉카라반캠핑장", eta: "도착", distance: "0km", battery: 71 }
  ],
  todos: [
    { id: "tent-check", title: "텐트 팩 수량 확인", ownerVehicleId: "princess", done: true },
    { id: "shopping", title: "비빔라면과 햇반 구매", ownerVehicleId: "princess", done: false },
    { id: "ice", title: "얼음 2봉 구매", ownerVehicleId: "tralala", done: true },
    { id: "checkin", title: "캠핑장 체크인 연락", ownerVehicleId: "tralala", done: false },
    { id: "site", title: "사이트 위치 확인", ownerVehicleId: "tiramisu", done: true },
    { id: "parking", title: "주차 공간 확보", ownerVehicleId: "tiramisu", done: true }
  ],
  memos: [
    { id: "memo-1", vehicleId: "tralala", text: "마트에서 비빔라면도 사 달라고 해줘", time: "3분 전" },
    { id: "memo-2", vehicleId: "tiramisu", text: "햇반도 10개 부탁해. 나는 먼저 도착했어.", time: "2분 전" }
  ],
  requests: [
    { id: "request-1", fromVehicleId: "tralala", toVehicleId: "princess", item: "비빔라면 구매" },
    { id: "request-2", fromVehicleId: "tiramisu", toVehicleId: "princess", item: "햇반 10개 구매" }
  ],
  summary: "티라미수케는 캠핑장에 도착했어요. 공주등장은 장보기 요청을 확인해야 해요.",
  headline: `${withSubjectParticle("티라미수케")} 캠핑장에 도착했어요.`,
  relativeTime: "방금 전",
  updatedAt: new Date().toISOString()
};

let state = await loadState();

async function loadState() {
  try {
    return { ...defaultState, ...JSON.parse(await readFile(dataFile, "utf8")) };
  } catch {
    return structuredClone(defaultState);
  }
}

async function persist() {
  state.revision += 1;
  state.updatedAt = new Date().toISOString();
  await mkdir(dirname(dataFile), { recursive: true });
  await writeFile(dataFile, JSON.stringify(state, null, 2), "utf8");
  broadcast();
}

function broadcast() {
  const packet = `event: state\ndata: ${JSON.stringify(state)}\n\n`;
  for (const response of clients) response.write(packet);
}

function json(response, status, body) {
  response.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Access-Control-Allow-Origin": "*",
    "Cache-Control": "no-store"
  });
  response.end(JSON.stringify(body));
}

async function body(request) {
  const chunks = [];
  for await (const chunk of request) chunks.push(chunk);
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function vehicleName(id) {
  return state.vehicles.find((vehicle) => vehicle.id === id)?.name || "그룹원";
}

function withSubjectParticle(name) {
  const characters = Array.from(String(name));
  const last = characters.at(-1);
  if (!last) return String(name);
  const code = last.codePointAt(0);
  if (code < 0xAC00 || code > 0xD7A3) return `${name}이(가)`;
  return `${name}${(code - 0xAC00) % 28 === 0 ? "가" : "이"}`;
}

function deterministicExtraction(text, fromVehicleId) {
  const assignee = text.includes("트랄랄라") ? "tralala" : text.includes("티라미수") ? "tiramisu" : "princess";
  let item = null;
  if (text.includes("비빔라면") && text.includes("햇반")) item = "비빔라면과 햇반 구매";
  else if (text.includes("비빔라면")) item = "비빔라면 구매";
  else if (text.includes("햇반")) item = "햇반 구매";
  else if (text.includes("얼음")) item = "얼음 구매";
  else if (text.includes("생수") || (text.includes("물") && /사|부탁/.test(text))) item = "생수 구매";
  else if (/픽업|태워/.test(text)) item = "탑승자 픽업";
  else if (/부탁|해줘|사줘/.test(text)) {
    const core = text
      .replace(/(공주등장|트랄랄라|티라미수케?)[에게는아,\s]*/g, "")
      .replace(/(도|를|을)?\s*(부탁해|부탁해줘|사줘|해줘).*$/g, "")
      .replace(/[.!?]/g, "")
      .trim();
    item = `${core || "요청 항목"} 구매`.slice(0, 28);
  }
  return item ? { isRequest: true, item, fromVehicleId, toVehicleId: assignee } : { isRequest: false };
}

async function extractRequest(text, fromVehicleId) {
  if (!process.env.OPENAI_API_KEY) return deterministicExtraction(text, fromVehicleId);
  try {
    const result = await fetch("https://api.openai.com/v1/responses", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${process.env.OPENAI_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        model: process.env.OPENAI_MODEL || "gpt-5-mini",
        input: `차량 그룹의 음성 메모에서 실행할 요청만 추출하세요. 발화자=${fromVehicleId}\n메모=${text}`,
        text: {
          format: {
            type: "json_schema",
            name: "group_request",
            strict: true,
            schema: {
              type: "object",
              properties: {
                isRequest: { type: "boolean" },
                item: { type: ["string", "null"] },
                toVehicleId: { type: ["string", "null"], enum: ["princess", "tralala", "tiramisu", null] }
              },
              required: ["isRequest", "item", "toVehicleId"],
              additionalProperties: false
            }
          }
        }
      })
    });
    if (!result.ok) throw new Error(`OpenAI ${result.status}`);
    const payload = await result.json();
    const parsed = JSON.parse(payload.output_text);
    return { ...parsed, fromVehicleId, toVehicleId: parsed.toVehicleId || "princess" };
  } catch (error) {
    console.warn("AI extraction fallback:", error.message);
    return deterministicExtraction(text, fromVehicleId);
  }
}

function updateBriefing(extracted, text) {
  const arrived = state.vehicles.filter((vehicle) => vehicle.status === "도착").map((vehicle) => vehicle.name);
  const pending = state.todos.filter((todo) => !todo.done);
  if (extracted.isRequest) {
    state.headline = `새 요청: ${extracted.item}`;
    const arrival = arrived.length === state.vehicles.length
      ? "3대 모두 도착했어요."
      : arrived.length ? `${arrived.join(", ")} 도착.` : "";
    state.summary = `${vehicleName(extracted.toVehicleId)}: ${extracted.item}. ${arrival}`.trim();
  } else {
    state.headline = "새 음성 메모가 등록됐어요.";
    state.summary = `${text.slice(0, 36)}${text.length > 36 ? "…" : ""}${pending.length ? ` 남은 할 일은 ${pending.length}개예요.` : ""}`;
  }
  state.relativeTime = "방금 전";
}

const server = createServer(async (request, response) => {
  const url = new URL(request.url, `http://${request.headers.host || "localhost"}`);
  if (request.method === "OPTIONS") {
    response.writeHead(204, {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "GET,POST,PATCH,DELETE,OPTIONS",
      "Access-Control-Allow-Headers": "Content-Type"
    });
    return response.end();
  }

  try {
    if (request.method === "GET" && url.pathname === "/api/state") return json(response, 200, state);
    if (request.method === "GET" && url.pathname === "/api/health") return json(response, 200, { ok: true, revision: state.revision });
    if (request.method === "GET" && url.pathname === "/api/events") {
      response.writeHead(200, {
        "Content-Type": "text/event-stream; charset=utf-8",
        "Access-Control-Allow-Origin": "*",
        "Cache-Control": "no-cache",
        "Connection": "keep-alive"
      });
      response.write(`event: state\ndata: ${JSON.stringify(state)}\n\n`);
      clients.add(response);
      request.on("close", () => clients.delete(response));
      return;
    }

    if (request.method === "POST" && url.pathname === "/api/vehicles") {
      const input = await body(request);
      let changed = false;
      for (const incoming of input.vehicles || []) {
        const index = state.vehicles.findIndex((vehicle) => vehicle.id === incoming.id);
        if (index >= 0) {
          const next = { ...state.vehicles[index], ...incoming };
          if (JSON.stringify(next) !== JSON.stringify(state.vehicles[index])) {
            state.vehicles[index] = next;
            changed = true;
          }
        }
      }
      if (changed) await persist();
      return json(response, 200, state);
    }

    if (request.method === "POST" && url.pathname === "/api/memos") {
      const input = await body(request);
      const text = String(input.text || "").trim();
      if (!text) return json(response, 400, { error: "text is required" });
      const vehicleId = input.vehicleId || "princess";
      const id = `memo-${Date.now()}`;
      state.memos.push({ id, vehicleId, text, time: "방금 전" });
      state.memos = state.memos.slice(-8);
      const extracted = await extractRequest(text, vehicleId);
      if (extracted.isRequest && extracted.item) {
        const requestId = `request-${Date.now()}`;
        state.requests.push({ id: requestId, fromVehicleId: vehicleId, toVehicleId: extracted.toVehicleId, item: extracted.item });
        state.requests = state.requests.slice(-6);
      }
      updateBriefing(extracted, text);
      await persist();
      return json(response, 201, { state, extracted });
    }

    if (request.method === "POST" && url.pathname === "/api/todos") {
      const input = await body(request);
      const title = String(input.title || "").trim();
      if (!title) return json(response, 400, { error: "title is required" });
      state.todos.push({ id: `todo-${Date.now()}`, title, ownerVehicleId: input.ownerVehicleId || "princess", done: false });
      state.headline = `새 할 일: ${title}`;
      state.relativeTime = "방금 전";
      await persist();
      return json(response, 201, state);
    }

    const requestTodoMatch = url.pathname.match(/^\/api\/requests\/([^/]+)\/todo$/);
    if (request.method === "POST" && requestTodoMatch) {
      const requestItem = state.requests.find((item) => item.id === decodeURIComponent(requestTodoMatch[1]));
      if (!requestItem) return json(response, 404, { error: "request not found" });
      const existing = state.todos.find((todo) => todo.ownerVehicleId === requestItem.toVehicleId && todo.title === requestItem.item);
      if (!existing) {
        state.todos.push({ id: `todo-${requestItem.id}`, title: requestItem.item, ownerVehicleId: requestItem.toVehicleId, done: false });
      }
      state.headline = `TO-DO에 추가: ${requestItem.item}`;
      state.summary = `새 할 일: ${requestItem.item}`;
      state.relativeTime = "방금 전";
      await persist();
      return json(response, existing ? 200 : 201, state);
    }

    const requestMatch = url.pathname.match(/^\/api\/requests\/([^/]+)$/);
    if (request.method === "DELETE" && requestMatch) {
      const requestId = decodeURIComponent(requestMatch[1]);
      const requestIndex = state.requests.findIndex((item) => item.id === requestId);
      if (requestIndex < 0) return json(response, 404, { error: "request not found" });
      const [removed] = state.requests.splice(requestIndex, 1);
      state.headline = `요청 삭제: ${removed.item}`;
      state.summary = `요청에서 삭제했어요: ${removed.item}`;
      state.relativeTime = "방금 전";
      await persist();
      return json(response, 200, state);
    }

    const todoMatch = url.pathname.match(/^\/api\/todos\/([^/]+)$/);
    if (request.method === "PATCH" && todoMatch) {
      const input = await body(request);
      const todo = state.todos.find((item) => item.id === decodeURIComponent(todoMatch[1]));
      if (!todo) return json(response, 404, { error: "todo not found" });
      todo.done = Boolean(input.done);
      state.headline = `${todo.done ? "완료" : "남은 할 일"}: ${todo.title}`;
      state.summary = todo.done ? `완료한 할 일: ${todo.title}` : `남은 할 일: ${todo.title}`;
      state.relativeTime = "방금 전";
      await persist();
      return json(response, 200, state);
    }

    if (request.method === "POST" && url.pathname === "/api/reset") {
      state = structuredClone(defaultState);
      await persist();
      return json(response, 200, state);
    }

    json(response, 404, { error: "not found" });
  } catch (error) {
    console.error(error);
    json(response, 500, { error: error.message || "server error" });
  }
});

server.listen(port, "0.0.0.0", () => {
  console.log(`Group sync server: http://127.0.0.1:${port}`);
  console.log("Pleos emulator URL: http://10.0.2.2:8787");
});
