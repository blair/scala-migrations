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

class PostgresqlBigintColumnDefinition
    extends DefaultBigintColumnDefinition
    with ColumnSupportsAutoIncrement {
  override protected def sql: String = {
    if (isAutoIncrement) "BIGSERIAL"
    else super.sql
  }
}

class PostgresqlByteaColumnDefinition
    extends DefaultBlobColumnDefinition
    with ColumnSupportsDefault {
  override protected def sql = "BYTEA"
}

class PostgresqlIntegerColumnDefinition
    extends DefaultIntegerColumnDefinition
    with ColumnSupportsAutoIncrement {
  override protected def sql: String = {
    if (isAutoIncrement) "SERIAL"
    else super.sql
  }
}

class PostgresqlSmallintColumnDefinition
    extends DefaultSmallintColumnDefinition
    with ColumnSupportsAutoIncrement {
  override protected def sql: String = {
    if (isAutoIncrement) "SMALLSERIAL"
    else super.sql
  }
}

class PostgresqlDatabaseAdapter(override val schemaNameOpt: Option[String])
    extends DatabaseAdapter(schemaNameOpt) {
  override val vendor = Postgresql

  override val quoteCharacter = '"'

  override val unquotedNameConverter = LowercaseUnquotedNameConverter

  override val userFactory = PlainUserFactory

  override val alterTableDropForeignKeyConstraintPhrase = "CONSTRAINT"

  override val addingForeignKeyConstraintCreatesIndex = false

  override val supportsCheckConstraints = true

  override def columnDefinitionFactory(columnType: SqlType,
                                       characterSetOpt: Option[CharacterSet]): ColumnDefinition = {
    characterSetOpt match {
      case None =>
      case Some(charset @ CharacterSet(_, _)) =>
        logger.warn("Ignoring '{}' as the character set encoding can only " +
          "be specified in PostgreSQL when the database is created.",
          charset)
    }

    columnType match {
      case BigintType =>
        new PostgresqlBigintColumnDefinition
      case BlobType =>
        new PostgresqlByteaColumnDefinition
      case BooleanType =>
        new DefaultBooleanColumnDefinition
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new PostgresqlIntegerColumnDefinition
      case SmallintType =>
        new PostgresqlSmallintColumnDefinition
      case TimestampType =>
        new DefaultTimestampColumnDefinition
      case VarbinaryType =>
        new PostgresqlByteaColumnDefinition
      case VarcharType =>
        new DefaultVarcharColumnDefinition
    }
  }

  override protected def alterColumnSql(schemaNameOpt: Option[String],
                                        columnDefinition: ColumnDefinition): String = {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, columnDefinition.getTableName))
      .append(" ALTER COLUMN ")
      .append(quoteColumnName(columnDefinition.getColumnName))
      .append(" TYPE ")
      .append(columnDefinition.toSql)
      .toString
  }
}
