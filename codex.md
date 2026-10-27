# Visor + Claude Code bridge (Claude subscription on the glasses)

How this fork of [Visor](https://github.com/macedonga/visor) answers "Hey Meta" with a Claude Pro/Max subscription instead of an API key. A small HTTP bridge on the phone turns Visor's OpenAI-style requests into questions for a Claude Code process. Notes and findings from the 2026-10-27 work are at the end.

## Architecture

```text
Ray-Ban Meta glasses ──Bluetooth──> Visor (Android app)
                                       │  OpenAI-compatible request, http://127.0.0.1:8080/v1
                                       ▼
                         bridge.js  (Termux > Ubuntu proot, Node)
                                       │  stream-json over stdin/stdout, one warm process
                                       ▼
                         claude -p  (Claude Code, logged in with the subscription, model haiku)
```

- Visor answers every wake phrase ("Hey Meta", a custom "Hey Jarvis") with one configured model. It plays Meta's assistant server on the glasses' link, so there is no per-phrase choice and no hand-off to Meta's own AI.
- The app has three providers: **Claude API** (Anthropic key), **Claude subscription** (this bridge) and **OpenAI-compatible** (anything else). The subscription tab is the OpenAI-compatible path with the bridge's address and model filled in.

## Setup on the phone

1. Install Termux and its add-ons (`com.termux`, `com.termux.api`) from F-Droid or GitHub.
2. In Termux: `pkg install proot-distro openssh`, then `proot-distro install ubuntu` and `proot-distro login ubuntu`.
3. In Ubuntu: `apt install nodejs npm`, `npm install -g @anthropic-ai/claude-code`, run `claude` once and log in with the subscription.
4. Put `bridge.js` (this repo's root) in `/root` of the proot. Optional `control.sh {start|stop|status}` there keeps a PID file for starting it by hand.
5. For the app's Start/Stop button: add `allow-external-apps = true` to `~/.termux/termux.properties` in Termux, run `termux-reload-settings`, then tap **Allow** on the Bridge row in Visor. Turn off battery optimisation for Termux.
6. In Visor: Settings > Meta AI > Provider > **Claude subscription**. Server `http://127.0.0.1:8080/v1`, model `claude-code`, key blank.

Backups of earlier bridges on the phone sit next to it as `bridge.js.bak` (the original), `.v2`, `.v3`.

## The Visor side (changes in this fork)

- `Provider.CLAUDE_SUBSCRIPTION` in `Brain.kt`, handled like OpenAI-compatible in `Brain.kt` and `Models.kt`; `SettingsScreen.kt` and `MetaAiScreen.kt` treat any non-Claude-API provider as configured without a key.
- `AssistantSettings.kt`: three provider pills; picking the subscription sets the model to `claude-code` and the server to `http://127.0.0.1:8080/v1` unless the address already points at this phone. A **Bridge** row on that tab shows the status ("Running in Termux." when `/v1/models` answers), a note that the subscription only works while the bridge runs, and one button: Allow, then Start or Stop.
- `TermuxBridge.kt`: the button's work. Termux's `RUN_COMMAND` service runs `proot-distro login ubuntu -- bash -c "cd /root && exec node bridge.js >> bridge.log 2>&1"` to start, and `pkill -f "node bridge.js"` to stop. The manifest adds the `com.termux.permission.RUN_COMMAND` permission and a `<queries>` entry for `com.termux`.
- Strings in English, Italian and Russian.

Keep fork changes in these few places (and new files) so `git merge upstream/main` stays quiet.

## The bridge (`bridge.js`)

- **One warm process**: a long-lived `claude -p --input-format stream-json --output-format stream-json` answers every question, so Claude Code does not start per request. It restarts after 10 idle minutes (`BRIDGE_IDLE_MIN`), 40 turns, an error or a 40 s timeout, and starts again right away.
- **Flags**: model `haiku` (`CLAUDE_MODEL`), a short voice system prompt, `--tools ""`, `--strict-mcp-config`, `--disable-slash-commands`, `--setting-sources ""`, `--no-session-persistence`, run from `~/.visor-bridge`.
- **Context**: the process remembers the conversation until it restarts. Saying "clear session" (or `/clear`) restarts it. Only Visor's last user message is used; its system prompt and tool list are ignored.
- **Vision**: `image_url` data URLs become Claude image blocks. No temp files and no Read tool.
- **No shell**: questions never touch a command line, so spoken `$(...)` or backticks run nothing (the first bridge built a shell string from the question).
- **Ending a conversation**: the prompt asks Claude to end its answer with `END_CONVERSATION` when the wearer is done (thanks, that's all, goodbye) and is not being asked something. The bridge strips the marker and, if Visor offered `end_conversation`, answers with that tool call. Visor then asks once more with a `tool` message; the bridge answers that from the earlier text without calling Claude. Visor sends the glasses the reply without the follow-up flag, so they stop listening.
- **Clipped words**: the glasses' recogniser often drops the first word of a follow-up ("and times two" heard as "n times two"). The prompt tells Claude to read such fragments as follow-ups to its last answer.
- **Auth**: optional `BRIDGE_TOKEN`; use the same value as Visor's API key. Other apps on the phone can reach a loopback port, so set it if that matters.
- Env: `BRIDGE_PORT` (8080), `CLAUDE_MODEL`, `BRIDGE_TOKEN`, `BRIDGE_IDLE_MIN`.

## Findings

**Speed.** Old bridge (`claude -c -p` per request, default model, long default prompt): 7-12 s from the glasses' question to the answer. Now: about 1.4-2.0 s for the model. Overall about 5 s from "Hey Meta", because the glasses spend about 3 s recording and recognising. With the phone's voice, answers take longer than 2 s since the speech is made on the phone.

**Follow-ups.** They chain only with the glasses' own switch **Respond without "Hey Meta"** on (`assistant_follow_up_enabled`). Visor's **More follow-ups** feature (MobileConfig flags) is separate and needs a glasses restart. With the follow-up switch on, the glasses open a new listening turn about 0.1 s after each answer (wake type 5) and close it themselves after 7 to 20 s of silence. Visor sends the follow-up flag for both voices; the glasses' own voice works with it on this pair.

**Wake phrases.** The glasses' transcript includes the wake phrase ("hey jarvis what's three plus three"), and the interaction start lists the custom phrase in both cases, so a phrase can only be told by the text. A custom wake word ("Hey Jarvis") is learned on the glasses in Settings > Meta AI > Custom wake word and works. Both phrases reach the same model.

**Meta's own AI is out of reach.** With Visor as the assistant, nothing hands "Hey Meta" to Meta's real assistant. Doing it would mean Visor calling Meta's private backend with the Meta login: unsupported, fragile, and a terms risk. Using Meta's AI means the Meta app owns the glasses again, which stops Visor.

**Phone actions don't work through the bridge.** Visor's tools (music, photos, memory, MCP) arrive as tool declarations, which the bridge ignores. Only `end_conversation` is honoured.

**The proot kills what it started.** A bridge started inside a proot session dies when that session or command ends. That is why the app's Start runs Node as the command itself, and why `control.sh start` only works while the Ubuntu shell stays open.

**Tried on the phone.** Text answers, follow-ups and "thanks that's all" on the glasses; the three provider tabs and Try it; the Bridge row status. Start kept the bridge up for a couple of minutes. Not tried: images on the glasses, the 10-minute idle restart, Start from the background.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Glasses say "Sorry, I couldn't get an answer", log has `401 invalid x-api-key` | Provider is Claude API, so Visor called api.anthropic.com. Pick Claude subscription. |
| Same message, nothing in the bridge log | Bridge not running. Check the Bridge row, or `./control.sh status`. |
| Bridge died after leaving the Ubuntu shell | See "The proot kills what it started". Use the Start button. |
| Follow-up not understood ("n times two") | First word clipped by the glasses' recogniser. Pause half a second after the answer; the prompt already reads such fragments as follow-ups. |
| No follow-up listening at all | Turn on Respond without "Hey Meta" under Settings > Meta AI. |
| Start does nothing | Termux needs `allow-external-apps = true` and Visor needs the Allow grant. |

## Working on the fork

- `upstream` is `macedonga/visor`. Merge often: `git fetch upstream && git merge upstream/main`. The last merge (2026-10-27) had no conflicts. Don't delete upstream files; keep additions in new files.
- Debug builds: `./gradlew :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Logs: `files/events.log` and `files/wire.log` through `adb exec-out run-as com.macedonga.visor cat files/<name>`.
- Test on the Mac first: `bridge.js` runs there with the `claude` CLI (`BRIDGE_PORT=18080 node bridge.js`, then curl `/v1/chat/completions`).
- A foldable has two displays: `adb exec-out screencap -p -d <id>`. The same phone can appear twice in `adb devices`; use `-s`.
- Glasses behaviour is only proven by trying it on the glasses.

## Not done

- Per-phrase routing ("Hey Jarvis" to a different persona or model). Possible by matching the transcript prefix; dropped for now.
- Starting the bridge on demand when a question arrives and it is down (Android limits starting other apps' services from the background).
- Tool calls through the bridge (phone actions, MCP).
- A relay to Meta's real assistant.
