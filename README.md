# TS3 Client (Android 14+)

A minimal, open-source TeamSpeak 3–compatible voice client for Android, built
around the reverse-engineered [`ts3j`](https://github.com/Manevolent/ts3j)
full-client protocol library. Its reason to exist: **let you pick the input and
output audio device, and default to your connected Bluetooth device** — the
thing the official Android client doesn't reliably do.

> Status: **working scaffold**. Everything compiles and runs (UI, settings,
> permissions, device routing, connection lifecycle, Opus encode/decode, the
> foreground service). The one seam that needs finishing against the live ts3j
> API is the voice frame send/receive path — see *Known gaps* below. This is
> deliberately isolated in a single class so the rest is stable.

## Architecture

```
ui/                      Activities (connect screen + settings)
  MainActivity           server form, runtime permissions, start/stop service
  SettingsActivity       input/output device pickers, "prefer Bluetooth" switch
prefs/
  AppPrefs               SharedPreferences: server, nick, chosen device ids
audio/
  AudioDeviceManager     enumerates endpoints; resolves AUTO -> Bluetooth-first
  MicCapture             AudioRecord -> Opus frames; binds preferred input device
  SpeakerPlayback        Opus frames -> AudioTrack; binds preferred output device
net/
  Ts3Connection          wraps ts3j LocalTeamspeakClientSocket (connect/lifecycle)
  VoiceBridge            THE SEAM: ts3j voice path <-> MicCapture/SpeakerPlayback
  VoiceService           foreground (microphone) service hosting it all
```

### Why this fixes the Bluetooth problem
On Android, the failure in many clients is silent fallback to the built-in mic.
Here, `AudioDeviceManager.resolve()` returns the connected Bluetooth endpoint
for any device left on *Automatic*, and `MicCapture`/`SpeakerPlayback` bind it
explicitly via `setPreferredDevice()`. For classic HFP/SCO headsets,
`MicCapture` also brings up the link with `AudioManager.setCommunicationDevice()`
(the Android 12+ unified-routing API) and sets `MODE_IN_COMMUNICATION`.

## Build

1. Open in Android Studio (Giraffe or newer), or build from CLI.
2. The ts3j dependency resolves from JitPack (configured in `settings.gradle`).
3. `minSdk 34` (Android 14), `targetSdk 35`, Java 17.

```
./gradlew assembleDebug
```

Dependencies of note:
- `com.github.manevolent:ts3j:1.0.2` — TS3 full-client protocol (JitPack)
- `io.github.jaredmdobson:concentus:1.0.2` — pure-Java Opus (classes live in the
  `io.github.jaredmdobson.concentus` package, not upstream's `org.concentus`)
- `org.bouncycastle:bcprov-jdk18on` — protocol crypto (Android's bundled BC is stripped)

## Known gaps (be honest with yourself before relying on this)

1. **Voice frame bridge (`VoiceBridge`)** — ts3j was built mainly for *music
   bots*, so pushing mic audio *up* is its well-trodden path while pulling other
   clients' voice *down* is less documented. `VoiceBridge.pushOpus()` and
   `onServerVoice()` are the two methods to wire to the actual ts3j entry points
   (in `ts3j-musicbot` audio flows through a `Mixer` the client pulls from).
   Until wired, the app connects, routes audio devices, and encodes/decodes —
   but frames aren't yet handed to/from the socket.

2. **Identity persistence** — `Ts3Connection` generates a fresh identity per
   connect. Persist the `LocalIdentity` so the server recognises you across
   sessions.

3. **Multiple talkers** — `SpeakerPlayback` uses one decoder. Real use needs a
   decoder + jitter buffer per client and a software mixer.

4. **Channel list / UI** — no channel tree or talker indicators yet; this is a
   connect-and-talk scaffold.

5. **ts3j is dormant** and tracks TS3; expect possible protocol drift with newer
   TS6 servers. It does interoperate with TS3 servers that TS5/6 clients use.

## Legal
ts3j is a reverse-engineered implementation of a proprietary protocol. Fine for
a personal/open-source project; do not ship it as an official TeamSpeak client.
