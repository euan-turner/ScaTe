# Tensor DSL Compilation Pipeline

Outline of the IRs used in this DSL, the responsibilities and optimisations at each stage, and how a simple expression

    y = relu(a + b)

is represented as it flows through the pipeline.

The guiding principle is **separation of concerns**: each IR exists to enable a
specific class of optimisations and should not leak responsibilities from
lower layers.

---

## 1. User AST (Embedded DSL)

### Purpose

Represents the user program as written in Scala. This IR preserves source-level
structure and benefits from Scala’s type system.

### Characteristics

- Tree-structured
- Strongly typed via Scala
- May contain symbolic or partially-known shapes

### Responsibilities

- Semantic correctness (types)
- Device compatibility checks
- Early shape validation
- Error reporting

### Optimisations

- Trivial constant folding

### Example: `relu(a + b)`

```scala
Relu(
  Add(
    Tensor("a"),
    Tensor("b")
  )
)
```

---

## 2. Graph IR (Explicit DAG)

### Purpose

Makes dataflow explicit and enables whole-graph reasoning and rewrites.

### Characteristics

- Explicit DAG (no nesting)
- Stable node/value identifiers
- Shapes and dtypes fully attached

### Responsibilities

- Dependency tracking
- Structural graph optimisations
- Shape inference and propagation

### Optimisations

- Common subexpression elimination (CSE)
- Dead code elimination (DCE)
- Constant folding and propagation
- Algebraic simplification

### Example

```text
  v0 = Input(a)        // shape [N]
  v1 = Input(b)        // shape [N]
  v2 = Add(v0, v1)     // shape [N]
  v3 = Relu(v2)        // shape [N]
```

---

## 3. Normalised Tensor IR (Canonical Op IR)

### Purpose

Provides a stable, minimal semantic layer with a reduced operator set.

### Characteristics

- Small, fixed set of primitive tensor ops
- No syntactic sugar
- Broadcasts and layouts made explicit
- Aliasing behaviour well-defined

Responsibilities

- Operator decomposition
- Explicit broadcast semantics
- Layout and stride semantics
- Alias analysis (may-alias / no-alias)
- Marking purity and mutability

Optimisations

- Operator decomposition
- Broadcast normalisation
- Semantic canonicalisation
- Removal of redundant layout transforms

### Example

```text
v0 = Input(a, layout=contiguous)
v1 = Input(b, layout=contiguous)
v2 = TensorAdd(v0, v1, broadcast=none)
v3 = Relu(v2)
```

---

## 4. Loop IR (Fusible Kernel IR)

### Purpose

Expresses computations as explicit loop nests over tensor elements.
This is the lowest IR that still understands tensors and enables fusion.

### Characteristics

- Explicit iteration space
- Scalar operations inside loops
- Temporaries are implicit (register-level)
- Selectively applied to fusible subgraphs

Responsibilities

- Operator fusion
- Loop construction
- Memory access ordering
- Scheduling decisions (basic initially)

Optimisations

- Elementwise and reduction fusion
- Loop reordering
- Tiling / blocking
- Temporary elimination
- Kernel specialization (later)

### Example

```text
for i in 0 .. N:
  t0 = a[i] + b[i]
  y[i] = max(t0, 0.0)
```

Internally this is represented as a short sequence of scalar ops executed per
element.

---

## 5. Backend Execution (C++ Interpreter / FFI)

### Purpose

Executes the Loop IR or dispatches to precompiled kernels via FFI.

### Characteristics

- Stable native ABI
- Minimal number of entry points
- No semantic decisions

### Responsibilities

- Memory allocation and access
- Interpreting Loop IR
- Calling external kernels
- Returning results to Scala

### Optimisations

- Avoiding kernel launch overhead
- Efficient memory access
- (Later) JIT, vectorisation, or architecture-specific lowering

### Example (Conceptual C++)

```cpp
for (int i = 0; i < N; ++i) {
  float t0 = a[i] + b[i];
  y[i] = std::max(t0, 0.0f);
}
