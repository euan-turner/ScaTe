package dsl

import scala.collection.immutable.Vector
import java.lang.foreign.MemorySegment

/**
  * Low-level IR for tensor operations.
  * This is a linear sequence of operations that can be executed by the backend.
  * The IR abstracts away the AST structure and provides a flat execution plan.
  */
enum TensorOp:
  /** Allocate a buffer with given ID, shape, and element type */
  case Alloc(bufferId: Int, shape: Vector[Int], dtype: String)
  
  /** Fill a buffer with a constant value */
  case Fill(bufferId: Int, value: Any)
  
  /** Copy data from a source MemorySegment to a buffer */
  case CopyFrom(bufferId: Int, sourceSegment: MemorySegment, size: Int)
  
  /** Element-wise vector addition: out = left + right */
  case VecAdd(outBufferId: Int, leftBufferId: Int, rightBufferId: Int, size: Int)

  override def toString: String = this match
    case Alloc(id, shape, dtype) => 
      s"  ALLOC   buf[$id] : $dtype${shape.mkString("[", ",", "]")}"
    case Fill(id, value) => 
      s"  FILL    buf[$id] <- $value"
    case CopyFrom(id, _, size) => 
      s"  COPY    buf[$id] <- external($size elements)"
    case VecAdd(out, left, right, size) => 
      s"  VECADD  buf[$out] <- buf[$left] + buf[$right] ($size elements)"

/**
  * A compiled program ready for execution.
  * Contains the linear sequence of ops and metadata about the output.
  */
case class CompiledProgram(
  ops: Vector[TensorOp],
  outputBufferId: Int,
  outputShape: Vector[Int],
  outputDType: String,
  bufferCount: Int
):
  def prettyPrint(): Unit =
    println("=== Compiled Program ===")
    println(s"Buffers: $bufferCount")
    println(s"Output: buf[$outputBufferId] : $outputDType${outputShape.mkString("[", ",", "]")}")
    println("Ops:")
    ops.foreach(op => println(op.toString))
    println("========================")
