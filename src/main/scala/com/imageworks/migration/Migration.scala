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

import java.sql.{
  Connection,
  PreparedStatement,
  ResultSet
}

/**
 * Due to the JVM erasure, the scala.Predef.ArrowAssoc.->
 * method generates a Tuple2 and the following cannot be distinguished
 *
 *   "table_name" -> "column1"
 *
 *   "table_name" -> ("column1", "column2")
 *
 * After erasure a Tuple2[String,String] is identical to a
 * Tuple2[String,Tuple2[String,String]].  So to work around this, the
 * -> operator is redefined to operate only on String's, which
 * effectively removes the type from the first type of the Tuple2 and
 * allows it to be overloaded on the second type of the Tuple2.  The
 * MigrationArrowAssoc class has the new -> method.
 */
class MigrationArrowAssoc(s: String) {
  def `->`(other: String): TableColumnDefinition = {
    new TableColumnDefinition(s, Array(other))
  }

  def `->`(other: (String, String)): TableColumnDefinition = {
    new TableColumnDefinition(s, Array(other._1, other._2))
  }
}

abstract class Migration {
  private final val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Concrete migration classes must define this method to migrate the
   * database up to a new migration.
   */
  def up()

  /**
   * Concrete migration classes must define this method to back out of
   * this migration.  If the migration cannot be reversed, then a
   * IrreversibleMigrationException should be thrown.
   */
  def down()

  /**
   * The raw connection to the database that underlies the logging
   * connection.  This is provided in case the real database
   * connection is needed because the logging connection does not
   * provide a required feature.  This connection should not be used
   * in normal use.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  private[migration] var rawConnectionOpt: Option[Connection] = None

  /**
   * Get the raw connection to the database the migration can use for
   * any custom work.  This connection is the raw connection that
   * underlies the logging connection and does not log any operations
   * performed on it.  It should only be used when the logging
   * connection does not provide a required feature.  The Migration
   * subclass must be careful with this connection and leave it in a
   * good state, as all of the other migration methods defined in
   * Migration use the same connection.
   */
  def rawConnection = rawConnectionOpt.get

  /**
   * The connection to the database that is used for the migration.
   * This connection also logs all operations performed on it.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  private[migration] var connectionOpt: Option[Connection] = None

  /**
   * Get the connection to the database the migration can use for any
   * custom work.  This connection logs all operations performed on
   * it.  The Migration subclass must be careful with this connection
   * and leave it in a good state, as all of the other migration
   * methods defined in Migration use the same connection.
   */
  def connection = connectionOpt.get

  /**
   * The database adapter that will be used for the migration.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  private[migration] var adapterOpt: Option[DatabaseAdapter] = None

  /**
   * The database adapter that will be used for the migration.
   */
  private def adapter = adapterOpt.get

  /**
   * The vendor of the database the migration is being run on.
   */
  def databaseVendor: Vendor = adapter.vendor

  /**
   * Override the -> implicit definition to create a
   * MigrationArrowAssoc instead of a scala.Predef.ArrowAssoc.  See
   * the above comment on the MigrationArrowAssoc class why this is
   * done.
   */
  implicit def stringToMigrationArrowAssoc(s: String): MigrationArrowAssoc = {
    new MigrationArrowAssoc(s)
  }

  /**
   * Convert a table and column name definition into a On foreign key
   * instance.
   */
  def on(definition: TableColumnDefinition): On = {
    new On(definition)
  }

  /**
   * Convert a table and column name definition into a References
   * foreign key instance.
   */
  def references(definition: TableColumnDefinition): References = {
    new References(definition)
  }

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
  def addingForeignKeyConstraintCreatesIndex: Boolean = {
    adapter.addingForeignKeyConstraintCreatesIndex
  }

  /**
   * Execute the given SQL string using the migration's connection.
   *
   * @param sql the SQL to execute
   */
  final def execute(sql: String) {
    With.autoClosingStatement(connection.createStatement) { s =>
      s.execute(sql)
    }
  }

