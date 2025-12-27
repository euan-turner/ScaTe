package tensordsl

import java.lang.foreign.*
import java.lang.invoke.MethodHandle

enum Device:
  case CPU //, CUDA

// Driver interface loads .so
class NativeBackend(libraryPath: String):
  val arena = Arena.ofAuto()
  val linker = Linker.nativeLinker()
  val symbolLookup = SymbolLookup.libraryLookup(libraryPath, arena)

  // Find driver functions
  private def find(name: String, desc: FunctionDescriptor): MethodHandle =
    linker.downcallHandle(symbolLookup.find(name).get(), desc)

  // Bind specific driver functions
  val alloc: MethodHandle = find("tensor_alloc", 
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG))
    
  val vecAdd: MethodHandle = find("vector_add", 
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT))

// Tensor
class Tensor(val size: Int, val device: Device)(using backend: NativeBackend):
  // C++ memory allocation
  val ptr: MemorySegment = backend.alloc.invokeExact(size * 4L).asInstanceOf[MemorySegment]
  // Reinterpret as float array
  val data: MemorySegment = ptr.reinterpret(size * 4L)

  def set(index: Int, value: Float): Unit = 
    data.setAtIndex(ValueLayout.JAVA_FLOAT, index.toLong, value)

  def get(index: Int): Float = 
    data.getAtIndex(ValueLayout.JAVA_FLOAT, index.toLong)

// DSL add
object Ops:
  def add(a: Tensor, b: Tensor)(using cpuBackend: NativeBackend): Tensor =
    if a.size != b.size then throw new IllegalArgumentException("Sizes must match")
    if a.device != b.device then throw new IllegalArgumentException("Devices must match")

    val result = new Tensor(a.size, a.device) // (using if a.device == Device.CPU then cpuBackend else cudaBackend)
    val driver = cpuBackend // if a.device == Device.CPU then cpuBackend else cudaBackend
    
    // Dispatch to C++ / CUDA
    driver.vecAdd.invoke(a.ptr, b.ptr, result.ptr, a.size)
    
    result
