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

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import org.jmock.{Expectations,
                  Mockery}

import com.imageworks.migration.{AutoCommit,
                                 DerbyDatabaseAdapter,
                                 DuplicateMigrationDescriptionException,
                                 DuplicateMigrationVersionException,
                                 InstallAllMigrations,
                                 MigrateToVersion,
                                 Migration,
                                 Migrator,
                                 RemoveAllMigrations,
                                 RollbackMigration,
                                 With}

import java.sql.{DriverManager,
                 ResultSet}

class MigrationTests
{
  private
  val context = new Mockery

  // Set the Derby system home to a test-databases directory so the
  // derby.log file and all databases will be placed in there.
  System.getProperties.setProperty("derby.system.home", "test-databases")

  // Load the Derby database driver.
  Class.forName("org.apache.derby.jdbc.EmbeddedDriver")

  private
  var migrator: Migrator = _

  private
  var url: String = _

  @Before
  def set_up(): Unit =
  {
    val db_name = System.currentTimeMillis.toString
    url = "jdbc:derby:" + db_name

    val url_ = url + ";create=true"

    // The default schema for a Derby database is "APP".
    migrator = new Migrator(url_, new DerbyDatabaseAdapter(Some("APP")))
  }

  @Test(expected=classOf[DuplicateMigrationDescriptionException])
  def duplicate_descriptions_throw_exception: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_descriptions",
                     false)
  }

  @Test(expected=classOf[DuplicateMigrationVersionException])
  def duplicate_versions_throw_exception: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_versions",
                     false)
  }

  @Test(expected=classOf[IllegalArgumentException])
  def scale_without_precision: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.scale_without_precision",
                     false)
  }

  @Test
  def migrate_up_and_down: Unit =
  {
    // There should be no tables in the schema initially.
    assertEquals(0, migrator.getTableNames.size)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations table now.
    assertEquals(1, migrator.getTableNames.size)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "location").isDefined)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.up_and_down",
                                       false).isDefined)

    val statuses1 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.up_and_down",
                                    false)

    assertEquals(2, statuses1.not_installed.size)
    assertTrue(statuses1.not_installed.contains(20081118201000L))
    assertTrue(statuses1.not_installed.contains(20081118201742L))
    assertEquals(0, statuses1.installed_with_available_implementation.size)
    assertEquals(0, statuses1.installed_without_available_implementation.size)

    // Apply all the migrations.
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    assertEquals(3, migrator.getTableNames.size)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "location").isDefined)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should be completely migrated.
    assertFalse(migrator.whyNotMigrated("com.imageworks.migration.tests.up_and_down",
                                        false).isDefined)

    // With a empty set of migrations the database should not be
    // completely migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.no_migrations",
                                       true).isDefined)

    val statuses2 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.up_and_down",
                                    false)

    assertEquals(0, statuses2.not_installed.size)
    assertEquals(2, statuses2.installed_with_available_implementation.size)
    assertTrue(statuses2.installed_with_available_implementation.contains(20081118201000L))
    assertTrue(statuses2.installed_with_available_implementation.contains(20081118201742L))
    assertEquals(0, statuses2.installed_without_available_implementation.size)

    // Rollback a single migration.
    migrator.migrate(RollbackMigration(1),
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations and location tables
    // now.
    assertEquals(2, migrator.getTableNames.size)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "location").isDefined)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.up_and_down",
                                       false).isDefined)

    // With a empty set of migrations the database should not be
    // completely migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.no_migrations",
                                       true).isDefined)

    val statuses3 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.up_and_down",
                                    false)

    assertEquals(1, statuses3.not_installed.size)
    assertTrue(statuses3.not_installed.contains(20081118201742L))
    assertEquals(1, statuses3.installed_with_available_implementation.size)
    assertTrue(statuses3.installed_with_available_implementation.contains(20081118201000L))
    assertEquals(0, statuses3.installed_without_available_implementation.size)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations table now.
    assertEquals(1, migrator.getTableNames.size)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.up_and_down",
                                       false).isDefined)

    val statuses4 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.up_and_down",
                                    false)

    assertEquals(2, statuses4.not_installed.size)
    assertTrue(statuses4.not_installed.contains(20081118201000L))
    assertTrue(statuses4.not_installed.contains(20081118201742L))
    assertEquals(0, statuses4.installed_with_available_implementation.size)
    assertEquals(0, statuses4.installed_without_available_implementation.size)
  }

  @Test
  def get_migration_statuses_does_not_create_schema_migrations: Unit =
  {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    val statuses1 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.no_migrations",
                                    false)

    // Calling getMigrationStatuses() should not have created any
    // tables.
    assertEquals(0, migrator.getTableNames.size)

    assertEquals(0, statuses1.not_installed.size)
    assertEquals(0, statuses1.installed_with_available_implementation.size)
    assertEquals(0, statuses1.installed_without_available_implementation.size)

    // In a brand new database with available migrations, the database
    // should not be migrated.
    val statuses2 =
      migrator.getMigrationStatuses("com.imageworks.migration.tests.up_and_down",
                                    false)

    assertEquals(2, statuses2.not_installed.size)
    assertTrue(statuses2.not_installed.contains(20081118201000L))
    assertTrue(statuses2.not_installed.contains(20081118201742L))
    assertEquals(0, statuses2.installed_with_available_implementation.size)
    assertEquals(0, statuses2.installed_without_available_implementation.size)

    // Calling getMigrationStatuses() should not have created any
    // tables.
    assertEquals(0, migrator.getTableNames.size)
  }

  @Test
  def why_not_migrated_does_not_create_schema_migrations: Unit =
  {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    // In a brand new database with no available migrations, the
    // database should not be completely migrated.  The
    // com.imageworks.migration.tests.no_migrations package contains
    // no concrete Migration subclasses.
    assertFalse(migrator.whyNotMigrated("com.imageworks.migration.tests.no_migrations",
                                          false).isDefined)

    // Running whyNotMigrated() should not have created any tables.
    assertEquals(0, migrator.getTableNames.size)

    // In a brand new database with available migrations, the database
    // should not be migrated.
    assertTrue(migrator.whyNotMigrated("com.imageworks.migration.tests.up_and_down",
                                       false).isDefined)

    // Running whyNotMigrated() should not have created any tables.
    assertEquals(0, migrator.getTableNames.size)
  }

  @Test
  def grant_and_revoke: Unit =
  {
    // create a second user, make a table
    migrator.migrate(MigrateToVersion(200811241940L),
                     "com.imageworks.migration.tests.grant_and_revoke",
                     false)

    // "Reboot" database for database property changes to take effect by
    // shutting down the database.  Connection shuts the database down,
    // but also throws an exception.
    try {
      DriverManager.getConnection(url + ";shutdown=true")
    }
    catch {
      // For JDBC3 (JDK 1.5)
      case e: org.apache.derby.impl.jdbc.EmbedSQLException =>

      // For JDBC4 (JDK 1.6), a
      // java.sql.SQLNonTransientConnectionException is
      // thrown, but this exception class does not exist in JDK 1.5,
      // so catch a java.sql.SQLException instead.
      case e: java.sql.SQLException =>
    }

    // new connection with test user
    val test_migrator = new Migrator(url,
                                     "test",
                                     "password",
                                     new DerbyDatabaseAdapter(Some("APP")))

    val select_sql = "SELECT name FROM APP.location"

    def run_select: Unit =
    {
      test_migrator.withLoggingConnection(AutoCommit) { connection =>
        val statement = connection.prepareStatement(select_sql)
        val rs = statement.executeQuery
        rs.close()
        statement.close()
      }
    }

    // try to select table, should give a permissions error
    try {
      run_select

      // failure if got here
      fail("SELECT permission failure expected")
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compaitibility, only a
      // java.sql.SQLException is caught.
      case e: java.sql.SQLException => // expected
    }

    // new connection with APP user
    val migrator2 = new Migrator(url,
                                 "APP",
                                 "password",
                                 new DerbyDatabaseAdapter(Some("APP")))

    // perform grants
    migrator2.migrate(MigrateToVersion(200811261513L),
                      "com.imageworks.migration.tests.grant_and_revoke",
                      false)

    // try to select table, should succeed now that grant has been given
    try {
      run_select
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compaitibility, only a
      // java.sql.SQLException is caught.
      case e: java.sql.SQLException =>
        // failure if got here
        fail("SELECT permission failure unexpected")
    }

    // preform revoke
    migrator2.migrate(RollbackMigration(1),
                      "com.imageworks.migration.tests.grant_and_revoke",
                      false)

    // try to select table, should give a permissions error again
    try {
      run_select

      // failure if got here
      fail("SELECT permission failure expected")
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compaitibility, only a
      // java.sql.SQLException is caught.
      case e: java.sql.SQLException => // expected
    }
  }

  @Test
  def columns_can_hold_types: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.types",
                     false)

    val varbinary_array = (1 to 4).map(_.toByte).toArray
    val now = System.currentTimeMillis

    migrator.withLoggingConnection(AutoCommit) { connection =>
      for ((n, v) <- Array(("bigint_column", java.lang.Long.MIN_VALUE),
                           ("bigint_column", java.lang.Long.MAX_VALUE),
                           ("char_column", "ABCD"),
                           ("decimal_column", 3.14),
                           ("integer_column", java.lang.Integer.MIN_VALUE),
                           ("integer_column", java.lang.Integer.MAX_VALUE),
                           ("timestamp_column", new java.sql.Date(now)),
                           ("varbinary_column", varbinary_array),
                           ("varchar_column", "ABCD"))) {
        val insert_sql = "INSERT INTO types_test (" + n + ") VALUES (?)"
        val insert_statement = connection.prepareStatement(insert_sql)
        insert_statement.setObject(1, v)
        insert_statement.executeUpdate
        insert_statement.close()

        // Make sure that the value exists.
        val select_sql = "SELECT COUNT(1) from types_test where " + n + " = ?"
        val select_statement = connection.prepareStatement(select_sql)
        select_statement.setObject(1, v)
        With.resultSet(select_statement.executeQuery()) { rs =>
          var counts: List[Int] = Nil
          while (rs.next()) {
            counts = rs.getInt(1) :: counts
          }

          assertEquals(1, counts.size)
          assertEquals(1, counts.head)
        }
      }
    }
  }

  @Test
  def with_result_set_closes_on_normal_return: Unit =
  {
    val mock_rs = context.mock(classOf[ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1: ResultSet = null

    val m = new Migration {
              override
              def up(): Unit =
              {
                withResultSet(mock_rs) { rs2 =>
                  rs1 = rs2
                }
              }

              override
              def down(): Unit =
              {
              }
            }

    m.up()

    context.assertIsSatisfied()

    assertSame(mock_rs, rs1)
  }

  @Test
  def with_result_set_closes_on_throw: Unit =
  {
    val mock_rs = context.mock(classOf[ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1: ResultSet = null

    class ThisSpecialException
      extends java.lang.Throwable

    val m = new Migration {
              override
              def up(): Unit =
              {
                withResultSet(mock_rs) { rs2 =>
                  rs1 = rs2
                  throw new ThisSpecialException
                }
              }

              override
              def down(): Unit =
              {
              }
            }

    try {
      m.up()
    }
    catch {
      case _: ThisSpecialException =>
    }

    context.assertIsSatisfied()

    assertSame(mock_rs, rs1)
  }
}
