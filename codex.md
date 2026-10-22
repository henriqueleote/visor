# Claude subscription without an API key: findings

Checked 2026-10-22.

## Short answer

A Claude Pro or Max subscription **does not include Anthropic API access**, so it cannot be used by Visor's existing `Claude` provider. Anthropic explicitly treats Claude.ai subscriptions and Console/API usage as separate products: [Anthropic Help Center](https://support.anthropic.com/en/articles/9876003-i-subscribe-to-a-paid-claude-ai-plan-why-do-i-have-to-pay-separately-for-api-usage-on-console).

The subscription can authenticate **Claude Code**, including its documented non-interactive/SDK mode. That makes a local bridge technically possible, but it is not an API, is not a feature of the Claude Android app, and is not a supported Android/Termux deployment. Do not treat it as a stable replacement for the API without confirming that this particular always-on assistant use is permitted for the account/plan.

## What this project already supports

Visor has two model protocols:

- `Provider.CLAUDE`: fixed `https://api.anthropic.com/v1/messages`, with an Anthropic API key.
- `Provider.OPENAI_COMPATIBLE`: `BASE_URL/chat/completions`, with an optional bearer key.

The latter is the integration point for a companion. It already carries system prompt, recent turns, JPEG vision data, OpenAI-style tools, and a 45-second HTTP read timeout. The settings UI permits a blank key for this provider. See `core/assistant/.../Brain.kt`, `Models.kt`, and `AssistantConfig.kt`.

The Android manifest already enables cleartext HTTP because local-network models are supported. Therefore an HTTP service bound to `127.0.0.1` needs no Visor-side networking change.

## Claude products and supported surfaces

- Claude Pro/Max can sign in to **Claude Code**, and plan usage is included subject to its own limits. It is distinct from opting into API-credit billing: [using Claude Code with Pro/Max](https://support.anthropic.com/en/articles/11145838-using-claude-code-with-your-pro-or-max-plan).
- Anthropic documents Claude Code system support as macOS, Ubuntu/Debian, and Windows/WSL—not Android or Termux: [setup requirements](https://docs.anthropic.com/en/docs/claude-code/getting-started). A Termux install may happen to work, but it is unsupported and can break on updates.
- The Claude Android app is a consumer chat client. I found no documented localhost server, Android intent/API for submitting a prompt, or credential hand-off interface for another Android app.
- Claude Code does document non-interactive use: `claude -p`, JSON/stream-JSON output, and resume-by-session-ID. Its reference calls this programmatic/SDK use: [CLI reference](https://docs.anthropic.com/en/docs/claude-code/cli-usage). The Claude Code SDK similarly runs the CLI as a subprocess: [SDK documentation](https://docs.anthropic.com/en/docs/claude-code/sdk).

## Viable experiment: a Termux localhost bridge

Architecture:

```text
Visor (OpenAI-compatible provider)
  POST http://127.0.0.1:<port>/v1/chat/completions
       ↓
Termux bridge (localhost only, authenticated)
       ↓ subprocess / SDK
Claude Code signed in with the user's Pro/Max account
       ↓
Anthropic service
```

The bridge translates a narrow subset of OpenAI Chat Completions into a Claude Code prompt and translates its final JSON/text output back into `choices[0].message.content`. It should reject any request that it cannot faithfully implement rather than pretending that a tool or vision request succeeded.

Recommended first scope:

1. Text-only, one request at a time.
2. Use `claude -p --output-format json --max-turns 1`; choose an explicit model and parse the structured final result.
3. Add a high-entropy local bearer secret; bind only to `127.0.0.1`/`::1`; never expose the port on Wi-Fi.
4. Return clear errors for plan limits, login expiry, process death, and timeout.
5. Keep the bridge foreground/visible while in use. Android can stop Termux in the background, and Visor cannot launch or supervise a Termux process directly because apps are sandboxed from each other.

Conversation continuity can use Claude Code session IDs, but only if the bridge maps each Visor conversation to a session ID. Visor's own recent-turn payload is a safer initial implementation and avoids depending on Claude Code's on-device session storage.

## Important gaps and risks

- **Not local inference.** “Local session” only means the adapter runs on the phone. Prompts and images still leave the device for Anthropic, and network access is required.
- **Subscription limits are shared.** Glasses interactions consume the same Claude Code plan allocation as coding work; limits can interrupt an assistant turn. Anthropic documents reset/limit behaviour and optional API-credit fallback in its Pro/Max guidance above.
- **Tools do not map automatically.** Visor expects function calls for memories, phone actions, and MCP servers. Claude Code has its own tool/permission model. Start without tools; later expose only deliberately designed MCP tools and never enable unrestricted shell/filesystem access for voice-triggered prompts.
- **Vision is separate work.** Visor supplies JPEGs as an OpenAI `image_url` data URI. A bridge must make that image available to Claude Code in a supported form and ensure it is cleaned up. Do not base64-dump camera images into arbitrary shell arguments.
- **Security/privacy.** A loopback service is still reachable by every app that can connect to it. Require a secret, avoid logging prompts/photos/tokens, cap request sizes, and redact diagnostics. Claude Code credentials stay inside Termux; Visor should never read, copy, or ask the user to paste them.
- **Compatibility.** Claude Code is designed as an agentic coding tool. Its CLI flags, output schema, authentication behaviour, and plan policy can change independently of Visor. Pinning its version is sensible for a prototype, but it does not make Termux supported.

## Recommendation

For dependable daily glasses use, use an Anthropic API key in the existing Claude provider (or another supported OpenAI-compatible provider). That is the supported product designed for embedding a model in an app.

If avoiding API spend is more important than reliability, build the Termux bridge only as an **experimental, opt-in `OpenAI-compatible` endpoint**. It should be documented as “requires Claude Code; unofficial Android/Termux setup; subscription limits apply,” begin text-only without tools or vision, and fail closed. There is no viable native Claude-app integration to implement today.

No application code was changed for these findings.
