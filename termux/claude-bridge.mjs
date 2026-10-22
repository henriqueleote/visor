#!/usr/bin/env node
/** A localhost-only OpenAI Chat Completions adapter for Claude Code. */
import { createServer } from "node:http";
import { spawn } from "node:child_process";
import { randomUUID } from "node:crypto";

const host = process.env.VISOR_BRIDGE_HOST || "127.0.0.1";
const port = number("VISOR_BRIDGE_PORT", 3210);
const token = process.env.VISOR_BRIDGE_TOKEN;
const command = process.env.CLAUDE_COMMAND || "claude";
const model = process.env.CLAUDE_MODEL || "sonnet";
const timeoutMs = number("VISOR_BRIDGE_TIMEOUT_MS", 50_000);
const maxBody = number("VISOR_BRIDGE_MAX_BODY_BYTES", 1_000_000);

if (!token || token.length < 24) {
  console.error("Set VISOR_BRIDGE_TOKEN to a random secret of at least 24 characters.");
  process.exit(2);
}

function number(name, fallback) {
  const value = Number.parseInt(process.env[name] || "", 10);
  return Number.isSafeInteger(value) && value > 0 ? value : fallback;
}

function reply(res, status, body) {
  res.writeHead(status, { "content-type": "application/json; charset=utf-8" });
  res.end(JSON.stringify(body));
}

function failure(res, status, message, type = "invalid_request_error") {
  reply(res, status, { error: { message, type } });
}

function authorised(req) {
  return req.headers.authorization === `Bearer ${token}`;
}

async function body(req) {
  const chunks = [];
  let size = 0;
  for await (const chunk of req) {
    size += chunk.length;
    if (size > maxBody) throw new Error("request body is too large");
    chunks.push(chunk);
  }
  return JSON.parse(Buffer.concat(chunks).toString("utf8"));
}

function text(content) {
  if (typeof content === "string") return content;
  if (!Array.isArray(content)) throw new Error("each message content must be text");
  const parts = [];
  for (const part of content) {
    if (part?.type !== "text" || typeof part.text !== "string") {
      throw new Error("this bridge is text-only; disable Vision and do not send images");
    }
    parts.push(part.text);
  }
  return parts.join("\n");
}

function prompt(request) {
  if (!Array.isArray(request.messages) || request.messages.length === 0) throw new Error("messages is required");
  const lines = [
    "You are the speech assistant inside Visor smart glasses.",
    "Answer the wearer directly. Be concise and speakable. Do not use markdown.",
    "Do not use shell, filesystem, network, MCP, or any other tools. Visor's tool declarations are unavailable.",
  ];
  for (const message of request.messages) {
    const role = message?.role;
    if (!["system", "user", "assistant"].includes(role)) throw new Error(`unsupported message role: ${role}`);
    lines.push(`${role.toUpperCase()}: ${text(message.content)}`);
  }
  lines.push("ASSISTANT:");
  return lines.join("\n\n");
}

function invoke(input) {
  return new Promise((resolve, reject) => {
    const child = spawn(command, ["-p", "--output-format", "json", "--max-turns", "1", "--model", model, input], {
      stdio: ["ignore", "pipe", "pipe"],
      env: { ...process.env, CLAUDECODE: undefined },
    });
    let stdout = "";
    let stderr = "";
    child.stdout.on("data", chunk => { stdout += chunk; });
    child.stderr.on("data", chunk => { stderr += chunk; });
    const timer = setTimeout(() => child.kill("SIGTERM"), timeoutMs);
    child.on("error", error => { clearTimeout(timer); reject(error); });
    child.on("close", code => {
      clearTimeout(timer);
      if (code !== 0) return reject(new Error((stderr || `Claude Code exited ${code}`).trim().slice(0, 500)));
      try {
        const parsed = JSON.parse(stdout);
        if (parsed.is_error || typeof parsed.result !== "string" || !parsed.result.trim()) {
          throw new Error(parsed.result || "Claude Code returned no final text");
        }
        resolve(parsed.result.trim());
      } catch (error) {
        reject(new Error(`could not read Claude Code response: ${error.message}`));
      }
    });
  });
}

const server = createServer(async (req, res) => {
  if (!authorised(req)) return failure(res, 401, "missing or invalid bearer token", "authentication_error");
  if (req.method === "GET" && req.url === "/v1/models") {
    return reply(res, 200, { object: "list", data: [{ id: "claude-code", object: "model", owned_by: "local" }] });
  }
  if (req.method !== "POST" || req.url !== "/v1/chat/completions") {
    return failure(res, 404, "use POST /v1/chat/completions or GET /v1/models");
  }
  try {
    const request = await body(req);
    if (request.stream) throw new Error("streaming is not supported");
    const answer = await invoke(prompt(request));
    reply(res, 200, {
      id: `chatcmpl-${randomUUID()}`,
      object: "chat.completion",
      created: Math.floor(Date.now() / 1000),
      model: request.model || "claude-code",
      choices: [{ index: 0, message: { role: "assistant", content: answer }, finish_reason: "stop" }],
    });
  } catch (error) {
    console.error(error.message);
    failure(res, 400, error.message);
  }
});

server.listen(port, host, () => console.log(`Visor Claude bridge listening at http://${host}:${port}/v1`));
