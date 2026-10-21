# Developer mode

Developer mode lets other apps, automations and the launcher work the glasses' camera through Visor. They can take a photo, start and stop a video, import a capture, and hear about new captures.

## Turn it on

Open Settings, Advanced, and turn on **Developer mode** under Tools.

The glasses take a photo for the phone only when a Meta switch, `wearables_robusta.demo_enabled`, is on. Developer mode turns it on, and turns it off again when you turn the mode off. If Visor has never read the glasses' configuration, it reads it first, which can take a minute over Bluetooth.

While the mode is on:

- Visor answers the actions below.
- The launcher offers two shortcuts on Visor's icon: Take photo and Record video.
- Visor broadcasts the events below.

When the mode is off, Android does not deliver the actions to Visor at all.

## Actions

Send each action as a broadcast to the package `com.macedonga.visor`, or start it as an activity. The activity shows nothing and closes at once.

| Action | Extras | What it does |
|---|---|---|
| `com.macedonga.visor.action.TAKE_PHOTO` | none | Takes a photo. |
| `com.macedonga.visor.action.START_VIDEO` | `capture_type` (optional): `video` or `hyperlapse` | Starts a video. |
| `com.macedonga.visor.action.STOP_VIDEO` | none | Stops the video. |
| `com.macedonga.visor.action.TOGGLE_VIDEO` | `capture_type` (optional) | Stops the video if one runs, else starts one. |
| `com.macedonga.visor.action.IMPORT_MEDIA` | `id`: the id from `MEDIA_AVAILABLE` | Imports that capture to the phone. |

If the glasses are not connected, Visor tries to connect and waits up to 20 seconds before it acts.

From a computer:

```
adb shell am broadcast -p com.macedonga.visor -a com.macedonga.visor.action.TAKE_PHOTO
adb shell am broadcast -p com.macedonga.visor -a com.macedonga.visor.action.IMPORT_MEDIA --es id <id>
```

From an app:

```kotlin
context.sendBroadcast(Intent("com.macedonga.visor.action.TAKE_PHOTO").setPackage("com.macedonga.visor"))
```

Set the package. Android does not deliver a broadcast without one to Visor. In Tasker, use Send Intent with the package `com.macedonga.visor` and the target Broadcast Receiver.

## Events

Visor sends these as broadcasts while developer mode is on.

| Event | When | Extras |
|---|---|---|
| `com.macedonga.visor.event.MEDIA_AVAILABLE` | The glasses have a capture that this phone does not have yet. | `id`, `type`, `time`, `size` |
| `com.macedonga.visor.event.IMPORT_PROGRESS` | The photo or video of a capture is on its way, once per whole percent. | `id`, `received`, `size`, `percent` |
| `com.macedonga.visor.event.NEW_MEDIA` | A capture is in the gallery. | `id`, `type`, `time`, `uri`, `path` |
| `com.macedonga.visor.event.IMPORT_FAILED` | An import did not finish. | `id`, `reason` |
| `com.macedonga.visor.event.VIDEO_STARTED` | The glasses started a video. | `time` |
| `com.macedonga.visor.event.VIDEO_STOPPED` | The glasses stopped a video. | `time` |

The extras:

- `id`: the capture's id on the glasses. `IMPORT_MEDIA` takes it.
- `type`: `photo`, `live` (a photo with its clip) or `video`.
- `time`: milliseconds since 1970. For a capture, when the glasses took it. For a video event, when Visor saw it.
- `size` and `received`: bytes, as longs.
- `percent`: 0 to 100, as an int.
- `uri`: the capture's `content://media/...` address in the gallery.
- `path`: the file, for example `/storage/emulated/0/Pictures/Visor/Visor_20261021_103433.jpg`. Photos go to `Pictures/Visor` and videos to `Movies/Visor`.
- `reason`: why the import failed. `NOT_FOUND` means the glasses have no capture with that id. `WIFI_OFF`, `NOT_ALLOWED`, `CUT_OFF` and the others come from Visor's import.

To receive the events, register a receiver while your app runs:

```kotlin
val filter = IntentFilter().apply {
    addAction("com.macedonga.visor.event.MEDIA_AVAILABLE")
    addAction("com.macedonga.visor.event.NEW_MEDIA")
}
ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
```

A receiver declared in your manifest does not get them, because Android delivers broadcasts without a package only to receivers registered at run time.

## Things to know

- A typical flow: take a photo, wait for its `MEDIA_AVAILABLE`, send `IMPORT_MEDIA` with that id, and read the file from `NEW_MEDIA`. In a test, `MEDIA_AVAILABLE` came less than a second after the photo.
- An import goes over Wi-Fi Direct, so the phone's Wi-Fi must be on.
- The glasses also send captures still waiting in their own queue from an earlier import. Each one gets its own `NEW_MEDIA`, so check the `id`.
- `IMPORT_MEDIA` for a capture already on the phone imports nothing and sends `NEW_MEDIA` again.
- After Visor restarts, it sends `MEDIA_AVAILABLE` again for every capture still waiting on the glasses.
- Visor's own imports, from the gallery or on their own, send `IMPORT_PROGRESS`, `NEW_MEDIA` and `IMPORT_FAILED` too.
- To read the file at `uri` or `path`, your app needs Android's permission to read photos and videos.
- While the mode is on, any app on the phone can send these actions. The glasses' capture light still turns on for every photo and video.
- The glasses' call app also refers to `wearables_robusta.demo_enabled`. What the switch does there is not known.
- Visor's event log (Settings, Advanced, Event log) shows each event it sends, with its extras.
