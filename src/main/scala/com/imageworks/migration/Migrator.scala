/*
 * Copyright (c) 2010 Sony Pictures Imageworks Inc.
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

import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.sql.DataSource

/**
 * A migration to create the schema_migrations table that records
 * which migrations have been applied to a database.
 */
private
class CreateSchemaMigrationsTableMigration
  extends Migration
{
  override
  def up(): Unit =
  {
    createTable(Migrator.schemaMigrationsTableName) { t =>
      t.varchar("version", Limit(32), NotNull)
    }

    addIndex(Migrator.schemaMigrationsTableName,
             Array("version"),
             Unique,
             Name("unique_schema_migrations"))
  }

  override
  def down(): Unit =
  {
    throw new IrreversibleMigrationException
  }
}

object Migrator
{
  /**
   * The name of the table that stores all the installed migration
   * version numbers.
   */
  val schemaMigrationsTableName = "schema_migrations"

  /**
   * Given a path to a JAR file, return a set of all the names of all
   * the classes the JAR file contains.
   *
   * @param path path to the JAR file
   * @param package_name the package name that the classes should be
   *        in
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   * @return a set of the class names the JAR file contains
   */
  private
  def classNamesInJar
    (path: String,
     package_name: String,
     search_sub_packages: Boolean): scala.collection.mutable.HashSet[String] =
  {
    // Search for the package in the JAR file by mapping the package
    // name to the expected name in the JAR file, then append a '/' to
    // the package name to ensure that no matches are done on
    // different packages that share a common substring.
    val pn = package_name.replace('.', '/') + '/'

    val class_names = new scala.collection.mutable.HashSet[String]
    var jar: java.util.jar.JarFile = null
    try {
      jar = new java.util.jar.JarFile(path, false)
      val entries = jar.entries
      while (entries.hasMoreElements) {
        val name = entries.nextElement.getName
        if (name.startsWith(pn) && name.endsWith(".class")) {
          val class_name = name.substring(0, name.length - ".class".length)
                               .replace('/', '.')
          if (search_sub_packages) {
            class_names += class_name
          }
          else if (! class_name.substring(pn.length).contains('.')) {
            class_names += class_name
          }
        }
      }
      class_names
    }
    finally {
      if (jar ne null) {
        jar.close()
      }
    }
  }

  /**
   * Given a java.io.File for a directory containing compiled Java and
   * Scala classes, return a set of all names of the classes the
   * directory contains.
   *
   * @param file the java.io.File corresponding to the directory
   * @param package_name the package name that the classes should be
   *        in
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   * @return a set of the class names the directory contains
   */
  private
  def classNamesInDir(file: java.io.File,
                      package_name: String,
                      search_sub_packages: Boolean): scala.collection.mutable.HashSet[String] =
  {
    val class_names = new scala.collection.mutable.HashSet[String]

    def scan(f: java.io.File,
             pn: String): Unit =
    {
      val child_files = f.listFiles

      for (child_file <- child_files) {
        if (search_sub_packages && child_file.isDirectory) {
          val child_package_name = if (pn.length > 0)
                                     pn + '.' + child_file.getName
                                   else
                                     child_file.getName
          scan(child_file, child_package_name)
        }
        else if (child_file.isFile) {
          val name = child_file.getName
          if (name.endsWith(".class")) {
            val class_name = pn +
                             '.' +
                             name.substring(0,
                                            name.length - ".class".length)
            class_names += class_name
          }
        }
      }
    }

    scan(file, package_name)

    class_names
  }

