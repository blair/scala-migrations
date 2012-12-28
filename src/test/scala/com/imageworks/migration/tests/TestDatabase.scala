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

import com.imageworks.migration.{
  AutoCommit,
  ConnectionBuilder,
  DatabaseAdapter,
  DerbyDatabaseAdapter,
  MysqlDatabaseAdapter,
  With
}

import java.sql.{
  DriverManager,
  SQLException
}

/**
 * Sealed trait abstracting the database to use for testing.
 */
sealed trait TestDatabase {
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
    extends TestDatabase {
  // Username of the admin account, which will be the owner of the
  // database.
  private val admin_username = "admin"

  override def getAdminAccountName = admin_username

  // Password for the admin account.
  private val admin_password = "foobar"

  // Username of the user account.
  private val user_username = "user"

  override def getUserAccountName = user_username

  // Password for the user account.
  private val user_password = "baz"

  // The base JDBC URL.
  private val url = {
    "jdbc:derby:memory:" + System.currentTimeMillis.toString
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
  With.autoClosingConnection(DriverManager.getConnection(
    url + ";create=true",
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
    With.autoClosingConnection(DriverManager.getConnection(
      url + ";shutdown=true",
      admin_username,
      admin_password)) { _ =>
    }
  }
  catch {
    // For JDBC3 (JDK 1.5)
    case _: org.apache.derby.impl.jdbc.EmbedSQLException =>

    // For JDBC4 (JDK 1.6), a
    // java.sql.SQLNonTransientConnectionException is thrown, but this
    // exception class does not exist in JDK 1.5, so catch a
    // java.sql.SQLException instead.
    case _: SQLException =>
  }

  override def getSchemaName: String = {
    admin_username
  }

  override def getAdminConnectionBuilder: ConnectionBuilder = {
    new ConnectionBuilder(url, admin_username, admin_password)
  }

  override def getUserConnectionBuilder: ConnectionBuilder = {
    new ConnectionBuilder(url, user_username, user_password)
  }

  override def getDatabaseAdapter: DatabaseAdapter = {
    new DerbyDatabaseAdapter(Some(getSchemaName))
  }
}

/**
 * MySQL test database implementation.  Enabled when the Java
 * "scala-migrations.db.vendor" property is set to "mysql".
 *
 * Assumes the following setup:
 *
 * 1) A user named "test-admin" with password "test-admin" exists.
 * 2) The "test-admin" user owns a database named "test".
 * 3) The "test-admin" user has rights to grant other rights in the
 *    "test" database.
 * 4) A user named "test-user" with password "test-user" exists.
 * 5) The "test-user" has no rights to the "test" database.
 *
 * To override the defaults, set any of the following Java properties:
 *
 *   "scala-migrations.db.schema": database name to test with ("test")
 *   "scala-migrations.db.admin.name": admin user username ("test-admin")
 *   "scala-migrations.db.admin.passwd": admin user password ("test-admin")
 *   "scala-migrations.db.user.name": plain user username ("test-user")
 *   "scala-migrations.db.user.passwd": plain user password ("test-user")
 */
object MysqlTestDatabase
    extends TestDatabase {
  // Username of the admin account, which will be the owner of the
  // database.
  private val admin_username = {
    System.getProperty(TestDatabase.adminUserNameProperty, "test-admin")
  }

  override def getAdminAccountName = admin_username

  // Password for the admin account.
  private val admin_password = {
    System.getProperty(TestDatabase.adminUserPasswordProperty, "test-admin")
  }

  // Username of the user account.
  private val user_username = {
    System.getProperty(TestDatabase.userUserNameProperty, "test-user")
  }

  override def getUserAccountName = user_username

  // Password for the user account.
  private val user_password = {
    System.getProperty(TestDatabase.userUserPasswordProperty, "test-user")
  }

  override def getSchemaName: String = {
    System.getProperty(TestDatabase.schemaProperty, "test")
  }

  // The base JDBC URL.
  private val url = {
    "jdbc:mysql://localhost/" + getSchemaName
  }

  // Load the MySQL database driver.
  Class.forName("com.mysql.jdbc.Driver")

  override def getAdminConnectionBuilder: ConnectionBuilder = {
    new ConnectionBuilder(url, admin_username, admin_password)
  }

  override def getUserConnectionBuilder: ConnectionBuilder = {
    new ConnectionBuilder(url, user_username, user_password)
  }

  override def getDatabaseAdapter: DatabaseAdapter = {
    new MysqlDatabaseAdapter(Some(getSchemaName))
  }
}

/**
 * Object which builds the correct TestDatabase according to the
 * system property "scala-migrations.db.vendor", defaulting to Derby if
 * the property is not set.
 */
object TestDatabase
    extends TestDatabase {
  val adminUserNameProperty = "scala-migrations.db.admin.name"
  val adminUserPasswordProperty = "scala-migrations.db.admin.passwd"
  val schemaProperty = "scala-migrations.db.schema"
  val userUserNameProperty = "scala-migrations.db.user.name"
  val userUserPasswordProperty = "scala-migrations.db.user.passwd"
  val vendorProperty = "scala-migrations.db.vendor"

  private val db: TestDatabase = {
    System.getProperty(vendorProperty, "derby") match {
      case "derby" =>
        DerbyTestDatabase
      case "mysql" =>
        MysqlTestDatabase
      case v =>
        throw new RuntimeException("Unexpected value for \"" +
          vendorProperty +
          "\" property: " +
          v)
    }
  }

  override def getSchemaName = db.getSchemaName

  override def getAdminAccountName = db.getAdminAccountName

  override def getAdminConnectionBuilder = db.getAdminConnectionBuilder

  override def getUserAccountName = db.getUserAccountName

  override def getUserConnectionBuilder = db.getUserConnectionBuilder

  override def getDatabaseAdapter = db.getDatabaseAdapter

  def execute(connection_builder: ConnectionBuilder,
              sql: String): Boolean = {
    connection_builder.withConnection(AutoCommit) { c =>
      With.autoClosingStatement(c.prepareStatement(sql)) { s =>
        s.execute()
      }
    }
  }
}
