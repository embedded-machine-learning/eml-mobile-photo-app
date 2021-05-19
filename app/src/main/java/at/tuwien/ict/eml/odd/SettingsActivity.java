/* Copyright 2021 CDL EML, TU Wien, Austria

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package at.tuwien.ict.eml.odd;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.SeekBar;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.NavUtils;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SeekBarPreference;

public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        // create new settings fragment
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        Toolbar actionBar = findViewById(R.id.toolbar);
        setSupportActionBar(actionBar);
        actionBar.setNavigationIcon(R.drawable.ic_baseline_arrow_back_24);
        actionBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
                //NavUtils.navigateUpFromSameTask(SettingsActivity.this);
            }
        });
    }

    /**
     * Set the settings from preferences resource and set listeners for snapping the values of boxplot samples and config threshold
     */
    public static class SettingsFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            if(findPreference("boxplot_number_samples")!=null) {
                ((SeekBarPreference) (findPreference("boxplot_number_samples"))).setUpdatesContinuously(false);
                findPreference("boxplot_number_samples").setOnPreferenceChangeListener(
                        (preference, newValue) -> {
                            ((SeekBarPreference) (preference)).setValue((((int) newValue / 10) * 10));
                            return false;
                        }
                );
            }

            if(findPreference("confidence_threshold")!=null) {
                ((SeekBarPreference) (findPreference("confidence_threshold"))).setUpdatesContinuously(false);
                findPreference("confidence_threshold").setOnPreferenceChangeListener(
                        (preference, newValue) -> {
                            ((SeekBarPreference) (preference)).setValue((((int) newValue / 5) * 5));
                            return false;
                        }
                );
            }
        }

    }

}