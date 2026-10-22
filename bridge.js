const http = require('http');
const { exec } = require('child_process');

const PORT = 8080;
let useContinueFlag = true;

const server = http.createServer((req, res) => {
  console.log(`[INCOMING] ${req.method} ${req.url}`);

  if (req.method === 'GET') {
    res.writeHead(200, { 'Content-Type': 'application/json' });
    return res.end(JSON.stringify({
      object: 'list',
      data: [{ id: 'claude-code', object: 'model', created: 1700000000, owned_by: 'local' }]
    }));
  }

  if (req.method === 'POST' && req.url.includes('chat/completions')) {
    let body = '';
    req.on('data', chunk => { body += chunk.toString(); });
    req.on('end', () => {
      try {
        const parsed = JSON.parse(body);
        const userMessages = parsed.messages ? parsed.messages.filter(m => m.role === 'user') : [];
        const lastMessage = userMessages.length > 0 ? userMessages[userMessages.length - 1].content : '';

        if (!lastMessage) {
          throw new Error("No user message found.");
        }

        let responseText = "";
        const lowerMsg = lastMessage.trim().toLowerCase();

        if (lowerMsg === '/remote session' || lowerMsg === 'remote session') {
          responseText = `Persistent session mode is active (${useContinueFlag ? 'Continuing latest thread' : 'Fresh sessions'}). Say "clear session" to reset.`;
          return sendResponse(res, responseText);
        }

        if (lowerMsg === '/clear' || lowerMsg === 'clear session') {
          useContinueFlag = false;
          setTimeout(() => { useContinueFlag = true; }, 2000);
          responseText = "Session context cleared. Starting a fresh conversation.";
          return sendResponse(res, responseText);
        }

        // Voice optimization instruction injected before every user prompt
        const voiceDirective = "[System: Keep your response extremely short, concise, and direct, as it will be spoken aloud via a voice assistant.] ";
        const combinedPrompt = voiceDirective + lastMessage;
        
        const safePrompt = combinedPrompt.replace(/"/g, '\\"').replace(/\n/g, ' ');
        const flag = useContinueFlag ? '-c -p' : '-p';
        const cmd = `claude ${flag} "${safePrompt}" --output-format json --max-turns 1`;

        exec(cmd, { timeout: 40000 }, (error, stdout, stderr) => {
          responseText = "Sorry, I couldn't process that request.";

          if (!error && stdout) {
            try {
              const jsonRes = JSON.parse(stdout);
              responseText = jsonRes.result || jsonRes.text || stdout;
            } catch (e) {
              responseText = stdout.trim();
            }
          } else {
            console.error("Claude Error:", stderr || error);
          }

          sendResponse(res, responseText);
        });
      } catch (err) {
        console.error("Payload error:", err);
        res.writeHead(400, { 'Content-Type': 'application/json' });
        res.end(JSON.stringify({ error: "Invalid payload" }));
      }
    });
    return;
  }

  res.writeHead(404, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: `Route not found: ${req.url}` }));
});

function sendResponse(res, text) {
  const responsePayload = {
    id: 'chatcmpl-termux-bridge',
    object: 'chat.completion',
    created: Math.floor(Date.now() / 1000),
    choices: [{
      index: 0,
      message: { role: 'assistant', content: text },
      finish_reason: 'stop'
    }]
  };
  res.writeHead(200, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify(responsePayload));
}

server.listen(PORT, '127.0.0.1', () => {
  console.log(`Persistent voice-optimized bridge server running at http://127.0.0.1:${PORT}`);
});