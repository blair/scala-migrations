/*
 * Copyright (c) 2009 Sony Pictures Imageworks
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

class VendorTests
{
  @Test
  def for_driver : Unit =
  {
    assertSame(Oracle,
               Vendor.for_driver("oracle.jdbc.OracleDriver"))

    assertSame(Derby,
               Vendor.for_driver("org.apache.derby.jdbc.EmbeddedDriver"))

    assertSame(Derby,
               Vendor.for_driver(classOf[org.apache.derby.jdbc.EmbeddedDriver]))

    assertSame(Derby,
               Vendor.for_driver("org.apache.derby.jdbc.ClientDriver"))

    assertSame(Postgresql,
               Vendor.for_driver("org.postgresql.Driver"))
  }

  @Test { val expected = classOf[scala.MatchError] }
  def for_non_existent_driver : Unit =
  {
    Vendor.for_driver("no.such.driver")
  }

  @Test { val expected = classOf[scala.MatchError] }
  def for_non_driver_class : Unit =
  {
    Vendor.for_driver(classOf[java.lang.String])
  }

  @Test { val expected = classOf[java.lang.IllegalArgumentException] }
  def for_null_existent_driver_class : Unit =
  {
    Vendor.for_driver(null : Class[_])
  }

  @Test { val expected = classOf[java.lang.IllegalArgumentException] }
  def for_null_existent_driver_class_name: Unit =
  {
    Vendor.for_driver(null : String)
  }
}
