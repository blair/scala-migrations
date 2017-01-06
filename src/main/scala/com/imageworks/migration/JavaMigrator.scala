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
package com.imageworks.migration

/**
 * The Scala Migrator class uses Scala case classes in its public
 * constructors and public methods which makes it difficult to use
 * from a pure Java environment.  This class exposes a Java-style
 * interface and delegates to the Scala Migrator class.
 */
class JavaMigrator private (migrator: Migrator) {
  /**
   * JavaMigrator constructor.
   *
   * @param connectionBuilder a builder of connections to the
   *        database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(connectionBuilder: ConnectionBuilder,
           adapter: DatabaseAdapter) {
    this(new Migrator(connectionBuilder, adapter))
  }

  /**
   * JavaMigrator constructor.
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
   * JavaMigrator constructor.
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
    this(new ConnectionBuilder(jdbcUrl, jdbcUsername, jdbcPassword),
      adapter)
  }

  /**
   * Get a list of table names.  If the database adapter was given a
   * schema name then only the tables in that schema are returned.
   *
   * @return a set of table names; no modifications of the case of
   *         table names is done
   */
  def getTableNames: java.util.Set[String] = {
    val tableNames = migrator.getTableNames

    val set = new java.util.HashSet[String](tableNames.size)

    for (tableName <- tableNames)
      set.add(tableName)

    set
  }

  /**
   * Install all available migrations into the database.
   *
   * @param packageNames the sequence of package names that the Migration subclasses
   *        should be searched for
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   */
  def installAllMigrations(packageNames: Seq[String],
                           searchSubPackages: Boolean) {
    migrator.migrate(InstallAllMigrations, packageNames, searchSubPackages)
  }

  /**
   * Remove all installed migrations from the database.
   *
   * @param packageNames the sequence of package names that the Migration subclasses
   *        should be searched for
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   */
  def removeAllMigrations(packageNames: Seq[String],
                          searchSubPackages: Boolean) {
    migrator.migrate(RemoveAllMigrations, packageNames, searchSubPackages)
  }

  /**
   * Migrate the database to the given version.
   *
   * @param version the version number the database should be migrated
   *        to
   * @param packageNames the sequence of package names that the Migration subclasses
   *        should be searched for
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   */
  def migrateTo(version: Long,
                packageNames: Seq[String],
                searchSubPackages: Boolean) {
    migrator.migrate(MigrateToVersion(version), packageNames, searchSubPackages)
  }

  /**
   * Rollback a given number of migrations in the database.
   *
   * @param count the number of migrations to rollback
   *        to
   * @param packageNames the sequence of package names that the Migration subclasses
   *        should be searched for
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   */
  def rollback(count: Int,
               packageNames: Seq[String],
               searchSubPackages: Boolean) {
    migrator.migrate(RollbackMigration(count),
      packageNames,
      searchSubPackages)
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
   * @param packageNames the sequence of Java package names to search for Migration
   *        subclasses
   * @param searchSubPackages true if sub-packages of packageName
   *        should be searched
   * @return null if all available migrations are installed and all
   *         installed migrations have a corresponding Migration
   *         subclass; a non-null message suitable for logging with
   *         the not-installed migrations and the installed migrations
   *         that do not have a matching Migration subclass
   */
  def whyNotMigrated(packageNames: Seq[String],
                     searchSubPackages: Boolean): String = {
    migrator.whyNotMigrated(packageNames, searchSubPackages) match {
      case Some(message) => message
      case None => null
    }
  }
}
