#ifdef __cplusplus
extern "C" {
#endif

    // Allocates memory (Standard malloc for CPU, cudaMallocManaged for GPU)
    void* tensor_alloc(long size_bytes);
    
    // Frees memory
    void tensor_free(void* ptr);

    // Fill operations
    void tensor_fill_float(float* ptr, int n, float value);
    void tensor_fill_int(int* ptr, int n, int value);

    // Copy operation
    void tensor_copy(float* dst, float* src, int n);

    // The kernel
    void vector_add(float* a, float* b, float* out, int n);

#ifdef __cplusplus
}
#endif