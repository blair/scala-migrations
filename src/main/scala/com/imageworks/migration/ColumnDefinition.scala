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

import org.slf4j.LoggerFactory

/**
 * Marker trait for a ColumnDefinition subclass that the column type
 * supports a default value.
 */
trait ColumnSupportsDefault
{
  this: ColumnDefinition =>
}

/**
 * Marker trait for a ColumnDefinition subclass that the column type
 * supports a limit on the range of values it supports,
 * e.g. VARCHAR(32).
 */
trait ColumnSupportsLimit
{
  this: ColumnDefinition =>
}

/**
 * Marker trait for a ColumnDefinition subclass that the column type
 * supports a precision on numerical values it stores,
 * e.g. DECIMAL(10).
 */
trait ColumnSupportsPrecision
{
  this: ColumnDefinition =>
}

/**
 * Marker trait for a ColumnDefinition subclass that the column type
 * supports a precision on numerical values it stores,
 * e.g. DECIMAL(10, 5), where 5 is the scale.
 */
trait ColumnSupportsScale
{
  this: ColumnDefinition =>
}

/**
 * Abstract base class for the definition of a column type.  It stores
 * all the information for the column type, e.g. if it supports a
 * default value, if it supports a limit on the range of values it can
 * hold, etc.
 */
abstract
class ColumnDefinition
{
  private final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * The database adapter associated with this column definition,
   * which may or may not be set.
   */
  protected[migration] var adapterOpt: Option[DatabaseAdapter] = None

  /**
   * The database adapter associated with this column definition.
   *
   * @return the database adapter associated with this column
   *         definition
   * @throws java.util.NoSuchElementException if the database adapter
   *         has not been associated with this column definition
   */
  private def adapter = adapterOpt.get

  /**
   * The table name the column is defined in, which may or may not be
   * set.
   */
  protected[migration] var tableNameOpt: Option[String] = None

  /**
   * Get the table name the column is defined in.
   *
   * @return the table name the column is defined in
   * @throws java.util.NoSuchElementException if the table name has
   *         not been set
   */
  private def tableName = tableNameOpt.get

  /**
   * The column's name, which may or may not be set.
   */
  protected[migration] var columnNameOpt: Option[String] = None

  /**
   * Get the column's name.
   *
   * @return the column name
   * @throws java.util.NoSuchElementException if the column name has
   *         not been set
   */
  private def columnName = columnNameOpt.get

  /**
   * Column options.
   */
  protected[migration] var options: List[ColumnOption] = _

  /**
   * If a default is specified for the column.
   */
  private
  var default: Option[String] = None

  /**
   * Called after the above properties have been wired.
   */
  def initialize(): Unit =
  {
    if (this.isInstanceOf[ColumnSupportsLimit]) {
      checkForLimit()
    }

    if (this.isInstanceOf[ColumnSupportsDefault]) {
      checkForDefault()
    }

    if (this.isInstanceOf[ColumnSupportsPrecision]) {
      checkForPrecision()
    }

    if (this.isInstanceOf[ColumnSupportsScale]) {
      checkForScale()
    }
  }

  /**
   * If a column can have a default value, then the derived class
   * should call this method to check for a specified default.  This
   * will remove the default option from the option list.
   */
  private
  def checkForDefault() =
  {
    for (option @ Default(value) <- options) {
      options = options.filter(_ != option)

      if (default.isDefined && default.get != value) {
        logger.warn("Redefining the default value for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](columnName, default.get, value))
      }
      default = Some(value)
    }
  }

  /**
   * If a limit is specified for the column.
   */
  private
  var limit_ : Option[String] = None

  /**
   * Get the limit for the column.
   */
  protected
  def limit = limit_

  /**
   * If a column can have a limit, then the derived class should call
   * this method to check for the limit.  This will remove the limit
   * option from the option list.
   */
  private
  def checkForLimit() =
  {
    for (option @ Limit(length) <- options) {
      options = options.filter(_ != option)

      if (limit_.isDefined && limit_.get != length) {
        logger.warn("Redefining the limit for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](columnName, limit_.get, length))
      }
      limit_ = Some(length)
    }
  }

  /**
   * If the column can or cannot be null.
   */
  private
  lazy
  val notNullOpt: Option[Boolean] =
  {
    var n1: Option[Boolean] = None

    for (option <- options) {
      val n2 = option match {
                 case NotNull => Some(true)
                 case Nullable => Some(false)
                 case _ => None
               }
      if (n2.isDefined) {
        options = options.filter(_ != option)

        if (n1.isDefined && n1 != n2) {
          logger.warn("Redefining the '{}' column's nullability " +
                      "from {} to {}.",
                      Array[AnyRef](columnName,
                                    if (n1.get) "NOT NULL" else "NULL",
                                    if (n2.get) "NOT NULL" else "NULL"))
        }
        n1 = n2
      }
    }

    n1
  }

  /**
   * If the column is a primary key.
   */
  private
  lazy
  val isPrimaryKey: Boolean =
  {
    var is_primary = false

    for (option @ PrimaryKey <- options) {
      options = options.filter(_ != option)
      is_primary = true
    }

    is_primary
  }

  /**
   * The precision for the column, used for DECIMAL and NUMERIC column
   * types.
   */
  private
  var precision_ : Option[Int] = None

  /**
   * Get the precision for the column.
   */
  def precision = precision_

  /**
   * Look for a Precision column option.
   */
  private
  def checkForPrecision() =
  {
    for (option @ Precision(value) <- options) {
      options = options.filter(_ != option)

      if (precision_.isDefined && precision_.get != value) {
        logger.warn("Redefining the precision for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](columnName,
                                  java.lang.Integer.valueOf(precision_.get),
                                  java.lang.Integer.valueOf(value)))
      }
      precision_ = Some(value)
    }
  }

  /**
   * The scale for the column, used for DECIMAL and NUMERIC column
   * types.
   */
  private
  var scale_ : Option[Int] = None

  /**
   * Get the scale for the column.
   */
  def scale = scale_

  /**
   * Look for a Scale column option.
   */
  private
  def checkForScale() =
  {
    for (option @ Scale(value) <- options) {
      options = options.filter(_ != option)

      if (scale_.isDefined && scale_.get != value) {
        logger.warn("Redefining the scale for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](columnName,
                                  java.lang.Integer.valueOf(scale_.get),
                                  java.lang.Integer.valueOf(value)))
      }
      scale_ = Some(value)
    }
  }

