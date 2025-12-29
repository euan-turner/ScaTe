package dsl

import scala.collection.immutable.Vector
import scala.reflect.ClassTag

/**
  * DSL type hierarchy
  */
sealed trait DType
object DType:
  sealed trait FP32 extends DType
  sealed trait INT32 extends DType

/**
  * Mapping Scala types to DSL types
  */
trait ScalaType[H]:
  type DSL <: DType

object ScalaType:
  given ScalaType[Float] with
    type DSL = DType.FP32
  given ScalaType[Int] with
    type DSL = DType.INT32

export ScalaType.given

// TODO: Device type-safety as well?
enum Device:
  case CPU //, CUDA

sealed trait AnyNode:
  def typeName: String

// Typed node for type-safe AST
sealed trait Node[T <: DType] extends AnyNode

// Tensor literal in the graph
final case class Tensor[T <: DType : ClassTag](shape: Vector[Int], device: Device) extends Node[T]:
  val strides: Vector[Int] = shape.scanRight(1)(_ * _).tail
  private val ct = summon[ClassTag[T]]
  def typeName: String = ct.runtimeClass.getSimpleName
  override def toString: String = s"Tensor[$typeName]($shape, $device)"

object Tensor:
  // Factories are unary operator nodes
  // Infers DSL type from the Scala literal value
  def of[H](shape: Vector[Int], value: H, device: Device = Device.CPU)(using st: ScalaType[H], ct: ClassTag[st.DSL]): Node[st.DSL] =
    Of(Tensor[st.DSL](shape, device), Literal[st.DSL](value))

  def zero[T <: DType : ClassTag](shape: Vector[Int], device: Device = Device.CPU): Node[T] =
    Zero(Tensor(shape, device))

sealed trait Operator[T <: DType] extends Node[T]

// Unary ops
sealed trait Unary[T <: DType] extends Operator[T]

// Initialisers
final case class Zero[T <: DType](x: Node[T]) extends Unary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Zero[$typeName]($x)"

final case class Of[T <: DType](x: Node[T], value: Literal[T]) extends Unary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Of[$typeName]($x, $value)"

// Binary ops
sealed trait Binary[T <: DType] extends Operator[T]

final case class Add[T <: DType](x: Node[T], y: Node[T]) extends Binary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Add[$typeName]($x, $y)"

// Literals of element type (to avoid raw Scala values)
final case class Literal[T <: DType : ClassTag](repr: Any) extends Node[T]:
  private val ct = summon[ClassTag[T]]
  def typeName: String = ct.runtimeClass.getSimpleName
  override def toString: String = s"Literal[$typeName]($repr)"

// Future: explicit cast node from A to B
// final case class Cast[A <: DType, B <: DType](x: Node[A]) extends Node[B]

extension [T <: DType](left: Node[T])
  def +(right: Node[T]): Node[T] = Add(left, right)