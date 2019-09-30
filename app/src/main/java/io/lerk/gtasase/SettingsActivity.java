package io.lerk.gtasase;

import android.preference.PreferenceManager;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Switch;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        boolean infoToastsEnabled = PreferenceManager
                .getDefaultSharedPreferences(getApplicationContext())
                .getBoolean("infoToasts", false);

        Switch infoToastSwitch = findViewById(R.id.infoToastSwitch);
        infoToastSwitch.setChecked(infoToastsEnabled);
        infoToastSwitch.setOnCheckedChangeListener((buttonView, isChecked) ->
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit()
                        .putBoolean("infoToasts", isChecked).apply());

    }
}
