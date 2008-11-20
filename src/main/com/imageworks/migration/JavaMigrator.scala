package com.imageworks.migration

/**
 * The Scala Migrator class uses Scala case classes in its public
 * constructors and public methods which makes it difficult to use
 * from a pure Java environment.  This class exposes a Java-style
 * interface and delegates to the Scala Migrator class.
 */
class JavaMigrator private (migrator : Migrator)
{
  /**
   * JavaMigrator constructor.
   *
   * @param jdbc_url the JDBC URL to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   * @param schema_name the schema name to use to qualify all table
   *        names; may be null if the migrator should not use a schema
   *        name and the table names should be unqualified
   */
  def this(jdbc_url : String,
           adapter : DatabaseAdapter,
           schema_name : String) = {
    this(new Migrator(jdbc_url,
                      adapter,
                      if (schema_name eq null) None else Some(schema_name)))
  }

  /**
   * JavaMigrator constructor.
   *
   * @param jdbc_url the JDBC URL to connect to the database
   * @param jdbc_username the username to log into the database
   * @param jdbc_password the password associated with the database
   *        username
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   * @param schema_name the schema name to use to qualify all table
   *        names; may be null if the migrator should not use a schema
   *        name and the table names should be unqualified
   */
  def this(jdbc_url : String,
           jdbc_username : String,
           jdbc_password : String,
           adapter : DatabaseAdapter,
           schema_name : String) = {
    this(new Migrator(jdbc_url,
                      jdbc_username,
                      jdbc_password,
                      adapter,
                      if (schema_name eq null) None else Some(schema_name)))
  }

  /**
   * Get a list of table names in the schema.
   *
   * @return a set of table names; no modifications of the case of
   *         table names is done
   */
  def table_names : java.util.Set[String] =
  {
    val table_names = migrator.table_names

    val set = new java.util.HashSet[String](table_names.size)

    for (table_name <- table_names)
      set.add(table_name)

    set
  }

  /**
   * Install all available migrations into the database.
   *
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @parm search_sub_packages true if sub-packages of package_name
   *       should be searched
   */
  def install_all_migrations(package_name : String,
                             search_sub_packages : Boolean) : Unit =
  {
    migrator.migrate(InstallAllMigrations,
                     package_name,
                     search_sub_packages)
  }

  /**
   * Remove all installed migrations from the database.
   *
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @parm search_sub_packages true if sub-packages of package_name
   *       should be searched
   */
  def remove_all_migrations(package_name : String,
                            search_sub_packages : Boolean) : Unit =
  {
    migrator.migrate(RemoveAllMigrations,
                     package_name,
                     search_sub_packages)
  }

  /**
   * Migrate the database to the given version.
   *
   * @param version the version number the database should be migrated
   *        to
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @parm search_sub_packages true if sub-packages of package_name
   *       should be searched
   */
  def migrate_to(version : Long,
                 package_name : String,
                 search_sub_packages : Boolean) : Unit =
  {
    migrator.migrate(MigrateToVersion(version),
                     package_name,
                     search_sub_packages)
  }

  /**
   * Rollback a given number of migrations in the database.
   *
   * @param count the number of migrations to rollback
   *        to
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @parm search_sub_packages true if sub-packages of package_name
   *       should be searched
   */
  def rollback(count : Int,
               package_name : String,
               search_sub_packages : Boolean) : Unit =
  {
    migrator.migrate(RollbackMigration(count),
                     package_name,
                     search_sub_packages)
  }
}