  /**
   * Given a SQL string and a Function1[PreparedStatement,Unit], start
   * a new transaction by turning off auto-commit mode on the
   * connection then create a new prepared statement with the SQL
   * string and pass the prepared statement to the closure argument.
   * The closure should not perform the commit as this method will
   * commit the transaction.  If the closure throws an exception then
   * the transaction is rolled back and the exception that caused the
   * rollback is re-thrown.  Finally, the auto-commit state is reset
   * to the value the connection had before this method was called.
   *
   * @param sql the SQL text that will be prepared
   * @param f the Function1[PreparedStatement,Unit] that will be given
   *        a new prepared statement
   */
  final def withPreparedStatement(sql: String)(f: PreparedStatement => Unit) {
    With.autoCommittingConnection(connection,
      CommitUponReturnOrRollbackUponException) { c =>
        With.autoClosingStatement(c.prepareStatement(sql))(f)
      }
  }

  /**
   * Given a SQL result set and a Function1[ResultSet,R], pass the
   * result set to the closure.  After the closure has completed,
   * either normally via a return or by throwing an exception, close
   * the result set.
   *
   * @param rs the SQL result set
   * @param f the Function1[ResultSet,R] that will be given the result
   *        set
   * @return the result of f if f returns normally
   */
  final def withResultSet[R](rs: ResultSet)(f: ResultSet => R): R = {
    With.autoClosingResultSet(rs)(f)
  }

  final def createTable(tableName: String,
                        options: TableOption*)(body: TableDefinition => Unit) {
    val tableDefinition = new TableDefinition(adapter, tableName)

    body(tableDefinition)

    val sql = new java.lang.StringBuilder(512)
      .append("CREATE TABLE ")
      .append(adapter.quoteTableName(tableName))
      .append(" (")
      .append(tableDefinition.toSql)
      .append(')')
      .toString
    execute(sql)
  }

  final def addColumn(tableName: String,
                      columnName: String,
                      columnType: SqlType,
                      options: ColumnOption*) {
    val tableDefinition = new TableDefinition(adapter, tableName)

    tableDefinition.column(columnName, columnType, options: _*)
    val sql = new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(adapter.quoteTableName(tableName))
      .append(" ADD ")
      .append(tableDefinition.toSql)
      .toString
    execute(sql)
  }

  /**
   * Alter the definition of an existing column.
   *
   * NOTE: if the original column definition uses CharacterSet() then
   * it must be used here again, unless the base SQL data type is
   * being changed.  For example, on Oracle, creating a column without
   * CharacterSet uses VARCHAR2 while using CharacterSet(Unicode) uses
   * NVARCHAR2, so if the original column used CharacterSet(Unicode)
   * and #alterColumn() is not passed CharacterSet(Unicode), then the
   * column's data type will be change from NVARCHAR2 to VARCHAR2.
   *
   * @param tableName the name of the table with the column
   * @param columnName the name of the column
   * @param columnType the type the column is being altered to
   * @param options a possibly empty array of column options to
   *        customize the column
   */
  final def alterColumn(tableName: String,
                        columnName: String,
                        columnType: SqlType,
                        options: ColumnOption*) {
    execute(adapter.alterColumnSql(tableName,
      columnName,
      columnType,
      options: _*))
  }

  final def removeColumn(tableName: String,
                         columnName: String) {
    execute(adapter.removeColumnSql(tableName, columnName))
  }

  final def dropTable(tableName: String) {
    val sql = new java.lang.StringBuilder(512)
      .append("DROP TABLE ")
      .append(adapter.quoteTableName(tableName))
      .toString
    execute(sql)
  }

  private def indexNameFor(tableName: String,
                           columnNames: Array[String],
                           options: IndexOption*): (String, List[IndexOption]) = {
    var opts = options.toList

    var indexNameOpt: Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts = opts filter { _ ne opt }
      if (indexNameOpt.isDefined && indexNameOpt.get != name) {
        logger.warn("Redefining the index name from '{}' to '{}'.",
          Array[AnyRef](indexNameOpt.get, name): _*)
      }
      indexNameOpt = Some(name)
    }

    val name = indexNameOpt.getOrElse {
      "idx_" +
        tableName +
        "_" +
        columnNames.mkString("_")
    }

