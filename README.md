<p align="center">
  <img src="docs/banner.png" alt="Visor: a de-metaified Meta Glasses client" width="100%">
</p>

<p align="center">
  <a href="https://github.com/macedonga/visor/releases/latest"><img src="https://img.shields.io/github/v/release/macedonga/visor?style=for-the-badge&label=Download&color=FFD2A1&labelColor=161617&v=1" alt="Download the latest release"></a>
</p>

Visor is an Android app for Ray-Ban Meta glasses that replaces the Meta AI app. It speaks the glasses' own protocol over Bluetooth and Wi-Fi Direct, keeps your photos on your phone, and answers "Hey Meta" with a model you choose.

Visor is an independent project. It is not affiliated with, endorsed by or sponsored by Meta Platforms or EssilorLuxottica.

## What it does

- **Home.** A 3D model of your pair, lit by a light that shows their state: connected, charging, asleep, recording, out of reach. Under it, the glasses' battery and the case's, free space, volume, temperature and your latest photos.
- **Gallery.** Imports photos and videos over Wi-Fi Direct, on its own when the glasses charge or when you ask. Live photos become Motion Photos. Everything goes to `Pictures/Visor` and `Movies/Visor`.
- **Live view.** The glasses' camera on your phone, with recording, an instant replay of the last minutes, snapshots, RTMP broadcast and Live AI.
- **Assistant.** "Hey Meta" goes to your own model: Claude, or any OpenAI-compatible endpoint. The glasses read the answer in their own voice. Every conversation is kept on the phone in a diary, and you can continue one from the app.
- **Notifications.** Message notifications go to the glasses, which announce them with the sender's name from your contacts.
- **Settings.** Every setting the glasses have, the way the Meta app shows them, plus an Advanced section with all the rest, assistant language packs and the firmware's feature flags.
- **Outside the app.** A home screen widget, a Quick Settings tile, launcher shortcuts and a Live Update notification while the Live view runs.
- **Developer mode.** Other apps and automations can take photos and videos, import captures and hear about new ones. See [Developer mode](docs/developer-mode.md).

## What it sends, and where

Visor has no server of its own, no analytics and no crash reporting. It connects to:

- Meta, during setup: the login (`accounts.meta.com`) and the ownership receipt that makes Visor the owner app of your glasses (`graph.facebook-hardware.com`).
- Meta, when it needs to: the frame style and lens colour of your pair, and assistant language packs (`ar-genai.graph.meta.com`).
- The model provider you set for the assistant, with your own API key.
- The RTMP server you enter, when you broadcast from the Live view.
- GitHub, twice a day: the latest release, to tell you when there is a new version (`api.github.com`). Turn it off in Settings, About.

Everything else stays between the phone and the glasses.

## Requirements

- Ray-Ban Meta (Gen 2). Visor is tested on firmware 72387580077200100. Other Meta glasses are not tested.
- An Android phone with Android 10 or later.
- A Meta account, for setup. Log in with a password or an email code. Passkeys do not work.
- For the assistant: an API key from Anthropic or from an OpenAI-compatible service.

## Install

1. Download `visor-<version>.apk` from the [latest release](https://github.com/macedonga/visor/releases/latest).
2. Compare its SHA-256 with the `.sha256` file next to it.
3. Install the APK. Android asks you to allow installs from your browser or file manager.
4. Open Visor and follow the setup: permissions, Meta login, pairing, then pick your glasses.

To put the glasses in pairing mode, fold both arms and hold the capture button for about 5 seconds. The LED inside the frame blinks blue.

Release APKs are signed with this certificate:

```
CN=Visor
SHA-256: F6:01:C7:FE:63:EF:04:E0:7F:66:8A:EC:BA:5A:2B:9C:23:90:C4:C0:22:B5:2B:5F:B0:F6:D3:04:22:7C:B9:C7
```

## Things to know

- The glasses accept one session at a time. If the Meta AI app connects to them, the session Visor holds ends, and the other way round.
- A build you made yourself has a different signing key from a release. Uninstall it before you install a release, and the other way round. Uninstalling removes the login and the glasses' keys from the phone, so you set up again.
- For English answers in the glasses' own voice, Visor sometimes turns off a Meta setting and restarts the glasses' assistant when it connects. The first "Hey Meta" or two after that can go unheard.
- The assistant answers only as well as the model you give it. Visor sends it the question, the recent conversation and, when you ask about what you see, the photo the glasses take.

## Build from source

You need JDK 21 (the one bundled with Android Studio works) and the Android SDK with platform 37.

Setup logs in to Meta with the Meta AI app's own client tokens. They are not in this repository: extract them from the official Meta AI app, where they are the strings that start with `HW|`, `AR|` and `FRL|`. Add them to `local.properties` before you build:

```
VISOR_META_HW_TOKEN=HW|...
VISOR_META_AR_TOKEN=AR|...
VISOR_META_FRL_TOKEN=FRL|...
```

Without them Visor builds and runs, but the Meta login and the ownership step of setup fail. Release builds in CI read them from the repository secrets with the same names.

```
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Debug builds write every frame to `files/wire.log` on the phone:

```
adb exec-out run-as com.macedonga.visor cat files/wire.log
```

They also have a broadcast receiver that drives the app from adb (`app/src/debug`). Release builds have neither.

A tag like `v1.2.3` builds the signed release APK in CI and publishes it as a GitHub release (`.github/workflows/release.yml`).

## How it is built

| Module | What it holds |
|---|---|
| `core/protocol` | Wire format, ciphers, handshake messages. Plain JVM. |
| `core/transport` | The RFCOMM byte pipe and the wire log. |
| `core/session` | The link: handshake, channel mux, reconnect ladder. |
| `core/identity` | Owner key store, trust, Meta login, change of owner, language packs. |
| `core/connection` | `GlassesConnection` and `GlassesState`, device status. |
| `core/media` | Media import, the Live view, the glasses' MobileConfig. |
| `core/settings` | Glasses settings and contacts. |
| `core/assistant` | Plays Meta's assistant server on the glasses' side, with your model. |
| `core/design` | Colours and type. |
| `app` | The service, the screens, setup, the widget and the tile. |

[CLAUDE.md](CLAUDE.md) lists the protocol traps that cost the most time to find.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) first. There are no test suites: changes are tried on real glasses, and a pull request says what was tried.

Report security problems privately, as [SECURITY.md](SECURITY.md) explains, not in a public issue.

## License

Visor is free software under the [GNU Affero General Public License v3.0](LICENSE).

Meta, Ray-Ban and Ray-Ban Meta are trademarks of their owners. Visor uses the names only to say which glasses it works with.
