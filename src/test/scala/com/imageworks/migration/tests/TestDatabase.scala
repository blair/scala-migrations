/*
 * Copyright (c) 2011 Sony Pictures Imageworks Inc.
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

import com.imageworks.migration.{AutoCommit,
                                 ConnectionBuilder,
                                 DatabaseAdapter,
                                 DerbyDatabaseAdapter,
                                 With}

import java.sql.DriverManager

/**
 * Sealed trait abstracting the database to use for testing.
 */
sealed trait TestDatabase
{
  /**
   * Get the schema name the tests are being run in.
   */
  def getSchemaName: String

  /**
   * Get the username of the admin account.
   */
  def getAdminAccountName: String

  /**
   * Get a connection builder that builds connections with access to
   * the entire schema.
   */
  def getAdminConnectionBuilder: ConnectionBuilder

  /**
   * Get the username of the user account.
   */
  def getUserAccountName: String

  /**
   * Get a connection builder that builds connections that connect as
   * a user with restricted privileges.
   */
  def getUserConnectionBuilder: ConnectionBuilder

  /**
   * The DatabaseAdapter to use for the test database.
   */
  def getDatabaseAdapter: DatabaseAdapter
}

/**
 * Derby test database implementation.
 */
object DerbyTestDatabase
  extends TestDatabase
{
  // Username of the admin account, which will be the owner of the
  // database.
  private
  val admin_username = "admin"

  override
  def getAdminAccountName = admin_username

  // Password for the admin account.
  private
  val admin_password = "foobar"

  // Username of the user account.
  private
  val user_username = "user"

  override
  def getUserAccountName = user_username

  // Password for the user account.
  private
  val user_password = "baz"

  // The base JDBC URL.
  private
  val url =
  {
    "jdbc:derby:" + System.currentTimeMillis.toString
  }

  // Set the Derby system home directory to "target/test-databases" so
  // the derby.log file and all databases will be placed in there.
  System.getProperties.setProperty("derby.system.home",
                                   "target/test-databases")

  // Load the Derby database driver.
  Class.forName("org.apache.derby.jdbc.EmbeddedDriver")

  // Create the database,  set it up for connection and SQL
  // authorization and then shut it down, so the next connection will
  // "boot" it with connection and SQL authorizations enabled.

  // Create the database.
  With.connection(DriverManager.getConnection(url + ";create=true",
                                              admin_username,
                                              admin_password)) { c =>
    TestDatabase.execute(
      getAdminConnectionBuilder,
      """CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
           'derby.connection.requireAuthentication', 'true')""")

    // Setting this property cannot be undone.  See
    // http://db.apache.org/derby/docs/10.7/ref/rrefpropersqlauth.html .
    TestDatabase.execute(
      getAdminConnectionBuilder,
      """CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
           'derby.database.sqlAuthorization', 'true')""")

    TestDatabase.execute(
      getAdminConnectionBuilder,
      """CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
           'derby.authentication.provider', 'BUILTIN')""")

    TestDatabase.execute(
      getAdminConnectionBuilder,
      """CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
           'derby.user.""" + admin_username + """', '""" + admin_password + """')""")

    TestDatabase.execute(
      getAdminConnectionBuilder,
        """CALL SYSCS_UTIL.SYSCS_SET_DATABASE_PROPERTY(
             'derby.user.""" + user_username + """', '""" + user_password + """')""")
  }

  // Shutdown Derby.
  try {
    With.connection(DriverManager.getConnection(url + ";shutdown=true",
                                                admin_username,
                                                admin_password)) { _ =>
    }
  }
  catch {
    // For JDBC3 (JDK 1.5)
    case e: org.apache.derby.impl.jdbc.EmbedSQLException =>

    // For JDBC4 (JDK 1.6), a
    // java.sql.SQLNonTransientConnectionException is thrown, but this
    // exception class does not exist in JDK 1.5, so catch a
    // java.sql.SQLException instead.
    case e: java.sql.SQLException =>
  }

  override
  def getSchemaName: String =
  {
    admin_username
  }

  override
  def getAdminConnectionBuilder: ConnectionBuilder =
  {
    new ConnectionBuilder(url, admin_username, admin_password)
  }

  override
  def getUserConnectionBuilder: ConnectionBuilder =
  {
    new ConnectionBuilder(url, user_username, user_password)
  }

  override
  def getDatabaseAdapter: DatabaseAdapter =
  {
    new DerbyDatabaseAdapter(Some(getSchemaName))
  }
}

/**
 * Object which builds the correct TestDatabase according to the
 * system property "scala-migrations.db.vendor", defaulting to Derby if
 * the property is not set.
 */
object TestDatabase
  extends TestDatabase
{
  private
  val db: TestDatabase =
  {
    System.getProperty("scala-migrations.db.vendor", "derby") match {
      case "derby" => {
        DerbyTestDatabase
      }
      case v => {
        val m = "Unexpected value for scala-migrations.db.vendor property: " +
                v
        throw new RuntimeException(m)
      }
    }
  }

  override def getSchemaName = db.getSchemaName

  override def getAdminAccountName = db.getAdminAccountName

  override def getAdminConnectionBuilder = db.getAdminConnectionBuilder

  override def getUserAccountName = db.getUserAccountName

  override def getUserConnectionBuilder = db.getUserConnectionBuilder

  override def getDatabaseAdapter = db.getDatabaseAdapter

  def execute(connection_builder: ConnectionBuilder,
              sql: String): Boolean =
  {
    connection_builder.withConnection(AutoCommit) { c =>
      With.statement(c.prepareStatement(sql)) { s =>
        s.execute()
      }
    }
  }
}