  /**
   * Given a Java package name, return a set of concrete classes with
   * a no argument constructor that implement Migration.
   *
   * Limitations:
   * 1) This function assumes that only a single directory or jar file
   *    provides classes in the Java package.
   * 2) It will descend into non-child directories of the package
   *    directory or other jars to find other migrations.
   * 3) It does not support remotely loaded classes and jar files.
   *
   * @param package_name the Java package name to search for Migration
   *        subclasses
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   * @return a sorted map with version number keys and the concrete
   *         Migration subclasses as the value
   */
  private
  def findMigrations(package_name: String,
                     search_sub_packages: Boolean,
                     logger: Logger): scala.collection.immutable.SortedMap[Long,Class[_ <: Migration]] =
  {
    // Ask the current class loader for the resource corresponding to
    // the package, which can refer to a directory, a jar file
    // accessible via the local filesystem or a remotely accessible
    // jar file.  Only the first two are handled.
    val url =
      {
        val pn = package_name.replace('.', '/')
        val u = this.getClass.getClassLoader.getResource(pn)
        if (u eq null) {
          throw new RuntimeException("Cannot find a resource for the '" +
                                     package_name +
                                     "'.")
        }
        u.toString
      }

    val class_names =
      if (url.startsWith("jar:file:")) {
        // This URL ends with a ! character followed by the name of
        // the resource in the jar file, so just get the jar file
        // path.
        val index = url.lastIndexOf('!')
        val path = if (index == -1)
                     url.substring("jar:file:".length)
                   else
                     url.substring("jar:file:".length, index)
        classNamesInJar(path, package_name, search_sub_packages)
      }
      else if (url.startsWith("file:")) {
        val dir = url.substring("file:".length)
        val file = new java.io.File(dir)
        if (! file.isDirectory) {
          val message = "The resource URL '" +
                        url +
                        "' should be a directory but is not."
          throw new RuntimeException(message)
        }
        classNamesInDir(file, package_name, search_sub_packages)
      }
      else {
        val message = "Do not know how to get a list of classes in the " +
                      "resource at '" +
                      url +
                      "' corresponding to the package '" +
                      package_name +
                      "'."
        throw new RuntimeException(message)
      }

    // Search through the class names for ones that are concrete
    // subclasses of Migration that have a no argument constructor.
    // Use a sorted map mapping the version to the class name so the
    // final results will be sorted in numerically increasing order.
    var seen_versions = new scala.collection.immutable.TreeMap[Long,String]
    val seen_descriptions = new scala.collection.mutable.HashMap[String,String]

    // Search for classes that have the proper format.
    val re_str = """Migrate_(\d+)_([_a-zA-Z0-9]*)"""
    val re = java.util.regex.Pattern.compile(re_str)

    // Classes to be skipped.  class_names cannot have items removed from it
    // inside the for loop below or not all elements in class_names may be
    // visited (iterator corruption).
    val skip_names = new scala.collection.mutable.HashSet[String]

    for (class_name <- class_names) {
      val index = class_name.lastIndexOf('.')
      val base_name = if (index == -1)
                        class_name
                      else
                        class_name.substring(index + 1)
      val matcher = re.matcher(base_name)
      if (matcher.matches) {
        val version_str = matcher.group(1)
        val description = matcher.group(2)
        try {
          val version = java.lang.Long.parseLong(version_str)
          seen_versions.get(version) match {
            case Some(cn) => {
              val message = "The '" +
                            class_name +
                            "' migration defines a duplicate version number " +
                            "with '" +
                            cn +
                            "'."
              throw new DuplicateMigrationVersionException(message)
            }
            case None => {
              seen_versions = seen_versions.insert(version, class_name)
            }
          }

          seen_descriptions.get(description) match {
            case Some(cn) => {
              val message = "The '" +
                            class_name +
                            "' defines a duplicate description with '" +
                            cn +
                            "'."
              throw new DuplicateMigrationDescriptionException(message)
            }
            case None => {
              seen_descriptions.put(description, class_name)
            }
          }
        }
      }
      else {
        skip_names += class_name
        logger.debug("Skipping '{}' because it does not match '{}'.",
                     class_name,
                     re_str)
      }
    }

    // Remove all the skipped class names from class_names.
    class_names --= skip_names

    var results =
      new scala.collection.immutable.TreeMap[Long,Class[_ <: Migration]]

    for ((version, class_name) <- seen_versions) {
      var c: Class[_] = null
      try {
        c = Class.forName(class_name)
        if (classOf[Migration].isAssignableFrom(c) &&
            ! c.isInterface &&
            ! java.lang.reflect.Modifier.isAbstract(c.getModifiers)) {
          try {
            // Ensure that there is a no-argument constructor.
            c.getConstructor()
            val casted_class = c.asSubclass(classOf[Migration])
            results = results.insert(version, casted_class)
          }
          catch {
            case e: java.lang.NoSuchMethodException => {
              logger.debug("Unable to find a no-argument constructor for '" +
                           class_name +
                           "'",
                           e)
            }
          }
        }
      }
      catch {
        case e => {
          logger.debug("Unable to load class '" +
                       class_name +
                       "'.",
                       e)
        }
      }
    }

    results
  }
}

