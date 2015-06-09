/*
 * Copyright (c) 2015 Sony Pictures Imageworks Inc.
 *
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


trait H2AutoIncrementingColumnDefinitionMixin
    extends ColumnDefinition
    with ColumnSupportsAutoIncrement {
  override protected abstract def sql: String = {
    if (isAutoIncrement) super.sql + " AUTO_INCREMENT"
    else super.sql
  }
}

class H2BigintColumnDefinition
  extends DefaultBigintColumnDefinition
  with H2AutoIncrementingColumnDefinitionMixin


class H2IntegerColumnDefinition
  extends DefaultIntegerColumnDefinition
  with H2AutoIncrementingColumnDefinitionMixin

class H2SmallintColumnDefinition
  extends DefaultSmallintColumnDefinition
  with H2AutoIncrementingColumnDefinitionMixin

// H2 does not support size specifiers for the TIMESTAMP data type.
class H2TimestampColumnDefinition
    extends ColumnDefinition
    with ColumnSupportsDefault {
  override val sql = "TIMESTAMP"
}


class H2DatabaseAdapter(override val schemaNameOpt: Option[String])
    extends DatabaseAdapter(schemaNameOpt) {
  override val vendor = H2

  override val quoteCharacter = '`'

  override val unquotedNameConverter = UppercaseUnquotedNameConverter

  override val userFactory = PlainUserFactory

  override val alterTableDropForeignKeyConstraintPhrase = "CONSTRAINT"

  override val addingForeignKeyConstraintCreatesIndex = true

  override val supportsCheckConstraints = false

  override def columnDefinitionFactory(columnType: SqlType,
                                       characterSetOpt: Option[CharacterSet]): ColumnDefinition = {
    columnType match {
      case BigintType =>
        new H2BigintColumnDefinition
      case BlobType =>
        new DefaultBlobColumnDefinition
      case BooleanType =>
        new DefaultBooleanColumnDefinition
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new H2IntegerColumnDefinition
      case SmallintType =>
        new H2SmallintColumnDefinition
      case TimestampType =>
        new H2TimestampColumnDefinition
      case VarbinaryType =>
        new DefaultVarbinaryColumnDefinition
      case VarcharType =>
        new DefaultVarcharColumnDefinition
    }
  }

  override def lockTableSql(schemaNameOpt: Option[String],
                            tableName: String): String = {
    "SELECT * FROM " + quoteTableName(schemaNameOpt, tableName) + " FOR UPDATE"
  }

  override protected def alterColumnSql(schemaNameOpt: Option[String],
                                        columnDefinition: ColumnDefinition): String = {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, columnDefinition.getTableName))
      .append(" MODIFY COLUMN ")
      .append(quoteColumnName(columnDefinition.getColumnName))
      .append(columnDefinition.toSql)
      .toString
  }

  override def removeIndexSql(schemaNameOpt: Option[String],
                              tableName: String,
                              indexName: String): String = {
    new java.lang.StringBuilder(128)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, tableName))
      .append(" DROP INDEX ")
      .append(quoteIndexName(None, indexName))
      .toString
  }
}
