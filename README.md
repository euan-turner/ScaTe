## Scala Tensor DSL

### Usage

This is a normal sbt project. You can compile code with `sbt compile`, run it with `sbt run`, and `sbt console` will start a Scala 3 REPL.

### Project Structure

`src/main/native` Driver code

`src/main/native/include` Driver headers

`src/main/native/{device}` Device-specific driver

`lib` Compiled .so files

Requires JDK 21+ for [FFI](https://docs.oracle.com/en/java/javase/21/core/foreign-function-and-memory-api.html)