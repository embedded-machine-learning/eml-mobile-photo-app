rd /s /q _TF_repo
@echo Cloning tensorflow exaples from GitHub
mkdir _TF_repo
git clone https://github.com/tensorflow/models _TF_repo
@echo Compiling protobufs
cd _TF_repo/research 
protoc object_detection/protos/*.proto --python_out=.