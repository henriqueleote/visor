# Experimental Claude Code bridge for Termux

This turns a Claude Code login into a localhost-only, text-only OpenAI-compatible endpoint for Visor. It is an experiment: Claude Code does not list Android/Termux as a supported platform, and Claude Pro/Max limits apply.

## Install on the phone

Install [Termux from F-Droid](https://f-droid.org/packages/com.termux/) or GitHub, not the obsolete Play Store build. In Termux:

```sh
pkg update
pkg install nodejs openssl
npm install -g @anthropic-ai/claude-code
claude
```

At the Claude Code login prompt, choose the Claude.ai Pro/Max subscription and finish sign-in. Confirm it works with `claude -p "Reply with OK"`.

Copy this `termux` directory to the phone, for example with ADB after the phone is connected:

```sh
adb push termux /data/local/tmp/visor-termux
adb shell mkdir -p /sdcard/Download/visor-termux
adb shell cp -r /data/local/tmp/visor-termux/. /sdcard/Download/visor-termux/
```

Then allow Termux storage access once (`termux-setup-storage`) and copy the directory into Termux. The direct practical alternative is to clone this repository in Termux and run the script from there.

Start it with a freshly generated secret, keeping the same secret for Visor's API-key field:

```sh
cd ~/visor-project/termux
export VISOR_BRIDGE_TOKEN="$(openssl rand -hex 32)"
node claude-bridge.mjs
```

It prints `http://127.0.0.1:3210/v1`. Leave this Termux session open for the test.

## Configure Visor

In **Settings → Meta AI**:

1. Choose **OpenAI-compatible**.
2. Set Base URL to `http://127.0.0.1:3210/v1`.
3. Paste `VISOR_BRIDGE_TOKEN` as the API key.
4. Set model to `claude-code`.
5. Turn **Vision** off for this first test.

Tap Visor's test prompt. A short response confirms phone-to-Termux-to-Claude Code communication. Then connect the glasses and ask a simple text question such as “What is the capital of Austria?”

## Deliberate limitations

- No images/video: turn Visor Vision off. The bridge rejects image payloads.
- Visor function tools are ignored, so this bridge cannot perform phone actions, memory writes, or MCP calls. Streaming is unsupported.
- One Claude Code process per request; the bridge has a 50-second timeout.
- Binds only to loopback. Do not change `VISOR_BRIDGE_HOST` to a LAN address.
- The bearer secret is required because other apps can otherwise reach a loopback port.

## Troubleshooting

- `Could not reach the provider`: make sure the Termux process is running and the URL includes `/v1`.
- `missing or invalid bearer token`: the key in Visor must exactly equal `VISOR_BRIDGE_TOKEN`.
- A Claude Code login/limit error: run `claude -p "Reply with OK"` in Termux and resolve it there.
- Android killed Termux: disable battery optimisation for Termux and restart the bridge. This is one reason this remains experimental.
