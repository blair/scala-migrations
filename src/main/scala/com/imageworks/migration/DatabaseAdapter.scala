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

object DatabaseAdapter {
  /**
   * Return the appropriate database adapter for the given database
   * vendor.
   *
   * @param vendor the database vendor
   * @param schemaNameOpt an optional schema name used to qualify all
   *        table names in the generated SQL; if Some(), then all
   *        table names are qualified with the name, otherwise, table
   *        names are unqualified
   * @return a DatabaseAdapter suitable to use for the database
   */
  def forVendor(vendor: Vendor,
                schemaNameOpt: Option[String]): DatabaseAdapter = {
    vendor match {
      case Derby =>
        new DerbyDatabaseAdapter(schemaNameOpt)

      case Mysql =>
        new MysqlDatabaseAdapter(schemaNameOpt)

      case Oracle =>
        new OracleDatabaseAdapter(schemaNameOpt)

      case Postgresql =>
        new PostgresqlDatabaseAdapter(schemaNameOpt)

      case null =>
        throw new IllegalArgumentException("Must pass a non-null vendor to " +
          "this function.")
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
abstract class DatabaseAdapter(val schemaNameOpt: Option[String]) {
  protected final val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * The vendor of the database.
   */
  val vendor: Vendor

  /**
   * The character that is used to quote identifiers.
   */
  val quoteCharacter: Char

  /**
   * To properly quote table names the database adapter needs to know
   * how the database treats unquoted names.
   */
  val unquotedNameConverter: UnquotedNameConverter

  /**
   * A factory for creating User instances from a bare user name.
   */
  val userFactory: UserFactory[_ <: User]

  /**
   * The SQL keyword(s) or "phrase" used to drop a foreign key
   * constraint.  For example, Derby, Oracle and PostgreSQL use
   *
   *   ALTER TABLE child DROP CONSTRAINT idx_child_pk_parent;
   *                          ^^^^^^^^^^
   *
   * while MySQL uses
   *
   *   ALTER TABLE child DROP FOREIGN KEY idx_child_pk_parent;
   *                          ^^^^^^^^^^^
   */
  val alterTableDropForeignKeyConstraintPhrase: String

  /**
   * This value is true if the database implicitly adds an index on
   * the column that has a foreign key constraint added to it.
   *
   * The following SQL can be used to test the database.  The last
   * statement will fail with a message that there already is an index
   * on the column.
   *
   *   CREATE TABLE parent (pk INT PRIMARY KEY);
   *   CREATE TABLE child (pk INT PRIMARY KEY, pk_parent INT NOT NULL);
   *   ALTER TABLE child
   *     ADD CONSTRAINT idx_child_pk_parent FOREIGN KEY (pk_parent)
   *     REFERENCES parent (pk);
   *   CREATE INDEX idx_child_pk_parent ON child (pk_parent);
   */
  val addingForeignKeyConstraintCreatesIndex: Boolean

  /**
   * If the database supports table and column check constraints.
   */
  val supportsCheckConstraints: Boolean

  /**
   * Given a table name, column name and column data type, return a
   * newly constructed and fully initialized ColumnDefinition.  The
   * class of the returned ColumnDefinition only depends upon the
   * input column data type.
   *
   * @param tableName the name of the table the column is in
   * @param columnName the column's name
   * @param columnType the data type of the column
   * @param options a list of column options customizing the column
   * @return a new ColumnDefinition
   */
  def newColumnDefinition(tableName: String,
                          columnName: String,
                          columnType: SqlType,
                          options: ColumnOption*): ColumnDefinition = {
    var opts = options.toList

    // Search for a CharacterSet option.
    var characterSetOpt: Option[CharacterSet] = None

    for (opt @ CharacterSet(_, _) <- opts) {
      opts = opts filter { _ ne opt }
      if (characterSetOpt.isDefined && characterSetOpt.get != opt) {
        logger.warn("Redefining the character set from '{}' to '{}'.",
          Array[AnyRef](characterSetOpt.get, opt): _*)
      }
      characterSetOpt = Some(opt)
    }

    // Warn if a CharacterSet is being used for a non-character type
    // column.
    if (characterSetOpt.isDefined)
      columnType match {
        case CharType =>
        case VarcharType =>
        case _ =>
          logger.warn("The '{}' option cannot be used for a '{}' column type.",
            Array[AnyRef](characterSetOpt.get, columnType): _*)
      }

    val d = columnDefinitionFactory(columnType, characterSetOpt)

    d.adapterOpt = Some(this)
    d.tableNameOpt = Some(tableName)
    d.columnNameOpt = Some(columnName)
    d.options = opts

    d.initialize()

    d
  }

  /**
   * Concrete subclasses must define this method that returns a newly
   * constructed, but uninitialized, concrete ColumnDefinition
   * subclass for the given SQL data type and optional CharacterSet.
   *
   * @param columnType the column's data type
   * @param characterSetOpt an optional CharacterSet
   * @return a newly constructed but uninitialized ColumnDefinition
   *         for the columnType
   */
  protected def columnDefinitionFactory(columnType: SqlType,
                                        characterSetOpt: Option[CharacterSet]): ColumnDefinition

  /**
   * Quote a schema name.
   *
   * @param schemaName the name of the schema to quote
   * @return a properly quoted schema name
   */
  def quoteSchemaName(schemaName: String): String = {
    quoteCharacter + unquotedNameConverter(schemaName) + quoteCharacter
  }

  /**
   * Quote a table name, prepending the quoted schema name to the
   * quoted table name along with a '.' if a schema name is provided.
   *
   * @param schemaNameOpt an optional schema name
   * @param tableName the name of the table to quote
   * @return the table name properly quoted for the database,
   *         prepended with the quoted schema name and a '.' if a
   *         schema name is provided
   */
  def quoteTableName(schemaNameOpt: Option[String],
                     tableName: String): String = {
    val sb = new java.lang.StringBuilder(128)

    schemaNameOpt match {
      case Some(schemaName) =>
        sb.append(quoteSchemaName(schemaName))
          .append('.')
      case None =>
    }

    sb.append(quoteCharacter)
      .append(unquotedNameConverter(tableName))
      .append(quoteCharacter)
      .toString
  }

  /**
   * Quote a table name.  If the database adapter was provided with a
   * default schema name, then the quoted table name is prepended with
   * the quoted schema name along with a '.'.
   *
   * @param tableName the name of the table to quote
   * @return the table name properly quoted for the database,
   *         prepended with the quoted schema name and a '.' if the
   *         database adapter was provided with a default schema name
   */
  def quoteTableName(tableName: String): String = {
    // use the default schemaNameOpt defined in the adapter
    quoteTableName(schemaNameOpt, tableName)
  }

  /**
   * Quote an index name.
   *
   * @param schemaNameOpt an optional schema name
   * @param indexName the name of the index to quote
   * @return a properly quoted index name
   */
  def quoteIndexName(schemaNameOpt: Option[String],
                     indexName: String): String = {
    val sb = new java.lang.StringBuilder(128)

    schemaNameOpt match {
      case Some(schemaName) =>
        sb.append(quoteSchemaName(schemaName))
          .append('.')
      case None =>
    }

    sb.append(quoteCharacter)
      .append(unquotedNameConverter(indexName))
      .append(quoteCharacter)
      .toString
  }

  /**
   * Quote a column name.
   *
   * @param columnName the name of the column to quote
   * @return a properly quoted column name
   */
  def quoteColumnName(columnName: String): String = {
    quoteCharacter + unquotedNameConverter(columnName) + quoteCharacter
  }

  /**
   * Different databases require different SQL to lock a table.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table to lock
   * @return the SQL to lock the table
   */
  def lockTableSql(schemaNameOpt: Option[String],
                   tableName: String): String = {
    "LOCK TABLE " +
      quoteTableName(schemaNameOpt, tableName) +
      " IN EXCLUSIVE MODE"
  }

  /**
   * Different databases require different SQL to lock a table.
   *
   * @param tableName the name of the table to lock
   * @return the SQL to lock the table
   */
  def lockTableSql(tableName: String): String = {
    lockTableSql(schemaNameOpt, tableName)
  }

  protected def alterColumnSql(schemaNameOpt: Option[String],
                               columnDefinition: ColumnDefinition): String

  /**
   * Different databases require different SQL to alter a column's
   * definition.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table with the column
   * @param columnName the name of the column
   * @param columnType the type the column is being altered to
   * @param options a possibly empty array of column options to
   *        customize the column
   * @return the SQL to alter the column
   */
  def alterColumnSql(schemaNameOpt: Option[String],
                     tableName: String,
                     columnName: String,
                     columnType: SqlType,
                     options: ColumnOption*): String = {
    alterColumnSql(schemaNameOpt,
      newColumnDefinition(tableName, columnName, columnType, options: _*))
  }

  /**
   * Different databases require different SQL to alter a column's
   * definition.  Uses the schemaNameOpt defined in the adapter.
   *
   * @param tableName the name of the table with the column
   * @param columnName the name of the column
   * @param columnType the type the column is being altered to
   * @param options a possibly empty array of column options to
   *        customize the column
   * @return the SQL to alter the column
   */
  def alterColumnSql(tableName: String,
                     columnName: String,
                     columnType: SqlType,
                     options: ColumnOption*): String = {
    alterColumnSql(schemaNameOpt,
      tableName,
      columnName,
      columnType,
      options: _*)
  }

  /**
   * Different databases require different SQL to drop a column.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table with the column
   * @param columnName the name of the column
   * @return the SQL to drop the column
   */
  def removeColumnSql(schemaNameOpt: Option[String],
                      tableName: String,
                      columnName: String): String = {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, tableName))
      .append(" DROP ")
      .append(quoteColumnName(columnName))
      .toString
  }

  /**
   * Different databases require different SQL to drop a column.
   * Uses the schemaNameOpt defined in the adapter.
   *
   * @param tableName the name of the table with the column
   * @param columnName the name of the column
   * @return the SQL to drop the column
   */
  def removeColumnSql(tableName: String,
                      columnName: String): String = {
    removeColumnSql(schemaNameOpt, tableName, columnName)
  }

  /**
   * Different databases require different SQL to drop an index.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table with the index
   * @param indexName the name of the index
   * @return the SQL to drop the index
   */
  def removeIndexSql(schemaNameOpt: Option[String],
                     tableName: String,
                     indexName: String): String = {
    "DROP INDEX " +
      quoteTableName(schemaNameOpt, indexName)
  }

  /**
   * Different databases require different SQL to drop an index.
   * Uses the schemaNameOpt defined in the adapter.
   *
   * @param tableName the name of the table with the index
   * @param indexName the name of the index
   * @return the SQL to drop the index
   */
  def removeIndexSql(tableName: String,
                     indexName: String): String = {
    removeIndexSql(schemaNameOpt, tableName, indexName)
  }

  private def privilegeToString(privilege: Privilege): String = {
    def formatColumns(columns: Seq[String]): String = {
      if (columns.isEmpty) ""
      else columns.mkString(" (", ", ", ")")
    }

    privilege match {
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

      case UsagePrivilege =>
        "USAGE"
    }
  }

  sealed trait PrivilegeTarget
  case object SchemaPrivilegeTarget extends PrivilegeTarget
  case class TablePrivilegeTarget(tableName: String) extends PrivilegeTarget

  private def grantRevokeCommon(action: String,
                                preposition: String,
                                schemaNameOpt: Option[String],
                                privilegeTarget: PrivilegeTarget,
                                grantees: Array[User],
                                privileges: Privilege*): String = {
    // The GRANT and REVOKE syntax is basically the same
    val sb = new java.lang.StringBuilder(256)
      .append(action)
      .append(' ')

    sb.append(privileges map { privilegeToString(_) } mkString (", "))

    val quotedGrantees = for (g <- grantees)
      yield g.quoted(unquotedNameConverter)

    sb.append(" ON ")

    privilegeTarget match {
      case SchemaPrivilegeTarget =>
        sb.append("SCHEMA ")
          .append(quoteSchemaName(schemaNameOpt.get))
      case TablePrivilegeTarget(tableName) =>
        sb.append(quoteTableName(schemaNameOpt, tableName))
    }

    sb.append(' ')
      .append(preposition)
      .append(' ')
      .append(quotedGrantees.mkString(", "))
      .toString
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table with the index
   * @param grantees one or more objects to grant the new privileges to
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of privileges to grant
   * @return the SQL to grant privileges
   */
  def grantOnTableSql(schemaNameOpt: Option[String],
                      tableName: String,
                      grantees: Array[User],
                      privileges: GrantPrivilegeType*): String = {
    grantRevokeCommon("GRANT",
      "TO",
      schemaNameOpt,
      TablePrivilegeTarget(tableName),
      grantees,
      privileges: _*)
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   * Uses the schemaNameOpt defined in the adapter.
   *
   * @param tableName the name of the table with the index
   * @param grantees one or more objects to grant the new privileges to
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of privileges to grant
   * @return the SQL to grant privileges
   */
  def grantOnTableSql(tableName: String,
                      grantees: Array[User],
                      privileges: GrantPrivilegeType*): String = {
    grantOnTableSql(schemaNameOpt, tableName, grantees, privileges: _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   *
   * @param schemaNameOpt the optional schema name to qualify the
   *        table name
   * @param tableName the name of the table with the index
   * @param grantees one or more objects to revoke the privileges from
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of privileges to revoke
   * @return the SQL to revoke privileges
   */
  def revokeOnTableSql(schemaNameOpt: Option[String],
                       tableName: String,
                       grantees: Array[User],
                       privileges: GrantPrivilegeType*): String = {
    grantRevokeCommon("REVOKE",
      "FROM",
      schemaNameOpt,
      TablePrivilegeTarget(tableName),
      grantees,
      privileges: _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   * Uses the schemaNameOpt defined in the adapter.
   *
   * @param tableName the name of the table with the index
   * @param grantees one or more objects to revoke the privileges from
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of privileges to revoke
   * @return the SQL to revoke privileges
   */
  def revokeOnTableSql(tableName: String,
                       grantees: Array[User],
                       privileges: GrantPrivilegeType*): String = {
    revokeOnTableSql(schemaNameOpt, tableName, grantees, privileges: _*)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param schemaName the name of the schema to grant privileges on
   * @param grantees one or more objects to grant the new privileges
   *        to
   * @param privileges one or more SchemaPrivilege objects describing
   *        the types of privileges to grant
   * @return the SQL to grant privileges
   */
  def grantOnSchemaSql(schemaName: String,
                       grantees: Array[User],
                       privileges: SchemaPrivilege*): String = {
    grantRevokeCommon("GRANT",
      "TO",
      Some(schemaName),
      SchemaPrivilegeTarget,
      grantees,
      privileges: _*)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param grantees one or more objects to grant the new privileges
   *        to
   * @param privileges one or more SchemaPrivilege objects describing
   *        the types of privileges to grant
   * @return the SQL to grant privileges
   */
  def grantOnSchemaSql(grantees: Array[User],
                       privileges: SchemaPrivilege*): String = {
    grantOnSchemaSql(schemaNameOpt.get, grantees, privileges: _*)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param schemaName the name of the schema to revoke privileges
   *        from
   * @param grantees one or more objects to revoke the privileges from
   * @param privileges one or more SchemaPrivilege objects describing
   *        the types of privileges to revoke
   * @return the SQL to revoke privileges
   */
  def revokeOnSchemaSql(schemaName: String,
                        grantees: Array[User],
                        privileges: SchemaPrivilege*): String = {
    grantRevokeCommon("REVOKE",
      "FROM",
      Some(schemaName),
      SchemaPrivilegeTarget,
      grantees,
      privileges: _*)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param grantees one or more objects to revoke the privileges from
   * @param privileges one or more SchemaPrivilege objects describing
   *        the types of privileges to revoke
   * @return the SQL to revoke privileges
   */
  def revokeOnSchemaSql(grantees: Array[User],
                        privileges: SchemaPrivilege*): String = {
    revokeOnSchemaSql(schemaNameOpt.get, grantees, privileges: _*)
  }

  /**
   * Given a check constraint, create a name for it, using a Name() if it is
   * provided in the options.
   *
   * @param on the table and columns the check constraint is on
   * @param options a varargs list of CheckOptions
   * @return a two-tuple with the calculated name or the overridden
   *         name from a Name and the remaining options
   */
  def generateCheckConstraintName(on: On,
                                  options: CheckOption*): (String, List[CheckOption]) = {
    var opts = options.toList

    var chkNameOpt: Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts = opts filter { _ ne opt }
      if (chkNameOpt.isDefined && chkNameOpt.get != name) {
        logger.warn("Redefining the check constraint name from '{}' to '{}'.",
          Array[AnyRef](chkNameOpt.get, name): _*)
      }
      chkNameOpt = Some(name)
    }

    val name = chkNameOpt.getOrElse {
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
   * @param onDeleteOpt an Option[OnDelete]
   * @return the SQL text to append to the SQL to create a foreign
   *         key relationship
   */
  def onDeleteSql(onDeleteOpt: Option[OnDelete]): String = {
    onDeleteOpt match {
      case Some(onDelete) => "ON DELETE " + onDelete.action.sql
      case None => ""
    }
  }

  /**
   * Return the SQL text in a foreign key relationship for an optional
   * ON UPDATE clause.
   *
   * @param onUpdateOpt an Option[OnUpdate]
   * @return the SQL text to append to the SQL to create a foreign
   *         key relationship
   */
  def onUpdateSql(onUpdateOpt: Option[OnUpdate]): String = {
    onUpdateOpt match {
      case Some(onUpdate) => "ON UPDATE " + onUpdate.action.sql
      case None => ""
    }
  }
}
