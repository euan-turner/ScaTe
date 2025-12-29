# DSL Design Doc

JIT-only, no eager eval

## Tensors

Will eventually have a device/devices for sharding. Device will determine driver used, all sharded devices must be the same type.

Tensors are nodes in the AST.

They should be created using standard factory methods (e.g. zeroes, ones).
Initialisers are unary tensor operations in the AST.

Operations between tensors are other nodes in the AST, producing tensors with inferred shapes, types, devices, strides etc.

Need set and get

## Devices

Initially just CPU, eventually GPU + variants
