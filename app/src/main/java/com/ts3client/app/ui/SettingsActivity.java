package com.ts3client.app.ui;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.ts3client.app.R;
import com.ts3client.app.audio.AudioDeviceManager;
import com.ts3client.app.prefs.AppPrefs;

import java.util.List;

/**
 * Lets the user pick the capture and playback endpoints, and toggle the
 * "prefer Bluetooth when set to Automatic" default. Selecting a concrete
 * device pins it; leaving it on "Automatic" defers to the routing policy in
 * AudioDeviceManager (Bluetooth first when present).
 */
public class SettingsActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private AudioDeviceManager devices;

    private List<AudioDeviceManager.Endpoint> inputs;
    private List<AudioDeviceManager.Endpoint> outputs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        prefs = new AppPrefs(this);
        devices = new AudioDeviceManager(this);

        Spinner inputSpinner = findViewById(R.id.spinner_input);
        Spinner outputSpinner = findViewById(R.id.spinner_output);
        SwitchCompat btSwitch = findViewById(R.id.switch_prefer_bt);

        inputs = devices.listInputs();
        outputs = devices.listOutputs();

        inputSpinner.setAdapter(adapter(inputs));
        outputSpinner.setAdapter(adapter(outputs));

        inputSpinner.setSelection(indexOf(inputs, prefs.getInputDeviceId()));
        outputSpinner.setSelection(indexOf(outputs, prefs.getOutputDeviceId()));

        inputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                prefs.setInputDeviceId(inputs.get(pos).id);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
        outputSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, android.view.View v, int pos, long id) {
                prefs.setOutputDeviceId(outputs.get(pos).id);
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        btSwitch.setChecked(prefs.preferBluetooth());
        btSwitch.setOnCheckedChangeListener((CompoundButton b, boolean checked) ->
                prefs.setPreferBluetooth(checked));
    }

    private ArrayAdapter<AudioDeviceManager.Endpoint> adapter(List<AudioDeviceManager.Endpoint> data) {
        ArrayAdapter<AudioDeviceManager.Endpoint> a =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
        a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return a;
    }

    private int indexOf(List<AudioDeviceManager.Endpoint> data, int storedId) {
        for (int i = 0; i < data.size(); i++) if (data.get(i).id == storedId) return i;
        return 0; // Automatic
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}
