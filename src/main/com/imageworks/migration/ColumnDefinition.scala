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
  var limit_ : Option[Int] = None

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
                                  scala.Predef.int2Integer(limit_.get),
                                  scala.Predef.int2Integer(length)))
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
      sb.append(' ')
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
}

class DefaultCharColumnDefinition(name : String,
                                  options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  val sql = if (limit.isDefined) {
              "CHAR(" + limit.get + ")"
            }
            else {
              "CHAR"
            }
}

class DefaultVarcharColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  val sql = if (limit.isDefined) {
              "VARCHAR(" + limit.get + ")"
            }
            else {
              "VARCHAR"
            }
}