    (name, opts)
  }

  /**
   * Add an index to a table on a non-empty list of column names.  The
   * name of the index is automatically generated unless Name() is
   * given as an option.
   *
   * @param tableName the table to add the index to
   * @param columnNames a list of one or more column names that the
   *        index will be on
   * @param options a possibly empty list of index options to
   *        customize the creation of the index
   */
  final def addIndex(tableName: String,
                     columnNames: Array[String],
                     options: IndexOption*) {
    if (columnNames.isEmpty) {
      throw new IllegalArgumentException("Adding an index requires at " +
        "least one column name.")
    }

    var (name, opts) = indexNameFor(tableName, columnNames, options: _*)

    var unique = false
    for (opt @ Unique <- opts) {
      opts = opts filter { _ ne opt }
      unique = true
    }

    val a = adapter
    val quotedColumnNames = columnNames.map {
      a.quoteColumnName(_)
    }.mkString(", ")

    val sql = new java.lang.StringBuilder(512)
      .append("CREATE ")
      .append(if (unique) "UNIQUE " else "")
      .append("INDEX ")
      .append(a.quoteIndexName(None, name))
      .append(" ON ")
      .append(a.quoteTableName(tableName))
      .append(" (")
      .append(quotedColumnNames)
      .append(")")
      .toString

    execute(sql)
  }

  /**
   * Add an index to a table on a column.  The name of the index is
   * automatically generated unless Name() is given as an option.
   *
   * @param tableName the table to add the index to
   * @param columnName the name of the column that the index will be
   *        on
   * @param options a possibly empty list of index options to
   *        customize the creation of the index
   */
  final def addIndex(tableName: String,
                     columnName: String,
                     options: IndexOption*) {
    addIndex(tableName, Array(columnName), options: _*)
  }

  /**
   * Remove an index on a table that is composed of a non-empty list
   * of column names.  The name of the index to remove is
   * automatically generated unless Name() is given as an option.
   *
   * @param tableName the table to remove the index from
   * @param columnNames a list of one or more column names that the
   *        index is on
   * @param options a possibly empty list of index options to
   *        customize the removal of the index
   */
  final def removeIndex(tableName: String,
                        columnNames: Array[String],
                        options: Name*) {
    if (columnNames.isEmpty) {
      throw new IllegalArgumentException("Removing an index requires at " +
        "least one column name.")
    }

    val (name, _) = indexNameFor(tableName, columnNames, options: _*)

    val sql = adapter.removeIndexSql(tableName, name)

    execute(sql)
  }

  /**
   * Remove an index on a column in a table.  The name of the index to
   * remove is automatically generated unless Name() is given as an
   * option.
   *
   * @param tableName the table to remove the index from
   * @param columnName the name of the column the index is on
   * @param options a possibly empty list of index options to
   *        customize the removal of the index
   */
  final def removeIndex(tableName: String,
                        columnName: String,
                        options: Name*) {
    removeIndex(tableName, Array(columnName), options: _*)
  }

  /**
   * Given a foreign key relationship, create a name for it, using a
   * Name() if it is provided in the options.
   *
   * @param on the table and columns the foreign key constraint is on
   * @param references the table and columns the foreign key
   *        constraint references
   * @param options a varargs list of ForeignKeyOption's
   * @return a two-tuple with the calculated name or the overridden
   *         name from a Name and the remaining options
   */
  private def foreignKeyNameFor(on: On,
                                references: References,
                                options: ForeignKeyOption*): (String, List[ForeignKeyOption]) = {
    var opts = options.toList

    var fkNameOpt: Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts = opts filter { _ ne opt }
      if (fkNameOpt.isDefined && fkNameOpt.get != name) {
        logger.warn("Redefining the foreign key name from '{}' to '{}'.",
          Array[AnyRef](fkNameOpt.get, name): _*)
      }
      fkNameOpt = Some(name)
    }

    val name = fkNameOpt.getOrElse {
      "fk_" +
        on.tableName +
        "_" +
        on.columnNames.mkString("_") +
        "_" +
        references.tableName +
        "_" +
        references.columnNames.mkString("_")
    }

    (name, opts)
  }

  /**
   * Add a foreign key to a table.  The name of the foreign key is
   * automatically generated unless Name() is given as an option.
   *
   * @param on the table and column name(s) to place the foreign key
   *        on
   * @param references the table and column name(s) that the foreign
   *        key references
   * @param options a possibly empty list of foreign key options to
   *        customize the creation of the foreign key
   */
  def addForeignKey(on: On,
                    references: References,
                    options: ForeignKeyOption*) {
    if (on.columnNames.length == 0) {
      throw new IllegalArgumentException("Adding a foreign key constraint " +
        "requires at least one column name " +
        "in the table adding the constraint.")
    }

    if (references.columnNames.length == 0) {
      throw new IllegalArgumentException("Adding a foreign key constraint " +
        "requires at least one column name " +
        "from the table being referenced.")
    }

    var (name, opts) = foreignKeyNameFor(on, references, options: _*)

    val a = adapter
    val quotedOnColumnNames = on.columnNames.map {
      a.quoteColumnName(_)
    }.mkString(", ")

    val quotedReferencesColumnNames = references.columnNames.map {
      a.quoteColumnName(_)
    }.mkString(", ")

    var onDeleteOpt: Option[OnDelete] = None

    for (opt @ OnDelete(action) <- opts) {
      if (onDeleteOpt.isDefined && action != onDeleteOpt.get.action) {
        logger.warn("Overriding the ON DELETE action from '{}' to '{}'.",
          Array[AnyRef](onDeleteOpt.get.action, action): _*)
      }
      opts = opts filter { _ ne opt }
      onDeleteOpt = Some(opt)
    }

    var onUpdateOpt: Option[OnUpdate] = None

    for (opt @ OnUpdate(action) <- opts) {
      if (onUpdateOpt.isDefined && action != onUpdateOpt.get.action) {
        logger.warn("Overriding the ON UPDATE action from '{}' to '{}'.",
          Array[AnyRef](onUpdateOpt.get.action, action): _*)
      }
      opts = opts filter { _ ne opt }
      onUpdateOpt = Some(opt)
    }

    val sb = new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(a.quoteTableName(on.tableName))
      .append(" ADD CONSTRAINT ")
      .append(name)
      .append(" FOREIGN KEY (")
      .append(quotedOnColumnNames)
      .append(") REFERENCES ")
      .append(a.quoteTableName(references.tableName))
      .append(" (")
      .append(quotedReferencesColumnNames)
      .append(")")

    val onDeleteSql = a.onDeleteSql(onDeleteOpt)
    if (!onDeleteSql.isEmpty) {
      sb.append(' ')
        .append(onDeleteSql)
    }

    val onUpdateSql = a.onUpdateSql(onUpdateOpt)
    if (!onUpdateSql.isEmpty) {
      sb.append(' ')
        .append(onUpdateSql)
    }

    execute(sb.toString)
  }

  /**
   * Add a foreign key to a table.  The name of the foreign key is
   * automatically generated unless Name() is given as an option.
   *
   * @param references the table and column name(s) that the foreign
   *        key references
   * @param on the table and column name(s) to place the foreign key
   *        on
   * @param options a possibly empty list of foreign key options to
   *        customize the creation of the foreign key
   */
  def addForeignKey(references: References,
                    on: On,
                    options: ForeignKeyOption*) {
    addForeignKey(on, references, options: _*)
  }

  /**
   * Remove a foreign key from a table.  The name of the foreign key
   * is automatically generated unless Name() is given as an option.
   *
   * @param on the table and column name(s) to remove the foreign key
   *        from
   * @param references the table and column name(s) that the foreign
   *        key references
   * @param options a possibly empty list of foreign key options to
   *        customize the removal of the foreign key
   */
  def removeForeignKey(on: On,
                       references: References,
                       options: Name*) {
    if (on.columnNames.length == 0) {
      throw new IllegalArgumentException("Removing a foreign key constraint " +
        "requires at least one column name " +
        "in the table adding the constraint.")
    }

    if (references.columnNames.length == 0) {
      throw new IllegalArgumentException("Removing a foreign key constraint " +
        "requires at least one column name " +
        "from the table being referenced.")
    }

    val (name, _) = foreignKeyNameFor(on, references, options: _*)

    execute("ALTER TABLE " +
      adapter.quoteTableName(on.tableName) +
      " DROP " +
      adapter.alterTableDropForeignKeyConstraintPhrase +
      ' ' +
      name)
  }

  /**
   * Remove a foreign key from a table.  The name of the foreign key
   * is automatically generated unless Name() is given as an option.
   *
   * @param references the table and column name(s) that the foreign
   *        key references
   * @param on the table and column name(s) to remove the foreign key
   *        from
   * @param options a possibly empty list of foreign key options to
   *        customize the removal of the foreign key
   */
  def removeForeignKey(references: References,
                       on: On,
                       options: Name*) {
    removeForeignKey(on, references, options: _*)
  }

  /**
   * Add a grant on a table to one or more grantees.
   *
   * @param tableName the table name to add the grants to
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to grant to the
   *        grantees
   */
  final def grant(tableName: String,
                  grantees: Array[User],
                  privileges: GrantPrivilegeType*) {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Granting privileges requires " +
        "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Granting privileges requires " +
        "at least one privilege.")
    }

    val sql = adapter.grantOnTableSql(tableName, grantees, privileges: _*)

    execute(sql)
  }

  /**
   * Add a grant on a table to one or more grantees.
   *
   * @param tableName the table name to add the grants to
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to grant to the
   *        grantees
   */
  final def grant(tableName: String,
                  grantees: Array[String],
                  privileges: GrantPrivilegeType*) {
    grant(tableName,
      grantees map { adapter.userFactory.nameToUser(_) },
      privileges: _*)
  }

  /**
   * Add a grant on a table to a grantee.
   *
   * @param tableName the table name to add the grants to
   * @param grantee the grantee to grant the privileges to
   * @param privileges a non-empty array of privileges to grant to the
   *        grantee
   */
  final def grant(tableName: String,
                  grantee: User,
                  privileges: GrantPrivilegeType*) {
    grant(tableName, Array(grantee), privileges: _*)
  }

  /**
   * Add a grant on a table to a grantee.
   *
   * @param tableName the table name to add the grants to
   * @param grantee the grantee to grant the privileges to
   * @param privileges a non-empty array of privileges to grant to the
   *        grantee
   */
  final def grant(tableName: String,
                  grantee: String,
                  privileges: GrantPrivilegeType*) {
    grant(tableName,
      Array[User](adapter.userFactory.nameToUser(grantee)),
      privileges: _*)
  }

  /**
   * Remove privileges on a table from one or more grantees.
   *
   * @param tableName the table name to remove the grants from
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to remove from
   *        the grantees
   */
  final def revoke(tableName: String,
                   grantees: Array[User],
                   privileges: GrantPrivilegeType*) {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Revoking privileges requires " +
        "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Revoking privileges requires " +
        "at least one privilege.")
    }

    val sql = adapter.revokeOnTableSql(tableName, grantees, privileges: _*)

    execute(sql)
  }

  /**
   * Remove privileges on a table from one or more grantees.
   *
   * @param tableName the table name to remove the grants from
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to remove from
   *        the grantees
   */
  final def revoke(tableName: String,
                   grantees: Array[String],
                   privileges: GrantPrivilegeType*) {
    revoke(tableName,
      grantees map { adapter.userFactory.nameToUser(_) },
      privileges: _*)
  }

  /**
   * Remove privileges on a table from a grantee.
   *
   * @param tableName the table name to remove the grants from
   * @param grantee the grantee to revoke privileges from
   * @param privileges a non-empty array of privileges to remove from
   *        the grantee
   */
  final def revoke(tableName: String,
                   grantee: User,
                   privileges: GrantPrivilegeType*) {
    revoke(tableName, Array(grantee), privileges: _*)
  }

  /**
   * Remove privileges on a table from a grantee.
   *
   * @param tableName the table name to remove the grants from
   * @param grantee the grantee to revoke privileges from
   * @param privileges a non-empty array of privileges to remove from
   *        the grantee
   */
  final def revoke(tableName: String,
                   grantee: String,
                   privileges: GrantPrivilegeType*) {
    revoke(tableName,
      Array[User](adapter.userFactory.nameToUser(grantee)),
      privileges: _*)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to grant to the
   *        grantees
   */
  final def grantSchemaPrivilege(grantees: Array[User],
                                 privileges: SchemaPrivilege*) {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Granting privileges requires " +
        "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Granting privileges requires " +
        "at least one privilege.")
    }

    val sql = adapter.grantOnSchemaSql(grantees, privileges: _*)

    execute(sql)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to grant to the
   *        grantees
   */
  final def grantSchemaPrivilege(grantees: Array[String],
                                 privileges: SchemaPrivilege*) {
    grantSchemaPrivilege(grantees map { adapter.userFactory.nameToUser(_) },
      privileges: _*)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param grantee the grantee to grant the privileges to
   * @param privileges a non-empty array of privileges to grant to the
   *        grantee
   */
  final def grantSchemaPrivilege(grantee: User,
                                 privileges: SchemaPrivilege*) {
    grantSchemaPrivilege(Array(grantee), privileges: _*)
  }

  /**
   * Grant one or more privileges to a schema.
   *
   * @param grantee the grantee to grant the privileges to
   * @param privileges a non-empty array of privileges to grant to the
   *        grantee
   */
  final def grantSchemaPrivilege(grantee: String,
                                 privileges: SchemaPrivilege*) {
    grantSchemaPrivilege(adapter.userFactory.nameToUser(grantee),
      privileges: _*)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to revoke from the
   *        grantees
   */
  final def revokeSchemaPrivilege(grantees: Array[User],
                                  privileges: SchemaPrivilege*) {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Revoking privileges requires " +
        "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Revoking privileges requires " +
        "at least one privilege.")
    }

    val sql = adapter.revokeOnSchemaSql(grantees, privileges: _*)

    execute(sql)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param grantees a non-empty array of grantees
   * @param privileges a non-empty array of privileges to revoke from the
   *        grantees
   */
  final def revokeSchemaPrivilege(grantees: Array[String],
                                  privileges: SchemaPrivilege*) {
    revokeSchemaPrivilege(grantees map { adapter.userFactory.nameToUser(_) },
      privileges: _*)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param grantee the grantee to revoke the privileges from
   * @param privileges a non-empty array of privileges to revoke from
   *        the grantee
   */
  final def revokeSchemaPrivilege(grantee: User,
                                  privileges: SchemaPrivilege*) {
    revokeSchemaPrivilege(Array(grantee), privileges: _*)
  }

  /**
   * Revoke one or more privileges from a schema.
   *
   * @param grantee the grantee to revoke the privileges from
   * @param privileges a non-empty array of privileges to revoke from
   *        the grantee
   */
  final def revokeSchemaPrivilege(grantee: String,
                                  privileges: SchemaPrivilege*) {
    revokeSchemaPrivilege(adapter.userFactory.nameToUser(grantee),
      privileges: _*)
  }

  /**
   * Add a CHECK constraint on a table and one or more columns.  The
   * constraint name is automatically generated unless Name() is given
   * as an option.
   *
   * @param on the table and columns to add the CHECK constraint on
   * @param expr the expression to check
   * @param options a possibly empty list of check options to
   *        customize the creation of the CHECK constraint
   */
  def addCheck(on: On,
               expr: String,
               options: CheckOption*) {
    if (on.columnNames.isEmpty) {
      throw new IllegalArgumentException("Adding a check constraint " +
        "requires at least one column name " +
        "in the table adding the constraint.")
    }

    val a = adapter
    val (name, _) = a.generateCheckConstraintName(on, options: _*)

    val sql = new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(a.quoteTableName(on.tableName))
      .append(" ADD CONSTRAINT ")
      .append(name)
      .append(" CHECK (")
      .append(expr)
      .append(")")
      .toString

    if (adapter.supportsCheckConstraints)
      execute(sql)
    else
      logger.warn("Database does not support CHECK constraints; ignoring " +
        "request to add a CHECK constraint: {}",
        sql)
  }

  /**
   * Remove a CHECK constraint on a table and one or more columns.
   * The constraint name is automatically generated unless Name() is
   * given as an option.
   *
   * @param on the table and columns to remove the CHECK constraint
   *        from
   * @param options a possibly empty list of check options to
   *        customize the removal of the CHECK constraint
   */
  def removeCheck(on: On,
                  options: Name*) {
    if (on.columnNames.isEmpty) {
      throw new IllegalArgumentException("Removing a check constraint " +
        "requires at least one column " +
        "name in the table removing " +
        "the constraint.")
    }

    val (name, _) = adapter.generateCheckConstraintName(on, options: _*)

    val sql = new java.lang.StringBuilder(64)
      .append("ALTER TABLE ")
      .append(adapter.quoteTableName(on.tableName))
      .append(" DROP CONSTRAINT ")
      .append(name)
      .toString

    if (adapter.supportsCheckConstraints)
      execute(sql)
    else
      logger.warn("Database does not support CHECK constraints; ignoring " +
        "request to remove a CHECK constraint: {}",
        sql)
  }
}