private
class RawAndLoggingConnections(val raw: java.sql.Connection,
                               val logging: java.sql.Connection)

/**
 * This sealed trait's specifies the commit behavior on a database
 * connection and its subobjects are used as arguments to the
 * Migrator#with*Connection() methods.  The subobjects specify if a
 * new connection's auto-commit mode should be left on or disabled.
 * For connections with auto-commit mode disabled it specifies if the
 * current transaction should be rolled back or committed if the
 * closure passed to Migrator#with*Connection() throws an exception.
 */
private sealed trait CommitBehavior

/**
 * The new database connection's auto-commit mode is left on.  Because
 * the connection is in auto-commit mode the
 * Migrator#with*Connection() methods do not commit nor roll back the
 * transaction any before returning the result of the
 * Migrator#with*Connection()'s closure or rethrowing its exception.
 */
private case object AutoCommit
  extends CommitBehavior

/**
 * The new database connection's auto-commit mode is turned off.
 * Regardless if the closure passed to Migrator#with*Connection()
 * returns or throws an exception the transaction is committed.
 */
private case object CommitUponReturnAndException
  extends CommitBehavior

/**
 * The new database connection's auto-commit mode is turned off.  If
 * the closure passed to the Migrator#with*Connection() returns
 * normally then transaction is committed; if it throws an exception
 * then the transaction is rolled back.
 */
private case object CommitUponReturnRollbackUponException
  extends CommitBehavior

/**
 * This class migrates the database into the desired state.
 */
