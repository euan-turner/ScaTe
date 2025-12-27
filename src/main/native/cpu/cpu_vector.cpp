#include "vector.h"
#include <stdlib.h>

void* tensor_alloc(long size_bytes) {
    return malloc(size_bytes);
}

void tensor_free(void* ptr) {
    free(ptr);
}

void vector_add(float* a, float* b, float* out, int n) {
    for (int i = 0; i < n; i++) {
        out[i] = a[i] + b[i];
    }
}