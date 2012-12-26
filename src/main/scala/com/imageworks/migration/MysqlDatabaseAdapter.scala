/*
 * Copyright (c) 2012 Sony Pictures Imageworks Inc.
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
 * Map Unicode to the "utf8" UTF-8 character set.  If Unicode is
 * specified without a collation then use the "utf8_unicode_ci"
 * collation.  If a non-Unicode character set is specified then let
 * MySQL pick its default collation for the character set.  See
 * http://dev.mysql.com/doc/refman/5.5/en/charset-charsets.html for
 * more information.
 */
trait MysqlAppendCharacterSetToColumnDefinitionMixin
{
  /**
   * If a character set is specified, then append MySQL specific SQL
   * to the column definition.
   *
   * If Unicode is specified without a collation, then use the
   * utf8_unicode_ci collation.  While utf8_general_ci is MySQL's
   * default utf8 collation (at least for 5.5), and is faster, it is
   * also incorrect; see http://stackoverflow.com/questions/766809/ .
   *
   * @param data_type_sql the SQL for the data type
   * @param character_set_opt an optional character set
   */
  protected
  def sql(data_type_sql: String,
          character_set_opt: Option[CharacterSet]): String =
  {
    character_set_opt match {
      case Some(charset) => {
        val sb = new java.lang.StringBuilder(64)
        sb.append(data_type_sql)
          .append(" CHARACTER SET ")

        charset.name match {
          case Unicode => {
            sb.append("utf8 COLLATE ")
              .append(charset.collationOpt.getOrElse("utf8_unicode_ci"))
          }
          case name => {
            sb.append(name.toString)
            charset.collationOpt match {
              case Some(collation) =>
                sb.append(" COLLATE ")
                  .append(collation)
              case None =>
            }
          }
        }
        sb.toString
      }
      case None => data_type_sql
    }
  }
}

/**
 * Map BlobType to MySQL's LONGBLOB data type.
 *
 * MySQL supports four different BlobType data types with the
 * following properties:
 *
 * +------------+--------------------+------------------------------+
 * | Data Type  | Max Length (bytes) | Storage Requirements (bytes) |
 * +------------+--------------------+------------------------------+
 * | TINYBLOB   |           255      | length + 1                   |
 * | BLOB       |        65,535      | length + 2                   |
 * | MEDIUMBLOB |    16,777,215      | length + 3                   |
 * | LONGBLOB   | 4,294,967,295      | length + 4                   |
 * +------------+--------------------+------------------------------+
 *
 * Since the intention of BlobType is to store large amounts of data
 * and the additional overhead from TINYBLOB to LONGBLOB is three
 * bytes, LONGBLOB is used.
 */
class MysqlBlobColumnDefinition
  extends DefaultBlobColumnDefinition
{
  override
  val sql = "LONGBLOB"
}

class MysqlCharColumnDefinition(character_set_opt: Option[CharacterSet])
  extends DefaultCharColumnDefinition
  with MysqlAppendCharacterSetToColumnDefinitionMixin
{
  override protected
  def sql: String = sql(super.sql, character_set_opt)
}

// MySQL does not support size specifiers for the TIMESTAMP data type.
class MysqlTimestampColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  override
  val sql = "TIMESTAMP"
}

class MysqlVarcharColumnDefinition(character_set_opt: Option[CharacterSet])
  extends DefaultVarcharColumnDefinition
  with MysqlAppendCharacterSetToColumnDefinitionMixin
{
  override protected
  def sql: String = sql(super.sql, character_set_opt)
}

