package com.ts3client.app.audio;

import android.annotation.SuppressLint;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import io.github.jaredmdobson.concentus.OpusApplication;
import io.github.jaredmdobson.concentus.OpusEncoder;

/**
 * Captures microphone PCM and emits Opus frames. TeamSpeak 3 voice uses
 * 48 kHz; we capture mono 20 ms frames (960 samples) which is the canonical
 * Opus/TS frame size.
 *
 * The crucial bit for the Bluetooth use case: we call setPreferredDevice() on
 * the AudioRecord with the resolved endpoint, so capture binds to the chosen
 * (or auto-Bluetooth) mic instead of the built-in one.
 */
public class MicCapture {

    public interface FrameSink {
        /** Called from the capture thread with one Opus-encoded frame. */
        void onOpusFrame(byte[] data, int length);
    }

    private static final String TAG = "MicCapture";
    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SAMPLES = 960;          // 20 ms @ 48 kHz
    private static final int MAX_PACKET = 4000;

    private final AudioManager audioManager;
    private final AudioDeviceInfo preferredDevice; // may be null -> default
    private final FrameSink sink;

    private volatile boolean running;
    private Thread thread;
    private AudioRecord record;

    public MicCapture(AudioManager am, AudioDeviceInfo preferredDevice, FrameSink sink) {
        this.audioManager = am;
        this.preferredDevice = preferredDevice;
        this.sink = sink;
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO checked by caller before start()
    public void start() {
        // For classic Bluetooth headsets (HFP/SCO) the link must be brought up
        // explicitly. On Android 12+ setCommunicationDevice() is the modern API.
        maybeActivateBluetoothSco();

        int minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, FRAME_SAMPLES * 2 * 4);

        record = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // enables AEC/NS where available
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufSize);

        if (preferredDevice != null) {
            boolean ok = record.setPreferredDevice(preferredDevice);
            Log.i(TAG, "setPreferredDevice(input=" + preferredDevice.getId() + ") -> " + ok);
        }

        running = true;
        thread = new Thread(this::loop, "ts3-mic-capture");
        thread.start();
    }

    private void loop() {
        OpusEncoder encoder;
        try {
            encoder = new OpusEncoder(SAMPLE_RATE, 1, OpusApplication.OPUS_APPLICATION_VOIP);
            encoder.setBitrate(32000);
        } catch (Exception e) {
            Log.e(TAG, "Opus encoder init failed", e);
            return;
        }

        short[] pcm = new short[FRAME_SAMPLES];
        byte[] encoded = new byte[MAX_PACKET];

        record.startRecording();
        while (running) {
            int read = 0;
            while (read < FRAME_SAMPLES && running) {
                int r = record.read(pcm, read, FRAME_SAMPLES - read);
                if (r < 0) { Log.e(TAG, "AudioRecord.read error " + r); running = false; break; }
                read += r;
            }
            if (!running || read < FRAME_SAMPLES) break;
            try {
                int len = encoder.encode(pcm, 0, FRAME_SAMPLES, encoded, 0, MAX_PACKET);
                if (len > 0) sink.onOpusFrame(encoded, len);
            } catch (Exception e) {
                Log.w(TAG, "encode error", e);
            }
        }
        try { record.stop(); } catch (Exception ignored) {}
        record.release();
        record = null;
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
        releaseBluetoothSco();
    }

    private void maybeActivateBluetoothSco() {
        if (preferredDevice == null) return;
        int t = preferredDevice.getType();
        boolean btScoLike = t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || t == AudioDeviceInfo.TYPE_BLE_HEADSET;
        if (!btScoLike) return;
        try {
            // Android 12+ unified routing: route the whole comm session to the device.
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            boolean ok = audioManager.setCommunicationDevice(preferredDevice);
            Log.i(TAG, "setCommunicationDevice(BT) -> " + ok);
        } catch (Exception e) {
            Log.w(TAG, "BT comm-device routing failed", e);
        }
    }

    private void releaseBluetoothSco() {
        try {
            audioManager.clearCommunicationDevice();
            audioManager.setMode(AudioManager.MODE_NORMAL);
        } catch (Exception ignored) {}
    }
}