  /**
   * If the column is unique.
   */
  private
  lazy
  val isUnique: Boolean =
  {
    var unique = false

    for (option @ Unique <- options) {
      options = options.filter(_ != option)
      unique = true
    }

    unique
  }

  protected
  def sql: String

  final
  def toSql: String =
  {
    val sb = new java.lang.StringBuilder(512)
               .append(columnName)
               .append(' ')
               .append(sql)

    if (default.isDefined) {
      sb.append(" DEFAULT ")
      sb.append(default.get)
    }

    if (isPrimaryKey) {
      sb.append(" PRIMARY KEY")
    }

    if (isUnique) {
      sb.append(" UNIQUE")
    }

    // Not all databases, such as Derby, support specifying NULL for a
    // column that may have NULL values.
    notNullOpt match {
      case Some(true) => sb.append(" NOT NULL")
      case _ =>
    }

    for (option <- options) {
      def appendCheckSql(name: String,
                         expr: String): Unit =
      {
        options = options.filter(_ != option)

        sb.append(" CONSTRAINT ")
          .append(name)
          .append(" CHECK (")
          .append(expr)
          .append(")")
      }

      option match {
        case NamedCheck(name, expr) => {
          appendCheckSql(name, expr)
        }

        case Check(expr) => {
          val tbd = new TableColumnDefinition(tableName, Array(columnName))
          val on = new On(tbd)
          val (name, _) = adapter.generateCheckConstraintName(on)

          appendCheckSql(name, expr)
        }

        case _ =>
      }
    }

    // Warn for any unused options.
    if (! options.isEmpty) {
      logger.warn("The following options for the '{}' column are " +
                  "unused: {}.",
                  columnName,
                  options)
    }

    // Warn about illegal combinations in some databases.
    if (isPrimaryKey &&
        notNullOpt.isDefined &&
        notNullOpt.get == false) {
      logger.warn("Specifying PrimaryKey and Nullable in a column is not " +
                  "supported in all databases.")
    }

    // Warn when different options are used that specify the same
    // behavior so one can be removed.
    if (isPrimaryKey && notNullOpt.isDefined && notNullOpt.get == true) {
      logger.warn("Specifying PrimaryKey and NotNull is redundant.")
    }
    if (isPrimaryKey && isUnique) {
      logger.warn("Specifying PrimaryKey and Unique is redundant.")
    }

    sb.toString
  }

  /**
   * Format a column definition respecting the given limit.
   *
   * @param column_type_name the column type name
   * @param limit_opt optional column limit
   * @return the column type name with the limit syntax if a limit was
   *         given
   */
  def sqlForColumnType(column_type_name: String,
                       limit_opt: Option[String]): String =
  {
    limit_opt match {
      case Some(l) => column_type_name + "(" + l + ")"
      case None => column_type_name
    }
  }

  /**
   * Format a column definition respecting the limit on the column
   * definition.
   *
   * @param column_type_name the column type name
   * @return the column type name with the limit syntax if the column
   *         definition specifies a limit
   */
  def sqlForColumnType(column_type_name: String): String =
  {
    sqlForColumnType(column_type_name, limit)
  }
}

/**
 * This class is an abstract class to handle DECIMAL and NUMERIC
 * column types.
 */
abstract class AbstractDecimalColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
  with ColumnSupportsPrecision
  with ColumnSupportsScale
{
  /**
   * Concrete subclasses must define this to the name of the DECIMAL
   * or NUMERIC data type specific for the database.
   */
  val decimalSqlName: String

  override
  def sql =
  {
    (precision, scale) match {
      case (None, None) => {
        decimalSqlName
      }
      case (Some(p), None) => {
        decimalSqlName + "(" + p + ")"
      }
      case (Some(p), Some(s)) => {
        decimalSqlName + "(" + p + ", " + s + ")"
      }
      case (None, Some(_)) => {
        val message = "Cannot specify a scale without also specifying a " +
                      "precision."
        throw new IllegalArgumentException(message)
      }
    }
  }
}

class DefaultBigintColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  override
  val sql = "BIGINT"
}

class DefaultBlobColumnDefinition
  extends ColumnDefinition
{
  override
  val sql = "BLOB"
}

class DefaultBooleanColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  override
  val sql = "BOOLEAN"
}

class DefaultCharColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsLimit
  with ColumnSupportsDefault
{
  override
  def sql = sqlForColumnType("CHAR")
}

class DefaultDecimalColumnDefinition
  extends AbstractDecimalColumnDefinition
{
  override
  val decimalSqlName = "DECIMAL"
}

class DefaultIntegerColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  override
  val sql = "INTEGER"
}

class DefaultSmallintColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  override
  val sql = "SMALLINT"
}

class DefaultTimestampColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsLimit
  with ColumnSupportsDefault
{
  override
  def sql = sqlForColumnType("TIMESTAMP")
}

class DefaultVarcharColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsLimit
  with ColumnSupportsDefault
{
  override
  def sql = sqlForColumnType("VARCHAR")
}
