package com.imageworks.migration

class OracleDecimalColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends AbstractDecimalColumnDefinition(name, options)
{
  val decimal_sql_name = "NUMBER"
}

class OracleIntegerColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit
  check_for_default

  val sql = column_sql("NUMBER", limit.orElse(Some("38,0")))
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
