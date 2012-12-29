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

import net.sf.log4jdbc.ConnectionSpy

import org.slf4j.{
  Logger,
  LoggerFactory
}

import scala.collection.{
  immutable,
  mutable
}

import java.net.{
  URL,
  URLDecoder
}
import java.sql.Connection
import java.util.jar.JarFile
import javax.sql.DataSource

/**
 * A migration to create the schema_migrations table that records
 * which migrations have been applied to a database.
 */
private class CreateSchemaMigrationsTableMigration
    extends Migration {
  override def up() {
    createTable(Migrator.schemaMigrationsTableName) { t =>
      t.varchar("version", Limit(32), NotNull)
    }

    addIndex(Migrator.schemaMigrationsTableName,
      Array("version"),
      Unique,
      Name("unique_schema_migrations"))
  }

  override def down() {
    throw new IrreversibleMigrationException
  }
}

object Migrator {
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
   * @param packageName the package name that the classes should be in
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return a set of the class names the JAR file contains
   */
  private def classNamesInJar(path: String,
                              packageName: String,
                              searchSubPackages: Boolean): mutable.HashSet[String] = {
    // Search for the package in the JAR file by mapping the package
    // name to the expected name in the JAR file, then append a '/' to
    // the package name to ensure that no matches are done on
    // different packages that share a common substring.
    val pn = packageName.replace('.', '/') + '/'

    val classNames = new mutable.HashSet[String]
    With.jarFile(new JarFile(path, false)) { jar =>
      val entries = jar.entries
      while (entries.hasMoreElements) {
        val name = entries.nextElement.getName
        if (name.startsWith(pn) && name.endsWith(".class")) {
          val className = name.substring(0, name.length - ".class".length)
            .replace('/', '.')
          if (searchSubPackages) {
            classNames += className
          }
          else if (!className.substring(pn.length).contains('.')) {
            classNames += className
          }
        }
      }
      classNames
    }
  }

  /**
   * Given a java.io.File for a directory containing compiled Java and
   * Scala classes, return a set of all names of the classes the
   * directory contains.
   *
   * @param file the java.io.File corresponding to the directory
   * @param packageName the package name that the classes should be
   *        in
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return a set of the class names the directory contains
   */
  private def classNamesInDir(file: java.io.File,
                              packageName: String,
                              searchSubPackages: Boolean): mutable.HashSet[String] = {
    val classNames = new mutable.HashSet[String]

    def scan(f: java.io.File,
             pn: String) {
      val childFiles = f.listFiles

      for (childFile <- childFiles) {
        if (searchSubPackages && childFile.isDirectory) {
          val childPackageName = if (pn.length > 0)
            pn + '.' + childFile.getName
          else
            childFile.getName
          scan(childFile, childPackageName)
        }
        else if (childFile.isFile) {
          val name = childFile.getName
          if (name.endsWith(".class")) {
            val className = pn +
              '.' +
              name.substring(0, name.length - ".class".length)
            classNames += className
          }
        }
      }
    }

    scan(file, packageName)

    classNames
  }

