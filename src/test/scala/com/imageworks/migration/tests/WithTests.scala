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

import com.imageworks.migration.With

import org.jmock.{Expectations,
                  Mockery}

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import java.sql.{ResultSet,
                 SQLException}

class WithTests
{
  private
  val context = new Mockery

  @Test
  def with_result_set_closes_on_normal_return {
    val mock_rs = context.mock(classOf[ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1: ResultSet = null

    val result = With.resultSet(mock_rs) { rs2 =>
                   rs1 = rs2
                   "foobar"
                 }

    assertSame(mock_rs, rs1)
    assertEquals("foobar", result)
    context.assertIsSatisfied()
  }

  @Test
  def with_result_set_closes_on_throw {
    val mock_rs = context.mock(classOf[ResultSet])

    val e1 = new RuntimeException
    val e2 = new SQLException

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                       will(Expectations.throwException(e2))
                     })

    var caughtExceptionOpt: Option[Throwable] = None
    var rs1: ResultSet = null

    try {
      With.resultSet(mock_rs) { rs2 =>
        rs1 = rs2
        throw e1
      }
    }
    catch {
      case e => caughtExceptionOpt = Some(e)
    }

    assertSame(mock_rs, rs1)
    assertTrue("Failed to catch exception.", caughtExceptionOpt.isDefined)
    assertSame("Failed to catch expected exception.",
               e1, caughtExceptionOpt.get)
    context.assertIsSatisfied()
  }
}
