package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import org.jmock.{Expectations,
                  Mockery}

class WithTests
{
  private
  val context = new Mockery

  @Test
  def with_result_set_closes_on_normal_return : Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1 : java.sql.ResultSet = null

    val result = With.result_set(mock_rs) { rs2 =>
                   rs1 = rs2
                   "foobar"
                 }

    assertSame(mock_rs, rs1)
    assertEquals("foobar", result)
    context.assertIsSatisfied()
  }

  @Test
  def with_result_set_closes_on_throw : Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    class ThisSpecialException
      extends java.lang.Throwable

    var rs1 : java.sql.ResultSet = null

    try {
      With.result_set(mock_rs) { rs2 =>
        rs1 = rs2
        throw new ThisSpecialException
      }
    }
    catch {
      case _ : ThisSpecialException =>
    }

    assertSame(mock_rs, rs1)
    context.assertIsSatisfied()
  }
}
