const http = require('http');
const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { randomUUID } = require('crypto');

// Shown in Visor's Bridge row. The minor number goes up once per commit, the patch with each build tried between commits.
const VERSION = '1.3.4';
const PORT =Number(process.env.BRIDGE_PORT) || 8080;
const MODEL = process.env.CLAUDE_MODEL || 'haiku';
// What Visor's model picker may ask for; anything else (as the old 'claude-code') gets MODEL.
const MODELS = ['haiku', 'sonnet', 'opus'];
const SESSIONS = path.join(os.homedir(), '.visor-bridge', 'sessions.json');
// Optional: other apps on the phone can reach a loopback port, so set this and Visor's API key to the same secret.
const TOKEN = process.env.BRIDGE_TOKEN || '';
const TIMEOUT_MS = 40000;
const IDLE_MS = (Number(process.env.BRIDGE_IDLE_MIN) || 10) * 60000;
const MAX_TURNS = 40;
const MAX_BODY = 20e6;
const MAX_TOOL_ROUNDS = 4;
const WORKDIR = path.join(os.homedir(), '.visor-bridge');
const SYSTEM = 'You are a voice assistant on smart glasses. Your answer is spoken aloud: reply in one or two short ' +
  'sentences, plain words, no markdown, lists or emoji. If an image comes with the question, it is what the wearer sees. ' +
  'Search the web only when the answer depends on current or local facts (news, scores, prices, opening hours, ' +
  'recent events) and no tool you were given covers it; for the weather use the weather tool. Otherwise answer from what you know, since a search makes the wearer wait. ' +
  'The question comes from speech recognition, which can clip or mishear the first word (for example "and times two" ' +
  'arrives as "n times two"): when it only makes sense as a follow-up to your last answer, read it that way. ' +
  'The glasses keep listening after every answer. Judge by meaning, not by exact words: when the wearer is closing the ' +
  'conversation (thanks, thx, aight, cool that is it, nah I am good, nvm, bye, or anything else that dismisses you) and ' +
  'is not asking anything new, answer in a few words and end with the word END_CONVERSATION. Do not end it when they ' +
  'are still asking or when your answer asked them something.';
const END_TOOL = 'end_conversation';
const END = /\s*END_CONVERSATION\W*/gi;
const TOOL_CALL = /TOOL_CALL\s*(\{[\s\S]*\})/;
// The wake phrase starts the transcript ("hey jarvis what's..."); its word is the name the wearer gave the assistant.
const WAKE = /^\s*(?:hey|hi|ok|okay)\s+([a-z]+)\b/i;
const FALLBACK = "Sorry, I couldn't process that request.";

// One long-lived Claude Code process answers every question: starting `claude -p` per request is most of the wait.
let worker = null;
// Tool call ids the bridge handed to Visor, so a result can be named when it comes back.
const calls = new Map();

// Visor's model setting: the model, then the tab's switches and the custom wake word, as in "sonnet;resume;name=Jarvis".
// shortcut: the switches ride in the model name so Visor's core needs no new setting; give them fields if this stays.
function options(name) {
  const [model, ...flags] = String(name || '').split(';');
  return {
    model: MODELS.includes(model) ? model : MODEL,
    resume: flags.includes('resume'),
    name: (flags.find(f => f.startsWith('name=')) || '').slice(5).replace(/^(hey|hi|ok|okay)\s+/i, '').trim(),
  };
}
const key = o => [o.model, o.resume && 'resume'].filter(Boolean).join(';');

// The last conversation of each model, kept when resuming is on, so switching back or restarting picks it up.
let sessions = {};
try { sessions = JSON.parse(fs.readFileSync(SESSIONS, 'utf8')); } catch (e) {}
function keep(model, id) {
  if (sessions[model] === id) return;
  sessions[model] = id;
  try { fs.writeFileSync(SESSIONS, JSON.stringify(sessions)); } catch (e) {}
}

