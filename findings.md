# Visor + Claude Code bridge (Claude subscription on the glasses)

How this fork of [Visor](https://github.com/macedonga/visor) answers "Hey Meta" with a Claude Pro/Max subscription instead of an API key. A small HTTP bridge on the phone turns Visor's OpenAI-style requests, tool calls included, into questions for a Claude Code process. Notes and findings from the 2026-10-27 and 2026-10-28 work are at the end.

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
7. For music by voice: start something in Spotify (or another media app) once, so it has an active media session, and set the app in Visor's media settings.

Earlier bridges on the phone sit next to it as `bridge.js.bak` (the original) and `.v2` to `.v6`.

## The Visor side (changes in this fork)

- `Provider.CLAUDE_SUBSCRIPTION` in `Brain.kt`, handled like OpenAI-compatible in `Brain.kt` and `Models.kt`; `SettingsScreen.kt` and `MetaAiScreen.kt` treat any non-Claude-API provider as configured without a key.
- `AssistantSettings.kt`: three provider pills; picking the subscription sets the model to `claude-code` and the server to `http://127.0.0.1:8080/v1` unless the address already points at this phone. A **Bridge** row on that tab shows the status ("Running in Termux." when `/v1/models` answers), a note that the subscription only works while the bridge runs, and one button: Allow, then Start or Stop.
- `TermuxBridge.kt`: the button's work. Termux's `RUN_COMMAND` service runs `proot-distro login ubuntu -- bash -c "cd /root && exec node bridge.js >> bridge.log 2>&1"` to start, and `pkill -f "node bridge.js"` to stop. The manifest adds the `com.termux.permission.RUN_COMMAND` permission and a `<queries>` entry for `com.termux`.
- `Vision.kt`: a question whose glasses intent names an image (`IMPLICIT_DESCRIBE_IMAGE`, "what can I cook with this") now asks for the photo. It set none of the glasses' image flags and none of the word list, so before this no photo was requested and the model said it saw no image.
- `open_app` in `Phone.kt` and `PhoneActions.kt`: a tool that opens an installed app by its name ("open Spotify"). It looks among the apps with a launcher; the same name wins, else the closest one whose name contains what was said, and what was said may contain an app's name only from four letters up (an app called X once opened for "Zorbax"). Opened through the same path as directions, so while Visor is not in front, or the phone is locked, the app waits in a notification.
- Strings in English, Italian and Russian.

Keep fork changes in these few places (and new files) so `git merge upstream/main` stays quiet.

## The bridge (`bridge.js`)

- **One warm process**: a long-lived `claude -p --input-format stream-json --output-format stream-json` answers every question, so Claude Code does not start per request. It restarts after 10 idle minutes (`BRIDGE_IDLE_MIN`), 40 turns, an error or a 40 s timeout, and starts again right away.
- **Flags**: model `haiku` (`CLAUDE_MODEL`), a short voice system prompt, `--tools ""`, `--strict-mcp-config`, `--disable-slash-commands`, `--setting-sources ""`, `--no-session-persistence`, run from `~/.visor-bridge`.
- **Context**: the process remembers the conversation until it restarts. Saying "clear session" (or `/clear`) restarts it. Only Visor's last user message is used; Visor's system prompt is ignored.
- **Tool calls**: Visor's tool declarations (music, memory, MCP servers) are listed for Claude once per warm process, and again only if the list changes. Claude answers with one line, `TOOL_CALL {"name": ..., "arguments": {...}}`; the bridge turns it into an OpenAI `tool_calls` reply, Visor runs the tool, and the result goes back to the warm process as `TOOL_RESULT <name>: ...`. A call naming a tool Visor did not offer is spoken as text instead. After 4 results in a row for one question the list is withheld so the model must answer.
- **Vision**: `image_url` data URLs become Claude image blocks. No temp files and no Read tool.
- **No shell**: questions never touch a command line, so spoken `$(...)` or backticks run nothing (the first bridge built a shell string from the question).
- **Ending a conversation**: the prompt asks Claude to judge by meaning whether the wearer is closing the conversation ("thanks", "aight thx", "nvm", "nah I'm good", "ok bye"), not by a phrase list, and then to end its answer with `END_CONVERSATION`. The bridge strips the marker and, if Visor offered `end_conversation`, answers with that tool call. Visor then asks once more with a `tool` message; the bridge answers that from the earlier text without calling Claude. Visor sends the glasses the reply without the follow-up flag, so they stop listening. A short farewell ("You're welcome.") is still spoken, by choice.
- **Clipped words**: the glasses' recogniser often drops the first word of a follow-up ("and times two" heard as "n times two"). The prompt tells Claude to read such fragments as follow-ups to its last answer.
- **Auth**: optional `BRIDGE_TOKEN`; use the same value as Visor's API key. Other apps on the phone can reach a loopback port, so set it if that matters.
- Env: `BRIDGE_PORT` (8080), `CLAUDE_MODEL`, `BRIDGE_TOKEN`, `BRIDGE_IDLE_MIN`.

## Findings

**Speed.** Old bridge (`claude -c -p` per request, default model, long default prompt): 7-12 s from the glasses' question to the answer. Now: about 1.4-2.0 s for the model on the phone, 0.8 s on the Mac with the same bridge. Overall about 5 s from "Hey Meta", because the glasses spend about 3 s recording and recognising. Sending the tool list with every question cost about 0.3 s on the phone; sending it once per process fixed that on the Mac, but the phone's median stayed near 1.9 s, so the phone side is the limit. With the phone's voice, answers take longer since the speech is made on the phone.

**Follow-ups.** They chain only with the glasses' own switch **Respond without "Hey Meta"** on (`assistant_follow_up_enabled`). Visor's **More follow-ups** feature (MobileConfig flags) is separate and needs a glasses restart. With the follow-up switch on, the glasses open a new listening turn about 0.1 s after each answer (wake type 5) and close it themselves after 7 to 20 s of silence. Visor sends the follow-up flag for both voices; the glasses' own voice works with it on this pair. No setting for the length of that window was found; the MobileConfig flags `assistant_on_sn.default_asr_timeout_s` and `assistant_shutdown_waiting_interaction_loop_count` are candidates, untried.

**Wake phrases.** The glasses' transcript includes the wake phrase ("hey jarvis what's three plus three"), and the interaction start lists the custom phrase in both cases, so a phrase can only be told by the text. A custom wake word ("Hey Jarvis") is learned on the glasses in Settings > Meta AI > Custom wake word. Both phrases reach the same model.

**The custom wake word needs the US language pack.** After the glasses took the UK English pack (en_GB, voices `en_GB.0` and `.1`) and were closed and reopened, "Hey Jarvis" stopped waking them, and teaching it again failed with "Audio processing failed: resource 'audio_engine_voice_cww.json' not found". It worked again after going back to US English. Jarvis kept working for about ten minutes after the UK pack was sent, until the restart, which fits the glasses loading the new files at boot. That the UK pack lacks the file is an inference, not confirmed. Visor shows the word as set from its own saved state, not from the glasses.

**Stopping an answer.** A long press on the side button already stops the glasses' voice, and the glasses do it themselves. In the log it shows as a new, bare interaction (`2=3, 3=4`, no wake audio) that the glasses close within 0.1 s, and the interaction that was speaking ends with reason **17** (it was replaced). Normal ends are 12, and 16 showed up with the glasses handling something themselves. Answering the bare interaction as Meta's server does in Live AI (creation success, server connection status twice, `AsrStartListening`) did not make the glasses listen, so that experiment was removed. To ask something right after stopping, say the wake phrase again. A single click on the side runs the glasses' own media action (it resumed Spotify).

**Speed and voice of the glasses.** Visor's Settings > Language and voice has **Speech speed** for the glasses' own voice (0.75x, 1x, 1.25x, 1.5x, 1.75x, `tts_speak_speed`). The voice list (`assistant_voice_id`) holds Meta's voices only: Aria, Helio, celebrity and Italian ones, plus the British ones with the UK pack. A custom voice is possible only for the phone's voice, which is Android text-to-speech; Visor has no picker for the engine voice yet.

**Photo questions.** With the `Vision.kt` change, "what am I holding" and "what can I cook with this" request the photo and the model describes it. If the warm process remembers an earlier photo, a vague question such as "tell me about the roses" can be answered as if it were about that photo; "clear session" or a clearer question avoids it.

**Tools work through the bridge.** "Play some jazz" ran Visor's music tool and Spotify resumed once it had a media session. Before that Visor answered that the phone needed an app to play from, which was a Visor setting and the missing session, not the bridge. A Home Assistant tool is not set up in Visor, so "turn off the light" has nothing to call.

**Open apps by voice.** "Open Spotify" and "open WhatsApp" opened the apps in about 1.2 s from hearing the question; "open Zorbax" said there is no such app and opened nothing. The first version opened X for "Zorbax" because the word contains the letter x; the four-letter rule above fixed it. Visor's own `play_media` matching for music apps still uses the looser rule. Not tried: a locked phone (the app should wait in a notification).

**Quiet after actions.** The Visor switch Settings > Meta AI > **Quiet after actions** (`quiet_after_actions`, off by default) makes the glasses say nothing when every tool did what was asked. With it on, "open Spotify" and "open WhatsApp" ended with "Ended the turn without a word: every tool did what was asked" in the log, a plain question was still answered aloud, and a failed action still speaks. A reply that ends in a question is also spoken. It is all or nothing; a per-request "tell me when it's done" would need a Visor change.

**Remote Control.** Claude Code's Remote Control is Anthropic's relay between a session on your machine and claude.ai or the Claude mobile app. No supported API lets another program join a session by ID, so the glasses cannot attach to the Home Jarvis session on the Pi. `claude --resume <id>` works only on the machine that holds the conversation file, and Home Jarvis runs inside the Claude Terminal Pro add-on, which publishes no port. House control would have to come from Home Assistant's own MCP server added in Visor, not from that session.

**Meta's own AI is out of reach.** With Visor as the assistant, nothing hands "Hey Meta" to Meta's real assistant. Doing it would mean Visor calling Meta's private backend with the Meta login: unsupported, fragile, and a terms risk. Using Meta's AI means the Meta app owns the glasses again, which stops Visor.

**The proot kills what it started.** A bridge started inside a proot session dies when that session or command ends. That is why the app's Start runs Node as the command itself, and why `control.sh start` only works while the Ubuntu shell stays open.

**Tried on the phone and glasses.** Text answers, follow-ups, "thanks" and other closings, music, photo descriptions, opening apps by name, quiet after actions, the three provider tabs and Try it, the Bridge row status, Start keeping the bridge up for minutes. Not tried: the Home Assistant route, the 10-minute idle restart, Start from the background, opening an app with the phone locked.

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| Glasses say "Sorry, I couldn't get an answer", log has `401 invalid x-api-key` | Provider is Claude API, so Visor called api.anthropic.com. Pick Claude subscription. |
| Same message, nothing in the bridge log | Bridge not running. Check the Bridge row, or `./control.sh status`. |
| Bridge died after leaving the Ubuntu shell | See "The proot kills what it started". Use the Start button. |
| The glasses say "make sure Meta AI is active and Bluetooth is connected" | Visor's link to the glasses is down (log: "Link: lost them", "reaching the glasses"). Open Visor, wait for Home to show them connected, and ask again. |
| "Hey Jarvis" does nothing, "Hey Meta" works | The custom word needs the US language pack; see the finding above. Switch back to US English, restart the glasses, teach the word again. |
| Follow-up not understood ("n times two") | First word clipped by the glasses' recogniser. Pause half a second after the answer; the prompt already reads such fragments as follow-ups. |
| No follow-up listening at all | Turn on Respond without "Hey Meta" under Settings > Meta AI. |
| Music does not start | Start something in Spotify first so it has a media session, and choose it in Visor's media settings. |
| "Open <app>" says there is no such app | The name did not match an app with a launcher; say it as the app is labelled on the phone. |
| "Open <app>" only shows a notification | Android lets Visor open another app only while Visor is in front (or as a companion app); tap the notification. |
| Start does nothing | Termux needs `allow-external-apps = true` and Visor needs the Allow grant. |

## Working on the fork

- `upstream` is `macedonga/visor`. Merge often: `git fetch upstream && git merge upstream/main`. The last merge (2026-10-27) had no conflicts. Don't delete upstream files; keep additions in new files.
- Debug builds: `./gradlew :app:assembleDebug`, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`. Logs: `files/events.log` through `adb exec-out run-as com.macedonga.visor cat files/events.log`. `files/wire.log` is encrypted frames, so it does not help with unknown messages.
- Test on the Mac first: `bridge.js` runs there with the `claude` CLI (`BRIDGE_PORT=18080 node bridge.js`, then curl `/v1/chat/completions`, with a `tools` list to try tool calls).
- A foldable has two displays: `adb exec-out screencap -p -d <id>`. The same phone can appear twice in `adb devices`, or under a different name after reconnecting wireless debugging; use `-s`.
- Glasses behaviour is only proven by trying it on the glasses. Commit after the wearer has tried a change.

## Not done

- Per-phrase routing ("Hey Jarvis" to a different persona or model). Possible by matching the transcript prefix; dropped for now.
- Starting the bridge on demand when a question arrives and it is down (Android limits starting other apps' services from the background).
- Home Assistant as a tool: add its MCP server in Visor's integrations (expose only safe entities; locks and alarms stay out).
- Interrupt and listen with one press; a shorter follow-up listening window (the two flags above).
- A voice picker for the phone's text-to-speech, a chess helper (Stockfish plus a board-reading step).
- A relay to Meta's real assistant.
