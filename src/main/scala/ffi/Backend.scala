package ffi

import java.lang.foreign.{Arena, FunctionDescriptor, Linker, MemorySegment, SymbolLookup, ValueLayout}
import java.lang.invoke.MethodHandle
import scala.collection.immutable.Vector
import scala.reflect.ClassTag

import dsl.*

/**
  * Native backend for loading and calling C++ driver functions.
  * Uses Java's Foreign Function & Memory API (Project Panama).
  */
class NativeBackend(libraryPath: String):
  private val arena = Arena.ofAuto()
  private val linker = Linker.nativeLinker()
  private val symbolLookup = SymbolLookup.libraryLookup(libraryPath, arena)

  /** Find and bind a native function by name and descriptor */
  private def find(name: String, desc: FunctionDescriptor): MethodHandle =
    linker.downcallHandle(symbolLookup.find(name).get(), desc)

  // Bind specific driver functions
  val alloc: MethodHandle = find("tensor_alloc",
    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_LONG))

  val free: MethodHandle = find("tensor_free",
    FunctionDescriptor.ofVoid(ValueLayout.ADDRESS))

  val vecAddFloat: MethodHandle = find("vector_add",
    FunctionDescriptor.ofVoid(
      ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
    ))

  /** Allocate memory for a tensor of given size (in bytes) */
  def allocTensor(sizeBytes: Long): MemorySegment =
    // Use invoke (not invokeExact) for Scala compatibility
    val ptr = alloc.invoke(sizeBytes).asInstanceOf[MemorySegment]
    ptr.reinterpret(sizeBytes)

  /** Free tensor memory */
  def freeTensor(ptr: MemorySegment): Unit =
    free.invoke(ptr)

  /** Vector add for floats */
  def vectorAdd(a: MemorySegment, b: MemorySegment, out: MemorySegment, n: Int): Unit =
    vecAddFloat.invoke(a, b, out, n)

object NativeBackend:
  /** Default CPU backend - lazily initialized */
  lazy val cpu: NativeBackend = new NativeBackend("lib/libcpu.so")


/**
  * Graph evaluator - walks the AST and executes operations via native backend.
  */
object Evaluator:
  
  /** Evaluate a node graph and return a MaterialTensor with computed data */
  def evaluate[T <: DType : ClassTag](node: TypedNode[T])(using backend: NativeBackend): MaterialTensor[T] =
    node match
      case mt: MaterialTensor[T] => 
        // Already materialized, just return it
        mt
      
      case Add(x, y) =>
        // Recursively evaluate operands
        val leftMat = evaluate(x)
        val rightMat = evaluate(y)
        
        // Validate shapes match
        require(leftMat.shape == rightMat.shape, 
          s"Shape mismatch: ${leftMat.shape} vs ${rightMat.shape}")
        
        val size = leftMat.shape.product
        val sizeBytes = size * elementSize[T]
        
        // Allocate output
        val outData = backend.allocTensor(sizeBytes)
        
        // Dispatch to native backend based on element type
        summon[ClassTag[T]].runtimeClass.getSimpleName match
          case "FP32" =>
            backend.vectorAdd(leftMat.data, rightMat.data, outData, size)
          case "INT32" =>
            // TODO: implement int vector add in C++
            throw new UnsupportedOperationException("INT32 add not yet implemented in backend")
          case other =>
            throw new UnsupportedOperationException(s"Unknown type: $other")
        
        MaterialTensor[T](leftMat.shape, leftMat.device, outData)
      
      case Of(Tensor(shape, device), Literal(value)) =>
        // Initialize tensor with a constant value
        val size = shape.product
        val sizeBytes = size * elementSize[T]
        val data = backend.allocTensor(sizeBytes)
        
        // Fill with the value
        summon[ClassTag[T]].runtimeClass.getSimpleName match
          case "FP32" =>
            val floatVal = value.asInstanceOf[Float]
            for i <- 0.until(size) do
              data.setAtIndex(ValueLayout.JAVA_FLOAT, i.toLong, floatVal)
          case "INT32" =>
            val intVal = value.asInstanceOf[Int]
            for i <- 0.until(size) do
              data.setAtIndex(ValueLayout.JAVA_INT, i.toLong, intVal)
          case other =>
            throw new UnsupportedOperationException(s"Unknown type: $other")
        
        MaterialTensor[T](shape, device, data)
      
      case Zero(Tensor(shape, device)) =>
        // Initialize tensor with zeros
        val size = shape.product
        val sizeBytes = size * elementSize[T]
        val data = backend.allocTensor(sizeBytes)
        
        // Memory from malloc may not be zeroed, so explicitly zero it
        summon[ClassTag[T]].runtimeClass.getSimpleName match
          case "FP32" =>
            for i <- 0.until(size) do
              data.setAtIndex(ValueLayout.JAVA_FLOAT, i.toLong, 0.0f)
          case "INT32" =>
            for i <- 0.until(size) do
              data.setAtIndex(ValueLayout.JAVA_INT, i.toLong, 0)
          case other =>
            throw new UnsupportedOperationException(s"Unknown type: $other")
        
        MaterialTensor[T](shape, device, data)
      
      case _ =>
        throw new UnsupportedOperationException(s"Cannot evaluate node: $node")
  
  /** Get element size in bytes for a DType */
  private def elementSize[T <: DType : ClassTag]: Int =
    summon[ClassTag[T]].runtimeClass.getSimpleName match
      case "FP32" => 4
      case "INT32" => 4
      case other => throw new UnsupportedOperationException(s"Unknown type: $other")
