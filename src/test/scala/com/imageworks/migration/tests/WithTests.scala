/*
 * Copyright (c) 2009 Sony Pictures Imageworks Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer.
 * Redistributions in binary form must reproduce the above copyright
 * notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the
 * distribution.  Neither the name of Sony Pictures Imageworks nor the
 * names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import org.jmock.{Expectations,
                  Mockery}

import com.imageworks.migration.With

class WithTests
{
  private
  val context = new Mockery

  @Test
  def with_result_set_closes_on_normal_return: Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1: java.sql.ResultSet = null

    val result = With.resultSet(mock_rs) { rs2 =>
                   rs1 = rs2
                   "foobar"
                 }

    assertSame(mock_rs, rs1)
    assertEquals("foobar", result)
    context.assertIsSatisfied()
  }

  @Test
  def with_result_set_closes_on_throw: Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    class ThisSpecialException
      extends java.lang.Throwable

    var rs1: java.sql.ResultSet = null

    try {
      With.resultSet(mock_rs) { rs2 =>
        rs1 = rs2
        throw new ThisSpecialException
      }
    }
    catch {
      case _: ThisSpecialException =>
    }

    assertSame(mock_rs, rs1)
    context.assertIsSatisfied()
  }
}