// fresh: a new conversation even when resuming is on (after an error, the idle time or MAX_TURNS).
function start(o = options(), fresh = false) {
  fs.mkdirSync(WORKDIR, { recursive: true });
  const model = o.model;
  const resume = o.resume && !fresh && sessions[model];
  const child = spawn('claude', [
    '-p', '--input-format', 'stream-json', '--output-format', 'stream-json', '--verbose',
    '--model', model, '--system-prompt', SYSTEM + ` You run on Claude ${model[0].toUpperCase() + model.slice(1)}; asked your model, say just that, without who made it.`,
    // Web search only: answers that need fresh facts. No shell, files or fetching pages.
    '--tools', 'WebSearch', '--allowedTools', 'WebSearch', '--strict-mcp-config', '--disable-slash-commands', '--setting-sources', '',
    ...(o.resume ? (resume ? ['--resume', resume] : []) : ['--no-session-persistence']),
  ], { cwd: WORKDIR, stdio: ['pipe', 'pipe', 'pipe'] });
  console.log(`[${new Date().toISOString()}] Claude ${key(o)}${resume ? ` resuming ${resume}` : ''}`);
  const w = { child, opts: o, model, buf: '', err: '', pending: null, turns: 0, last: Date.now() };
  const lost = error => {
    if (worker === w) worker = null;
    if (w.pending) w.pending.reject(error);
  };
  child.stdout.on('data', chunk => {
    w.buf += chunk;
    let i;
    while ((i = w.buf.indexOf('\n')) >= 0) {
      const line = w.buf.slice(0, i);
      w.buf = w.buf.slice(i + 1);
      let event;
      try { event = JSON.parse(line); } catch (e) { continue; }
      if (event.type === 'result' && o.resume && event.session_id) keep(model, event.session_id);
      if (event.type === 'result' && w.pending) w.pending.resolve(event);
    }
  });
  child.stderr.on('data', chunk => { w.err = (w.err + chunk).slice(-500); });
  child.on('error', lost);
  child.on('close', () => lost(new Error(w.err || 'claude exited')));
  child.stdin.on('error', () => {});
  return w;
}

// Drops the conversation; the next one starts on a process that is already up.
function reset(o = worker ? worker.opts : options(), fresh = true) {
  const old = worker;
  worker = start(o, fresh);
  if (old) {
    old.pending = null;
    old.child.kill('SIGTERM');
  }
}

let queue = Promise.resolve();
function ask(content, tools, memory, o, name) {
  const run = queue.then(() => send(content, tools, memory, o, name));
  queue = run.catch(() => {});
  return run;
}

function send(content, tools, memory, o, name) {
  return new Promise((resolve, reject) => {
    // Another model or switch in Visor means a new process: its last conversation when resuming, else a fresh one.
    if (worker && worker.turns >= MAX_TURNS) reset();
    else if (worker && key(worker.opts) !== key(o)) reset(o, false);
    const w = worker || (worker = start(o));
    // The warm process keeps what it was given, so the tool list and the memories go again only when they change.
    const intro = [];
    if (tools && w.catalog !== tools) {
      intro.push(tools);
      w.catalog = tools;
    }
    if (memory && w.memory !== memory) {
      intro.push('What you know about the wearer, from Visor (it replaces any earlier version):' + memory);
      w.memory = memory;
    }
    if (name && w.name !== name) {
      const label = 'Claude ' + w.model[0].toUpperCase() + w.model.slice(1);
      intro.push(`The wearer woke you with "hey ${name}", so your name is ${name}: answer to it and call yourself ${name}. ` +
        `Asked your name, give only that; asked what model you are, say just ${label}, never who made it.`);
      w.name = name;
    }
    content = [...intro.map(text => ({ type: 'text', text })), ...content];
    const settle = (fn, value) => () => { clearTimeout(timer); w.pending = null; fn(value); };
    const timer = setTimeout(() => { reset(); reject(new Error('timeout')); }, TIMEOUT_MS);
    w.pending = {
      resolve: event => {
        w.last = Date.now();
        if (event.is_error || typeof event.result !== 'string' || !event.result.trim()) {
          reset();
          settle(reject, new Error(event.result || 'Claude Code returned no text'))();
        } else {
          settle(resolve, event.result.trim())();
        }
      },
      reject: error => settle(reject, error)(),
    };
    w.turns++;
    w.child.stdin.write(JSON.stringify({ type: 'user', message: { role: 'user', content } }) + '\n');
  });
}

