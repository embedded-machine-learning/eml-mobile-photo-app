clean: 
	@echo Remove temporary files
	-rd /s /q _TF_repo

clone:
	@echo Cloning tensorflow exaples from GitHub
	-mkdir _TF_repo
	git clone https://github.com/tensorflow/models _TF_repo

protobufs:
	@echo Compiling protobufs
	cd _TF_repo/research &&	protoc object_detection/protos/*.proto --python_out=.

install: clean clone protobufs

# EXAMPLES FOR CONVERTING WITH AND WITHOUT QUANTIZATION

# conv:
# 	python convert.py --model_dir models\tf2oda_ssdmobilenetv2_320x320_eml --output_dir models\tf2oda_ssdmobilenetv2_320x320_eml

# conv_quant:
# 	python convert.py --model_dir models\tf2oda_ssdmobilenetv2_320x320_eml --output_dir models\tf2oda_ssdmobilenetv2_320x320_eml --post_quantize True --dataset_size 320