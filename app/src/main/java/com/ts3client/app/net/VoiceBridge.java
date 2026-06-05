package com.ts3client.app.net;

import android.util.Log;

import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket;
import com.ts3client.app.audio.MicCapture;
import com.ts3client.app.audio.SpeakerPlayback;

/**
 * The single integration seam between Android audio and ts3j's voice channel.
 *
 * ts3j was written primarily for *music bots*, which means its best-trodden
 * path is "push audio TO the server". Pushing mic audio up is therefore the
 * well-supported direction; pulling other clients' voice down for playback is
 * the part most likely to need adaptation against the library source.
 *
 * Two things must be confirmed against the ts3j version you build against,
 * because they are not fully specified in the public README:
 *
 *  1. OUTBOUND (mic -> server): how a Microphone/audio provider is registered.
 *     In ts3j-musicbot this goes through a Mixer abstraction that the client
 *     pulls PCM from. Depending on the build this is either:
 *        client.setMicrophone(<provider>)        // older API
 *     or registering a provider on the client's audio component. Our
 *     MicCapture already produces 48 kHz mono Opus frames / PCM; adapt
 *     pushPcm()/pushOpus() below to whichever entry point exists.
 *
 *  2. INBOUND (server -> speaker): how received voice is surfaced. If the
 *     library exposes decoded PCM via a listener, route it straight to
 *     SpeakerPlayback. If it exposes raw Opus, SpeakerPlayback already decodes.
 *
 * Keeping this in one class means the rest of the app compiles and runs (UI,
 * settings, device routing, connection) while this seam is finalised.
 */
public class VoiceBridge implements MicCapture.FrameSink {

    private static final String TAG = "VoiceBridge";

    private final LocalTeamspeakClientSocket client;
    private final SpeakerPlayback playback;

    public VoiceBridge(LocalTeamspeakClientSocket client, SpeakerPlayback playback) {
        this.client = client;
        this.playback = playback;
    }

    /** Called by MicCapture for each encoded 20 ms frame. */
    @Override
    public void onOpusFrame(byte[] data, int length) {
        byte[] frame = new byte[length];
        System.arraycopy(data, 0, frame, 0, length);
        pushOpus(frame);
    }

    /**
     * OUTBOUND seam. Wire to the ts3j voice-send entry point.
     * Left as a logged no-op so the app is runnable before this is bound.
     */
    private void pushOpus(byte[] opusFrame) {
        // TODO(ts3j): forward `opusFrame` to the client's voice channel.
        // e.g. client.getMicrophone()... or client.sendAudio(...)
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "outbound opus frame " + opusFrame.length + "B (not yet bound to ts3j)");
        }
    }

    /**
     * INBOUND seam. Call this from the ts3j voice-receive callback once wired,
     * passing the raw Opus payload of another client's voice packet.
     */
    public void onServerVoice(byte[] opusFrame) {
        playback.enqueue(opusFrame);
    }
}
