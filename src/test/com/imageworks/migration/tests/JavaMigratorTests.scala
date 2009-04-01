package com.imageworks.migration.tests

import org.junit.Assert._
import org.junit.{Before,
                  Test}

class JavaMigratorTests
{
  // Set the Derby system home to a test-databases directory so the
  // derby.log file and all databases will be placed in there.
  System.getProperties.setProperty("derby.system.home", "test-databases")

  // Load the Derby database driver.
  Class.forName("org.apache.derby.jdbc.EmbeddedDriver")

  private
  var java_migrator : JavaMigrator = _

  @Before
  def set_up() : Unit =
  {
    val db_name = System.currentTimeMillis.toString
    val url = "jdbc:derby:" + db_name + ";create=true"

    // The default schema for a Derby database is "APP".
    java_migrator = new JavaMigrator(
      url,
      JavaDatabaseAdapter.getDerbyDatabaseAdapter("APP"))
  }

  @Test { val expected = classOf[DuplicateMigrationDescriptionException] }
  def duplicate_descriptions_throw_exception : Unit =
  {
    java_migrator.install_all_migrations("com.imageworks.migration.tests.duplicate_descriptions",
                                         false)
  }

  @Test { val expected = classOf[DuplicateMigrationVersionException] }
  def duplicate_versions_throw_exception : Unit =
  {
    java_migrator.install_all_migrations("com.imageworks.migration.tests.duplicate_versions",
                                         false)
  }

  @Test
  def migrate_up_and_down : Unit =
  {
    // There should be no tables in the schema initially.
    assertEquals(0, java_migrator.table_names.size)

    // Migrate down the whole way.
    java_migrator.remove_all_migrations("com.imageworks.migration.tests.up_and_down",
                                        false)

    // The database should not be completely migrated.
    assertFalse(java_migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                          false))

    // An empty array of Strings so that table_names.toArray returns
    // an Array[String] and not Array[AnyRef].
    val ea = new Array[String](0)

    // There should only be the schema migrations table now.
    assertEquals(1, java_migrator.table_names.size)
    assertFalse(java_migrator.table_names.toArray(ea).find(_.toLowerCase == "people").isDefined)

    // Apply all the migrations.
    java_migrator.install_all_migrations("com.imageworks.migration.tests.up_and_down",
                                         false)

    assertEquals(3, java_migrator.table_names.size)
    assertTrue(java_migrator.table_names.toArray(ea).find(_.toLowerCase == "people").isDefined)

    // The database should be completely migrated.
    assertTrue(java_migrator.is_migrated("com.imageworks.migration.tests.up_and_down",
                                         false))

    // Migrate down the whole way.
    java_migrator.remove_all_migrations("com.imageworks.migration.tests.up_and_down",
                                        false)

    // There should only be the schema migrations table now.
    assertEquals(1, java_migrator.table_names.size)
    assertFalse(java_migrator.table_names.toArray(ea).find(_.toLowerCase == "people").isDefined)
  }
}
