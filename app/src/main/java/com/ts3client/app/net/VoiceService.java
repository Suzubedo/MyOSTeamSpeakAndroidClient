package com.ts3client.app.net;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.ts3client.app.R;
import com.ts3client.app.audio.AudioDeviceManager;
import com.ts3client.app.audio.MicCapture;
import com.ts3client.app.audio.SpeakerPlayback;
import com.ts3client.app.prefs.AppPrefs;

/**
 * Holds the live connection + audio engines so voice keeps running when the
 * Activity is backgrounded. On Android 14 a microphone foreground service must
 * declare the type both in the manifest and at startForeground().
 */
public class VoiceService extends Service implements Ts3Connection.Listener {

    public static final String ACTION_CONNECT = "com.ts3client.app.CONNECT";
    public static final String ACTION_DISCONNECT = "com.ts3client.app.DISCONNECT";

    private static final String TAG = "VoiceService";
    private static final String CHANNEL_ID = "ts3_voice";
    private static final int NOTIF_ID = 42;

    private Ts3Connection connection;
    private MicCapture mic;
    private SpeakerPlayback speaker;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_DISCONNECT.equals(action)) {
            teardown();
            stopSelf();
            return START_NOT_STICKY;
        }
        startForegroundCompat();
        startSession();
        return START_STICKY;
    }

    private void startSession() {
        AppPrefs prefs = new AppPrefs(this);
        AudioDeviceManager devices = new AudioDeviceManager(this);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        boolean preferBt = prefs.preferBluetooth();
        speaker = new SpeakerPlayback(
                devices.resolve(prefs.getOutputDeviceId(), false, preferBt));
        speaker.start();

        connection = new Ts3Connection(this);

        // Mic + voice bridge are started after the connection is up (onConnected).
        mic = new MicCapture(
                am,
                devices.resolve(prefs.getInputDeviceId(), true, preferBt),
                // FrameSink is replaced by VoiceBridge once connected; until then
                // frames are discarded. Kept simple for the scaffold.
                (data, length) -> { /* bound in onConnected */ });

        connection.connect(prefs.getHost(), prefs.getPort(),
                prefs.getNickname(), prefs.getPassword());
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "connected; starting audio");
        VoiceBridge bridge = new VoiceBridge(connection.raw(), speaker);
        // Re-create mic with the bridge as the real sink now that we're connected.
        AppPrefs prefs = new AppPrefs(this);
        AudioDeviceManager devices = new AudioDeviceManager(this);
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mic = new MicCapture(am,
                devices.resolve(prefs.getInputDeviceId(), true, prefs.preferBluetooth()),
                bridge);
        mic.start();
        updateNotification("Connected to " + prefs.getHost());
    }

    @Override
    public void onDisconnected(String reason) {
        Log.i(TAG, "disconnected: " + reason);
        teardown();
        stopSelf();
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "error: " + message);
        updateNotification("Error: " + message);
    }

    private void teardown() {
        if (mic != null) { mic.stop(); mic = null; }
        if (speaker != null) { speaker.stop(); speaker = null; }
        if (connection != null && connection.isConnected()) connection.disconnect();
        connection = null;
    }

    private void startForegroundCompat() {
        createChannel();
        Notification n = buildNotification("Connecting…");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.notify(NOTIF_ID, buildNotification(text));
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_mic)
                .setOngoing(true)
                .build();
    }

    private void createChannel() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Voice connection", NotificationManager.IMPORTANCE_LOW));
        }
    }

    @Override
    public void onDestroy() {
        teardown();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) { return null; }
}
