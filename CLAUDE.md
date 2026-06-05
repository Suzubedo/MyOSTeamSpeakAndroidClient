# CLAUDE.md

Project context for Claude Code. Read this fully before making changes.

## What this project is

An open-source **TeamSpeak 3-compatible voice client for Android 14+** (minSdk 34,
targetSdk 35, Java 17). Its reason to exist: let the user **select input/output
audio devices in settings and default to the connected Bluetooth device** —
the official TS Android client fails to route Bluetooth microphones.

Built on [`ts3j`](https://github.com/Manevolent/ts3j), a reverse-engineered Java
implementation of the TS3 *full client* protocol (handshake, identity crypto,
encrypted UDP voice channel). We supply the Android shell, audio I/O, and routing.

The owner (Krys) administers his own TS3 server, so server-side permission
changes are possible when needed.

## Current state (verified on a real device)

WORKING:
- Gradle build is green (Android Studio, bundled Gradle; wrapper jar/script NOT in repo)
- App installs and runs on a physical Android phone
- Permissions flow (RECORD_AUDIO, BLUETOOTH_CONNECT, POST_NOTIFICATIONS)
- Settings screen: input/output device spinners + "prefer Bluetooth" switch
- Device enumeration and Bluetooth-first AUTO resolution (`AudioDeviceManager`)
- **Connection to a real TS3 server works** — client appears in a channel
- Foreground service (microphone type) lifecycle

NOT WORKING / NOT DONE:
1. **Post-connect error**: notification shows "insufficient client permission"
   even though the client lands in a channel. Root cause: in `Ts3Connection.connect()`,
   the post-connect calls (`subscribeAll()`, then `getClientInfo()` per client)
   throw on a missing server permission and the exception aborts the
   `onConnected` flow. The session is actually alive. FIX: make these calls
   non-fatal (catch, log, continue to `listener.onConnected()`); optionally the
   owner grants `i_channel_subscribe_power` / client-list perms server-side.
2. **No voice yet**: `net/VoiceBridge.java` is the single seam between Android
   audio and ts3j's voice path. `pushOpus()` (mic -> server) is a logged no-op;
   `onServerVoice()` (server -> speaker) is never called by anything. Wiring
   these to the real ts3j entry points is THE main remaining task.
3. **Identity is regenerated every connect** (`LocalIdentity.generateNew(10)` in
   `Ts3Connection`). Must be persisted (ts3j's LocalIdentity supports export;
   store in app-private storage) so the server recognises the client and
   security level survives. Note: fresh low-level identities can also trigger
   server-side permission/antiflood friction.
4. **Single Opus decoder** in `SpeakerPlayback` — fine for one talker; multiple
   simultaneous talkers need decoder + jitter buffer per client and a mixer.
5. No channel tree / talker indicators in the UI; connect screen only.
6. `MicCapture` rebuild in `VoiceService.onConnected()` is slightly awkward
   (a placeholder sink is created pre-connect, then a second MicCapture with the
   real VoiceBridge sink post-connect). Acceptable for now; clean up when wiring voice.

## Prioritized next steps

1. Make post-connect `subscribeAll()`/`getClientInfo()` non-fatal in `Ts3Connection`.
2. Persist `LocalIdentity` across sessions (new prefs entry; base64 of exported identity).
3. Wire OUTBOUND voice (mic -> server) in `VoiceBridge`. ts3j was built for music
   bots, so sending audio is its well-trodden path. Study how
   `Manevolent/manebot-ts3` (the bot plugin) and ts3j's own `Microphone` /
   audio abstractions feed PCM/Opus to `LocalTeamspeakClientSocket`.
   Our `MicCapture` already produces 48 kHz mono 20 ms Opus frames (and could
   hand over raw PCM instead if ts3j wants to own the encode).
4. Wire INBOUND voice (server -> `VoiceBridge.onServerVoice()` -> `SpeakerPlayback`).
   Less documented in ts3j; may require a packet/voice listener on the socket.
5. Per-client decoders + mixing; talker indicators; channel tree UI.

## Architecture map

```
app/src/main/java/com/ts3client/app/
  ui/MainActivity.java       connect form, runtime permissions, starts/stops service
  ui/SettingsActivity.java   device spinners + prefer-BT switch (writes AppPrefs)
  prefs/AppPrefs.java        SharedPreferences: host/port/nick/password,
                             inputDeviceId/outputDeviceId (0 = AUTO), preferBluetooth
  audio/AudioDeviceManager.java  enumerates endpoints; resolve(storedId, isInput,
                             preferBt) -> AudioDeviceInfo or null; AUTO prefers BT
  audio/MicCapture.java      AudioRecord (VOICE_COMMUNICATION, 48k mono 16-bit),
                             setPreferredDevice(), SCO/LE bring-up via
                             setCommunicationDevice(), Opus-encodes 960-sample
                             frames -> FrameSink callback
  audio/SpeakerPlayback.java AudioTrack (USAGE_VOICE_COMMUNICATION), bounded
                             queue, Opus decode, setPreferredDevice()
  net/Ts3Connection.java     wraps ts3j LocalTeamspeakClientSocket; connect/
                             disconnect threads; Listener callbacks to service
  net/VoiceBridge.java       THE SEAM. MicCapture.FrameSink impl; pushOpus() TODO,
                             onServerVoice() ready for inbound frames
  net/VoiceService.java      foreground service (type=microphone, mandatory on
                             API 34); owns connection + audio engines; notification
```

Key routing principle: a stored device id of 0 means AUTO; `resolve()` returns
the first Bluetooth endpoint if `preferBluetooth` is on, else null (= platform
default). Engines bind via `setPreferredDevice()`. For HFP/SCO and BLE headsets,
`MicCapture` additionally sets `MODE_IN_COMMUNICATION` + `setCommunicationDevice()`
and clears both on stop.

## Build

- Open in Android Studio; bundled Gradle handles everything. CLI users must run
  `gradle wrapper` once first (wrapper script/jar intentionally not committed yet).
- `compileSdk 35`, AGP 8.5.2, Gradle 8.9 (pinned in gradle-wrapper.properties).
- Repos: google(), mavenCentral(), JitPack (settings.gradle).

## Hard-won dependency gotchas — do NOT regress these

1. **ts3j coordinate is `com.github.manevolent:ts3j:1.0.2`** — lowercase group
   (JitPack lowercases GitHub usernames) and 1.0.2 is the version that exists.
   `com.github.Manevolent:ts3j:1.0.1` does NOT resolve.
2. **Concentus (pure-Java Opus) classes live in `io.github.jaredmdobson.concentus`**,
   NOT upstream's `org.concentus`. Verified by listing the jar. Artifact:
   `io.github.jaredmdobson:concentus:1.0.2` (Maven Central).
3. **Do NOT add an explicit BouncyCastle dependency.** ts3j transitively brings
   `bcprov-jdk15on:1.60`; adding `bcprov-jdk18on` puts the same classes on the
   classpath twice -> thousands of "Duplicate class" errors. If newer BC is ever
   needed (e.g. crypto failures at runtime), exclude ts3j's old one:
   `implementation('com.github.manevolent:ts3j:1.0.2') { exclude group: 'org.bouncycastle', module: 'bcprov-jdk15on' }`
   then add bcprov-jdk18on. Until a concrete need appears, keep it simple.
4. **Do NOT use opus4j** (`de.maxhenkel.opus4j`): JNI wrapper whose prebuilt
   natives are desktop-only — compiles fine, crashes on-device with
   UnsatisfiedLinkError. Concentus is pure Java and correct for Android.
5. `packaging { resources { excludes ... } }` in app/build.gradle filters
   META-INF and `org/bouncycastle/**/*.properties` duplicates. Keep it.

## Conventions

- Plain Java (no Kotlin) throughout; match existing style.
- All user-facing strings in `res/values/strings.xml`.
- Audio constants: 48 kHz, mono, 20 ms (960 samples) — TS3/Opus canonical. Do not
  change without changing both engines.
- Keep ALL ts3j-API uncertainty confined to `VoiceBridge` so the rest of the app
  stays stable; that isolation is deliberate.
- The README's "Known gaps" section is the user-facing status; keep it in sync
  with this file when closing gaps.

## Testing notes

- Real device needed for Bluetooth routing tests (emulator BT is useless here).
- Owner's own TS3 server is the test target; he can read server logs and change
  permissions. The server log names the exact missing permission on errors.
- Logcat tags: MicCapture, SpeakerPlayback, Ts3Connection, VoiceBridge, VoiceService.
