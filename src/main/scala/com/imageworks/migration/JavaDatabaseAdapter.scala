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
 * The Scala Database Adapter classes uses Scala case classes in their public
 * constructors and public methods which makes it difficult to use from a pure
 * Java environment.  This class provides Java-friendly factory functions to
 * create adapters.
 */
object JavaDatabaseAdapter {
  /**
   * Create a Derby Database Adapter.
   *
   * @return newly constructed DerbyDatabaseAdapter
   */
  def getDerbyDatabaseAdapter: DerbyDatabaseAdapter = {
    new DerbyDatabaseAdapter(None)
  }

  /**
   * Create a Derby Database Adapter.
   *
   * @param schemaName the default schema name in the adapter
   * @return newly constructed DerbyDatabaseAdapter
   */
  def getDerbyDatabaseAdapter(schemaName: String): DerbyDatabaseAdapter = {
    new DerbyDatabaseAdapter(Some(schemaName))
  }

  /**
   * Create a MySQL Database Adapter.
   *
   * @return newly constructed MysqlDatabaseAdapter
   */
  def getMysqlDatabaseAdapter: MysqlDatabaseAdapter = {
    new MysqlDatabaseAdapter(None)
  }

  /**
   * Create a MySQL Database Adapter.
   *
   * @param schemaName the default schema name in the adapter
   * @return newly constructed MysqlDatabaseAdapter
   */
  def getMysqlDatabaseAdapter(schemaName: String): MysqlDatabaseAdapter = {
    new MysqlDatabaseAdapter(Some(schemaName))
  }

  /**
   * Create an Oracle Database Adapter.
   *
   * @return newly constructed OracleDatabaseAdapter
   */
  def getOracleDatabaseAdapter: OracleDatabaseAdapter = {
    new OracleDatabaseAdapter(None)
  }

  /**
   * Create an Oracle Database Adapter.
   *
   * @param schemaName the default schema name in the adapter
   * @return newly constructed OracleDatabaseAdapter
   */
  def getOracleDatabaseAdapter(schemaName: String): OracleDatabaseAdapter = {
    new OracleDatabaseAdapter(Some(schemaName))
  }

  /**
   * Create a PostgreSQL Database Adapter.
   *
   * @return newly constructed PostgresqlDatabaseAdapter
   */
  def getPostgresqlDatabaseAdapter: PostgresqlDatabaseAdapter = {
    new PostgresqlDatabaseAdapter(None)
  }

  /**
   * Create a PostgreSQL Database Adapter.
   *
   * @param schemaName the default schema name in the adapter
   * @return newly constructed PostgresqlDatabaseAdapter
   */
  def getPostgresqlDatabaseAdapter(schemaName: String): PostgresqlDatabaseAdapter = {
    new PostgresqlDatabaseAdapter(Some(schemaName))
  }

  /**
   * Create a H2 Database Adapter.
   *
   * @return newly constructed H2DatabaseAdapter
   */
  def getH2DatabaseAdapter: H2DatabaseAdapter = {
    new H2DatabaseAdapter(None)
  }

  /**
   * Create a H2 Database Adapter.
   *
   * @param schemaName the default schema name in the adapter
   * @return newly constructed H2DatabaseAdapter
   */
  def getH2DatabaseAdapter(schemaName: String): H2DatabaseAdapter = {
    new H2DatabaseAdapter(Some(schemaName))
  }
}
