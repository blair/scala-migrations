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

import org.slf4j.LoggerFactory

object DatabaseAdapter
{
  /**
   * Return the appropriate database adapter for the given database
   * vendor.
   *
   * @param vendor the database vendor
   * @param schema_name_opt an optional schema name used to qualify
   *        all table names in the generated SQL; if Some(), then all
   *        table names are qualified with the name, otherwise, table
   *        names are unqualified
   * @return a DatabaseAdapter suitable to use for the database
   */
  def forVendor(vendor: Vendor,
                schema_name_opt: Option[String]): DatabaseAdapter =
  {
    vendor match {
      case Derby =>
        new DerbyDatabaseAdapter(schema_name_opt)

      case Oracle =>
        new OracleDatabaseAdapter(schema_name_opt)

      case Postgresql =>
        new PostgresqlDatabaseAdapter(schema_name_opt)

      case null =>
        throw new java.lang.IllegalArgumentException("Must pass a non-null " +
                                                     "vendor to this " +
                                                     "function.")
    }
  }
}

/**
 * Base class for classes to customize SQL generation for specific
 * database drivers.
 *
 * @param schemaNameOpt an optional schema name used to qualify all
 *        table names in the generated SQL; if Some(), then all table
 *        names are qualified with the name, otherwise, table names
 *        are unqualified
 */
abstract
class DatabaseAdapter(val schemaNameOpt: Option[String])
{
  protected final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * To properly quote table names the database adapter needs to know
   * how the database treats with unquoted names.
   */
  protected
  val unquotedNameConverter: UnquotedNameConverter

  /**
   * This value is true if the database implicitly adds an index on
   * the column that has a foreign key constraint added to it.
   *
   * The following SQL can be used to test the database.  The last
   * statement will fail with a message that there already is an index
   * on the column.
   *
   *   create table parent (pk int primary key);
   *   create table child (pk int primary key, pk_parent int not null);
   *   alter table child
   *     add constraint idx_child_pk_parent foreign key (pk_parent)
   *     references parent (pk);
   *   create index idx_child_pk_parent on child (pk_parent);
   */
  val addingForeignKeyConstraintCreatesIndex: Boolean

  /**
   * Given a table name, column name and column data type, return a
   * newly constructed and fully initialized ColumnDefinition.  The
   * class of the returned ColumnDefinition only depends upon the
   * input column data type.
   *
   * @param table_name the name of the table the column is in
   * @param column_name the column's name
   * @param column_type the data type of the column
   * @param options a list of column options customizing the column
   * @return a new ColumnDefinition
   */
  def newColumnDefinition(table_name: String,
                          column_name: String,
                          column_type: SqlType,
                          options: ColumnOption*): ColumnDefinition =
  {
    var opts = options.toList

    // Search for a CharacterSet option.
    var character_set_opt: Option[CharacterSet] = None

    for (opt @ CharacterSet(name) <- opts) {
      opts = opts.filter(_ != opt)
      if (character_set_opt.isDefined && character_set_opt.get != name) {
        logger.warn("Redefining the character set from '{}'' to '{}'.",
                    character_set_opt.get,
                    name)
      }
      character_set_opt = Some(opt)
    }

    // Warn if a CharacterSet is being used for a non-character type
    // column.
    if (character_set_opt.isDefined)
      column_type match {
        case CharType =>
        case VarcharType =>
        case column_type => {
          logger.warn("The '{}' option cannot be used for a '{}' column type.",
                      character_set_opt.get,
                      column_type)
      }
    }

    val d = columnDefinitionFactory(column_type, character_set_opt)

    d.adapterOpt = Some(this)
    d.tableNameOpt = Some(table_name)
    d.columnNameOpt = Some(column_name)
    d.options = opts

    d.initialize()

    d
  }

  /**
   * Concrete subclasses must define this method that returns a newly
   * constructed, but uninitialized, concrete ColumnDefinition
   * subclass for the given SQL data type and optional CharacterSet.
   *
   * @param column_type the column's data type
   * @param character_set_opt an optional CharacterSet
   * @return a newly constructed but uninitialized ColumnDefinition
   *         for the column_type
   */
  protected
  def columnDefinitionFactory
    (column_type: SqlType,
     character_set_opt: Option[CharacterSet]): ColumnDefinition

  def quoteColumnName(column_name: String): String =
  {
    '"' +
    unquotedNameConverter(column_name) +
    '"'
  }

  def quoteTableName(schema_name_opt: Option[String],
                     table_name: String): String =
  {
    if (schema_name_opt.isDefined) {
      '"' +
      unquotedNameConverter(schema_name_opt.get) +
      "\".\"" +
      unquotedNameConverter(table_name) +
      '"'
    }
    else {
      '"' + unquotedNameConverter(table_name) + '"'
    }
  }

  def quoteTableName(table_name: String): String =
  {
    // use the default schema_name_opt defined in the adapter
    quoteTableName(schemaNameOpt, table_name)
  }

