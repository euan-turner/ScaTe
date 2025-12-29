# Project Design Document

## Unified Tensor Compiler Core for an Embedded Scala DSL and a Standalone Tensor Language

---

## 1. Project Overview

This project aims to build a **shared tensor compilation core** that supports two distinct user-facing systems:

1. **An embedded DSL (EDSL) in Scala**
   - Users write tensor computations inside a Scala program
   - Results can be extracted and used in normal Scala control flow
   - Execution is primarily via FFI-backed kernels with selective fusion

2. **A standalone tensor language compiler**
   - Users write programs in a custom tensor language
   - The compiler performs whole-program optimisation
   - Full code generation (AOT or JIT) is supported, without host-language interleaving

The central design goal is to **maximise reuse of semantic IRs and optimisation logic**, while allowing the two systems to diverge cleanly where their execution and runtime requirements fundamentally differ.

---

## 2. High-Level Architecture

```
                +----------------------+
                |  Shared Compiler     |
                |      Core            |
                |----------------------|
                |  Graph IR            |
                |  Normalised Tensor   |
                |  Loop IR             |
                |  Optimisation Passes |
                +----------+-----------+
                           |
        +------------------+------------------+
        |                                     |
+-------------------------+        +-----------------------------+
| Embedded DSL (Scala)    |        | Standalone Tensor Compiler  |
|-------------------------|        |-----------------------------|
| Scala EDSL Frontend     |        | Parser + Typechecker        |
| Incremental Lowering    |        | Whole-Program Lowering      |
| FFI Runtime             |        | Codegen Backend             |
+-------------------------+        +-----------------------------+
```

**Key principle**:  
> *IRs and optimisations are shared; frontends, lowering policies, and runtimes are not.*

---

## 3. Design Principles

1. **Separation of concerns**
   - Higher IRs express *what* is computed
   - Lower IRs express *how* it is executed

2. **Policy vs representation**
   - IRs are declarative and policy-free
   - Decisions about lowering, fusion aggressiveness, and codegen live outside the IR

3. **Selective lowering**
   - Not all computations need to be lowered to low-level IRs
   - The EDSL and standalone compiler may make different choices

4. **Future-proofing**
   - Early IRs must not encode JVM, FFI, or codegen assumptions
   - Loop IR should support both interpretation and compilation

---

## 4. Frontends (Not Shared)

### 4.1 Scala Embedded DSL Frontend

**Responsibilities**

- Provide a type-safe, idiomatic Scala API for tensor operations
- Allow interleaving with Scala control flow
- Construct an initial AST incrementally

**Characteristics**

- Uses Scala’s type system for semantic correctness
- Executes eagerly or semi-lazily
- May deal with partially-known or runtime-dependent shapes

**Output**

- User AST → Graph IR

---

### 4.2 Standalone Tensor Language Frontend

**Responsibilities**

- Parse a custom tensor language
- Perform full syntactic and semantic analysis
- Produce a complete program representation

**Characteristics**

- Whole program available upfront
- No host-language interleaving
- Easier global analysis and optimisation

**Output**

- Parsed program → Graph IR

---

## 5. Shared Intermediate Representations

### 5.1 Graph IR (Explicit DAG)

**Purpose**

- Make dataflow explicit
- Enable global graph-level optimisations

**Characteristics**

- Explicit DAG with stable IDs
- SSA-like values
- Shapes and dtypes explicitly attached

**Optimisations**

- Common subexpression elimination (CSE)
- Dead code elimination (DCE)
- Constant folding and propagation
- Algebraic simplification
- Shape inference and validation

**Shared by**

- Embedded DSL
- Standalone compiler

---

### 5.2 Normalised Tensor IR (Canonical Semantic IR)

**Purpose**

- Provide a stable, minimal semantic definition of tensor operations

**Characteristics**

- Reduced operator set
- No syntactic sugar
- Explicit broadcast and layout semantics
- Well-defined aliasing behaviour

**Optimisations**

