package com.ts3client.app.audio;

import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.util.Log;

import io.github.jaredmdobson.concentus.OpusDecoder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Decodes incoming Opus voice frames and plays them through AudioTrack, bound
 * to the chosen (or auto-Bluetooth) output endpoint via setPreferredDevice().
 *
 * One decoder per playback engine is a simplification: with multiple
 * simultaneous talkers you'd want a decoder + jitter buffer per client and a
 * software mixer. That is called out in the README as a known limitation.
 */
public class SpeakerPlayback {

    private static final String TAG = "SpeakerPlayback";
    private static final int SAMPLE_RATE = 48000;
    private static final int FRAME_SAMPLES = 960; // 20 ms

    private final AudioDeviceInfo preferredDevice; // may be null -> default
    private final BlockingQueue<byte[]> queue = new ArrayBlockingQueue<>(64);

    private volatile boolean running;
    private Thread thread;
    private AudioTrack track;

    public SpeakerPlayback(AudioDeviceInfo preferredDevice) {
        this.preferredDevice = preferredDevice;
    }

    public void start() {
        int minBuf = AudioTrack.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufSize = Math.max(minBuf, FRAME_SAMPLES * 2 * 8);

        track = new AudioTrack.Builder()
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build())
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        if (preferredDevice != null) {
            boolean ok = track.setPreferredDevice(preferredDevice);
            Log.i(TAG, "setPreferredDevice(output=" + preferredDevice.getId() + ") -> " + ok);
        }

        running = true;
        thread = new Thread(this::loop, "ts3-speaker-playback");
        thread.start();
    }

    /** Enqueue a raw Opus frame received from the network. */
    public void enqueue(byte[] opusFrame) {
        if (!running) return;
        // Drop on overflow to bound latency rather than backing up the network thread.
        queue.offer(opusFrame);
    }

    private void loop() {
        OpusDecoder decoder;
        try {
            decoder = new OpusDecoder(SAMPLE_RATE, 1);
        } catch (Exception e) {
            Log.e(TAG, "Opus decoder init failed", e);
            return;
        }
        track.play();
        short[] pcm = new short[FRAME_SAMPLES * 6]; // headroom for FEC/larger frames
        while (running) {
            try {
                byte[] frame = queue.take();
                int samples = decoder.decode(frame, 0, frame.length, pcm, 0, FRAME_SAMPLES, false);
                if (samples > 0) track.write(pcm, 0, samples);
            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                Log.w(TAG, "decode error", e);
            }
        }
        try { track.stop(); } catch (Exception ignored) {}
        track.release();
        track = null;
    }

    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
            try { thread.join(500); } catch (InterruptedException ignored) {}
            thread = null;
        }
    }
}
