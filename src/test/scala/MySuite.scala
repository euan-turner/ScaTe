import scala.compiletime.testing.{typeChecks, typeCheckErrors}

class TypeSafetyTests extends munit.FunSuite:

  test("adding tensors of the same type compiles") {
    // This should compile: FP32 + FP32
    assert(typeChecks("""
      import dsl.*
      val t1 = Tensor.of(Vector(2, 3), 1.0f)
      val t2 = Tensor.zero[DType.FP32](Vector(2, 3))
      val t3 = t1 + t2
    """))
  }

  test("adding tensors of different types fails to compile") {
    // This should NOT compile: FP32 + INT32
    val errors = typeCheckErrors("""
      import dsl.*
      val t1 = Tensor.of(Vector(2, 3), 1.0f)
      val t2 = Tensor.zero[DType.INT32](Vector(2, 3))
      val t3 = t1 + t2
    """)
    assert(errors.nonEmpty, "Expected a type mismatch error")
    assert(
      errors.exists(_.message.contains("Required: dsl.Node[dsl.DType.FP32]")),
      s"Expected error about type mismatch, got: ${errors.map(_.message).mkString(", ")}"
    )
  }