class MysqlDatabaseAdapter(override val schemaNameOpt: Option[String])
  extends DatabaseAdapter(schemaNameOpt)
{
  override
  val vendor = Mysql

  // https://dev.mysql.com/doc/refman/5.5/en/identifiers.html
  override
  val quoteCharacter = '`'

  // mysql> create table PaReNt (pk INT PRIMARY KEY);
  // Query OK, 0 rows affected (0.14 sec)
  //
  // mysql> show tables;
  // +----------------+
  // | Tables_in_test |
  // +----------------+
  // | PaReNt         |
  // +----------------+
  // 1 row in set (0.00 sec)
  //
  // mysql> select * from parent;
  // ERROR 1146 (42S02): Table 'test.parent' doesn't exist
  // mysql> select * from PARENT;
  // ERROR 1146 (42S02): Table 'test.PARENT' doesn't exist
  // mysql> select * from PaReNt;
  // Empty set (0.00 sec)
  override
  val unquotedNameConverter = CasePreservingUnquotedNameConverter

  override
  val userFactory = MysqlUserFactory

  // https://dev.mysql.com/doc/refman/5.5/en/alter-table.html
  override
  val alterTableDropForeignKeyConstraintPhrase = "FOREIGN KEY"

  // mysql> CREATE TABLE parent (pk INT PRIMARY KEY);
  // Query OK, 0 rows affected (0.13 sec)
  //
  // mysql> CREATE TABLE child (pk INT PRIMARY KEY, pk_parent INT NOT NULL);
  // Query OK, 0 rows affected (0.10 sec)
  //
  // mysql> ALTER TABLE child
  //     ->   ADD CONSTRAINT idx_child_pk_parent FOREIGN KEY (pk_parent)
  //     ->   REFERENCES parent (pk);
  // Query OK, 0 rows affected (0.22 sec)
  // Records: 0  Duplicates: 0  Warnings: 0
  //
  // mysql> SHOW CREATE TABLE child;
  // | Table | Create Table
  // | child | CREATE TABLE `child` (
  //   `pk` int(11) NOT NULL,
  //   `pk_parent` int(11) NOT NULL,
  //   PRIMARY KEY (`pk`),
  //   KEY `idx_child_pk_parent` (`pk_parent`),
  //   CONSTRAINT `idx_child_pk_parent` FOREIGN KEY (`pk_parent`) REFERENCES `parent` (`pk`)
  // ) ENGINE=InnoDB DEFAULT CHARSET=latin1 |
  // 1 row in set (0.00 sec)
  //
  // mysql> CREATE INDEX idx_child_pk_parent ON child (pk_parent);
  // ERROR 1280 (42000): Incorrect index name 'idx_child_pk_parent'
  override
  val addingForeignKeyConstraintCreatesIndex = true

  // https://dev.mysql.com/doc/refman/5.5/en/alter-table.html
  override
  val supportsCheckConstraints = false

  override
  def columnDefinitionFactory
    (column_type: SqlType,
     character_set_opt: Option[CharacterSet]): ColumnDefinition =
  {
    column_type match {
      case BigintType =>
        new DefaultBigintColumnDefinition
      case BlobType =>
        new MysqlBlobColumnDefinition
      case BooleanType =>
        new DefaultBooleanColumnDefinition
      case CharType =>
        new MysqlCharColumnDefinition(character_set_opt)
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new DefaultIntegerColumnDefinition
      case TimestampType =>
        new MysqlTimestampColumnDefinition
      case SmallintType =>
        new DefaultSmallintColumnDefinition
      case VarbinaryType =>
        new DefaultVarbinaryColumnDefinition
      case VarcharType =>
        new MysqlVarcharColumnDefinition(character_set_opt)
    }
  }

  override
  def lockTableSql(schema_name_opt: Option[String],
                   table_name: String): String =
  {
    val sb = new java.lang.StringBuilder(64)
    sb.append("LOCK TABLES ")
      .append(quoteTableName(schema_name_opt, table_name))
      .append(" WRITE")
      .toString
  }

  override protected
  def alterColumnSql(schema_name_opt: Option[String],
                     column_definition: ColumnDefinition): String =
  {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schema_name_opt, column_definition.getTableName))
      .append(" MODIFY COLUMN ")
      .append(quoteColumnName(column_definition.getColumnName))
      .append(column_definition.toSql)
      .toString
  }

  override
  def removeIndexSql(schema_name_opt: Option[String],
                     table_name: String,
                     index_name: String): String =
  {
    new java.lang.StringBuilder(128)
      .append("ALTER TABLE ")
      .append(quoteTableName(schema_name_opt, table_name))
      .append(" DROP INDEX ")
      .append(quoteIndexName(None, index_name))
      .toString
  }
}
