package com.ts3client.app.audio;

import android.content.Context;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Enumerates physical audio endpoints and implements the routing policy:
 * when the user leaves a device on AUTO, we pick the connected Bluetooth
 * endpoint if one exists, otherwise fall back to the platform default.
 *
 * This is the piece that fixes the "Bluetooth mic doesn't work" problem in the
 * stock client: we explicitly resolve and bind the SCO/LE input device rather
 * than letting the framework silently fall back to the built-in mic.
 */
public class AudioDeviceManager {

    public static class Endpoint {
        public final int id;          // AudioDeviceInfo.getId(), 0 == AUTO sentinel
        public final String label;
        public final int type;        // AudioDeviceInfo.TYPE_*
        public final boolean bluetooth;

        Endpoint(int id, String label, int type, boolean bluetooth) {
            this.id = id; this.label = label; this.type = type; this.bluetooth = bluetooth;
        }
        @Override public String toString() { return label; }
    }

    private final AudioManager am;

    public AudioDeviceManager(Context ctx) {
        am = (AudioManager) ctx.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    public List<Endpoint> listInputs() { return list(AudioManager.GET_DEVICES_INPUTS); }
    public List<Endpoint> listOutputs() { return list(AudioManager.GET_DEVICES_OUTPUTS); }

    private List<Endpoint> list(int flag) {
        List<Endpoint> out = new ArrayList<>();
        // First entry is always the AUTO/default option.
        out.add(new Endpoint(0, "Automatic (prefer Bluetooth)", -1, false));
        for (AudioDeviceInfo d : am.getDevices(flag)) {
            out.add(new Endpoint(d.getId(), describe(d), d.getType(), isBluetooth(d.getType())));
        }
        return out;
    }

    /**
     * Resolve a stored device id into a concrete AudioDeviceInfo for binding.
     * If id == 0 (AUTO) and preferBt, returns the first Bluetooth endpoint,
     * else the system default (null lets the framework choose).
     */
    public AudioDeviceInfo resolve(int storedId, boolean input, boolean preferBt) {
        AudioDeviceInfo[] devices =
                am.getDevices(input ? AudioManager.GET_DEVICES_INPUTS
                                    : AudioManager.GET_DEVICES_OUTPUTS);

        if (storedId != 0) {
            for (AudioDeviceInfo d : devices) {
                if (d.getId() == storedId) return d;
            }
            // Stored device vanished (e.g. headset disconnected) -> fall through to AUTO.
        }

        if (preferBt) {
            for (AudioDeviceInfo d : devices) {
                if (isBluetooth(d.getType())) return d;
            }
        }
        return null; // null == let AudioRecord/AudioTrack use platform default
    }

    public boolean hasBluetoothInput() {
        for (AudioDeviceInfo d : am.getDevices(AudioManager.GET_DEVICES_INPUTS)) {
            if (isBluetooth(d.getType())) return true;
        }
        return false;
    }

    private static boolean isBluetooth(int type) {
        return type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                || type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                || type == AudioDeviceInfo.TYPE_BLE_HEADSET
                || type == AudioDeviceInfo.TYPE_BLE_SPEAKER;
    }

    private static String describe(AudioDeviceInfo d) {
        String name = d.getProductName() != null ? d.getProductName().toString() : "Device";
        return name + " — " + typeName(d.getType());
    }

    private static String typeName(int type) {
        switch (type) {
            case AudioDeviceInfo.TYPE_BUILTIN_MIC: return "Built-in mic";
            case AudioDeviceInfo.TYPE_BUILTIN_SPEAKER: return "Speaker";
            case AudioDeviceInfo.TYPE_BUILTIN_EARPIECE: return "Earpiece";
            case AudioDeviceInfo.TYPE_WIRED_HEADSET: return "Wired headset";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES: return "Wired headphones";
            case AudioDeviceInfo.TYPE_USB_HEADSET: return "USB headset";
            case AudioDeviceInfo.TYPE_USB_DEVICE: return "USB audio";
            case AudioDeviceInfo.TYPE_BLUETOOTH_SCO: return "Bluetooth (SCO)";
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP: return "Bluetooth (A2DP)";
            case AudioDeviceInfo.TYPE_BLE_HEADSET: return "Bluetooth LE headset";
            case AudioDeviceInfo.TYPE_BLE_SPEAKER: return "Bluetooth LE speaker";
            default: return "Type " + type;
        }
    }
}