// OpenAI message content to Claude's blocks; images arrive as data URLs.
function blocks(content) {
  if (typeof content === 'string') return [{ type: 'text', text: content }];
  const out = [];
  for (const part of content || []) {
    if (part.type === 'text' && part.text) {
      out.push({ type: 'text', text: part.text });
    } else if (part.type === 'image_url') {
      const url = (part.image_url && part.image_url.url) || '';
      const cut = url.indexOf(';base64,');
      if (url.startsWith('data:image/') && cut > 0) {
        out.push({ type: 'image', source: { type: 'base64', media_type: url.slice(5, cut), data: url.slice(cut + 8) } });
      }
    }
  }
  return out;
}

// Visor's tools for this question, one line each. The model answers with a TOOL_CALL line to use one.
function catalog(tools) {
  const lines = tools.map(t => {
    const fn = t.function;
    const props = Object.keys((fn.parameters && fn.parameters.properties) || {});
    return `- ${fn.name}(${props.join(', ')}): ${(fn.description || '').slice(0, 160)}`;
  });
  return 'Tools you can use:\n' + lines.join('\n') + '\n' +
    'To use one, reply with exactly one line and nothing else: TOOL_CALL {"name": "<tool>", "arguments": {...}}. ' +
    'Its result comes back as a TOOL_RESULT line; then answer the wearer in one or two short sentences. ' +
    'Use a tool only when the wearer asks for an action or a fact that needs it.';
}

// The memories part of Visor's system prompt: how to keep facts and the facts kept. The rest of that prompt is
// written for models that start fresh each time, so it stays out. shortcut: tied to Brain.kt's wording; update both.
const MEMORY_START = ' You can keep lasting facts';
const MEMORY_AFTER = [" You can also act on the wearer's phone", ' After each answer the glasses keep listening', " The glasses' photo comes"];
function memory(messages) {
  const system = (messages.find(m => m.role === 'system') || {}).content;
  if (typeof system !== 'string') return null;
  const from = system.indexOf(MEMORY_START);
  if (from < 0) return null;
  const ends = MEMORY_AFTER.map(s => system.indexOf(s, from)).filter(i => i > 0);
  return system.slice(from, ends.length ? Math.min(...ends) : system.length);
}

// Tool results since the wearer's last question: a model that keeps calling tools is stopped after MAX_TOOL_ROUNDS.
function toolRounds(messages) {
  let n = 0;
  for (let i = messages.length - 1; i >= 0 && messages[i].role !== 'user'; i--) if (messages[i].role === 'tool') n++;
  return n;
}

// The tool call in an answer, if it names one of the tools offered; null otherwise.
function parseCall(answer, tools) {
  const m = answer.match(TOOL_CALL);
  if (!m) return null;
  try {
    const call = JSON.parse(m[1]);
    return tools.some(t => t.function.name === call.name) ? { name: call.name, args: call.arguments || {} } : null;
  } catch (e) {
    return null;
  }
}

function completion(res, message, finish, model) {
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({
    id: 'chatcmpl-termux-bridge',
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    model: model || 'claude-code',
    choices: [{ index: 0, message, finish_reason: finish }],
  }));
}

// ends: Visor offered end_conversation and the wearer is done, so answer with that call; Visor then asks once more for the words.
function say(res, text, model, ends) {
  const message = { role: 'assistant', content: text };
  if (ends) message.tool_calls = [{ id: 'call_end', type: 'function', function: { name: END_TOOL, arguments: '{}' } }];
  completion(res, message, ends ? 'tool_calls' : 'stop', model);
}

function callReply(res, id, call, model) {
  calls.set(id, call.name);
  if (calls.size > 500) calls.clear();
  const tool_calls = [{ id, type: 'function', function: { name: call.name, arguments: JSON.stringify(call.args) } }];
  completion(res, { role: 'assistant', content: null, tool_calls }, 'tool_calls', model);
}

function fail(res, status, message) {
  res.writeHead(status, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: message }));
}

