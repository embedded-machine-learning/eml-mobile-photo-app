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
     *  interface for callback
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
     * downloads the remote config from firebase
     * @param key of the config field which contains the model name
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
     * get the downloaded configuration
     * @return the config value
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
     * extract the list of available models out of the json
     * @param modelConfig config in json string form
     * @return ArrayList of available models
     * @throws JSONException if the modelConfig is invalid
     */
    public static ArrayList<String> extractModelNameList(String modelConfig) throws JSONException {
        JSONObject json = new JSONObject(modelConfig);
        // iterate through the model name keys
        Iterator<String> models = json.keys();
        ArrayList<String> modelNameList = new ArrayList<>();
        while (models.hasNext()) {
            modelNameList.add(models.next());
        }
        return modelNameList;
    }

    /**
     * extracts the model file name for a given model from the JSON
     * @param modelConfig JSON String
     * @param modelName provided model label
     * @return model file name
     * @throws JSONException if modelConfig or modelName invalid
     */
    public static String extractModelFile(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getString("model");
    }

    /**
     * extracts the labelMap for a given model from the JSON
     * @param modelConfig JSON String
     * @param modelName provided model name
     * @return labelmap in ArrayList form
     * @throws JSONException if modelConfig or modelName invalid
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
     * extracts the model input size for a given model from the JSON
     * @return input size of the model
     */
    public static int extractInputSize(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getInt("size");
    }

    /**
     * extracts if the given model is quantized or not
     * @return True if quantized
     */
    public static boolean extractQuantized(String modelConfig, String modelName) throws JSONException {
        return new JSONObject(modelConfig).getJSONObject(modelName).getBoolean("quantized");
    }

    /**
     * starts the download of the given firebase model
     * @param model name of the model
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
     * get the downloaded model
     * @return the custom model file
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