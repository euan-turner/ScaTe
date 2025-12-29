package dsl

import scala.collection.mutable
import scala.reflect.ClassTag
import scala.collection.immutable.Vector

/**
  * Compiler that lowers the AST to linear IR.
  * Performs a post-order traversal of the AST, assigning buffer IDs
  * to each intermediate result.
  */
object Compiler:
  
  /**
    * Compile an AST node to a linear IR program.
    * 
    * @param node The root of the AST to compile
    * @return A CompiledProgram ready for execution
    */
  def compile[T <: DType : ClassTag](node: TypedNode[T], debug: Boolean = false): CompiledProgram =
    val ops = mutable.ArrayBuffer[TensorOp]()
    var nextBufferId = 0
    
    def freshBufferId(): Int =
      val id = nextBufferId
      nextBufferId += 1
      id
    
    /**
      * Lower a node to IR, returning the buffer ID holding the result.
      * This is a post-order traversal: children are lowered before parents.
      */
    def lower(n: TypedNode[?]): (Int, Vector[Int]) = n match
      case mt: MaterialTensor[?] =>
        // Already materialized - copy from existing memory
        val bufferId = freshBufferId()
        val size = mt.shape.product
        ops += TensorOp.Alloc(bufferId, mt.shape, mt.typeName)
        ops += TensorOp.CopyFrom(bufferId, mt.data, size)
        (bufferId, mt.shape)
      
      case Tensor(shape, device) =>
        // Bare tensor - just allocate (will be filled by parent op)
        val bufferId = freshBufferId()
        val ct = summon[ClassTag[T]]
        ops += TensorOp.Alloc(bufferId, shape, ct.runtimeClass.getSimpleName)
        (bufferId, shape)
      
      case Of(inner, Literal(value)) =>
        // Initialize with constant value
        val (bufferId, shape) = lower(inner)
        ops += TensorOp.Fill(bufferId, value)
        (bufferId, shape)
      
      case Zero(inner) =>
        // Initialize with zeros
        val (bufferId, shape) = lower(inner)
        // Fill with appropriate zero based on type
        val zeroValue: Any = inner.typeName match
          case "FP32" => 0.0f
          case "INT32" => 0
          case _ => 0.0f
        ops += TensorOp.Fill(bufferId, zeroValue)
        (bufferId, shape)
      
      case Add(x, y) =>
        // Binary op: lower both operands, then add
        val (leftId, leftShape) = lower(x)
        val (rightId, rightShape) = lower(y)
        
        require(leftShape == rightShape, 
          s"Shape mismatch in Add: $leftShape vs $rightShape")
        
        val outBufferId = freshBufferId()
        val size = leftShape.product
        ops += TensorOp.Alloc(outBufferId, leftShape, x.typeName)
        ops += TensorOp.VecAdd(outBufferId, leftId, rightId, size)
        (outBufferId, leftShape)
      
      case Literal(_) =>
        throw new IllegalArgumentException("Literal cannot be lowered directly - should be part of Of node")
    
    // Lower the entire AST
    val (outputId, outputShape) = lower(node)
    
    val program = CompiledProgram(
      ops = ops.toVector,
      outputBufferId = outputId,
      outputShape = outputShape,
      outputDType = node.typeName,
      bufferCount = nextBufferId
    )
    
    if debug then
      program.prettyPrint()
    
    program
