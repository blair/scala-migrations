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

import com.imageworks.migration._
import org.junit.Assert._
import org.junit.Test

class DatabaseAdapterTests {
  @Test
  def forVendor() {
    assertEquals(classOf[DerbyDatabaseAdapter],
      DatabaseAdapter.forVendor(Derby, None).getClass)

    assertEquals(classOf[MysqlDatabaseAdapter],
      DatabaseAdapter.forVendor(Mysql, None).getClass)

    assertEquals(classOf[OracleDatabaseAdapter],
      DatabaseAdapter.forVendor(Oracle, None).getClass)

    assertEquals(classOf[PostgresqlDatabaseAdapter],
      DatabaseAdapter.forVendor(Postgresql, None).getClass)

    assertEquals(classOf[H2DatabaseAdapter],
      DatabaseAdapter.forVendor(H2, None).getClass)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def forNullDriverClassThrows() {
    DatabaseAdapter.forVendor(null, None)
  }

  @Test
  def roundTrip() {
    for (vendor <- List(Derby, Mysql, Mariadb, Postgresql, H2, Oracle)) {
      val adapter = DatabaseAdapter.forVendor(vendor, None)
      assertSame(vendor, adapter.vendor)
    }
  }
}