- Operator decomposition
- Broadcast normalisation
- Canonicalisation of layouts and semantics
- Alias analysis (may-alias / no-alias)

**Shared by**

- Embedded DSL
- Standalone compiler

---

### 5.3 Loop IR (Fusible Kernel IR)

**Purpose**

- Express computations as explicit loop nests
- Enable fusion and scheduling transformations

**Characteristics**

- Explicit iteration spaces
- Scalar operations inside loops
- Temporaries implicit at the register level
- Abstract over execution backend

**Optimisations**

- Elementwise and reduction fusion
- Loop reordering
- Tiling / blocking
- Temporary elimination
- Kernel specialization (later)

**Shared by**

- Embedded DSL
- Standalone compiler

**Important constraint**
> Loop IR must remain backend-agnostic and must not encode FFI, JVM, or codegen assumptions.

---

## 6. Lowering and Optimisation Policy (Not Shared)

While the IRs are shared, **lowering decisions are context-dependent**.

### 6.1 Embedded DSL Policy

- Prioritise low compile latency
- Lower only small, clearly fusible regions to Loop IR
- Fall back to precompiled FFI kernels eagerly
- Avoid whole-graph lowering

### 6.2 Standalone Compiler Policy

- Aggressive lowering to Loop IR
- Whole-program fusion
- Global scheduling and memory planning
- Full code generation

These policies operate *on the same IRs* but make different decisions.

---

## 7. Backends and Runtime (Not Shared)

### 7.1 Embedded DSL Backend

**Execution model**

- Stable C++ ABI
- Interpreted Loop IR kernels
- FFI calls for precompiled ops

**Responsibilities**

- Memory allocation and lifetime management
- Interpreting Loop IR
- Dispatching to native kernels
- Returning results to Scala

**Goals**

- Minimal FFI surface
- Predictable performance
- Low overhead for interactive use

---

### 7.2 Standalone Compiler Backend

**Execution model**

- Full code generation (AOT or JIT)
- No FFI boundary at runtime

**Responsibilities**

- Code generation (LLVM, assembly, or other)
- Static memory planning
- Target-specific optimisation
- Producing executable binaries or libraries

**Goals**

- Maximum performance
- Full control over execution
- Architecture-specific tuning

---

## 8. Example: `y = relu(a + b)`

### Graph IR

```text
v0 = Input(a)
v1 = Input(b)
v2 = Add(v0, v1)
v3 = Relu(v2)
```

### Normalised Tensor IR

```text
v2 = TensorAdd(v0, v1, broadcast=none)
v3 = Relu(v2)
```

### Loop IR

```text
for i in 0 .. N:
  t0 = a[i] + b[i]
  y[i] = max(t0, 0.0)
```

### Execution

- Embedded DSL: interpreted fused kernel via FFI
- Standalone compiler: compiled into native code

---

## 9. Benefits of This Design

- **High reuse** of complex optimisation logic
- **Clean separation** between semantics and execution
- **Scales naturally** from interpreter → JIT → AOT
- **Supports both interactive and batch compilation workflows**
- **Avoids locking the IRs to a specific runtime**

---

## 10. Risks and Mitigations

### 10.1 Risk: IR contamination by runtime assumptions  

**Mitigation**: enforce strict layering and code review discipline

### 10.2 Risk: Over-engineering Loop IR too early  

**Mitigation**: start minimal (elementwise + simple reductions)

### 10.3 Risk: Diverging behaviour between EDSL and compiler  

**Mitigation**: share IR validation and optimisation passes

---

## 11. Conclusion

This project design enables a **single, coherent tensor compiler core** to serve
both an embedded Scala DSL and a standalone tensor language compiler. By sharing
semantic IRs and optimisations while allowing frontends, policies, and runtimes
to diverge, the system achieves both **flexibility** and **long-term scalability**.

The result is a platform that can start small—with an interpreted FFI backend—
and evolve toward full native code generation without architectural redesign.
```
