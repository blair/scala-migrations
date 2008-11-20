package com.imageworks.migration

class OracleIntegerColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  val sql = if (limit.isDefined) {
              "NUMBER(" + limit.get + ")"
            }
            else {
              "NUMBER(38,0)"
            }
}

class OracleVarbinaryColumnDefinition(name : String,
                                      options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  if (! limit.isDefined) {
    val message = "In Oracle, a RAW column must always specify its size."
    throw new IllegalArgumentException(message)
  }

  val sql = "RAW(" + limit.get + ")"
}

class OracleVarcharColumnDefinition(name : String,
                                    options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  val sql = if (limit.isDefined) {
              "VARCHAR2(" + limit.get + ")"
            }
            else {
              "VARCHAR2"
            }
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
      case CharType =>
        new DefaultCharColumnDefinition(column_name, options)
      case IntegerType =>
        new OracleIntegerColumnDefinition(column_name, options)
      case VarbinaryType =>
        new OracleVarbinaryColumnDefinition(column_name, options)
      case VarcharType =>
        new OracleVarcharColumnDefinition(column_name, options)
    }
  }
}
