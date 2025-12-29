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

  val fillFloatHandle: MethodHandle = find("tensor_fill_float",
    FunctionDescriptor.ofVoid(
      ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_FLOAT
    ))

  val fillIntHandle: MethodHandle = find("tensor_fill_int",
    FunctionDescriptor.ofVoid(
      ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT
    ))

  val copyHandle: MethodHandle = find("tensor_copy",
    FunctionDescriptor.ofVoid(
      ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT
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

  /** Fill a buffer with a float value */
  def fillFloat(ptr: MemorySegment, n: Int, value: Float): Unit =
    fillFloatHandle.invoke(ptr, n, value)

  /** Fill a buffer with an int value */
  def fillInt(ptr: MemorySegment, n: Int, value: Int): Unit =
    fillIntHandle.invoke(ptr, n, value)

  /** Copy data between memory segments */
  def copy(dst: MemorySegment, src: MemorySegment, n: Int): Unit =
    copyHandle.invoke(dst, src, n)

object NativeBackend:
  /** Default CPU backend - lazily initialized */
  lazy val cpu: NativeBackend = new NativeBackend("lib/libcpu.so")
