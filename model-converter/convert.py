import os
from os.path import join
import glob
import tensorflow as tf
import subprocess
import random
import numpy as np
from PIL import Image
import sys
import argparse
import json
import shutil
from pathlib import Path

from tensorflow.keras.preprocessing.image import ImageDataGenerator
from tensorflow.keras.applications.mobilenet import preprocess_input

# config argparse
parser=argparse.ArgumentParser(description='''Python script for converting saved model files to a mobile compatible .tflite flatbuffer file''')
parser.add_argument('--model_dir', dest='model_dir', type=str, help='directory which must contain a saved_model in a folder "saved_model"', required=True)
parser.add_argument('--output_dir', dest='output_dir', type=str, help='directory where the converted TFLite model should be saved"', required=True)
parser.add_argument('--post_quantize', dest='post_quantize', type=bool, default=False, help='set true, when a post training quantization should be performed')
parser.add_argument('--dataset_size',dest='dataset_size', required='--post_quantize' in " ".join(sys.argv),type=int, help='input size of the model in pixel')
args=parser.parse_args()

# declare paths
githubTFModelDir = "_TF_repo"
args.output_dir = join(args.output_dir, 'TFLITE_OUTPUT')

# remove and recreate the output folder
shutil.rmtree(args.output_dir, ignore_errors=True)
os.mkdir(args.output_dir)

################################################################################
# create saved intermediate model
frozenGraphCommand = \
    "python " + join(githubTFModelDir, "research\\object_detection\\export_tflite_graph_tf2.py") + \
        " --pipeline_config_path " + join(args.model_dir, 'pipeline.config') + \
                " --trained_checkpoint_dir " + join(args.model_dir, 'checkpoint') + \
                        " --output_directory " + args.output_dir
subprocess.call(frozenGraphCommand, shell=True)

################################################################################
# convert the intermediate saved models to flatbuffer.tflite
converter = tf.lite.TFLiteConverter.from_saved_model(join(args.output_dir, "saved_model"))
converter.optimizations = [tf.lite.Optimize.DEFAULT]
converter.experimental_new_converter = True

if(args.post_quantize == True):
    test_datagen = ImageDataGenerator(preprocessing_function=preprocess_input)

    test_generator = test_datagen.flow_from_directory(
        join(args.model_dir, "dataset"), 
        target_size=(args.dataset_size, args.dataset_size),
        batch_size=1, shuffle=True, class_mode='input')

    def represant_data_gen():
        for ind in range(len(test_generator.filenames)):
            img_with_label = test_generator.next()
            yield [np.array(img_with_label[0], dtype=np.float32, ndmin=2)]

    converter.representative_dataset = represant_data_gen
    # app setting
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8  # or tf.uint8
    converter.inference_output_type = tf.float32  # or tf.uint8
else:
    converter.target_spec.supported_ops = [
        tf.lite.OpsSet.TFLITE_BUILTINS, 
        tf.lite.OpsSet.SELECT_TF_OPS]

tflite_model = converter.convert()
file = os.path.basename(args.model_dir)
if(args.post_quantize):
    file = file + "_Q"
outfile = join(args.output_dir, file+ ".tflite")
fo = open(outfile, "wb")
fo.write(tflite_model)
fo.close

################################################################################
# write json config file
if(args.post_quantize):
    newPath = os.path.join(args.output_dir, os.path.basename(args.model_dir) + "_Q.json")
else:
    newPath = os.path.join(args.output_dir, os.path.basename(args.model_dir) + ".json")
labelmapJSON = open(newPath, "w")

# read labelmap file
tmp = Path(os.path.join(args.model_dir, "labelmap.txt"))
if tmp.is_file():
    # if labelmap found
    labelmap = open(tmp, "r")
    if labelmap:
        lines = labelmap.readlines()
        labellist = []
        for line in lines:
            labellist.append(line.replace("\n",""))
        labelmap.close()
else:
    # if no labelmap found
    labellist =[]

# write json file
data = {os.path.basename(args.model_dir):{"model": os.path.basename(args.model_dir), "quantized": args.post_quantize, "size" : args.dataset_size, "labelmap" : labellist}}
json.dump(data, labelmapJSON, indent=4)

labelmapJSON.close()
shutil.rmtree(os.path.join(args.output_dir, "saved_model"), ignore_errors=True)