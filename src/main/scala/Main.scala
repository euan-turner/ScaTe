import dsl.*
import ffi.NativeBackend

@main def run(): Unit =
  // Setup CPU backend
  given NativeBackend = NativeBackend.cpu

  println("--- ScaTe DSL Demo ---")
  
  // Build computation graph (no execution yet)
  val t1 = Tensor.of(Vector(5), 1.0f)   
  val t2 = Tensor.of(Vector(5), 10.0f)
  val t3 = t1 + t2
  
  println(s"Graph: $t3")
  
  // Trigger evaluation - compiles and executes the graph
  val t3Mat = t3.eval
  
  // Now we can access elements (only available on MaterialTensor)
  println(s"t3[0] = ${t3Mat.get(0)}")  // Should be 11.0
  println(s"t3[4] = ${t3Mat.get(4)}")  // Should be 11.0
  
  // Can also get all values as an array
  println(s"t3.toArray = ${t3Mat.toArray.mkString("[", ", ", "]")}")
  
  // Partial evaluation demo: use evaluated tensor in further computation
  val t4 = t3Mat + t1
  val t4Mat = t4.eval
  println(s"t4[0] = ${t4Mat.get(0)}")  // Should be 12.0