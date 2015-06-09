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

import org.jmock.{
  Expectations,
  Mockery
}

import org.junit.Assert._
import org.junit.{
  Before,
  Test
}

import java.sql.{
  ResultSet,
  SQLException
}

class MigrationTests {
  private val context = new Mockery

  private var migrator: Migrator = _

  @Before
  def setUp() {
    val connectionBuilder = TestDatabase.getAdminConnectionBuilder
    val databaseAdapter = TestDatabase.getDatabaseAdapter

    migrator = new Migrator(connectionBuilder, databaseAdapter)

    connectionBuilder.withConnection(AutoCommit) { c =>
      for (tableName <- migrator.getTableNames) {
        val tn = tableName.toLowerCase
        if (tn == "schema_migrations" || tn.startsWith("scala_migrations_")) {
          val sql = "DROP TABLE " + databaseAdapter.quoteTableName(tn)
          With.autoClosingStatement(c.prepareStatement(sql)) { _.execute() }
        }
      }
    }
  }

  @Test(expected = classOf[DuplicateMigrationDescriptionException])
  def duplicateDescriptionsThrows() {
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.duplicate_descriptions",
      false)
  }

  @Test(expected = classOf[DuplicateMigrationVersionException])
  def duplicateVersionsThrows() {
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.duplicate_versions",
      false)
  }

  @Test
  def vendor() {
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.vendor",
      false)
  }

  @Test(expected = classOf[IllegalArgumentException])
  def scaleWithoutPrecisionThrows() {
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.scale_without_precision",
      false)
  }

  @Test
  def migrateUpAndDown() {
    // There should be no tables in the schema initially.
    assertEquals(0, migrator.getTableNames.size)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
      "com.imageworks.migration.tests.up_and_down",
      false)

    // There should only be the schema_migrations table now.
    assertEquals(1, migrator.getTableNames.size)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.up_and_down",
      false).isDefined)

    val statuses1 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.up_and_down",
        false)

    assertEquals(2, statuses1.notInstalled.size)
    assertTrue(statuses1.notInstalled.contains(20081118201000L))
    assertTrue(statuses1.notInstalled.contains(20081118201742L))
    assertEquals(0, statuses1.installedWithAvailableImplementation.size)
    assertEquals(0, statuses1.installedWithoutAvailableImplementation.size)

    // Apply all the migrations.
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.up_and_down",
      false)

    assertEquals(3, migrator.getTableNames.size)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should be completely migrated.
    assertFalse(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.up_and_down",
      false).isDefined)

    // With a empty set of migrations the database should not be
    // completely migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.no_migrations",
      true).isDefined)

    val statuses2 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.up_and_down",
        false)

    assertEquals(0, statuses2.notInstalled.size)
    assertEquals(2, statuses2.installedWithAvailableImplementation.size)
    assertTrue(statuses2.installedWithAvailableImplementation.contains(20081118201000L))
    assertTrue(statuses2.installedWithAvailableImplementation.contains(20081118201742L))
    assertEquals(0, statuses2.installedWithoutAvailableImplementation.size)

    // Rollback a single migration.
    migrator.migrate(RollbackMigration(1),
      "com.imageworks.migration.tests.up_and_down",
      false)

    // There should only be the schema_migrations and
    // scala_migrations_location tables now.
    assertEquals(2, migrator.getTableNames.size)
    assertTrue(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_location").isDefined)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.up_and_down",
      false).isDefined)

    // With a empty set of migrations the database should not be
    // completely migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.no_migrations",
      true).isDefined)

    val statuses3 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.up_and_down",
        false)

    assertEquals(1, statuses3.notInstalled.size)
    assertTrue(statuses3.notInstalled.contains(20081118201742L))
    assertEquals(1, statuses3.installedWithAvailableImplementation.size)
    assertTrue(statuses3.installedWithAvailableImplementation.contains(20081118201000L))
    assertEquals(0, statuses3.installedWithoutAvailableImplementation.size)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
      "com.imageworks.migration.tests.up_and_down",
      false)

    // There should only be the schema_migrations table now.
    assertEquals(1, migrator.getTableNames.size)
    assertFalse(migrator.getTableNames.find(_.toLowerCase == "scala_migrations_people").isDefined)

    // The database should not be completely migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.up_and_down",
      false).isDefined)

    val statuses4 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.up_and_down",
        false)

    assertEquals(2, statuses4.notInstalled.size)
    assertTrue(statuses4.notInstalled.contains(20081118201000L))
    assertTrue(statuses4.notInstalled.contains(20081118201742L))
    assertEquals(0, statuses4.installedWithAvailableImplementation.size)
    assertEquals(0, statuses4.installedWithoutAvailableImplementation.size)
  }

  @Test
  def methodGetMigrationStatusesDoesNotCreateSchemaMigrationsTable() {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    val statuses1 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.no_migrations",
        false)

    // Calling getMigrationStatuses() should not have created any
    // tables.
    assertEquals(0, migrator.getTableNames.size)

    assertEquals(0, statuses1.notInstalled.size)
    assertEquals(0, statuses1.installedWithAvailableImplementation.size)
    assertEquals(0, statuses1.installedWithoutAvailableImplementation.size)

    // In a brand new database with available migrations, the database
    // should not be migrated.
    val statuses2 =
      migrator.getMigrationStatuses(
        "com.imageworks.migration.tests.up_and_down",
        false)

    assertEquals(2, statuses2.notInstalled.size)
    assertTrue(statuses2.notInstalled.contains(20081118201000L))
    assertTrue(statuses2.notInstalled.contains(20081118201742L))
    assertEquals(0, statuses2.installedWithAvailableImplementation.size)
    assertEquals(0, statuses2.installedWithoutAvailableImplementation.size)

    // Calling getMigrationStatuses() should not have created any
    // tables.
    assertEquals(0, migrator.getTableNames.size)
  }

  @Test
  def whyNotMigratedDoesNotCreateSchemaMigrationsTable() {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    // In a brand new database with no available migrations, the
    // database should not be completely migrated.  The
    // com.imageworks.migration.tests.no_migrations package contains
    // no concrete Migration subclasses.
    assertFalse(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.no_migrations",
      false).isDefined)

    // Running whyNotMigrated() should not have created any tables.
    assertEquals(0, migrator.getTableNames.size)

    // In a brand new database with available migrations, the database
    // should not be migrated.
    assertTrue(migrator.whyNotMigrated(
      "com.imageworks.migration.tests.up_and_down",
      false).isDefined)

    // Running whyNotMigrated() should not have created any tables.
    assertEquals(0, migrator.getTableNames.size)
  }

  @Test
  def autoIncrement() {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    // Create a table with two columns, the first column as a
    // auto-incrementing integer primary key and the second as a
    // VarcharType column.
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.auto_increment",
      false)

    assertEquals(2, migrator.getTableNames.size)

    val connectionBuilder = TestDatabase.getAdminConnectionBuilder

    connectionBuilder.withConnection(AutoCommit) { c =>
      val valueSql =
        """INSERT INTO
             scala_migrations_auto_incr (pk_scala_migrations_auto_incr, name)
           VALUES
             (?, ?)"""
      val defaultSql =
        """INSERT INTO
             scala_migrations_auto_incr (name)
           VALUES
             (?)"""

      // For the explicitly set primary key values, use values that
      // are not numerically increasing in order to ensure that the
      // unit test code handles this case.
      val pkOptAndNameTuples = (None, "foo") ::
        (Some(123), "bar") ::
        (Some(789), "foobar") ::
        (None, "baz") ::
        (Some(456), "foobaz") ::
        (None, "bazfoo") ::
        Nil

      for ((pkOpt, name) <- pkOptAndNameTuples) {
        pkOpt match {
          case Some(pk) =>
            With.autoClosingStatement(c.prepareStatement(valueSql)) { s =>
              s.setInt(1, pk)
              s.setString(2, name)
              s.execute()
            }
          case None => {
            With.autoClosingStatement(c.prepareStatement(defaultSql)) { s =>
              s.setString(1, name)
              s.execute()
            }
          }
        }
      }

      val selectSql = """SELECT
                           pk_scala_migrations_auto_incr
                         FROM
                           scala_migrations_auto_incr
                         WHERE
                           name = ?"""

      // Some databases will set the auto-increment sequence value to
      // one larger than the inserted value if the inserted value is
      // larger than the current auto-increment sequence value.
      val autoIncrSetsToMaxValuePlusOne =
        TestDatabase.getDatabaseAdapter.vendor match {
          case Derby => false
          case Mysql => true
          case Oracle => false
          case Postgresql => false
          case H2 => true
        }
      var autoPk = 1

      for ((pkOpt, name) <- pkOptAndNameTuples) {
        With.autoClosingStatement(c.prepareStatement(selectSql)) { s =>
          s.setString(1, name)
          With.autoClosingResultSet(s.executeQuery()) { rs =>
            var pks: List[Int] = Nil
            while (rs.next()) {
              pks = rs.getInt(1) :: pks
            }

            assertEquals(1, pks.size)

            val expectedPk =
              pkOpt match {
                case Some(pk) => {
                  if (autoIncrSetsToMaxValuePlusOne)
                    autoPk = scala.math.max(autoPk, pk + 1)
                  pk
                }
                case None => {
                  val pk = autoPk
                  autoPk += 1
                  pk
                }
              }

            assertEquals(expectedPk, pks.head)
          }
        }
      }
    }
  }

  @Test
  def alterColumn() {
    // In a brand new database there should be no tables.
    assertEquals(0, migrator.getTableNames.size)

    // Create the table with a short VarcharType column.
    migrator.migrate(MigrateToVersion(20110214054347L),
      "com.imageworks.migration.tests.alter_column",
      false)

    assertEquals(2, migrator.getTableNames.size)

    // Assert that an INSERT with a short Varchar value works while a
    // long one fails.
    val name = "x" * 100
    val sqlStart = "INSERT INTO scala_migrations_altering VALUES ('"
    val sqlEnd = "')"
    val shortNameSql = sqlStart + name + sqlEnd
    val longNameSql = sqlStart + name + name + sqlEnd

    val connectionBuilder = TestDatabase.getAdminConnectionBuilder

    connectionBuilder.withConnection(AutoCommit) { c =>
      With.autoClosingStatement(c.prepareStatement(shortNameSql)) { s =>
        s.execute()
      }

      With.autoClosingStatement(c.prepareStatement(longNameSql)) { s =>
        try {
          s.execute()
          fail("Expected a truncation error from the database.")
        }
        catch {
          case _: SQLException =>
        }
      }
    }

    // Apply the migration that extends the length of the column then
    // assert that the same INSERT that failed now works.
    migrator.migrate(MigrateToVersion(20110214060042L),
      "com.imageworks.migration.tests.alter_column",
      false)

    connectionBuilder.withConnection(AutoCommit) { c =>
      With.autoClosingStatement(c.prepareStatement(longNameSql)) { s =>
        s.execute()
      }
    }

    // Do not rollback all the migrations because the last migration
    // is irreversible.  Let setUp() for the next unit test clean up
    // the left over tables.
  }

  @Test
  def grantAndRevoke() {
    val connectionBuilder = TestDatabase.getUserConnectionBuilder
    val databaseAdapter = TestDatabase.getDatabaseAdapter

    // Make a table, migrate with admin account.
    migrator.migrate(MigrateToVersion(200811241940L),
      "com.imageworks.migration.tests.grant_and_revoke",
      false)

    // New connection with user account.
    val testMigrator = new Migrator(connectionBuilder, databaseAdapter)

    val selectSql =
      "SELECT name FROM " +
        databaseAdapter.quoteTableName("scala_migrations_location")

    def runSelect() {
      testMigrator.withLoggingConnection(AutoCommit) { c =>
        With.autoClosingStatement(c.prepareStatement(selectSql)) { s =>
          With.autoClosingResultSet(s.executeQuery()) { rs => }
        }
      }
    }

    // try to select table, should give a permissions error
    try {
      runSelect()

      // failure if got here
      fail("SELECT permission failure expected")
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compatibility, only a
      // java.sql.SQLException is caught.
      case e: SQLException => // expected
    }

    // perform grants
    migrator.migrate(MigrateToVersion(200811261513L),
      "com.imageworks.migration.tests.grant_and_revoke",
      false)

    // try to select table, should succeed now that grant has been given
    try {
      runSelect()
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compatibility, only a
      // java.sql.SQLException is caught.
      case e: SQLException =>
        // failure if got here
        fail("SELECT permission failure unexpected: " + e)
    }

    // Migrate to 20121013072344 which show throw an
    // IllegalArgumentException.
    try {
      migrator.migrate(MigrateToVersion(20121013072344L),
        "com.imageworks.migration.tests.grant_and_revoke",
        false)
      // failure if got here
      fail("Expected IllegalArgumentException")
    }
    catch {
      case _: IllegalArgumentException => // expected
    }

    // preform revoke
    migrator.migrate(RollbackMigration(1),
      "com.imageworks.migration.tests.grant_and_revoke",
      false)

    // try to select table, should give a permissions error again
    try {
      runSelect()

      // failure if got here
      fail("SELECT permission failure expected")
    }
    catch {
      // With JDK 1.6 or later, a java.sql.SQLSyntaxErrorException
      // could be caught here, but for 1.5 compatibility, only a
      // java.sql.SQLException is caught.
      case _: SQLException => // expected
    }
  }

  @Test
  def columnsCanHoldTypes() {
    migrator.migrate(InstallAllMigrations,
      "com.imageworks.migration.tests.types",
      false)

    val varbinaryArray = (1 to 4).map(_.toByte).toArray
    val now = System.currentTimeMillis

    migrator.withLoggingConnection(AutoCommit) { c =>
      for (
        (n, v) <- Array(("bigint_column", java.lang.Long.MIN_VALUE),
          ("bigint_column", java.lang.Long.MAX_VALUE),
          ("char_column", "ABCD"),
          ("decimal_column", 3.14),
          ("integer_column", java.lang.Integer.MIN_VALUE),
          ("integer_column", java.lang.Integer.MAX_VALUE),
          ("timestamp_column", new java.sql.Date(now)),
          ("varbinary_column", varbinaryArray),
          ("varchar_column", "ABCD"))
      ) {
        val insertSql = """INSERT INTO
                             scala_migrations_types_test (""" + n + """)
                           VALUES
                             (?)""".replaceAll("\\s+", " ")
        val insertStatement = c.prepareStatement(insertSql)
        insertStatement.setObject(1, v)
        insertStatement.executeUpdate()
        insertStatement.close()

        // Make sure that the value exists.
        val selectSql = """SELECT
                             COUNT(1)
                           FROM
                             scala_migrations_types_test
                           WHERE
                             """ + n + """ = ?""".replaceAll("\\s+", " ")
        With.autoClosingStatement(c.prepareStatement(selectSql)) { s =>
          s.setObject(1, v)
          With.autoClosingResultSet(s.executeQuery()) { rs =>
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
  }

  @Test
  def withResultSetClosesOnNormalReturn() {
    val mockResultSet = context.mock(classOf[ResultSet])

    context.checking(new Expectations {
      oneOf(mockResultSet).close()
    })

    var rs1: ResultSet = null

    val m = new Migration {
      override def up() {
        withResultSet(mockResultSet) { rs2 =>
          rs1 = rs2
        }
      }

      override def down() {}
    }

    m.up()

    context.assertIsSatisfied()

    assertSame(mockResultSet, rs1)
  }

  @Test
  def withResultSetClosesOnThrow() {
    val mockResultSet = context.mock(classOf[ResultSet])

    context.checking(new Expectations {
      oneOf(mockResultSet).close()
    })

    var rs1: ResultSet = null

    class ThisSpecialException
      extends Throwable

    val m = new Migration {
      override def up() {
        withResultSet(mockResultSet) { rs2 =>
          rs1 = rs2
          throw new ThisSpecialException
        }
      }

      override def down() {}
    }

    try {
      m.up()
    }
    catch {
      case _: ThisSpecialException =>
    }

    context.assertIsSatisfied()

    assertSame(mockResultSet, rs1)
  }
}