  /**
   * Different databases require different SQL to drop a column.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the column
   * @param column_name the name of the column
   * @return the SQL to drop the column
   */
  def removeColumnSql(schema_name_opt: Option[String],
                      table_name: String,
                      column_name: String): String =
  {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schema_name_opt, table_name))
      .append(" DROP ")
      .append(quoteColumnName(column_name))
      .toString
  }

  /**
   * Different databases require different SQL to drop a column.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the column
   * @param column_name the name of the column
   * @return the SQL to drop the column
   */
  def removeColumnSql(table_name: String,
                      column_name: String): String =
  {
    removeColumnSql(schemaNameOpt, table_name, column_name)
  }

  /**
   * Different databases require different SQL to drop an index.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param index_name the name of the index
   * @return the SQL to drop the index
   */
  def removeIndexSql(schema_name_opt: Option[String],
                     table_name: String,
                     index_name: String): String

  /**
   * Different databases require different SQL to drop an index.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param index_name the name of the index
   * @return the SQL to drop the index
   */
  def removeIndexSql(table_name: String,
                     index_name: String): String =
  {
    removeIndexSql(schemaNameOpt, table_name, index_name)
  }

  private
  def grantRevokeCommon(action: String,
                        preposition: String,
                        schema_name_opt: Option[String],
                        table_name: String,
                        grantees: Array[String],
                        privileges: GrantPrivilegeType*): String =
  {
    // The GRANT and REVOKE syntax is basically the same
    val sql = new java.lang.StringBuilder(256)
               .append(action)
               .append(' ')

    def formatColumns(columns: Seq[String]): String =
    {
      if (columns.isEmpty) {
        ""
      }
      else {
        columns.mkString(" (", ", ", ")")
      }
    }

    sql.append((
      for (priv <- privileges) yield priv match {
        case AllPrivileges =>
          "ALL PRIVILEGES"
        case DeletePrivilege =>
          "DELETE"
        case InsertPrivilege =>
          "INSERT"
        case TriggerPrivilege =>
          "TRIGGER"

        case ReferencesPrivilege =>
          "REFERENCES"
        case SelectPrivilege =>
          "SELECT"
        case UpdatePrivilege =>
          "UPDATE"

        case ReferencesPrivilege(columns) =>
          "REFERENCES" + formatColumns(columns)
        case SelectPrivilege(columns) =>
          "SELECT" + formatColumns(columns)
        case UpdatePrivilege(columns) =>
          "UPDATE" + formatColumns(columns)
      }).mkString(", "))

    sql.append(" ON ")
       .append(quoteTableName(table_name))
       .append(' ')
       .append(preposition)
       .append(' ')
       .append(grantees.mkString(", "))
       .toString
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def grantSql(schema_name_opt: Option[String],
               table_name: String,
               grantees: Array[String],
               privileges: GrantPrivilegeType*): String =
  {
    val sql = new java.lang.StringBuilder(256)
               .append("GRANT")

    grantRevokeCommon("GRANT",
                      "TO",
                      schema_name_opt,
                      table_name,
                      grantees,
                      privileges: _*)
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def grantSql(table_name: String,
               grantees: Array[String],
               privileges: GrantPrivilegeType*): String =
  {
    grantSql(schemaNameOpt, table_name, grantees, privileges: _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def revokeSql(schema_name_opt: Option[String],
                table_name: String,
                grantees: Array[String],
                privileges: GrantPrivilegeType*): String =
  {
    grantRevokeCommon("REVOKE",
                      "FROM",
                      schema_name_opt,
                      table_name,
                      grantees,
                      privileges: _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def revokeSql(table_name: String,
                grantees: Array[String],
                privileges: GrantPrivilegeType*): String =
  {
    revokeSql(schemaNameOpt, table_name, grantees, privileges: _*)
  }

  /**
   * Given a check constraint, create a name for it, using a Name() if it is
   * provided in the options.
   *
   * @param on the table and columns the check constraint is on
   * @param options a varargs list of CheckOptions
   * @return a Tuple2 with the calculated name or the overridden name
   *         from a Name and the remaining options
   */
  def generateCheckConstraintName
    (on: On,
     options: CheckOption*) : Tuple2[String,List[CheckOption]] =
  {
    var opts = options.toList

    var chk_name_opt: Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts = opts.filter(_ != opt)
      if (chk_name_opt.isDefined && chk_name_opt.get != name) {
        logger.warn("Redefining the check constraint name from '{}'' to '{}'.",
                    chk_name_opt.get,
                    name)
      }
      chk_name_opt = Some(name)
    }

    val name = chk_name_opt.getOrElse {
                 "chk_" +
                 on.tableName +
                 "_" +
                 on.columnNames.mkString("_")
               }

    (name, opts)
  }

  /**
   * Return the SQL text in a foreign key relationship for an optional
   * ON DELETE clause.
   *
   * @param on_delete_opt an Option[OnDelete]
   * @param the SQL text to append to the SQL to create a foreign key
   *        relationship
   */
  def onDeleteSql(on_delete_opt: Option[OnDelete]): String =
  {
    on_delete_opt match {
      case Some(on_delete) => "ON DELETE " + on_delete.action.sql
      case None => ""
    }
  }

  /**
   * Return the SQL text in a foreign key relationship for an optional
   * ON UPDATE clause.
   *
   * @param on_update_opt an Option[OnUpdate]
   * @param the SQL text to append to the SQL to create a foreign key
   *        relationship
   */
  def onUpdateSql(on_update_opt: Option[OnUpdate]): String =
  {
    on_update_opt match {
      case Some(on_update) => "ON UPDATE " + on_update.action.sql
      case None => ""
    }
  }
}
