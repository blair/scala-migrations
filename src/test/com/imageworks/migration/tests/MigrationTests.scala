package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

class MigrationTests
{
  // Load the Derby database driver.
  Class.forName("org.apache.derby.jdbc.EmbeddedDriver")

  private
  var migrator : Migrator = _

  @Before
  def set_up() : Unit =
  {
    val db_name = System.currentTimeMillis.toString
    val url = "jdbc:derby:test-databases/" + db_name + ";create=true"

    // The default schema for a Derby database is "APP".
    migrator = new Migrator(url, new DerbyDatabaseAdapter, Some("APP"))
  }

  @Test { val expected = classOf[DuplicateMigrationDescriptionException] }
  def test_duplicate_descriptions_throw_exception : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_descriptions",
                     false)
  }

  @Test { val expected = classOf[DuplicateMigrationVersionException] }
  def test_duplicate_versions_throw_exception : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.duplicate_versions",
                     false)
  }

  @Test
  def test_migrate_up_and_down : Unit =
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

    // Apply all the migrations.
    migrator.migrate(InstallAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    assertEquals(3, migrator.table_names.size)
    assertTrue(migrator.table_names.find(_.toLowerCase == "location").isDefined)
    assertTrue(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // Rollback a single migration.
    migrator.migrate(RollbackMigration(1),
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations and location tables
    // now.
    assertEquals(2, migrator.table_names.size)
    assertTrue(migrator.table_names.find(_.toLowerCase == "location").isDefined)
    assertFalse(migrator.table_names.find(_.toLowerCase == "people").isDefined)

    // Migrate down the whole way.
    migrator.migrate(RemoveAllMigrations,
                     "com.imageworks.migration.tests.up_and_down",
                     false)

    // There should only be the schema migrations table now.
    assertEquals(1, migrator.table_names.size)
    assertFalse(migrator.table_names.find(_.toLowerCase == "people").isDefined)
  }
}
