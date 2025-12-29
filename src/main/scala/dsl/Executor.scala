package dsl

import ffi.NativeBackend
import java.lang.foreign.MemorySegment
import scala.collection.mutable
import scala.reflect.ClassTag
import scala.collection.immutable.Vector

/**
  * Executor that runs a compiled IR program using the native backend.
  * Manages buffer allocation and dispatches ops to FFI calls.
  */
object Executor:
  
  /**
    * Execute a compiled program and return the result as a MaterialTensor.
    * 
    * @param program The compiled IR program
    * @param backend The native backend for FFI calls
    * @return A MaterialTensor containing the computed result
    */
  def run[T <: DType : ClassTag](
    program: CompiledProgram
  )(using backend: NativeBackend): MaterialTensor[T] =
    
    // Map from buffer ID to allocated MemorySegment
    val buffers = mutable.Map[Int, MemorySegment]()
    
    // Track shapes for each buffer (needed for byte size calculation)
    val bufferShapes = mutable.Map[Int, Vector[Int]]()
    
    // Execute each op in sequence
    for op <- program.ops do
      op match
        case TensorOp.Alloc(bufferId, shape, dtype) =>
          val size = shape.product
          val elementSize = dtype match
            case "FP32" => 4
            case "INT32" => 4
            case _ => 4
          val sizeBytes = size.toLong * elementSize
          val segment = backend.allocTensor(sizeBytes)
          buffers(bufferId) = segment
          bufferShapes(bufferId) = shape
        
        case TensorOp.Fill(bufferId, value) =>
          val segment = buffers(bufferId)
          val size = bufferShapes(bufferId).product
          value match
            case f: Float => backend.fillFloat(segment, size, f)
            case i: Int => backend.fillInt(segment, size, i)
            case d: Double => backend.fillFloat(segment, size, d.toFloat)
            case _ => throw new UnsupportedOperationException(s"Unknown fill value type: ${value.getClass}")
        
        case TensorOp.CopyFrom(bufferId, sourceSegment, size) =>
          val destSegment = buffers(bufferId)
          backend.copy(destSegment, sourceSegment, size)
        
        case TensorOp.VecAdd(outId, leftId, rightId, size) =>
          val outSegment = buffers(outId)
          val leftSegment = buffers(leftId)
          val rightSegment = buffers(rightId)
          backend.vectorAdd(leftSegment, rightSegment, outSegment, size)
    
    // Extract the output buffer and wrap in MaterialTensor
    val outputSegment = buffers(program.outputBufferId)
    
    MaterialTensor[T](
      shape = program.outputShape,
      device = Device.CPU,
      data = outputSegment
    )
