package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

class DatabaseAdapterTests
{
  @Test
  def for_vendor : Unit =
  {
    assertEquals(classOf[DerbyDatabaseAdapter],
                 DatabaseAdapter.for_vendor(Derby, None).getClass)

    assertEquals(classOf[OracleDatabaseAdapter],
                 DatabaseAdapter.for_vendor(Oracle, None).getClass)

    assertEquals(classOf[PostgresqlDatabaseAdapter],
                 DatabaseAdapter.for_vendor(Postgresql, None).getClass)
  }

  @Test { val expected = classOf[java.lang.IllegalArgumentException] }
  def for_null_existent_driver_class : Unit =
  {
    DatabaseAdapter.for_vendor(null, None)
  }
}
