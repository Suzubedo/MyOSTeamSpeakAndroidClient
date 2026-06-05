package com.ts3client.app.net;

import android.util.Log;

import com.github.manevolent.ts3j.api.Client;
import com.github.manevolent.ts3j.identity.LocalIdentity;
import com.github.manevolent.ts3j.protocol.socket.client.LocalTeamspeakClientSocket;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/**
 * Owns the ts3j client socket and the connection lifecycle. Audio I/O is wired
 * in by VoiceService, which holds the MicCapture/SpeakerPlayback engines.
 *
 * ts3j surface used here (from its README / public API):
 *   new LocalTeamspeakClientSocket()
 *   setIdentity(LocalIdentity), setNickname(String), addListener(...)
 *   connect(InetSocketAddress, password, timeoutMillis)
 *   subscribeAll(), listClients(), getClientInfo(...), disconnect()
 *
 * NOTE: the voice send/receive path (feeding Opus frames in, and receiving
 * decoded/raw voice out) is the one area of ts3j that is sparsely documented.
 * The hooks are isolated in VoiceBridge so the rest of the app is stable while
 * that single seam is confirmed against the library source. See README.
 */
public class Ts3Connection {

    public interface Listener {
        void onConnected();
        void onDisconnected(String reason);
        void onError(String message);
    }

    private static final String TAG = "Ts3Connection";

    private final LocalTeamspeakClientSocket client = new LocalTeamspeakClientSocket();
    private final Listener listener;
    private volatile boolean connected;

    public Ts3Connection(Listener listener) {
        this.listener = listener;
    }

    public void connect(String host, int port, String nickname, String password) {
        new Thread(() -> {
            try {
                // A fresh identity is generated per install in a real app this
                // should be persisted so the server recognises the same client.
                LocalIdentity identity = LocalIdentity.generateNew(10);
                client.setIdentity(identity);
                client.setNickname(nickname);

                client.connect(
                        new InetSocketAddress(InetAddress.getByName(host), port),
                        password == null || password.isEmpty() ? null : password,
                        10000L);

                client.subscribeAll();
                for (Client c : client.listClients()) {
                    try { client.getClientInfo(c.getId()); } catch (Exception ignored) {}
                }

                connected = true;
                listener.onConnected();
            } catch (Exception e) {
                Log.e(TAG, "connect failed", e);
                listener.onError(e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }, "ts3-connect").start();
    }

    public LocalTeamspeakClientSocket raw() { return client; }

    public boolean isConnected() { return connected; }

    public void disconnect() {
        connected = false;
        new Thread(() -> {
            try {
                client.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "disconnect error", e);
            } finally {
                listener.onDisconnected("client requested");
            }
        }, "ts3-disconnect").start();
    }
}
