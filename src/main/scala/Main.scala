package tensordsl

@main def run(): Unit =
  // Setup Backends
  implicit val cpuBackend = new NativeBackend("lib/libcpu.so")
  // implicit val cudaBackend = new NativeBackend("./libcuda.so")

  println("--- CPU Run ---")
  val t1 = new Tensor(5, Device.CPU)
  val t2 = new Tensor(5, Device.CPU)
  t1.set(0, 1.0f); t2.set(0, 10.0f)
  
  val cpuRes = Ops.add(t1, t2)
  println(s"CPU Result: ${cpuRes.get(0)}") // Should be 11.0

  // println("--- CUDA Run ---")
  // val g1 = new Tensor(5, Device.CUDA)
  // val g2 = new Tensor(5, Device.CUDA)
  // g1.set(0, 2.0f); g2.set(0, 20.0f)

  // val gpuRes = Ops.add(g1, g2)
  // println(s"GPU Result: ${gpuRes.get(0)}") // Should be 22.0
