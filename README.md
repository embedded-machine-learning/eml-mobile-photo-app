# EML Object Detection Android App

![TensorFlow](https://img.shields.io/badge/TensorFlow-2.5_(nightly)-FF6F00?logo=tensorflow&style=flat-square)
![CUDA](https://img.shields.io/badge/CUDA-11.2-76B900?logo=nvidia&style=flat-square)
![cuDNN](https://img.shields.io/badge/cuDNN-8.1-76B900?logo=nvidia&style=flat-square)
![Python](https://img.shields.io/badge/Python-3.8-3776AB?logo=python&style=flat-square)

<img src="doc\tfl2a.png" width="500">

## Summary
*Last Update 17.05.2021 for app version 0.3.1-beta*

You can get the latest compiled version of the app [here](https://github.com/embedded-machine-learning/eml-mobile-photo-app/tree/main/app/release).

This readme contains a documentation of the EML Object Detection Android app. It includes a step by step guide to set up a tensorflow environment for converting TensorFlow models to app-compatible Tensorflow Lite models and to host them in the cloud with Google Firebase.

The guide is written for Windows 10 with a CUDA capable GPU. If you dont have CUDA support, just skip the CUDA and cuDNN intallation. When you just want to convert models and do no training, you probably see no notable speed increase with GPU support at all.

## Subjects
1. [Introduction](#1-introduction)
2. [Setup the environment](#2-setup-the-environment)
3. [Convert a model](#3-convert-a-model)
4. [Remote model hosting](#4-remote-model-hosting)
5. [App Documentaion](#5-app-documentation)

---

## 1. Introduction

Due to increasing mobile computing capabilities, machine learning applications on mobile Devices became more commonly used over the last years. Google is heavily pushing this development with Tensorflow Lite, Edge TPUs, Androids NN API and so forth.

You can find a lot of object detection demonstration apps (like the official one from the TensorFlow repository) and many code pieces and snippets.
Also there are a lot of good tutorials on converting models to the .tflite format and bringing it to an Android Smartphone. But due to the rapid development of the tensorflow ecosystem sometimes commands or APIs are deprecated or got replaced, workflows have changed with newer versions of the tools and so forth. Therefore you have to search multiple sites for answers. 

This guide should act as a single source for all tools you need to use the app. The goal is to get a detailed understanding of what steps it takes to get an existing model into the app and how the app is structured.

### Overview

In the image you can see a structured overview of the three main components of this project: The model conversion, the remote model hosting and the app itself.

<img src="doc\project-overview.png" width="650"/>

## 2. Setup the environment

### Installing Anaconda

First go to the official Anaconda Website https://www.anaconda.com/ and download the Windows x64 installer for the individual edition. Run the installer and leave all settings on default. 

After finishing open up the Anaconda Prompt with admin permissions. 

First update all conda packages with

```shell
conda update --all
```

Then create a virtual environment with the name 'tf' and switch to that environment
```shell
conda create -n tf pip python=3.8
activate tf
```

Lets make sure the latest version of pip is available with
```shell
python -m pip install --upgrade pip
```

Add the conda-forge packet channel to the configuration of conda.\
It often provides more recent package versions than the default conda channel.
```shell
conda config --add channels conda-forge
```

Note that we are installing specific versions of Tensorflow, Cuda and cudNN. You can find the list of the tested build configurations [here](https://www.tensorflow.org/install/source#tested_build_configurations).

Install Tensorflow with GPU support with
```shell
python -m pip install tf-nightly-gpu
```
Note: If you dont want the GPU support, replace tf-nightly-gpu with tf-nightly.

If you want the GPU support you also need to install Cuda and cudNN with 
```shell
conda install cudatoolkit=11.2 cudnn=8.1
```
The model converter needs the installation of the following additional packages
```shell
python -m pip install scipy matplotlib pyyaml tf_slim
```

and
```shell
conda install -c anaconda protobuf
```

### Installing Android Studio

The Project is configured for the usage with Android Studio. Therefore I recommend it for further development.
Get to the Android Developer Website https://developer.android.com/studio and Download the latest version of Android Studio.

Once downloaded, run the installer and follow the instructions.

### Setup the model converter

For converting the models to the correct format this repository provides a Python script - which needs additional setup steps.
First, if you have not already - clone this repo to your local drive e.g. C:/eml-object-detection-android-app.

Open your Anaconda prompt with admin permissions and navigate to the model_converter folder in this repo and run
```shell
activate tf
make install
```
if you have "GNU make" installed. 
If not, you can simply run the install.bat from the anaconda prompt.

The installer clones the Tensforflow/models repo into the model-converter folder and compiles a collection of protobufs which are needed for the converter.

As last step you have to add the two follwing paths to yout PYTHONPATH system path variable:

```shell
<LOCATION_OF_THE_REPO>/models/_TF_repo
<LOCATION_OF_THE_REPO>/models/_TF_repo/research
```

## 3. Convert a model

Now with all setup, you are ready to start to convert a given model to get it in the app. The model_converter tool which is included in this repository is configured to take a TensorFlow model saved as saved_model and convert it to a TensorFlow Lite (.tflite) model. You can find the tool in the model_converter folder.

What the converter basically does is to convert a given saved_model to an intermediate model, where all dynamic sizes are replaced with static sizes. Then this intermediate model is converted to the .tflite flatbuffer format and optionally post training quantized with a dataset.

In addition to the model file the converter writes a JSON file for the remote configuration for the remote model hosting.

### Preparing the existing model
To run the converter you need to prepare your model. You need to provide the model directory in a specific structure.

```shell
model-name/
├── checkpoint
├── dataset
│   └── test
│       ├── img_001.jpg
│       ├── img_002.jpg
│       └── ... 
├── labelmap.txt
├── pipeline.config
└── saved_model
    └── saved_model.pb
```

The parent directory should be named after the model. The folder name is then usedfor naming the output files.

In the model folder there must exist a saved_model folder (which contains the model in the .pb format) and a checkpoint folder with the checkpoints. Also necessary is the pipeline.config file.

If you provide a labelmap as labelmap.txt file in the following format, the converter can include the labelmap in the json file. If you dont provide one, you still can add the labelmap later.

```shell
Class Label 1
Class Label 2
Class Label 3
...
Class Label N
```

When you want to perform a post training quantization you must provide a dataset folder which contains a test folder which contains .jpg validation pictures. The test folder should contain at least a 2 digit number of images.

### Use the model converter

To perform a conversion you have to start the anaconda prompt and activate the environment you created before with

```shell
activate tf
```

Then you can call the converter in the model_converter folder with

```shell
convert.py [-h] --model_dir MODEL_DIR --output_dir OUTPUT_DIR 
[--post_quantize POST_QUANTIZE --dataset_size DATASET_SIZE]
```

--model_dir MODEL_DIR  
directory which must contain a saved_model in a folder "saved_model"

--output_dir OUTPUT_DIR  
directory where the converted TFLite model should be saved". In the Output folder there will be a TFLITE_OUTPUT folder generated.

--post_quantize POST_QUANTIZE  
set to True, when a post training quantization should be performed. If true, you must also provide the dataset_size parameter.

--dataset_size DATASET_SIZE  
input size of the model in pixel.

The conversion can take up to a few minutes. After the conversion is finished, you should see a folder TFLITE_OUTPUT in your OUTPUT_DIR directory. In this folder there are 2 files. One is the actual .tflite model file, and one is the JSON file for the remote configuration.

## 4. Remote model hosting

The app is designed to work with the object detection models hosted on Google Firebase. The big advantage with this implementation is, that you don't have to recompile the app when you want to change a model. 

For this the app is using 2 Google Firebase APIs: Machine Learning and Remote Config.

<img src="doc\project-model-hosting.png" width="400"/>

The machine learning API is responsible for downloading the models from the Google cloud. 

The remote Config API is responsible for downloading a specific key from the google cloud. In this key we store the model label, the linked model file from the machine learning, the model input size and the labelmap.

The communication happens in the following manner: The app is requesting the remote config from Firebase. The config contains a list of all available models in the cloud with their specific properties.
The app uses this information to generate a list of the model names and present it to the user. When the user has selected a model, this specific model file is requested from Firebase. 

### Create a new Firebase project

If you haven't already an active Firebase project connected with the app or if u want to create a new Firebase project with another Google account you first need to configure Google Firebase and setup a new android project. 

For this just follow the steps from the [Google guide](https://Firebase.google.com/docs/android/setup#console). When asked for the package name of the app - type in

```shell  
at.tuwien.ict.eml.odd
````
Click through the rest of the dialogue. Now repeat the above step with the different package name:

```shell  
at.tuwien.ict.eml.odd.debug
````
and at the end save the google-services.json file to your local drive. You must copy this file to the Android Studio folder into the /app folder. to connect the app with this specific Firebase project.

Why the second app configuration with the ".debug" package name is needed, is because the app project is configured to create the beta and release versions of the app under the package name "at.tuwien.ict.eml.odd" and the version which is used for debugging under "at.tuwien.ict.eml.odd.debug". This has the advantage, that the debug version does not overwrite a realease version installed on your phone. This can be very useful when you want to compare your debug version with the latest release version.

### Create / Edit the remote config

To create a new modelConfig click on "Remote Config" in the left navigation panel. There you click on "Add parameter". Type "modelConfig" as parameter key. For the parameter value copy the content from the .json file, which was generated by the model_converter in the privious step. This json data contains for example this information:
```javascript
{
    "EML - SSD Mobilenet v2 Q": {
        "model": "modelFileName",
        "quantized": true,
        "size": 320,
        "labelmap": [
              "demonstrator",
              "cpu",
              "gpu",
              "npu",
              "person",
              "other"
        ]
    },
    // copy your other models in here
}
```

After that click on "Update" and then on "Publish Changes".

### Add / Edit the model files

For adding a model to Google Firebase you have to click on "Machine Learning" in the left navigation window.

Then click on the "Custom" tab.
There you click on "Add model" and upload your .tflite file. As name type in the value of the "model"-key in the genereated json file.

Click through the dialogue. Your Firebase configuration is now ready.

## 5. App documentation

In this section the app itself is described. You should get a feeling of what part of the app is responsible for what functionality. Detailed descriptions of the methodes are provided in the source code.
### Activity overview

Here you can see a navigational diagram of the activities and their coresponding screenshots: 

<img src="doc\project-app-activity.png" width="700"/>

<p align="left">
    <img src="doc\activity_modelchooser.png" width="250"/>
    <img src="doc\activity_camera.png" width="250"/>
     <img src="doc\activity_settings.png" width="250"/>
</p>

As you can see in the diagram, the app has 3 activities with the socalled "modelChooser" activity as an entry point to the app. So when your start the app you will start in the modelChooser activity.

#### ModelChooser activity

In the modelChooser activity the first thing that happens is that the app will request the modelConfig from your created Google Firebase project. The remote config gets downloaded and the app creates a list of all the available models. This list is then presented to the user.

The user selects a model and presses the "Start" button. After the button is pressed the selected modelfile is getting downloaded. When the download is finished the modelConfig as String and the modelFile as file location String get passed to the main activity: the camera activity.

#### Camera activity

In the camera activity you see the camera screen with ongoing object detection. You see a navigation bar which you can use to switch models and navigate to the settings activity. And what you can also see on the bottom of the screen is a statistics panel which can be extended.

In the default state the object detection is performed in the "Continuous" mode. That means that every image that comes from the camera stream, is processed and the object detection is performed. 

You can change this behaviour in the settings under ["OneShot mode"](#one-shot-mode). With this mode selected the object detection is only performed when the capture button is pressed.
To illustrate the data flow of the camera streams see the following picture

<img src="doc\project-app-detail.png" width="700"/>

Notice that the tracker in the "Continious mode" (analysis stream) is drawing the bounding boxes of the detected objects onto the tracking screen overlay, while the "OneShot mode" is drawing the boxes onto the picture itself, and saves this picture onto the devices storage.

The preview stream is just to draw the current camera image onto the screen, so you can see it. This setup with this 2 camera streams is realised via CameraX.

Lets have a closer look at the extended statistics panel:

<img src="doc\activity_camera_stat_detail.png" width="400"/>

It gives you an overview over the inferences performed through the app. You can see the last inference time, the inferences per second (which includes the whole processing pipeline) and also a boxplot of the last n inferences performed.
At the bottom you see basic information of the currently loaded model.

#### Settings activity

When you press the "setting icon" in the camera activity you get to the setting activity, where you can change different parameters and setting. See detailed description under [Settings](#settings)
### Settings

#### One Shot Mode 
When enabled, the object detection is only performed when the capture button is pressed. The image with drawn bounding boxes is then shown to the user.

#### Crop Mode  
If "Cover": The whole visible image is croped to the model input size. This aspect ratio will not be preserved.  
If "Contain": A part of the image in the middle is cut out with an aspect ratio of 1:1 with the biggest possible size. This cut out is represented with the darkened areas on the screen.

#### Confidence Threshold
Minimum confidence value to draw the bounding box.

#### Bound Box Color Mode
If "Classes": Every class gets a random chosen color.
If "Confidence": The higher the confidence, the greener the bounding box get.

#### Display confidence in Bounding Box
If enabled, the confidence value will be drawn into the bounding box header.

#### Number of Threads
Number of threads that can be used for the detection API. The effect of this differs on hadrware and model size.

#### Enable NNAPI usage
Allows the TensorFlow Lite API to use the Android Neural Network API.

#### Boxplot Sample Number
Changes the buffer size of the inference values shown by the boxplot in the statistics panel.
