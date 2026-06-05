package com.ts3client.app.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.ts3client.app.R;
import com.ts3client.app.net.VoiceService;
import com.ts3client.app.prefs.AppPrefs;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private EditText hostInput, portInput, nickInput, passwordInput;
    private Button connectBtn;
    private TextView statusText;
    private boolean connected = false;

    private final ActivityResultLauncher<String[]> permLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean micOk = Boolean.TRUE.equals(
                                result.getOrDefault(Manifest.permission.RECORD_AUDIO, false));
                        if (micOk) startVoiceService();
                        else Toast.makeText(this,
                                "Microphone permission is required", Toast.LENGTH_LONG).show();
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = new AppPrefs(this);

        hostInput = findViewById(R.id.input_host);
        portInput = findViewById(R.id.input_port);
        nickInput = findViewById(R.id.input_nick);
        passwordInput = findViewById(R.id.input_password);
        connectBtn = findViewById(R.id.btn_connect);
        statusText = findViewById(R.id.text_status);

        hostInput.setText(prefs.getHost());
        portInput.setText(String.valueOf(prefs.getPort()));
        nickInput.setText(prefs.getNickname());
        passwordInput.setText(prefs.getPassword());

        connectBtn.setOnClickListener(v -> {
            if (connected) {
                disconnect();
            } else {
                if (!saveInputs()) return;
                requestPermissionsThenConnect();
            }
        });

        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private boolean saveInputs() {
        String host = hostInput.getText().toString().trim();
        if (TextUtils.isEmpty(host)) {
            hostInput.setError("Server address required");
            return false;
        }
        int port;
        try {
            port = Integer.parseInt(portInput.getText().toString().trim());
        } catch (NumberFormatException e) {
            portInput.setError("Invalid port");
            return false;
        }
        prefs.setHost(host);
        prefs.setPort(port);
        prefs.setNickname(nickInput.getText().toString().trim());
        prefs.setPassword(passwordInput.getText().toString());
        return true;
    }

    private void requestPermissionsThenConnect() {
        List<String> needed = new ArrayList<>();
        addIfMissing(needed, Manifest.permission.RECORD_AUDIO);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            addIfMissing(needed, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfMissing(needed, Manifest.permission.POST_NOTIFICATIONS);
        }
        if (needed.isEmpty()) startVoiceService();
        else permLauncher.launch(needed.toArray(new String[0]));
    }

    private void addIfMissing(List<String> list, String perm) {
        if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
            list.add(perm);
        }
    }

    private void startVoiceService() {
        Intent i = new Intent(this, VoiceService.class).setAction(VoiceService.ACTION_CONNECT);
        ContextCompat.startForegroundService(this, i);
        connected = true;
        statusText.setText(getString(R.string.status_connecting));
        connectBtn.setText(R.string.action_disconnect);
    }

    private void disconnect() {
        Intent i = new Intent(this, VoiceService.class).setAction(VoiceService.ACTION_DISCONNECT);
        startService(i);
        connected = false;
        statusText.setText(getString(R.string.status_disconnected));
        connectBtn.setText(R.string.action_connect);
    }
}
