package com.ts3client.app.prefs;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Thin wrapper over SharedPreferences holding everything the client needs to
 * reconnect and to route audio. Device ids of 0 mean "auto / follow default
 * routing" which, with our routing policy, resolves to the connected
 * Bluetooth device when one is present.
 */
public class AppPrefs {

    public static final int DEVICE_AUTO = 0;

    private static final String FILE = "ts3client";
    private static final String K_HOST = "host";
    private static final String K_PORT = "port";
    private static final String K_NICK = "nickname";
    private static final String K_PASSWORD = "password";
    private static final String K_INPUT_DEVICE = "input_device_id";
    private static final String K_OUTPUT_DEVICE = "output_device_id";
    private static final String K_PREFER_BLUETOOTH = "prefer_bluetooth";

    private final SharedPreferences sp;

    public AppPrefs(Context ctx) {
        sp = ctx.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public String getHost() { return sp.getString(K_HOST, ""); }
    public void setHost(String v) { sp.edit().putString(K_HOST, v).apply(); }

    public int getPort() { return sp.getInt(K_PORT, 9987); }
    public void setPort(int v) { sp.edit().putInt(K_PORT, v).apply(); }

    public String getNickname() { return sp.getString(K_NICK, "AndroidUser"); }
    public void setNickname(String v) { sp.edit().putString(K_NICK, v).apply(); }

    public String getPassword() { return sp.getString(K_PASSWORD, ""); }
    public void setPassword(String v) { sp.edit().putString(K_PASSWORD, v).apply(); }

    /** AudioDeviceInfo id selected for capture, or DEVICE_AUTO. */
    public int getInputDeviceId() { return sp.getInt(K_INPUT_DEVICE, DEVICE_AUTO); }
    public void setInputDeviceId(int v) { sp.edit().putInt(K_INPUT_DEVICE, v).apply(); }

    /** AudioDeviceInfo id selected for playback, or DEVICE_AUTO. */
    public int getOutputDeviceId() { return sp.getInt(K_OUTPUT_DEVICE, DEVICE_AUTO); }
    public void setOutputDeviceId(int v) { sp.edit().putInt(K_OUTPUT_DEVICE, v).apply(); }

    /** When true (default) and a device is on AUTO, prefer Bluetooth routing. */
    public boolean preferBluetooth() { return sp.getBoolean(K_PREFER_BLUETOOTH, true); }
    public void setPreferBluetooth(boolean v) { sp.edit().putBoolean(K_PREFER_BLUETOOTH, v).apply(); }
}
