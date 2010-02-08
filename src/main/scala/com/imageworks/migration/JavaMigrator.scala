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
class JavaMigrator private (migrator: Migrator)
{
  /**
   * JavaMigrator constructor.
   *
   * @param jdbc_url the JDBC URL to connect to the database
   * @param adapter a concrete DatabaseAdapter that the migrator uses
   *        to handle database specific features
   */
  def this(jdbc_url: String,
           adapter: DatabaseAdapter) =
  {
    this(new Migrator(jdbc_url, adapter))
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
   */
  def this(jdbc_url: String,
           jdbc_username: String,
           jdbc_password: String,
           adapter: DatabaseAdapter) =
  {
    this(new Migrator(jdbc_url,
                      jdbc_username,
                      jdbc_password,
                      adapter))
  }

  /**
   * Get a list of table names.  If the database adapter was given a
   * schema name then only the tables in that schema are returned.
   *
   * @return a set of table names; no modifications of the case of
   *         table names is done
   */
  def getTableNames: java.util.Set[String] =
  {
    val table_names = migrator.getTableNames

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
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   */
  def installAllMigrations(package_name: String,
                           search_sub_packages: Boolean): Unit =
  {
    migrator.migrate(InstallAllMigrations, package_name, search_sub_packages)
  }

  /**
   * Remove all installed migrations from the database.
   *
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   */
  def removeAllMigrations(package_name: String,
                          search_sub_packages: Boolean): Unit =
  {
    migrator.migrate(RemoveAllMigrations, package_name, search_sub_packages)
  }

  /**
   * Migrate the database to the given version.
   *
   * @param version the version number the database should be migrated
   *        to
   * @param package_name the package name that the Migration
   *        subclasses should be searched for
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   */
  def migrateTo(version: Long,
                package_name: String,
                search_sub_packages: Boolean): Unit =
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
   * @param search_sub_packages true if sub-packages of package_name
   *        should be searched
   */
  def rollback(count: Int,
               package_name: String,
               search_sub_packages: Boolean): Unit =
  {
    migrator.migrate(RollbackMigration(count),
                     package_name,
                     search_sub_packages)
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
   * @return null if all available migrations are installed and all
   *         installed migrations have a corresponding Migration
   *         subclass; a non-null message suitable for for logging
   *         with the not-installed migrations and the installed
   *         migrations that do not have a matching Migration subclass
   */
  def whyNotMigrated(package_name: String,
                     search_sub_packages: Boolean): String =
  {
    migrator.whyNotMigrated(package_name, search_sub_packages) match {
      case Some(message) => message
      case None => null
    }
  }
}
