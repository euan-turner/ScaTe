LIB_DIR = lib

CXX = g++
NVCC = nvcc
CXXFLAGS = -fPIC -I src/main/native/include -O3 -shared
# CUDAFLAGS = -Xcompiler -fPIC -I src/main/native/include -O3 -shared

all: cpu # cuda

init:
	mkdir -p $(LIB_DIR)

cpu: init
	$(CXX) $(CXXFLAGS) -o $(LIB_DIR)/libcpu.so src/main/native/cpu/cpu_vector.cpp

# cuda: init
# 	$(NVCC) $(CUDAFLAGS) -o $(LIB_DIR)/libcuda_driver.so src/main/native/cuda/cuda_driver.cu

clean:
	rm -rf $(LIB_DIR)