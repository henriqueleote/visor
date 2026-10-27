# Meta AI Visor + Claude Code Local Bridge (Full Setup & Guide)

A complete step-by-step setup guide and architecture overview for routing voice and vision queries from the [Visor](https://github.com/macedonga/visor) app (running on Ray-Ban Meta glasses via Android) through a local **Claude Code** CLI session, leveraging a Claude Pro/Max subscription without separate API usage charges.

---

## Architecture Overview

```text
[Ray-Ban Meta Glasses] 
       │ (Visor App / Bluetooth / ADB)
       ▼
[Android / Termux / Ubuntu Proot-Distro] 
       │ (Local HTTP Bridge on 127.0.0.1:8080)
       ▼
[Claude Code CLI (-c -p)] 
       │ (Authenticated Pro/Max Account)
       ▼
[Anthropic Claude API]
```

---

## 1. Prerequisites & macOS Connection

- macOS client connecting via wireless ADB (`adb-RFCX61JBHVJ-F7wjF4._adb-tls-connect._tcp.`)
- OpenSSH daemon running on port `8022` to connect to Termux on Android.

---

## 2. Termux & Ubuntu Proot-Distro Environment Setup

1. **Install Termux & Termux:API** on your Android device (`com.termux`, `com.termux.api`).
2. **Install and Configure Ubuntu Proot-Distro**:
   ```bash
   pkg update && pkg install proot-distro openssh
   proot-distro install ubuntu
   proot-distro login ubuntu
   ```
3. **Configure SSH Access**:
   - Set a user password in Termux (`passwd`).
   - Start the SSH daemon on port `8022` to allow remote management from macOS:
     ```bash
     sshd
     ```

---

## 3. Tooling & Claude Code CLI Installation

Inside your Ubuntu proot container:

1. **Install Node.js & npm**:
   ```bash
   apt update && apt install -y nodejs npm
   ```
2. **Install Claude Code CLI**:
   ```bash
   npm install -g @anthropic-ai/claude-code
   ```
3. **Authenticate**:
   - Run the initial login flow to link your Claude Pro/Max account:
     ```bash
     claude
     ```
   - Complete the browser authentication steps.

---

## 4. Visor App & Meta AI Token Extraction

1. Build and configure the Visor project from source on macOS using ADB and decompilation tools (`apktool`, `jadx`).
2. Extract official Meta AI client authentication tokens (`HW|`, `AR|`, `FRL|`) by pulling and decompiling the Meta AI APK:
   ```bash
   adb shell pm path <meta_ai_package_name>
   adb pull <path_to_apk>
   # Decompile using apktool/jadx to locate client tokens
   ```

---

## 5. Local Bridge Architecture (`bridge.js`)

The bridge script runs on `127.0.0.1:8080`, exposing an OpenAI-compatible endpoint (`/v1/chat/completions`) that translates incoming Visor voice and image payloads into execution flags for Claude Code.

### How it works now

- **One warm process**: a single long-lived `claude -p --input-format stream-json` answers every question, so there is no Claude Code start-up per request (about 0.7-2 s per answer, down from 5-12 s). It is started with the bridge and restarted after 10 idle minutes (`BRIDGE_IDLE_MIN`), 40 turns, an error, or a timeout.
- **Fast flags**: model `haiku` (`CLAUDE_MODEL` to change it), a short voice system prompt, no tools, no MCP, no skills, no settings, no session files.
- **Context**: the process remembers the conversation until it restarts. "clear session" (or `/clear`) restarts it by hand.
- **Vision**: `image_url` data URLs from Visor go to Claude as image blocks. No temporary files and no Read tool.
- **No shell**: the question never touches a shell command line, so spoken text like `$(...)` cannot run anything.
- **Optional token**: set `BRIDGE_TOKEN` and use the same value as Visor's API key; any other app on the phone can reach a loopback port.
- Visor's system prompt and tool declarations are ignored: only the last user message is used.

---

## 6. Running the Bridge

Start the bridge server inside your Ubuntu proot environment:

```bash
node bridge.js
```

The bridge code itself is maintained in **`bridge.js`** at your project root.