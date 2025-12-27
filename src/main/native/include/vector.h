#ifdef __cplusplus
extern "C" {
#endif

    // Allocates memory (Standard malloc for CPU, cudaMallocManaged for GPU)
    void* tensor_alloc(long size_bytes);
    
    // Frees memory
    void tensor_free(void* ptr);

    // The kernel
    void vector_add(float* a, float* b, float* out, int n);

#ifdef __cplusplus
}
#endif