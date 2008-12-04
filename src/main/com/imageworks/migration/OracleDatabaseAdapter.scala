package com.imageworks.migration

class OracleDecimalColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends AbstractDecimalColumnDefinition(name, options)
{
  val decimal_sql_name = "NUMBER"
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
 * running against an Oracle database to such that is does not assume
 * it can use 38-digit integer values in case the data needs to be
 * exported to another database or if the code needs to work with
 * other databases.  Columns wishing to use a NUMBER(38) should use a
 * DecimalType column.
 */
class OracleIntegerColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_default

  val sql = "NUMBER(10, 0)"
}

class OracleVarbinaryColumnDefinition(name : String,
                                      options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  if (! limit.isDefined) {
    val message = "In Oracle, a RAW column must always specify its size."
    throw new IllegalArgumentException(message)
  }

  val sql = column_sql("RAW")
}

class OracleVarcharColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  val sql = column_sql("VARCHAR2")
}

class OracleDatabaseAdapter
  extends DatabaseAdapter
{
  def new_column_definition(column_name : String,
                            column_type : SqlType,
                            options : List[ColumnOption]) : ColumnDefinition =
  {
    column_type match {
      case BooleanType => {
        val message = "Oracle does not support a boolean type, you must " +
                      "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case BigintType =>
        new OracleIntegerColumnDefinition(column_name, options)
      case CharType =>
        new DefaultCharColumnDefinition(column_name, options)
      case DecimalType =>
        new OracleDecimalColumnDefinition(column_name, options)
      case IntegerType =>
        new OracleIntegerColumnDefinition(column_name, options)
      case TimestampType =>
        new DefaultTimestampColumnDefinition(column_name, options)
      case VarbinaryType =>
        new OracleVarbinaryColumnDefinition(column_name, options)
      case VarcharType =>
        new OracleVarcharColumnDefinition(column_name, options)
    }
  }

  def remove_index_sql(schema_name_opt : Option[String],
                       table_name : String,
                       index_name : String) : String =
  {
    "DROP INDEX " +
    quote_column_name(index_name) +
    " ON " +
    quote_table_name(schema_name_opt, table_name)
  }

  override
  def grant_sql(schema_name_opt : Option[String],
                table_name : String,
                grantees : Array[String],
                privileges : GrantPrivilegeType*) : String =
  {
    // Check that no columns are defined for any SELECT privs
    for {
      SelectPrivilege(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support granting select to " +
                    "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.grant_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }

  override
  def revoke_sql(schema_name_opt : Option[String],
                 table_name : String,
                 grantees : Array[String],
                 privileges : GrantPrivilegeType*) : String =
  {
    // Check that no columns are defined for any privs with columns
    for {
      PrivilegeWithColumns(columns) <- privileges
      if !columns.isEmpty
    } {
      val message = "Oracle does not support revoking permissions from " +
                    "individual columns"
      throw new IllegalArgumentException(message)
    }

    super.revoke_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }
}
