package com.imageworks.migration

/**
 * Base sealed trait for the objects that refer to different
 * databases.
 */
sealed trait Vendor

case object Derby
  extends Vendor
case object Oracle
  extends Vendor
case object Postgresql
  extends Vendor

object Vendor
{
  /**
   * Return the database vendor for the given database driver class
   * name.
   *
   * @param driver_class_name the class name of the JDBC database
   *        driver
   * @return the corresponding Vendor object for the database
   * @throws java.lang.IllegalArgumentException if the argument is
   *         null, scala.MatchError if an appropriate vendor cannot be
   *         found
   */
  def for_driver(driver_class_name : String) : Vendor =
  {
    driver_class_name match {
      case "oracle.jdbc.driver.OracleDriver" =>
        Oracle

      case "oracle.jdbc.OracleDriver" =>
        Oracle

      case "org.apache.derby.jdbc.EmbeddedDriver" =>
        Derby

      case "org.apache.derby.jdbc.ClientDriver" =>
        Derby

      case "org.postgresql.Driver" =>
        Postgresql

      case null =>
        throw new java.lang.IllegalArgumentException("Must pass a non-null " +
                                                     "JDBC driver class " +
                                                     "name to this function.")

      case _ =>
        throw new scala.MatchError("No vendor can be found for the JDBC " +
                                   "driver class '" +
                                   driver_class_name +
                                   "'.'")
    }
  }

  /**
   * Return the database vendor for the given database driver class.
   *
   * @param driver_class the class of the JDBC database driver
   * @return the corresponding Vendor object for the database
   * @throws java.lang.IllegalArgumentException if the argument is
   *         null, scala.MatchError if an appropriate vendor cannot be
   *         found
   */
  def for_driver(driver_class : Class[_]) : Vendor =
  {
    if (driver_class eq null) {
      val message = "Must pass a non-null JDBC driver class to this function."
      throw new java.lang.IllegalArgumentException(message)
    }
    else {
      for_driver(driver_class.getName)
    }
  }
}