const server = http.createServer((req, res) => {
  if (TOKEN && req.headers.authorization !== `Bearer ${TOKEN}`) return fail(res, 401, 'missing or invalid bearer token');

  if (req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      object: 'list',
      data: [{ id: 'claude-code', name: `Claude Code, bridge ${VERSION}` }, ...MODELS.map(id => ({ id }))]
        .map(m => ({ ...m, object: 'model', created: 1700000000, owned_by: 'local' })),
    }));
  }

  if (req.method !== 'POST' || !req.url.includes('chat/completions')) return fail(res, 404, `Route not found: ${req.url}`);

  let body = '';
  req.on('data', chunk => {
    body += chunk;
    if (body.length > MAX_BODY) req.destroy();
  });
  req.on('end', async () => {
    let request;
    let messages;
    let content;
    let isResult = false;
    try {
      request = JSON.parse(body);
      messages = request.messages || [];
      const last = messages[messages.length - 1] || {};
      // The round after end_conversation: nothing to ask Claude, the words already went out with the call.
      if (last.role === 'tool' && last.tool_call_id === 'call_end') {
        const before = messages.slice().reverse().find(m => m.role === 'assistant');
        return say(res, (before && typeof before.content === 'string' && before.content) || 'Okay.', request.model);
      }
      if (last.role === 'tool') {
        // A tool's result: the warm process already holds the question, so it only needs the result.
        isResult = true;
        const name = calls.get(last.tool_call_id) || 'tool';
        const result = typeof last.content === 'string' ? last.content : JSON.stringify(last.content);
        content = [{ type: 'text', text: `TOOL_RESULT ${name}: ${result}` }];
      } else {
        const users = messages.filter(m => m.role === 'user');
        content = blocks(users.length ? users[users.length - 1].content : '');
        if (!content.length) throw new Error('No user message found.');
      }
    } catch (error) {
      return fail(res, 400, 'Invalid payload');
    }

    const spoken = content.filter(b => b.type === 'text').map(b => b.text).join(' ').trim().toLowerCase();
    if (!isResult && (spoken === '/clear' || spoken === 'clear session')) {
      reset();
      return say(res, 'Session cleared. Starting a fresh conversation.', request.model);
    }

    const tools = (request.tools || []).filter(t => t.function && t.function.name !== END_TOOL);
    const offered = (request.tools || []).some(t => t.function && t.function.name === END_TOOL);
    const useTools = tools.length > 0 && toolRounds(messages) < MAX_TOOL_ROUNDS;
    const list = useTools ? catalog(tools) : null;

    const started = Date.now();
    try {
      const wake = !isResult && spoken.match(WAKE);
      // The word just said, else the glasses' custom wake word from Visor, else the last one said (also across restarts).
      const o = options(request.model);
      if (wake) keep('_name', wake[1][0].toUpperCase() + wake[1].slice(1).toLowerCase());
      const name = (wake && sessions._name) || o.name || sessions._name || null;
      const answer = await ask(content, list, memory(messages), o, name);
      const done = new RegExp(END.source, 'i').test(answer);
      const call = useTools ? parseCall(answer, tools) : null;
      console.log(`[${new Date().toISOString()}] answered in ${Date.now() - started} ms` +
        `${call ? ` (tool ${call.name})` : ''}${done ? ' (conversation ends)' : ''}`);
      if (call) return callReply(res, 'call_' + randomUUID().slice(0, 12), call, request.model);
      const text = answer.replace(TOOL_CALL, ' ').replace(END, ' ').trim();
      say(res, text || 'Okay.', request.model, done && offered);
    } catch (error) {
      console.error('Claude error:', error.message);
      say(res, FALLBACK, request.model);
    }
  });
});

server.listen(PORT, '127.0.0.1', () => {
  worker = start();
  setInterval(() => { if (worker && worker.turns && Date.now() - worker.last > IDLE_MS) reset(worker.opts, false); }, 60000).unref();
  console.log(`Claude voice bridge ${VERSION} on http://127.0.0.1:${PORT} (model ${MODEL})`);
});

process.on('exit', () => { if (worker) worker.child.kill('SIGTERM'); });
process.on('SIGINT', () => process.exit(0));
process.on('SIGTERM', () => process.exit(0));
