package com.imageworks.migration

import org.slf4j.LoggerFactory

abstract
class ColumnDefinition(name : String,
                       protected var options : List[ColumnOption])
{
  private final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * If a default is specified for the column.
   */
  private
  var default : Option[String] = None

  /**
   * If a column can have a default value, then the derived class
   * should call this method to check for a specified default.  This
   * will remove the default option from the option list.
   */
  protected
  def check_for_default =
  {
    for (option @ Default(value) <- options) {
      options -= option

      if (default.isDefined && default.get != value) {
        logger.warn("Redefining the default value for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](name, default.get, value))
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
  protected
  def check_for_limit =
  {
    for (option @ Limit(length) <- options) {
      options -= option

      if (limit_.isDefined && limit_.get != length) {
        logger.warn("Redefining the limit for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](name,
                                  limit_.get,
                                  length))
      }
      limit_ = Some(length)
    }
  }

  /**
   * If the column can or cannot be null.
   */
  private
  val not_null_opt : Option[Boolean] =
  {
    var n1 : Option[Boolean] = None

    for (option <- options) {
      val n2 = option match {
                 case NotNull => Some(true)
                 case Nullable => Some(false)
                 case _ => None
               }
      if (n2.isDefined) {
        options -= option

        if (n1.isDefined && n1 != n2) {
          logger.warn("Redefining the '{}' column's nullability " +
                      "from {} to {}.",
                      Array[AnyRef](name,
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
  val is_primary_key : Boolean =
  {
    var is_primary = false

    for (option @ PrimaryKey <- options) {
      options -= option
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
  protected
  def check_for_precision =
  {
    for (option @ Precision(value) <- options) {
      options -= option

      if (precision_.isDefined && precision_.get != value) {
        logger.warn("Redefining the precision for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](name,
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
  protected
  def check_for_scale =
  {
    for (option @ Scale(value) <- options) {
      options -= option

      if (scale_.isDefined && scale_.get != value) {
        logger.warn("Redefining the scale for the '{}' column " +
                    "from '{}' to '{}'.",
                    Array[AnyRef](name,
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
  val is_unique : Boolean =
  {
    var unique = false

    for (option @ Unique <- options) {
      options -= option
      unique = true
    }

    unique
  }

  protected
  def sql : String

  final
  def to_sql : String =
  {
    // Warn for any unused options.
    if (! options.isEmpty) {
      logger.warn("The following options for the '{}' column are " +
                  "unused: {}.",
                  name,
                  options)
    }

    // Warn about illegal combinations in some databases.
    if (is_primary_key &&
        not_null_opt.isDefined &&
        not_null_opt.get == false) {
      logger.warn("Specifying PrimaryKey and Nullable in a column is not " +
                  "supported in all databases.")
    }

    // Warn when different options are used that specify the same
    // behavior so one can be removed.
    if (is_primary_key && not_null_opt.isDefined && not_null_opt.get == true) {
      logger.warn("Specifying PrimaryKey and NotNull is redundant.")
    }
    if (is_primary_key && is_unique) {
      logger.warn("Specifying PrimaryKey and Unique is redundant.")
    }

    val sb = new java.lang.StringBuilder(512)
               .append(name)
               .append(' ')
               .append(sql)

    if (default.isDefined) {
      sb.append(" DEFAULT ")
      sb.append(default.get)
    }

    if (is_primary_key) {
      sb.append(" PRIMARY KEY")
    }

    if (is_unique) {
      sb.append(" UNIQUE")
    }

    // Not all databases, such as Derby, support specifying NULL for a
    // column that may have NULL values.
    not_null_opt match {
      case Some(true) => sb.append(" NOT NULL")
      case _ =>
    }

    sb.toString
  }

  /**
   * Format a column definition respecting limit.
   * @param t column type name
   * @param limit optional column limit
   */
  def column_sql(t : String, limit : Option[String]) : String = {
    if (limit.isDefined) {
      t + "(" + limit.get + ")"
    }
    else {
      t
    }
  }

  /**
   * Format a column definition respecting limit.
   * @param t column type name
   */
  def column_sql(t : String) : String = column_sql(t, limit)
}

/**
 * This class is an abstract class to handle DECIMAL and NUMERIC
 * column types.
 */
abstract class AbstractDecimalColumnDefinition(name : String,
                                               options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  /**
   * Concrete subclasses must define this to the name of the DECIMAL
   * or NUMERIC data type specific for the database.
   */
  def decimal_sql_name : String

  check_for_default
  check_for_precision
  check_for_scale

  if (! precision.isDefined && scale.isDefined) {
    val message = "Cannot specify a scale without also specifying a " +
                  "precision."
    throw new IllegalArgumentException(message)
  }

  val sql = (precision, scale) match {
              case (None, None) => {
                decimal_sql_name
              }
              case (Some(p), None) => {
                decimal_sql_name + "(" + p + ")"
              }
              case (Some(p), Some(s)) => {
                decimal_sql_name + "(" + p + ", " + s + ")"
              }
              case (None, Some(_)) => {
                val message = "Having a scale with no precision should " +
                              "never occur."
                throw new java.lang.RuntimeException(message)
              }
            }
}

class DefaultBigintColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_default

  val sql = "BIGINT"
}

class DefaultCharColumnDefinition(name : String,
                                  options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  val sql = column_sql("CHAR")
}

class DefaultDecimalColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends AbstractDecimalColumnDefinition(name, options)
{
  val decimal_sql_name = "DECIMAL"
}

class DefaultIntegerColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_default

  val sql = "INTEGER"
}

class DefaultTimestampColumnDefinition(name : String,
                                       options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  val sql = column_sql("TIMESTAMP")
}

class DefaultVarcharColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  val sql = column_sql("VARCHAR")
}
