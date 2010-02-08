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
  def forDriver(driver_class_name: String): Vendor =
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
  def forDriver(driver_class: Class[_]): Vendor =
  {
    if (driver_class eq null) {
      val message = "Must pass a non-null JDBC driver class to this function."
      throw new java.lang.IllegalArgumentException(message)
    }
    else {
      forDriver(driver_class.getName)
    }
  }
}
