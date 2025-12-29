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
- [ ] Update IR traversal to full stack in `IR Design.md`
- [ ] Update drivers with necessary calls
- [ ] Delay driver calls until `eval`

### **2.3 Buffer Management**

- [ ] Stop allocating fresh memory for every intermediate result
- [ ] Implement basic "Arena" or "Pool" in C++
- [ ] Add Scala controls for memory pool

## **Phase 3: Optimization & Scale**

**Objective:** Optimise captured operations

### **3.1 Fusing**

- [ ] Detect patterns like `Add -> Relu` in the AST
- [ ] Call single fused kernel for detected patterns

### **3.2 Asynchrony**

- [ ] Move CUDA calls to separate thread OR use CUDA Streams
- [ ] Ensure Scala isn't blocking while GPU crunches
