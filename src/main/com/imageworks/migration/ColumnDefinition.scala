package com.imageworks.migration

abstract
class ColumnDefinition(name : String,
                       protected var options : List[ColumnOption])
{
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
        val message = "Redefining the default value for the '" +
                      name +
                      "' column from '" +
                      default.get +
                      "' to '" +
                      value +
                      "'."
        System.out.println(message)
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
        val message = "Redefining the limit for the XXX column " +
                      "from '" +
                      limit_.get +
                      "' to '" +
                      length +
                      "'."
        System.out.println(message)
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
          val message = "Redefining the XXX column's nullability."
          System.out.println(message)
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

  protected
  def sql : String

  final
  def to_sql : String =
  {
    // Warn for any unused options.
    if (! options.isEmpty) {
      val message = "The following options for the XXX column are unused: " +
                    options +
                    '.'
      System.out.println(message)
    }

    // Warn about illegal combinations in some databases.
    if (is_primary_key &&
        not_null_opt.isDefined &&
        not_null_opt.get == false) {
      val message = "Specifying a PRIMARY KEY and a NULL column is not " +
                    "supported in all databases."
      System.out.println(message)
    }

    val sb = new java.lang.StringBuilder()
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
