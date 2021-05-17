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

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.firebase.FirebaseException;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.List;

import at.tuwien.ict.eml.odd.detection.FirebaseML;

public class ModelChooseActivity extends AppCompatActivity {
    // region constants
    // name of the JSON entry in firebase
    private static final String MODEL_LIST_KEY = "modelConfig";
    //endregion

    // region variables
    private FirebaseML fire;
    private String modelConfigJSON;
    private String chosenModelLabel;
    private ArrayList<String> availableModelList = new ArrayList<>();
    private TextView screenLog;
    private Spinner spinner;
    private Button startButton;
    // endregion

    /** sets the log Textview to the passed message and sets the text color to normal grey */
    private void logMessage(String message){
        if(screenLog != null){
            screenLog.setTextColor(getColor(R.color.eml_grey));
            screenLog.setText(message);
        }
    }

    /** sets the log Textview to the passed message and sets the text color to yellow */
    private void logError(FirebaseException e, String message){
        if(screenLog != null){
            screenLog.setTextColor(getColor(R.color.eml_warning));
            screenLog.setText("ERROR: "  +  e.getMessage() + ". " +message);
        }
    }

    /** overload logError(FirebaseException e, String message)*/
    private void logError(FirebaseException e){
        logError(e, "");
    }

    /** sets the user interaction with the model spinner and the start button */
    private void setAllowInteraction(boolean set, boolean buttonOnly){
        float alpha = set? 1.0f : 0.6f;
        if(!buttonOnly){
            spinner.setEnabled(set);
            spinner.setAlpha(alpha);
        }
        startButton.setClickable(set);
        startButton.setAlpha(alpha);
    }

    /** request remote config */
    private void fetchConfig(){
        // disable interactions while fetching config
        setAllowInteraction(false, false);
        logMessage("Fetching model list from server ...");
        // requesting remote model config
        fire = new FirebaseML();
        fire.requestRemoteConfig(MODEL_LIST_KEY, new FirebaseML.onCompleteCallback() {
            @Override
            public void onSuccess() {
                // read config field as json String, convert it to a JSON object and create an arrayList with the models *//*
                modelConfigJSON = fire.getRemoteConfig().asString();
                try {
                    availableModelList = FirebaseML.extractModelNameList(modelConfigJSON);

                    // setup spinner adapter
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.custom_spinner, availableModelList);
                    adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
                    spinner.setAdapter(adapter);

                    // set listener for button click to start model download
                    startButton.setOnClickListener(buttonHandlerConfigSuccess);
                    // switch back(if changed) to start label
                    startButton.setText("Start");
                    // enable button click
                    setAllowInteraction(true, false);
                    logMessage("Select your model");
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
            @Override
            public void onError(FirebaseException e) {
                // block button and spinner interactions
                startButton.setOnClickListener(buttonHandlerConfigError);
                startButton.setText("Retry");
                // display log message
                logError(e);
                // enable button interaction only
                setAllowInteraction(true, true);
            }
        });
    }

    /** start button listener for success of config fetch - extract labelmap and request download */
    View.OnClickListener buttonHandlerConfigSuccess = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            // deactivate
            setAllowInteraction(false, false);
            logMessage("Downloading model ...");
            chosenModelLabel = spinner.getSelectedItem().toString();
            try {
                fire.requestRemoteModel(FirebaseML.extractModelFile(modelConfigJSON, chosenModelLabel),
                        new FirebaseML.onCompleteCallback() {
                            @Override
                            public void onSuccess() {
                                // show status message
                                logMessage("Starting camera ...");
                                // create intent and hand over to camera activity
                                Intent cameraActivityIntent = new Intent(getApplicationContext(), CameraActivity.class);
                                cameraActivityIntent.putExtra("modelFilePath", fire.getRemoteModel().getLocalFilePath());
                                cameraActivityIntent.putExtra("modelConfigJSON", modelConfigJSON);
                                cameraActivityIntent.putExtra("chosenModelLabel", chosenModelLabel);
                                startActivity(cameraActivityIntent);
                            }

                            @Override
                            public void onError(FirebaseException e) {
                                // show error message and enable interactions
                                logError(e, "Contact admin or try another model.");
                                setAllowInteraction(true, false);
                            }
                        });

            } catch (JSONException e) {
                // should never reached because chosenModelLabel is extracted from JSON before
                e.printStackTrace();
            }
        }
    };

    /** start button listener for error fetching config - retry */
    View.OnClickListener buttonHandlerConfigError = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            setAllowInteraction(false, false);
            fetchConfig();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_model_choose);

        // layout references
        screenLog = findViewById(R.id.log);
        spinner = findViewById(R.id.spinner);
        startButton = findViewById(R.id.button);

        // set initial spinner message
        // set Spinner to not loaded message
        List<String> list = new ArrayList<>();
        list.add("Model list not available");
        // setup spinner adapter
        ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.custom_spinner, list);
        adapter.setDropDownViewResource(R.layout.custom_spinner_dropdown);
        spinner.setAdapter(adapter);

        // fetch initial try
        fetchConfig();
    }

    @Override
    protected void onRestart() {
        // reset log message and interactions when coming back from camera activity
        super.onRestart();
        logMessage("Select your model");
        setAllowInteraction(true, false);
    }
}