class Migrator private (jdbc_conn: Either[DataSource, String],
                        jdbc_login: Option[Tuple2[String,String]],
                        adapter: DatabaseAdapter)
{
  import Migrator._
  import RichConnection._

  // Load the log4jdbc database wrapper driver.
  Class.forName("net.sf.log4jdbc.DriverSpy")

  private final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Construct a migrator to a database that does not need a username
   * and password.
   * @param jdbc_url the JDBC URL to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbc_url: String,
           adapter: DatabaseAdapter) =
  {
    this(Right(jdbc_url), None, adapter)
  }

  /**
   * Construct a migrator to a database that needs a username and password.
   * @param jdbc_url the JDBC URL to connect to the database
   * @param jdbc_username the username to log into the database
   * @param jdbc_password the password associated with the database
   *        username
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbc_url: String,
           jdbc_username: String,
           jdbc_password: String,
           adapter: DatabaseAdapter) =
  {
    this(Right(jdbc_url),
         Some((jdbc_username, jdbc_password)),
         adapter)
  }

  /**
   * Construct a migrator to a database with an existing DataSource.
   * @param jdbc_datasource the JDBC DataSource to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbc_datasource: DataSource,
           adapter: DatabaseAdapter) =
  {
    this(Left(jdbc_datasource), None, adapter)
  }

  /**
   * Construct a migrator to a database with an existing DataSource but
   * override default username and password.
   * @param jdbc_datasource the JDBC DataSource to connect to the database
   * @param jdbc_username the username to log into the database
   * @param jdbc_password the password associated with the database
   *        username
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbc_datasource: DataSource,
           jdbc_username: String,
           jdbc_password: String,
           adapter: DatabaseAdapter) =
  {
    this(Left(jdbc_datasource),
         Some((jdbc_username, jdbc_password)),
         adapter)
  }

  /**
   * Get a raw database connection and pass it to a closure for the
   * closure to use.  After the closure returns, normally or by
   * throwing an exception, close the connection.
   *
   * @param commit_behavior specify the auto-commit mode on the
   *        connection and whether to commit() or rollback() the
   *        transaction if the auto-commit mode is disabled on the
   *        connection
   * @param f a Function1[java.sql.Connection,T] that is passed a new
   *        connection
   * @return what f returns
   */
  // http://lampsvn.epfl.ch/trac/scala/ticket/442
  private[migration] def withRawConnection[T]
    (commit_behavior: CommitBehavior)
    (f: java.sql.Connection => T): T =
  {
    val raw_connection = {
      (jdbc_conn, jdbc_login) match {
        case (Left(jdbc_datasource), Some((username, password))) => {
          jdbc_datasource.getConnection(username, password)
        }

        case (Left(jdbc_datasource), None) => {
          jdbc_datasource.getConnection
        }

        case (Right(jdbc_url), Some((username, password))) => {
          java.sql.DriverManager.getConnection(jdbc_url, username, password)
        }

        case (Right(jdbc_url), None) => {
          java.sql.DriverManager.getConnection(jdbc_url)
        }
      }
    }

    try {
      val auto_commit = commit_behavior match {
                          case AutoCommit => true
                          case CommitUponReturnAndException => false
                          case CommitUponReturnRollbackUponException => false
                        }
      raw_connection.setAutoCommit(auto_commit)

      val result = f(raw_connection)

      commit_behavior match {
        case AutoCommit =>
        case CommitUponReturnAndException => raw_connection.commit()
        case CommitUponReturnRollbackUponException => raw_connection.commit()
      }

      result
    }
    catch {
      case e1 => {
        val (operation,
             thunk) = commit_behavior match {
                        case AutoCommit =>
                          ("", () => ())

                        case CommitUponReturnAndException =>
                          ("commit", () => { raw_connection.commit() })

                        case CommitUponReturnRollbackUponException =>
                          ("rollback", () => { raw_connection.rollback() })
                      }

        try {
          thunk()
        }
        catch {
          case e2 => {
            logger.warn("Trying to " +
                        operation +
                        " the connection after catching " +
                        e1 +
                        " threw:",
                        e2)
          }
        }

        throw e1
      }
    }
    finally {
      try {
        raw_connection.close()
      }
      catch {
        case e => logger.warn("Error in closing connection:", e)
      }
    }
  }

  /**
   * Get a database connection that logs all calls to it and pass it
   * to a closure for the closure to use.  After the closure returns,
   * normally or by throwing an exception, close the connection.
   *
   * @param commit_behavior specify the auto-commit mode on the
   *        connection and whether to commit() or rollback() the
   *        transaction if the auto-commit mode is disabled on the
   *        connection
   * @param f a Function1[java.sql.Connection,T] that is passed a new
   *        connection
   * @return what f returns
   */
  private[migration] def withLoggingConnection[T]
    (commit_behavior: CommitBehavior)
    (f: java.sql.Connection => T): T =
  {
    withRawConnection(commit_behavior) { raw_connection =>
      f(new net.sf.log4jdbc.ConnectionSpy(raw_connection))
    }
  }

  /**
   * Get a tuple of database connections, a raw one and one that logs
   * all calls to it and pass both to a closure for the closure to
   * use.  After the closure returns, normally or by throwing an
   * exception, close the raw connection.
   *
   * @param commit_behavior specify the auto-commit mode on the
   *        connection and whether to commit() or rollback() the
   *        transaction if the auto-commit mode is disabled on the
   *        connection
   * @param f a Function1[RawAndLoggingConnections,T] that is passed a
   *        pair of related connections
   * @return what f returns
   */
  private[migration] def withConnections[T]
    (commit_behavior: CommitBehavior)
    (f: RawAndLoggingConnections => T): T =
  {
    withRawConnection(commit_behavior) { raw_connection =>
      val logging_connection =
        new net.sf.log4jdbc.ConnectionSpy(raw_connection)
      f(new RawAndLoggingConnections(raw_connection, logging_connection))
    }
  }

  /**
   * Get a list of table names.  If the database adapter was given a
   * schema name then only the tables in that schema are returned.
   *
   * @return a set of table names; no modifications of the case of
   *         table names is done
   */
  def getTableNames: scala.collection.mutable.Set[String] =
  {
    withLoggingConnection(AutoCommit) { connection =>
      val metadata = connection.getMetaData
      With.resultSet(metadata.getTables(null,
                                        adapter.schemaNameOpt.getOrElse(null),
                                        null,
                                        Array("TABLE"))) { rs =>
        val names = new scala.collection.mutable.HashSet[String]
        while (rs.next()) {
          names += rs.getString(3)
        }
        names
      }
    }
  }

  /**
   * Execute a migration in the given direction.
   *
   * @param migration_class the class of migration to execute
   * @param direction the direction the migration should be run
   * @param version_update_opt if provided, the schema_migrations
   *        table is updated using the given connection and migration
   *        version number; this allows this method to
   */
  private
  def runMigration
    (migration_class: Class[_ <: Migration],
     direction: MigrationDirection,
     version_update_opt: Option[Tuple2[java.sql.Connection,Long]]): Unit =
  {
    logger.info("Migrating {} with '{}'.",
                direction.str,
                migration_class.getName)

    val migration = migration_class.getConstructor().newInstance()
    withConnections(AutoCommit) { connections =>
      migration.adapterOpt = Some(adapter)
      migration.rawConnectionOpt = Some(connections.raw)
      migration.connectionOpt = Some(connections.logging)

      direction match {
        case Up => migration.up()
        case Down => migration.down()
      }
    }

    version_update_opt match {
      case Some((schema_connection, version)) => {
        val table_name = adapter.quoteTableName(schemaMigrationsTableName)
        val sql =
          direction match {
            case Up => "INSERT INTO " +
                       table_name +
                       " (version) VALUES (?)"
            case Down => "DELETE FROM " +
                         table_name +
                         " WHERE version = ?"
          }

        schema_connection.withPreparedStatement(sql) { statement =>
          statement.setString(1, version.toString)
          statement.execute()
        }
      }
      case None =>
    }
  }

  /**
   * Determine if the "schema_migrations" table exists.
   *
   * @return true if the "schema_migration" table exists
   */
  private
  def doesSchemaMigrationsTableExist: Boolean =
  {
    val smtn = Migrator.schemaMigrationsTableName.toLowerCase
    getTableNames.find(_.toLowerCase == smtn) match {
      case Some(_) => true
      case None => false
    }
  }

  /**
   * Creates the schema migrations table if it does not exist.
   */
  private
  def initializeSchemaMigrationsTable(): Unit =
  {
    if (! doesSchemaMigrationsTableExist) {
      runMigration(classOf[CreateSchemaMigrationsTableMigration], Up, None)
    }
  }

  /**
   * Get a sorted list of all the installed migrations using a query
   * on the given connection.
   *
   * @param connection the connection to perform the query on
   * @return a sorted set of version numbers of the installed
   *         migrations
   */
  private
  def getInstalledVersions
    (connection: java.sql.Connection): scala.collection.SortedSet[Long] =
  {
    val sql = "SELECT version FROM " +
              adapter.quoteTableName(schemaMigrationsTableName)
    connection.withPreparedStatement(sql) { statement =>
      With.resultSet(statement.executeQuery()) { rs =>
        var versions = new scala.collection.immutable.TreeSet[Long]
        while (rs.next()) {
          val version_str = rs.getString(1)
          try {
            val version = java.lang.Long.parseLong(version_str)
            versions = versions.insert(version)
          }
          catch {
            case e: java.lang.NumberFormatException => {
              logger.warn("Ignoring installed migration with unparsable " +
                          "version number '" +
                          version_str +
                          "'.",
                          e)
            }
          }
        }

        versions
      }
    }
  }

  /**
   * Get a sorted list of all the installed migrations.
   *
   * @return a sorted set of version numbers of the installed
   *         migrations
   */
  def getInstalledVersions: scala.collection.SortedSet[Long] =
  {
    withLoggingConnection(AutoCommit) { connection =>
      getInstalledVersions(connection)
    }
  }

  /**
   * Migrate the database.
   *
   * Running this method, even if no concrete Migration subclasses are
   * found in the given package name, will result in the creation of
   * the schema_migrations table in the database, if it does not
   * currently exist.
   *
   * @param package_name the Java package name to search for Migration
   *        subclasses
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   * @param operation the migration operation that should be performed
   */
  def migrate(operation: MigratorOperation,
              package_name: String,
              search_sub_packages: Boolean): Unit =
  {
    initializeSchemaMigrationsTable()

    // Get a new connection that locks the schema_migrations table.
    // This will prevent concurrent migrations from running.  Commit
    // any modifications to schema_migrations regardless if an
    // exception is thrown or not, this ensures that any migrations
    // that were successfully run are recorded.
    withLoggingConnection(CommitUponReturnAndException) { schema_connection =>
      {
        logger.debug("Getting an exclusive lock on the '{}' table.",
                     schemaMigrationsTableName)
        val sql = "LOCK TABLE " +
                  adapter.quoteTableName(schemaMigrationsTableName) +
                  " IN EXCLUSIVE MODE"
        schema_connection.withPreparedStatement(sql) { statement =>
          statement.execute()
        }
      }

      // Get a list of all available and installed migrations.  Check
      // that all installed migrations have a migration class
      // available to migrate out of that migration.  This can happen
      // if the migration is applied by one copy of an application but
      // another copy does not have that migration, say the migration
      // was not checked into a source control system.  Having a
      // missing migration for an installed migration is not fatal
      // unless the migration needs to be rolled back.
      val installed_versions = getInstalledVersions(schema_connection).toArray
      val available_migrations = findMigrations(package_name,
                                                search_sub_packages,
                                                logger)
      val available_versions = available_migrations.keySet.toArray

      for (installed_version <- installed_versions) {
        if (! available_versions.contains(installed_version)) {
          logger.warn("The migration version '{}' is installed but " +
                      "there is no migration class available to back " +
                      "it out.",
                      installed_version)
        }
      }

      if (available_migrations.isEmpty) {
        logger.info("No migrations found, nothing to do.")
      }

      case class InstallRemove(install_versions: Array[Long],
                               remove_versions: Array[Long])

      // From the operation, determine the migrations to install and
      // the ones to uninstall.
      val install_remove =
        operation match {
          case InstallAllMigrations => {
            new InstallRemove(available_versions, new Array[Long](0))
          }
          case RemoveAllMigrations => {
            new InstallRemove(new Array[Long](0),
                              installed_versions.reverse)
          }
          case MigrateToVersion(version) => {
            val index = available_versions.findIndexOf(_ == version)
            if (index == -1) {
              val message = "The target version " +
                            version +
                            " does not exist as a migration."
              throw new RuntimeException(message)
            }
            new InstallRemove(available_versions.take(index + 1).toArray,
                              installed_versions.filter(_ > version).reverse)
          }
          case RollbackMigration(count) => {
            if (count > installed_versions.length) {
              val message = "Attempting to rollback " +
                            count +
                            " migrations but the database only has " +
                            installed_versions.length
                            " installed in it."
              throw new RuntimeException(message)
            }
            new InstallRemove(new Array[Long](0),
                              installed_versions.reverse.take(count))
          }
        }

      // Always remove migrations before installing new ones.
      for (remove_version <- install_remove.remove_versions) {
        // At the beginning of the method it wasn't a fatal error to
        // have a missing migration class for an installed migration,
        // but when it cannot be removed, it is.
        available_migrations.get(remove_version) match {
          case Some(clazz) => {
            runMigration(clazz,
                         Down,
                         Some((schema_connection, remove_version)))
          }
          case None => {
            val message = "The database has migration version '" +
                          remove_version +
                          "' installed but there is no migration class " +
                          "available with that version."
            logger.error(message)
            throw new MissingMigrationClass(message)
          }
        }
      }

      for (install_version <- install_remove.install_versions) {
        if (! installed_versions.contains(install_version)) {
          available_migrations.get(install_version) match {
            case Some(clazz) => {
              runMigration(clazz,
                           Up,
                           Some((schema_connection, install_version)))
            }
            case None => {
              val message = "Illegal state: trying to install a migration " +
                            "with version '" +
                            install_version +
                            "' that should exist."
              throw new MissingMigrationClass(message)
            }
          }
        }
      }
    }
  }

  /**
   * Get the status of all the installed and available migrations.  A
   * tuple-like class is returned that contains three groups of
   * migrations: installed migrations with an associated Migration
   * subclass, installed migration without an associated Migration
   * subclass and Migration subclasses that are not installed.
   *
   * @param package_name the Java package name to search for Migration
   *        subclasses
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   */
  def getMigrationStatuses(package_name: String,
                           search_sub_packages: Boolean): MigrationStatuses =
  {
    val available_migrations = findMigrations(package_name,
                                              search_sub_packages,
                                              logger)
    val installed_versions = if (doesSchemaMigrationsTableExist) {
                               getInstalledVersions
                             }
                             else {
                               new scala.collection.immutable.TreeSet[Long]
                             }

    var not_installed = available_migrations
    var installed_with_available_implementation =
      new scala.collection.immutable.TreeMap[Long,Class[_ <: Migration]]
    var installed_without_available_implementation =
      new scala.collection.immutable.TreeSet[Long]

    for (installed_version <- installed_versions) {
      not_installed -= installed_version
      available_migrations.get(installed_version) match {
        case Some(clazz) => {
          installed_with_available_implementation =
            installed_with_available_implementation.insert(installed_version,
                                                           clazz)
        }
        case None => {
          installed_without_available_implementation += installed_version
        }
      }
    }

    new MigrationStatuses(not_installed,
                          installed_with_available_implementation,
                          installed_without_available_implementation)
  }

  /**
   * Determine if the database has all available migrations installed
   * in it and no migrations installed that do not have a
   * corresponding concrete Migration subclass; that is, the database
   * must have only those migrations installed that are found by
   * searching the package name for concrete Migration subclasses.
   *
   * Running this method does not modify the database in any way.  The
   * schema migrations table is not created.
   *
   * @param package_name the Java package name to search for Migration
   *        subclasses
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   * @return None if all available migrations are installed and all
   *         installed migrations have a corresponding Migration
   *         subclass; Some(message) with a message suitable for
   *         logging with the not-installed migrations and the
   *         installed migrations that do not have a matching
   *         Migration subclass
   */
  def whyNotMigrated(package_name: String,
                     search_sub_packages: Boolean): Option[String] =
  {
    val migration_statuses = getMigrationStatuses(package_name,
                                                  search_sub_packages)

    val not_installed = migration_statuses.not_installed
    val installed_without_available_implementation =
      migration_statuses.installed_without_available_implementation

    if (not_installed.isEmpty &&
        installed_without_available_implementation.isEmpty) {
      None
    }
    else {
      val sb = new java.lang.StringBuilder(256)
      sb.append("The database is not fully migrated because ")

      if (! not_installed.isEmpty) {
        sb.append("the following migrations are not installed: ")
        sb.append(not_installed.values.map(_.getName).mkString(", "))
        if (! installed_without_available_implementation.isEmpty) {
          sb.append(" and ")
        }
      }

      if (! installed_without_available_implementation.isEmpty) {
        sb.append("the following migrations are installed without a " +
                  "matching Migration subclass: ")
        sb.append(installed_without_available_implementation.mkString(", "))
      }

      sb.append('.')

      Some(sb.toString)
    }
  }
}
