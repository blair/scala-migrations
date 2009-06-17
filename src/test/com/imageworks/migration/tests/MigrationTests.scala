package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

import org.jmock.{Expectations,
                  Mockery}

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
  var migrator : Migrator = _

  private
  var url : String = _

  @Before
  def set_up() : Unit =
  {
    val db_name = System.currentTimeMillis.toString
    url = "jdbc:derby:" + db_name

    val url_ = url + ";create=true"

    // The default schema for a Derby database is "APP".
    migrator = new Migrator(url_, new DerbyDatabaseAdapter(Some("APP")))
  }

  @Test { val expected = classOf[DuplicateMigrationDescriptionException] }
  def duplicate_descriptions_throw_exception : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_descriptions",
                     false)
  }

  @Test { val expected = classOf[DuplicateMigrationVersionException] }
  def duplicate_versions_throw_exception : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_versions",
                     false)
  }

  @Test { val expected = classOf[IllegalArgumentException] }
  def scale_without_precision : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.scale_without_precision",
                     false)
  }

  @Test
  def migrate_up_and_down : Unit =
  {
    // There should be no tables in the schema initially.
    assertEquals(0, migrator.table_names.size)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations table now.
    assertEquals(1, migrator.table_names.size)
    assertFalse(migrator.table_names.find(_.toLowerCase == "locations").isDefined)
    assertFalse(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // The database should not be completely migrated.
    assertFalse(migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                     false))

    // Apply all the migrations.
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    assertEquals(3, migrator.table_names.size)
    assertTrue(migrator.table_names.find(_.toLowerCase == "location").isDefined)
    assertTrue(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // The database should be completely migrated.
    assertTrue(migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                    false))

    // Rollback a single migration.
    migrator.migrate(RollbackMigration(1),
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations and location tables
    // now.
    assertEquals(2, migrator.table_names.size)
    assertTrue(migrator.table_names.find(_.toLowerCase == "location").isDefined)
    assertFalse(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // The database should not be completely migrated.
    assertFalse(migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                     false))

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations table now.
    assertEquals(1, migrator.table_names.size)
    assertFalse(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // The database should not be completely migrated.
    assertFalse(migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                     false))
  }

  @Test
  def is_migrated_does_not_create_schema_migrations : Unit =
  {
    // In a brand new database with no available migrations, the
    // database should not be completely migrated.  The
    // com.imageworks.migration.tests.no_migrations package contains
    // no concrete Migration subclasses.
    assertTrue(migrator.is_migrated("com.imageworks.migration.tests.no_migrations",
                                    false))

    // Running is_migrated() should not have created any tables.
    assertEquals(0, migrator.table_names.size)

    // In a brand new database with available migrations, the database
    // should not be migrated.
    assertFalse(migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                     false))

    // Running is_migrated() should not have created any tables.
    assertEquals(0, migrator.table_names.size)
  }

  @Test
  def grant_and_revoke : Unit =
  {
    // create a second user, make a table
    migrator.migrate(MigrateToVersion(200811241940L),
                     "com.imageworks.migration.tests.grant_and_revoke",
                     false)

    // "Reboot" database for database property changes to take effect by
    // shutting down the database.  Connection shuts the database down,
    // but also throws an exception.
    try {
      java.sql.DriverManager.getConnection(url + ";shutdown=true")
    }
    catch {
      // For JDBC3 (JDK 1.5)
      case e : org.apache.derby.impl.jdbc.EmbedSQLException =>

      // For JDBC4 (JDK 1.6), a
      // java.sql.SQLNonTransientConnectionException is
      // thrown, but this exception class does not exist in JDK 1.5,
      // so catch a java.sql.SQLException instead.
      case e : java.sql.SQLException =>
    }

    // new connection with test user
    val test_migrator = new Migrator(url,
                                     "test",
                                     "password",
                                     new DerbyDatabaseAdapter(Some("APP")))

    val select_sql = "SELECT name FROM APP.location"

    def run_select : Unit =
    {
      test_migrator.with_logging_connection { connection =>
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
      case e : java.sql.SQLException => // expected
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
      case e : java.sql.SQLException =>
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
      case e : java.sql.SQLException => // expected
    }
  }

  @Test
  def columns_can_hold_types : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.types",
                     false)

    val varbinary_array = (1 to 4).map(_.toByte).toArray
    val now = System.currentTimeMillis

    migrator.with_logging_connection { connection =>
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
        With.result_set(select_statement.executeQuery()) { rs =>
          var counts : List[Int] = Nil
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
  def with_result_set_closes_on_normal_return : Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1 : java.sql.ResultSet = null

    val m = new Migration {
              override
              def up() : Unit =
              {
                with_result_set(mock_rs) { rs2 =>
                  rs1 = rs2
                }
              }

              override
              def down() : Unit =
              {
              }
            }

    m.up()

    context.assertIsSatisfied()

    assertSame(mock_rs, rs1)
  }

  @Test
  def with_result_set_closes_on_throw : Unit =
  {
    val mock_rs = context.mock(classOf[java.sql.ResultSet])

    context.checking(new Expectations {
                       oneOf (mock_rs).close()
                     })

    var rs1 : java.sql.ResultSet = null

    class ThisSpecialException
      extends java.lang.Throwable

    val m = new Migration {
              override
              def up() : Unit =
              {
                with_result_set(mock_rs) { rs2 =>
                  rs1 = rs2
                  throw new ThisSpecialException
                }
              }

              override
              def down() : Unit =
              {
              }
            }

    try {
      m.up()
    }
    catch {
      case _ : ThisSpecialException =>
    }

    context.assertIsSatisfied()

    assertSame(mock_rs, rs1)
  }
}
