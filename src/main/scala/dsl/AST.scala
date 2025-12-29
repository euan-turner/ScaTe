package dsl

import scala.collection.immutable.Vector
import scala.reflect.ClassTag
import scala.quoted.Type
import java.lang.foreign.{MemorySegment, ValueLayout}

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

/**
  * Mapping DSL types to Scala types
  */
trait ElementType[T <: DType]:
  type Elem
  def classTag: ClassTag[Elem]

object ElementType:
  given ElementType[DType.FP32] with
    type Elem = Float
    def classTag: ClassTag[Float] = summon[ClassTag[Float]]
  given ElementType[DType.INT32] with
    type Elem = Int
    def classTag: ClassTag[Int] = summon[ClassTag[Int]]

// TODO: Device type-safety as well?
enum Device:
  case CPU //, CUDA

sealed trait Node:
  def typeName: String

// Typed node for type-safe AST
sealed trait TypedNode[T <: DType] extends Node

// Symbolic node pending compilation and execution
sealed trait SymbolicTypedNode[T <: DType] extends TypedNode[T]
// Materialised node, backed by data
sealed trait MaterialTypedNode[T <: DType] extends TypedNode[T]

// Symbolic tensor in the graph
final case class Tensor[T <: DType : ClassTag](shape: Vector[Int], device: Device) extends SymbolicTypedNode[T]:
  val strides: Vector[Int] = shape.scanRight(1)(_ * _).tail
  private val ct = summon[ClassTag[T]]
  def typeName: String = ct.runtimeClass.getSimpleName
  override def toString: String = s"Tensor[$typeName]($shape, $device)"

object Tensor:
  // Factories are unary operator nodes
  // Infers DSL type from the Scala literal value
  def of[H](shape: Vector[Int], value: H, device: Device = Device.CPU)(using st: ScalaType[H], ct: ClassTag[st.DSL]): SymbolicTypedNode[st.DSL] =
    Of(Tensor[st.DSL](shape, device), Literal[st.DSL](value))

  def zero[T <: DType : ClassTag](shape: Vector[Int], device: Device = Device.CPU): SymbolicTypedNode[T] =
    Zero(Tensor(shape, device))

final case class MaterialTensor[T <: DType : ClassTag](shape: Vector[Int], device: Device, val data: MemorySegment) extends TypedNode[T]:
  val strides: Vector[Int] = shape.scanRight(1)(_ * _).tail
  private val ct = summon[ClassTag[T]]
  def typeName: String = ct.runtimeClass.getSimpleName
  override def toString: String = s"Tensor[$typeName]($shape, $device)"


sealed trait Operator[T <: DType] extends SymbolicTypedNode[T]

// Unary ops
sealed trait Unary[T <: DType] extends Operator[T]

// Initialisers
final case class Zero[T <: DType](x: TypedNode[T]) extends Unary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Zero[$typeName]($x)"

final case class Of[T <: DType](x: TypedNode[T], value: Literal[T]) extends Unary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Of[$typeName]($x, $value)"

// Binary ops
sealed trait Binary[T <: DType] extends Operator[T]

final case class Add[T <: DType](x: TypedNode[T], y: TypedNode[T]) extends Binary[T]:
  def typeName: String = x.typeName
  override def toString: String = s"Add[$typeName]($x, $y)"

// Literals of element type (to avoid raw Scala values)
final case class Literal[T <: DType : ClassTag](repr: Any) extends TypedNode[T]:
  private val ct = summon[ClassTag[T]]
  def typeName: String = ct.runtimeClass.getSimpleName
  override def toString: String = s"Literal[$typeName]($repr)"

// Future: explicit cast node from A to B
// final case class Cast[A <: DType, B <: DType](x: Node[A]) extends Node[B]

// Syntactic sugar for AST nodes
extension [T <: DType](left: TypedNode[T])
  def +(right: TypedNode[T]): TypedNode[T] = Add(left, right)

// Eval extension - triggers computation and returns MaterialTensor
extension [T <: DType : ClassTag](node: TypedNode[T])
  def eval(using backend: ffi.NativeBackend): MaterialTensor[T] =
    ffi.Evaluator.evaluate(node)

// Accessors - only available on MaterialTensor (compile-time safety)
extension [T <: DType](tensor: MaterialTensor[T])(using et: ElementType[T])
  /** Get element at the given indices */
  def get(indices: Int*): et.Elem =
    require(indices.length == tensor.shape.length, 
      s"Expected ${tensor.shape.length} indices, got ${indices.length}")
    // Bounds check
    indices.zip(tensor.shape).foreach { (idx, dim) =>
      require(idx >= 0 && idx < dim, s"Index $idx out of bounds for dimension $dim")
    }
    val flatIndex = indices.zip(tensor.strides).map((a, b) => a * b).sum
    if et.classTag == summon[ClassTag[Float]] then
      tensor.data.getAtIndex(ValueLayout.JAVA_FLOAT, flatIndex.toLong).asInstanceOf[et.Elem]
    else if et.classTag == summon[ClassTag[Int]] then
      tensor.data.getAtIndex(ValueLayout.JAVA_INT, flatIndex.toLong).asInstanceOf[et.Elem]
    else
      throw new UnsupportedOperationException(s"Unknown element type: ${et.classTag}")
  
  /** Convert entire tensor to a Scala array */
  def toArray: Array[et.Elem] =
    val size = tensor.shape.product
    given ClassTag[et.Elem] = et.classTag
    val arr = new Array[et.Elem](size)
    if et.classTag == summon[ClassTag[Float]] then
      for i <- 0.until(size) do
        arr(i) = tensor.data.getAtIndex(ValueLayout.JAVA_FLOAT, i.toLong).asInstanceOf[et.Elem]
    else if et.classTag == summon[ClassTag[Int]] then
      for i <- 0.until(size) do
        arr(i) = tensor.data.getAtIndex(ValueLayout.JAVA_INT, i.toLong).asInstanceOf[et.Elem]
    arr