  /**
   * Given a resource's URL, return the names of all the classes in
   * the resource.
   *
   * @param url the resource's URL
   * @param packageName the Java package name to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return a set of class names in the resource
   */
  private def classNamesInResource(
    url: URL,
    packageName: String,
    searchSubPackages: Boolean): mutable.HashSet[String] = {
    val u = URLDecoder.decode(url.toString, "UTF-8")

    if (u.startsWith("jar:file:")) {
      // This URL ends with a ! character followed by the name of the
      // resource in the jar file, so just get the jar file path.
      val index = u.lastIndexOf('!')
      val path = if (index == -1)
        u.substring("jar:file:".length)
      else
        u.substring("jar:file:".length, index)
      classNamesInJar(path, packageName, searchSubPackages)
    }
    else if (u.startsWith("file:")) {
      val dir = u.substring("file:".length)
      val file = new java.io.File(dir)
      if (!file.isDirectory) {
        val message = "The resource URL '" +
          u +
          "' should be a directory but is not."
        throw new RuntimeException(message)
      }
      classNamesInDir(file, packageName, searchSubPackages)
    }
    else {
      val message = "Do not know how to get a list of classes in the " +
        "resource at '" +
        u +
        "' corresponding to the package '" +
        packageName +
        "'."
      throw new RuntimeException(message)
    }
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
   * @param packageName the Java package name to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return a sorted map with version number keys and the concrete
   *         Migration subclasses as the value
   */
  private def findMigrations(
    packageName: String,
    searchSubPackages: Boolean,
    logger: Logger): immutable.SortedMap[Long, Class[_ <: Migration]] = {
    // Ask the current class loader for the resources corresponding to
    // the package, which can refer to directories, jar files
    // accessible via the local filesystem or remotely accessible jar
    // files.  Only the first two are handled.
    val pn = packageName.replace('.', '/')

    val urls = this.getClass.getClassLoader.getResources(pn)
    if (!urls.hasMoreElements) {
      throw new RuntimeException("Cannot find a resource for package '" +
        packageName +
        "'.")
    }

    val classNames = new mutable.HashSet[String]
    while (urls.hasMoreElements) {
      val url = urls.nextElement
      logger.debug("For package '{}' found resource at '{}'.",
        Array[AnyRef](packageName, url): _*)

      classNames ++= classNamesInResource(url,
        packageName,
        searchSubPackages)
    }

    // Search through the class names for ones that are concrete
    // subclasses of Migration that have a no argument constructor.
    // Use a sorted map mapping the version to the class name so the
    // final results will be sorted in numerically increasing order.
    var seenVersions = new immutable.TreeMap[Long, String]
    val seenDescriptions = new mutable.HashMap[String, String]

    // Search for classes that have the proper format.
    val reStr = """Migrate_(\d+)_([_a-zA-Z0-9]*)"""
    val re = java.util.regex.Pattern.compile(reStr)

    // Classes to be skipped.  classNames cannot have items removed from it
    // inside the for loop below or not all elements in classNames may be
    // visited (iterator corruption).
    val skipNames = new mutable.HashSet[String]

    for (className <- classNames) {
      val index = className.lastIndexOf('.')
      val baseName = if (index == -1)
        className
      else
        className.substring(index + 1)
      val matcher = re.matcher(baseName)
      if (matcher.matches) {
        val versionStr = matcher.group(1)
        val description = matcher.group(2)
        try {
          val version = java.lang.Long.parseLong(versionStr)
          seenVersions.get(version) match {
            case Some(cn) => {
              val message = "The '" +
                className +
                "' migration defines a duplicate version number " +
                "with '" +
                cn +
                "'."
              throw new DuplicateMigrationVersionException(message)
            }
            case None => {
              seenVersions = seenVersions.insert(version, className)
            }
          }

          seenDescriptions.get(description) match {
            case Some(cn) => {
              val message = "The '" +
                className +
                "' defines a duplicate description with '" +
                cn +
                "'."
              throw new DuplicateMigrationDescriptionException(message)
            }
            case None => {
              seenDescriptions.put(description, className)
            }
          }
        }
      }
      else {
        skipNames += className
        logger.debug("Skipping '{}' because it does not match '{}'.",
          Array[AnyRef](className, reStr): _*)
      }
    }

    // Remove all the skipped class names from classNames.
    classNames --= skipNames

    var results = new immutable.TreeMap[Long, Class[_ <: Migration]]

    for ((version, className) <- seenVersions) {
      var c: Class[_] = null
      try {
        c = Class.forName(className)
        if (classOf[Migration].isAssignableFrom(c) &&
          !c.isInterface &&
          !java.lang.reflect.Modifier.isAbstract(c.getModifiers)) {
          try {
            // Ensure that there is a no-argument constructor.
            c.getConstructor()
            val castedClass = c.asSubclass(classOf[Migration])
            results = results.insert(version, castedClass)
          }
          catch {
            case e: NoSuchMethodException => {
              logger.debug("Unable to find a no-argument constructor for '" +
                className +
                "'",
                e)
            }
          }
        }
      }
      catch {
        case e: Exception => {
          logger.debug("Unable to load class '" +
            className +
            "'.",
            e)
        }
      }
    }

    results
  }
}

private class RawAndLoggingConnections(val raw: Connection,
                                       val logging: Connection)

/**
 * This class migrates the database into the desired state.
 */
class Migrator(connectionBuilder: ConnectionBuilder,
               adapter: DatabaseAdapter) {
  import Migrator._
  import RichConnection._

  private final val logger = LoggerFactory.getLogger(this.getClass)

  // Since log4jdbc is not in any public Maven repository [1][2], to
  // make it easier for developers that want to use Scala Migrations
  // and are using a build tool which has automatic dependency
  // resolution, so they do not need to manually download log4jdbc
  // themselves, make log4jdbc optional by dynamically checking if it
  // is available and use it if it is.
  // [1] http://code.google.com/p/log4jdbc/wiki/FAQ
  // [2] http://code.google.com/p/log4jdbc/issues/detail?id=19
  private final val log4jdbcAvailable = try {
    Class.forName("net.sf.log4jdbc.ConnectionSpy")
    true
  }
  catch {
    case _: Exception => false
  }

  /**
   * Construct a migrator to a database that does not need a username
   * and password.
   *
   * @param jdbcUrl the JDBC URL to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbcUrl: String,
           adapter: DatabaseAdapter) {
    this(new ConnectionBuilder(jdbcUrl), adapter)
  }

  /**
   * Construct a migrator to a database that needs a username and password.
   *
   * @param jdbcUrl the JDBC URL to connect to the database
   * @param jdbcUsername the username to log into the database
   * @param jdbcPassword the password associated with the database
   *        username
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbcUrl: String,
           jdbcUsername: String,
           jdbcPassword: String,
           adapter: DatabaseAdapter) {
    this(new ConnectionBuilder(jdbcUrl, jdbcUsername, jdbcPassword), adapter)
  }

  /**
   * Construct a migrator to a database with an existing DataSource.
   *
   * @param jdbcDatasource the JDBC DataSource to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbcDatasource: DataSource,
           adapter: DatabaseAdapter) {
    this(new ConnectionBuilder(jdbcDatasource), adapter)
  }

  /**
   * Construct a migrator to a database with an existing DataSource but
   * override default username and password.
   *
   * @param jdbcDatasource the JDBC DataSource to connect to the database
   * @param jdbcUsername the username to log into the database
   * @param jdbcPassword the password associated with the database
   *        username
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbcDatasource: DataSource,
           jdbcUsername: String,
           jdbcPassword: String,
           adapter: DatabaseAdapter) {
    this(new ConnectionBuilder(jdbcDatasource, jdbcUsername, jdbcPassword),
      adapter)
  }

  /**
   * Get a database connection that logs all calls to it and pass it
   * to a closure for the closure to use.  After the closure returns,
   * normally or by throwing an exception, close the connection.
   *
   * @param commitBehavior specify the auto-commit mode on the
   *        connection and whether to commit() or rollback() the
   *        transaction if the auto-commit mode is disabled on the
   *        connection
   * @param f a Function1[Connection,T] that is passed a new
   *        connection
   * @return what f returns
   */
  private[migration] def withLoggingConnection[T](commitBehavior: CommitBehavior)(f: Connection => T): T = {
    connectionBuilder.withConnection(commitBehavior) { rawConnection =>
      val c = if (log4jdbcAvailable)
        new ConnectionSpy(rawConnection)
      else
        rawConnection
      f(c)
    }
  }

  /**
   * Get a tuple of database connections, a raw one and one that logs
   * all calls to it and pass both to a closure for the closure to
   * use.  After the closure returns, normally or by throwing an
   * exception, close the raw connection.
   *
   * @param commitBehavior specify the auto-commit mode on the
   *        connection and whether to commit() or rollback() the
   *        transaction if the auto-commit mode is disabled on the
   *        connection
   * @param f a Function1[RawAndLoggingConnections,T] that is passed a
   *        pair of related connections
   * @return what f returns
   */
  private[migration] def withConnections[T](commitBehavior: CommitBehavior)(f: RawAndLoggingConnections => T): T = {
    connectionBuilder.withConnection(commitBehavior) { rawConnection =>
      val c = if (log4jdbcAvailable)
        new ConnectionSpy(rawConnection)
      else
        rawConnection
      f(new RawAndLoggingConnections(rawConnection, c))
    }
  }

  /**
   * Get a list of table names.  If the database adapter was given a
   * schema name then only the tables in that schema are returned.
   *
   * @return a set of table names; no modifications of the case of
   *         table names is done
   */
  def getTableNames: mutable.Set[String] = {
    withLoggingConnection(AutoCommit) { connection =>
      val schemaPattern = adapter.schemaNameOpt match {
        case Some(n) => adapter.unquotedNameConverter(n)
        case None => null
      }
      val metadata = connection.getMetaData
      With.autoClosingResultSet(metadata.getTables(null,
        schemaPattern,
        null,
        Array("TABLE"))) { rs =>
        val names = new mutable.HashSet[String]
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
   * @param migrationClass the class of migration to execute
   * @param direction the direction the migration should be run
   * @param versionUpdateOpt if provided, the schema_migrations table
   *        is updated using the given connection and migration
   *        version number; this allows this method to
   */
  private def runMigration(migrationClass: Class[_ <: Migration],
                           direction: MigrationDirection,
                           versionUpdateOpt: Option[(Connection, Long)]) {
    logger.info("Migrating {} with '{}'.",
      Array[AnyRef](direction.str, migrationClass.getName): _*)

    val migration = migrationClass.getConstructor().newInstance()
    withConnections(AutoCommit) { connections =>
      migration.adapterOpt = Some(adapter)
      migration.rawConnectionOpt = Some(connections.raw)
      migration.connectionOpt = Some(connections.logging)

      direction match {
        case Up => migration.up()
        case Down => migration.down()
      }
    }

    versionUpdateOpt match {
      case Some((schemaConnection, version)) => {
        val tableName = adapter.quoteTableName(schemaMigrationsTableName)
        val sql =
          direction match {
            case Up => "INSERT INTO " +
              tableName +
              " (version) VALUES (?)"
            case Down => "DELETE FROM " +
              tableName +
              " WHERE version = ?"
          }

        schemaConnection.withPreparedStatement(sql) { statement =>
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
  private def doesSchemaMigrationsTableExist: Boolean = {
    val smtn = Migrator.schemaMigrationsTableName.toLowerCase
    getTableNames.find(_.toLowerCase == smtn) match {
      case Some(_) => true
      case None => false
    }
  }

  /**
   * Creates the schema migrations table if it does not exist.
   */
  private def initializeSchemaMigrationsTable() {
    if (!doesSchemaMigrationsTableExist) {
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
  private def getInstalledVersions(connection: Connection): scala.collection.SortedSet[Long] = {
    val sql = "SELECT version FROM " +
      adapter.quoteTableName(schemaMigrationsTableName)
    connection.withPreparedStatement(sql) { statement =>
      With.autoClosingResultSet(statement.executeQuery()) { rs =>
        var versions = new immutable.TreeSet[Long]
        while (rs.next()) {
          val versionStr = rs.getString(1)
          try {
            val version = java.lang.Long.parseLong(versionStr)
            versions = versions.insert(version)
          }
          catch {
            case e: NumberFormatException => {
              logger.warn("Ignoring installed migration with unparsable " +
                "version number '" +
                versionStr +
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
  def getInstalledVersions: scala.collection.SortedSet[Long] = {
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
   * @param packageName the Java package name to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @param operation the migration operation that should be performed
   */
  def migrate(operation: MigratorOperation,
              packageName: String,
              searchSubPackages: Boolean) {
    initializeSchemaMigrationsTable()

    // Get a new connection that locks the schema_migrations table.
    // This will prevent concurrent migrations from running.  Commit
    // any modifications to schema_migrations regardless if an
    // exception is thrown or not, this ensures that any migrations
    // that were successfully run are recorded.
    withLoggingConnection(CommitUponReturnOrException) { schemaConnection =>
      logger.debug("Getting an exclusive lock on the '{}' table.",
        schemaMigrationsTableName)
      val sql = adapter.lockTableSql(schemaMigrationsTableName)
      schemaConnection.withPreparedStatement(sql) { statement =>
        statement.execute()
      }

      // Get a list of all available and installed migrations.  Check
      // that all installed migrations have a migration class
      // available to migrate out of that migration.  This can happen
      // if the migration is applied by one copy of an application but
      // another copy does not have that migration, say the migration
      // was not checked into a source control system.  Having a
      // missing migration for an installed migration is not fatal
      // unless the migration needs to be rolled back.
      val installedVersions = getInstalledVersions(schemaConnection).toArray
      val availableMigrations = findMigrations(packageName,
        searchSubPackages,
        logger)
      val availableVersions = availableMigrations.keySet.toArray

      for (installedVersion <- installedVersions) {
        if (!availableVersions.contains(installedVersion)) {
          logger.warn("The migration version '{}' is installed but " +
            "there is no migration class available to back " +
            "it out.",
            installedVersion)
        }
      }

      if (availableMigrations.isEmpty) {
        logger.info("No migrations found, nothing to do.")
      }

      case class InstallRemove(installVersions: Array[Long],
                               removeVersions: Array[Long])

      // From the operation, determine the migrations to install and
      // the ones to uninstall.
      val installRemove =
        operation match {
          case InstallAllMigrations => {
            new InstallRemove(availableVersions, new Array[Long](0))
          }
          case RemoveAllMigrations => {
            new InstallRemove(new Array[Long](0),
              installedVersions.reverse)
          }
          case MigrateToVersion(version) => {
            val index = availableVersions.indexWhere(_ == version)
            if (index == -1) {
              val message = "The target version " +
                version +
                " does not exist as a migration."
              throw new RuntimeException(message)
            }
            new InstallRemove(availableVersions.take(index + 1).toArray,
              installedVersions.filter(_ > version).reverse)
          }
          case RollbackMigration(count) => {
            if (count > installedVersions.length) {
              val message = "Attempting to rollback " +
                count +
                " migrations but the database only has " +
                installedVersions.length +
                " installed in it."
              throw new RuntimeException(message)
            }
            new InstallRemove(new Array[Long](0),
              installedVersions.reverse.take(count))
          }
        }

      // Always remove migrations before installing new ones.
      for (removeVersion <- installRemove.removeVersions) {
        // At the beginning of the method it wasn't a fatal error to
        // have a missing migration class for an installed migration,
        // but when it cannot be removed, it is.
        availableMigrations.get(removeVersion) match {
          case Some(clazz) => {
            runMigration(clazz,
              Down,
              Some((schemaConnection, removeVersion)))
          }
          case None => {
            val message = "The database has migration version '" +
              removeVersion +
              "' installed but there is no migration class " +
              "available with that version."
            logger.error(message)
            throw new MissingMigrationClass(message)
          }
        }
      }

      for (installVersion <- installRemove.installVersions) {
        if (!installedVersions.contains(installVersion)) {
          availableMigrations.get(installVersion) match {
            case Some(clazz) => {
              runMigration(clazz,
                Up,
                Some((schemaConnection, installVersion)))
            }
            case None => {
              val message = "Illegal state: trying to install a migration " +
                "with version '" +
                installVersion +
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
   * @param packageName the Java package name to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   */
  def getMigrationStatuses(packageName: String,
                           searchSubPackages: Boolean): MigrationStatuses = {
    val availableMigrations = findMigrations(packageName,
      searchSubPackages,
      logger)
    val installedVersions = if (doesSchemaMigrationsTableExist)
      getInstalledVersions
    else
      new immutable.TreeSet[Long]

    var notInstalled = availableMigrations
    var installedWithAvailableImplementation =
      new immutable.TreeMap[Long, Class[_ <: Migration]]
    var installedWithoutAvailableImplementation =
      new immutable.TreeSet[Long]

    for (installedVersion <- installedVersions) {
      notInstalled -= installedVersion
      availableMigrations.get(installedVersion) match {
        case Some(clazz) => {
          installedWithAvailableImplementation =
            installedWithAvailableImplementation.insert(installedVersion,
              clazz)
        }
        case None => {
          installedWithoutAvailableImplementation += installedVersion
        }
      }
    }

    new MigrationStatuses(notInstalled,
      installedWithAvailableImplementation,
      installedWithoutAvailableImplementation)
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
   * @param packageName the Java package name to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return None if all available migrations are installed and all
   *         installed migrations have a corresponding Migration
   *         subclass; Some(message) with a message suitable for
   *         logging with the not-installed migrations and the
   *         installed migrations that do not have a matching
   *         Migration subclass
   */
  def whyNotMigrated(packageName: String,
                     searchSubPackages: Boolean): Option[String] = {
    val migrationStatuses = getMigrationStatuses(packageName,
      searchSubPackages)

    val notInstalled = migrationStatuses.notInstalled
    val installedWithoutAvailableImplementation =
      migrationStatuses.installedWithoutAvailableImplementation

    if (notInstalled.isEmpty &&
      installedWithoutAvailableImplementation.isEmpty) {
      None
    }
    else {
      val sb = new java.lang.StringBuilder(256)
      sb.append("The database is not fully migrated because ")

      if (!notInstalled.isEmpty) {
        sb.append("the following migrations are not installed: ")
        sb.append(notInstalled.valuesIterator.map(_.getName).mkString(", "))
        if (!installedWithoutAvailableImplementation.isEmpty) {
          sb.append(" and ")
        }
      }

      if (!installedWithoutAvailableImplementation.isEmpty) {
        sb.append("the following migrations are installed without a " +
          "matching Migration subclass: ")
        sb.append(installedWithoutAvailableImplementation.mkString(", "))
      }

      sb.append('.')

      Some(sb.toString)
    }
  }
}
