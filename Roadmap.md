# Roadmap

## **Foundation**

**Objective:** Prove the end-to-end "round trip" from Scala to C++ and CUDA using Foreign Function & Memory API

### **1.1 C++ CPU Slice**

- [x] Compile `libcpu.so`
- [x] Define how C++ allocates memory and Scala accesses
- [x] Bind `Linker` setups in Scala to call C functions
- [x] Implement `VecAdd` CPU slice

### **1.2 CUDA GPU Slice**

- [ ] Compile `libgpu.so`
- [ ] Define how CUDA allocates memory (`cudaMallocManaged`) and Scala accesses
- [ ] Bind `Linker` setups for CUDA backend
- [ ] Implement `VecAdd` GPU slice

## **Graph & Abstraction**

**Objective:** Decouple "defining" an operation from "executing" it.

### **2.1 The AST**

- [x] Create Scala case classes for `Tensor`
- [x] Create Scala case classes for `Op` (Add, Mul)
- [x] Create Scala case classes for `Graph`

### **2.2 Lazy Evaluation**

- [x] Implement `.eval()` method that executes the graph
- [ ] Extend AST to several pointwise ops (`sub,hadamard,relu` and `matmul`)
- [ ] Semantic checking on AST (types, devices, shapes)
- [ ] Convert AST to DAG
- [ ] Convert DAG to normalised Tensor IR
- [ ] Convert normalised Tensor IR to Loop IR and fused pointwise ops (no broadcasting initially)
- [ ] Update c++ driver with matmul, pointwise ops and fused interpreter kernel
- [ ] Fuse pointwise ops and keep matmul separate


## **Phase 3: Optimization & Scale**

**Objective:** Optimise captured operations

### **3.1 Graph Optimisations**
 
- [ ] Common subexpression elimination
- [ ] Dead code elimination
- [ ] Algebraic simplification
- [ ] Constant folding and propagation

### **3.2 Tensor Extensions**

- [ ] Broadcasting
- [ ] Decompose complex ops (e.g. Softmax)

### **3.3 Loop Optimisations**

- [ ] Fuse reductions with elementwise ops
- [ ] Loop reordering
- [ ] Tiling
- [ ] Temporary elimination
