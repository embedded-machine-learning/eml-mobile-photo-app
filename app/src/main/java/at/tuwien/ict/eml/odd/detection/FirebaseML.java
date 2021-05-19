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

package at.tuwien.ict.eml.odd.detection;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.ml.modeldownloader.CustomModel;
import com.google.firebase.ml.modeldownloader.CustomModelDownloadConditions;
import com.google.firebase.ml.modeldownloader.DownloadType;
import com.google.firebase.ml.modeldownloader.FirebaseModelDownloader;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;

public class FirebaseML {
    // interval in seconds when firebase should be checked for a new config
    private int fetchInt = 60;

    private FirebaseRemoteConfig firebaseRemoteConfig;
    private CustomModelDownloadConditions conditions;

    private CustomModel modelFile;
    private boolean modelFileReady = false;
    private FirebaseRemoteConfigValue configValue;
    private boolean configValueReady = false;

    /**
     *  Interface for callback
     */
    public interface onCompleteCallback{
        void onSuccess();
        void onError(FirebaseException e);
    }

    /**
     *  firebase constructor
     */
    public FirebaseML(){
        firebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        FirebaseRemoteConfigSettings configSettings = new FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(fetchInt)
                .build();
        firebaseRemoteConfig.setConfigSettingsAsync(configSettings);
    }

    /**
     * Requests and downloads the remote config from firebase.
     * @param key Key name of the remote config parameter you want to download
     */
    public void requestRemoteConfig(String key, onCompleteCallback complete) {
        configValueReady = false;
        firebaseRemoteConfig.fetchAndActivate().addOnCompleteListener(
            new OnCompleteListener<Boolean>() {
                @Override
                public void onComplete(@NonNull Task<Boolean> task) {

                    if (task.isSuccessful()) {
                        configValue = firebaseRemoteConfig.getValue(key);
                        configValueReady = true;
                        complete.onSuccess();
                    } else {
                        complete.onError((FirebaseException) task.getException());
                    }
                }
            }
        );
    }

    /**
     * Get the downloaded remote config parameter value
     * @return The remote config parameter value
     */
    public FirebaseRemoteConfigValue getRemoteConfig(){
        if (configValueReady){
            configValueReady = false;
            return configValue;
        } else {
            return null;
        }
    }

    /**
     * Extract the list of available models out of the json
     * @param modelConfig Config in json string form
     * @return ArrayList of available model names
     * @throws JSONException If the modelConfig is invalid
     */
    public static ArrayList<String> extractModelNameList(String modelConfig) throws JSONException {
        JSONObject json = new JSONObject(modelConfig);
        // iterate through the model name keys and adds them to a list
        Iterator<String> models = json.keys();
        ArrayList<String> modelNameList = new ArrayList<>();
        while (models.hasNext()) {
            modelNameList.add(models.next());
        }
        return modelNameList;
    }

    /**
     * Extracts the model file name for a given model from the JSON
     * @param modelConfig JSON String
     * @param modelName Provided model name
     * @return Model file name
     * @throws JSONException If modelConfig or modelName invalid
     */
    public static String extractModelFile(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getString("model");
    }

    /**
     * Extracts the labelMap for a given model from the JSON
     * @param modelConfig JSON String
     * @param modelName Provided model name
     * @return Labelmap in ArrayList form
     * @throws JSONException If modelConfig or modelName invalid
     */
    public static ArrayList<String> extractLabelMap(String modelConfig, String modelName) throws JSONException {
        ArrayList<String> labelMapList= new ArrayList<>();
        JSONArray labelMap = new JSONObject(modelConfig).getJSONObject(modelName).getJSONArray("labelmap");
        for (int i=0;i<labelMap.length();i++){
            labelMapList.add(labelMap.getString(i));
        }
        return labelMapList;
    }

    /**
     * Extracts the model input size for a given model from the JSON
     * @return Input size of the model
     */
    public static int extractInputSize(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getInt("size");
    }

    /**
     * Extracts if the given model is quantized or not
     * @return True if quantized
     */
    public static boolean extractQuantized(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getBoolean("quantized");
    }

    /**
     * Starts the download of the given firebase model. Calls complete when complete.
     * @param model Name of the model
     */
    public void requestRemoteModel(String model, onCompleteCallback complete) {
        modelFileReady = false;
        conditions = new CustomModelDownloadConditions.Builder().build();
        FirebaseModelDownloader.getInstance()
                .getModel(model, DownloadType.LATEST_MODEL, conditions)
                .addOnCompleteListener(
                        new OnCompleteListener<CustomModel>() {
                            @Override
                            public void onComplete(@NonNull Task<CustomModel> task){
                                if (task.isSuccessful()) {
                                    modelFile = task.getResult();
                                    modelFileReady = true;
                                    complete.onSuccess();
                                } else {
                                    complete.onError((FirebaseException) task.getException());
                                }
                            }
                        }
                );
    }

    /**
     * Get the downloaded model
     * @return The downloaded model file
     */
    public CustomModel getRemoteModel(){
        if (modelFileReady){
            modelFileReady = false;
            return modelFile;
        } else {
            return null;
        }
    }
}