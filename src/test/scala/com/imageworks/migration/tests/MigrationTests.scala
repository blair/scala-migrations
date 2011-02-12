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

import com.imageworks.migration.{AutoCommit,
                                 DuplicateMigrationDescriptionException,
                                 DuplicateMigrationVersionException,
                                 InstallAllMigrations,
                                 MigrateToVersion,
                                 Migration,
                                 Migrator,
                                 RemoveAllMigrations,
                                 RollbackMigration,
                                 With}

import org.jmock.{Expectations,
                  Mockery}

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import java.sql.{DriverManager,
                 ResultSet}

class MigrationTests
{
  private
  val context = new Mockery

  private
  var migrator: Migrator = _

  @Before
  def set_up(): Unit =
  {
    val connection_builder = TestDatabase.getAdminConnectionBuilder
    val database_adapter = TestDatabase.getDatabaseAdapter

    migrator = new Migrator(connection_builder, database_adapter)

    connection_builder.withConnection(AutoCommit) { c =>
      for (table_name <- migrator.getTableNames) {
        val tn = table_name.toLowerCase
        if (tn == "schema_migrations" || tn.startsWith("scala_migrations_")) {
          val sql = "DROP TABLE " + database_adapter.quoteTableName(tn)
          With.statement(c.prepareStatement(sql)) { _.execute }
        }
      }
    }
  }

  @Test { val expected = classOf[DuplicateMigrationDescriptionException] }
  def duplicate_descriptions_throw_exception: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_descriptions",
                     false)
  }

  @Test { val expected = classOf[DuplicateMigrationVersionException] }
  def duplicate_versions_throw_exception: Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_versions",
                     false)
  }

  @Test { val expected = classOf[IllegalArgumentException] }
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
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
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
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
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

    // There should only be the schema migrations and
    // scala_migrations_location tables now.
    assertEquals(2, migrator.getTableNames.size)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
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
    val connection_builder = TestDatabase.getUserConnectionBuilder
    val database_adapter = TestDatabase.getDatabaseAdapter

    // Make a table, migrate with admin account.
    migrator.migrate(MigrateToVersion(200811241940L),
                     "com.imageworks.migration.tests.grant_and_revoke",
                     false)

    // New connection with user account.
    val test_migrator = new Migrator(connection_builder, database_adapter)

    val select_sql =
      "SELECT name FROM " +
      database_adapter.quoteTableName("scala_migrations_location")

    def run_select: Unit =
    {
      test_migrator.withLoggingConnection(AutoCommit) { connection =>
        With.statement(connection.prepareStatement(select_sql)) { statement =>
          With.resultSet(statement.executeQuery()) { rs => }
        }
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

    // perform grants
    migrator.migrate(MigrateToVersion(200811261513L),
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
        fail("SELECT permission failure unexpected: " + e)
    }

    // preform revoke
    migrator.migrate(RollbackMigration(1),
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
        val insert_sql = """INSERT INTO
                              scala_migrations_types_test (""" + n + """)
                            VALUES
                              (?)""".replaceAll("\\s+", " ")
        val insert_statement = connection.prepareStatement(insert_sql)
        insert_statement.setObject(1, v)
        insert_statement.executeUpdate
        insert_statement.close()

        // Make sure that the value exists.
        val select_sql = """SELECT
                              COUNT(1)
                            FROM
                              scala_migrations_types_test
                            WHERE
                              """ + n + """ = ?""".replaceAll("\\s+", " ")
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
