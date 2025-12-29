#include "vector.h"
#include <stdlib.h>
#include <string.h>

void* tensor_alloc(long size_bytes) {
    return malloc(size_bytes);
}

void tensor_free(void* ptr) {
    free(ptr);
}

void tensor_fill_float(float* ptr, int n, float value) {
    for (int i = 0; i < n; i++) {
        ptr[i] = value;
    }
}

void tensor_fill_int(int* ptr, int n, int value) {
    for (int i = 0; i < n; i++) {
        ptr[i] = value;
    }
}

void tensor_copy(float* dst, float* src, int n) {
    memcpy(dst, src, n * sizeof(float));
}

void vector_add(float* a, float* b, float* out, int n) {
    for (int i = 0; i < n; i++) {
        out[i] = a[i] + b[i];
    }
}