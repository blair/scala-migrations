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
