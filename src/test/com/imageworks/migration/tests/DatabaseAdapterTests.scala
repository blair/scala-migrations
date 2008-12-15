package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

class DatabaseAdapterTests
{
  @Test
  def test_for_driver : Unit =
  {
    assertEquals(classOf[OracleDatabaseAdapter],
                 DatabaseAdapter.for_driver("oracle.jdbc.driver.OracleDriver",
                                            None).getClass)

    assertEquals(classOf[DerbyDatabaseAdapter],
                 DatabaseAdapter.for_driver("org.apache.derby.jdbc.EmbeddedDriver",
                                            None).getClass)

    assertEquals(classOf[DerbyDatabaseAdapter],
                 DatabaseAdapter.for_driver(classOf[org.apache.derby.jdbc.EmbeddedDriver],
                                            None).getClass)

    assertEquals(classOf[DerbyDatabaseAdapter],
                 DatabaseAdapter.for_driver("org.apache.derby.jdbc.ClientDriver",
                                            None).getClass)
  }

  @Test { val expected = classOf[scala.MatchError] }
  def test_for_non_existent_driver : Unit =
  {
    DatabaseAdapter.for_driver("no.such.driver", None)
  }

  @Test { val expected = classOf[scala.MatchError] }
  def test_for_non_driver_class : Unit =
  {
    DatabaseAdapter.for_driver(classOf[java.lang.String], None)
  }

  @Test { val expected = classOf[java.lang.IllegalArgumentException] }
  def test_for_null_existent_driver_class : Unit =
  {
    DatabaseAdapter.for_driver(null : Class[_], None)
  }

  @Test { val expected = classOf[java.lang.IllegalArgumentException] }
  def test_for_null_existent_driver_class_name: Unit =
  {
    DatabaseAdapter.for_driver(null : String, None)
  }

}
