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

/**
 * Map the BIGINT SQL type to a NUMBER(19, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * BIGINT as a 8-byte signed integer type.  On Oracle a NUMBER(19, 0)
 * is large enough to store any integers from -9223372036854775808 to
 * 9223372036854775807 but not any integers with more digits.  A
 * NUMBER(19, 0) does allow a larger range of values than the other
 * databases, from -9999999999999999999 to 9999999999999999999, but
 * this seems like an acceptable solution without using a CHECK
 * constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(19, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleBigintColumnDefinition
    extends DefaultBigintColumnDefinition {
  override protected def sql = "NUMBER(19, 0)"
}

class OracleCharColumnDefinition(useNcharType: Boolean)
    extends DefaultCharColumnDefinition {
  override protected def sql = {
    optionallyAddLimitToDataType(if (useNcharType) "NCHAR" else "CHAR")
  }
}

class OracleDecimalColumnDefinition
    extends AbstractDecimalColumnDefinition {
  override val decimalSqlName = "NUMBER"
}

/**
 * Map the INTEGER SQL type to a NUMBER(10, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * INTEGER as a 4-byte signed integer type.  On Oracle a NUMBER(10, 0)
 * is large enough to store any integers from -2147483648 to
 * 2147483647 but not any integers with more digits.  A NUMBER(10, 0)
 * does allow a larger range of values than the other databases, from
 * -9999999999 to 9999999999, but this seems like an acceptable
 * solution without using a CHECK constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(10, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleIntegerColumnDefinition
    extends DefaultIntegerColumnDefinition {
  override protected def sql = "NUMBER(10, 0)"
}

/**
 * Map the SMALLINT SQL type to a NUMBER(5, 0).
 *
 * A few other databases, such as Derby, MySQL and PostgreSQL, treat
 * SMALLINT as a 2-byte signed integer type.  On Oracle a NUMBER(5, 0)
 * is large enough to store any integers from -32768 to 32767 but not
 * any integers with more digits.  A NUMBER(5, 0) does allow a larger
 * range of values than the other databases, from -99999 to 99999, but
 * this seems like an acceptable solution without using a CHECK
 * constraint.
 *
 * This behavior is different than Oracle's default.  If a column is
 * defined using "INTEGER" and not a "NUMBER", Oracle uses a
 * NUMBER(38) to store it:
 *
 * http://download-west.oracle.com/docs/cd/B19306_01/server.102/b14200/sql_elements001.htm#sthref218
 *
 * Using a NUMBER(5, 0) helps ensure the compatibility of any code
 * running against an Oracle database so that is does not assume it
 * can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleSmallintColumnDefinition
    extends DefaultSmallintColumnDefinition {
  override protected def sql = "NUMBER(5, 0)"
}

class OracleVarbinaryColumnDefinition
    extends DefaultVarbinaryColumnDefinition {
  override protected def sql = {
    if (!limit.isDefined) {
      val message = "In Oracle, a RAW column must always specify its size."
      throw new IllegalArgumentException(message)
    }

    optionallyAddLimitToDataType("RAW")
  }
}

class OracleVarcharColumnDefinition(useNcharType: Boolean)
    extends DefaultVarcharColumnDefinition {
  override protected def sql = {
    optionallyAddLimitToDataType(if (useNcharType) "NVARCHAR2" else "VARCHAR2")
  }
}

class OracleDatabaseAdapter(override val schemaNameOpt: Option[String])
    extends DatabaseAdapter(schemaNameOpt) {
  override val vendor = Oracle

  override val quoteCharacter = '"'

  override val unquotedNameConverter = UppercaseUnquotedNameConverter

  override val userFactory = PlainUserFactory

  override val alterTableDropForeignKeyConstraintPhrase = "CONSTRAINT"

  override val addingForeignKeyConstraintCreatesIndex = false

  override val supportsCheckConstraints = true

  override def columnDefinitionFactory(columnType: SqlType,
                                       characterSetOpt: Option[CharacterSet]): ColumnDefinition = {
    val useNcharType =
      characterSetOpt match {
        case None => {
          false
        }
        case Some(CharacterSet(Unicode, None)) => {
          true
        }
        case Some(charset @ CharacterSet(Unicode, Some(collation))) => {
          logger.warn("Ignoring collation '{}' in '{}' as Oracle only " +
            "supports setting the collation using the NLS_SORT " +
            "session parameter.",
            Array[AnyRef](collation, charset): _*)
          true
        }
        case Some(charset @ CharacterSet(_, _)) => {
          logger.warn("Ignoring '{}' as Oracle only supports specifying no " +
            "explicit character set encoding, which defaults the " +
            "column to use the database's character set, or " +
            "Unicode.",
            charset)
          false
        }
      }

    columnType match {
      case BigintType =>
        new OracleBigintColumnDefinition
      case BlobType =>
        new DefaultBlobColumnDefinition
      case BooleanType => {
        val message = "Oracle does not support a boolean type, you must " +
          "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case UuidType => {
        val message = "Oracle does not support UUID as a legal data type"
        throw new UnsupportedColumnTypeException(message)
      }
      case CharType =>
        new OracleCharColumnDefinition(useNcharType)
      case DecimalType =>
        new OracleDecimalColumnDefinition
      case IntegerType =>
        new OracleIntegerColumnDefinition
      case SmallintType =>
        new OracleSmallintColumnDefinition
      case TimestampType =>
        new DefaultTimestampColumnDefinition
      case VarbinaryType =>
        new OracleVarbinaryColumnDefinition
      case VarcharType =>
        new OracleVarcharColumnDefinition(useNcharType)
    }
  }

  override protected def alterColumnSql(schemaNameOpt: Option[String],
                                        columnDefinition: ColumnDefinition): String = {
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, columnDefinition.getTableName))
      .append(" MODIFY (")
      .append(quoteColumnName(columnDefinition.getColumnName))
      .append(' ')
      .append(columnDefinition.toSql)
      .append(')')
      .toString
  }

  override def removeColumnSql(schemaNameOpt: Option[String],
                               tableName: String,
                               columnName: String): String = {
    // Oracle requires COLUMN keyword.
    new java.lang.StringBuilder(512)
      .append("ALTER TABLE ")
      .append(quoteTableName(schemaNameOpt, tableName))
      .append(" DROP COLUMN ")
      .append(quoteColumnName(columnName))
      .toString
  }

  override def grantOnTableSql(schemaNameOpt: Option[String],
                               tableName: String,
                               grantees: Array[User],
                               privileges: GrantPrivilegeType*): String = {
    // Check that no columns are defined for any SELECT privs
    for {
      SelectPrivilege(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support granting select to " +
        "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.grantOnTableSql(schemaNameOpt, tableName, grantees, privileges: _*)
  }

  override def revokeOnTableSql(schemaNameOpt: Option[String],
                                tableName: String,
                                grantees: Array[User],
                                privileges: GrantPrivilegeType*): String = {
    // Check that no columns are defined for any privs with columns
    for {
      PrivilegeWithColumns(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support revoking permissions from " +
        "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.revokeOnTableSql(schemaNameOpt, tableName, grantees, privileges: _*)
  }

  /**
   * Return the SQL text for the ON DELETE clause for a foreign key
   * relationship.
   *
   * Oracle rejects adding a foreign key relationship containing the
   * "ON DELETE RESTRICT" text, so do not generate any SQL text for
   * it.  The behavior is the same though.  Let any other unsupported
   * options pass through, such as "ON DELETE NO ACTION", in case
   * Oracle ever does support that clause, which it does not in 10g.
   *
   * @param onDeleteOpt an Option[OnDelete]
   * @return the SQL text to append to the SQL to create a foreign key
   *         relationship
   */
  override def onDeleteSql(onDeleteOpt: Option[OnDelete]): String = {
    onDeleteOpt match {
      case Some(OnDelete(Restrict)) => ""
      case opt => super.onDeleteSql(opt)
    }
  }
}
