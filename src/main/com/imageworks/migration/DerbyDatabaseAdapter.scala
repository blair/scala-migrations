package com.imageworks.migration

class DerbyIntegerColumnDefinition(name : String,
                                   options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  val sql = "INTEGER"
}

class DerbyVarbinaryColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_limit

  val sql = if (limit.isDefined) {
              "VARCHAR(" + limit.get + ") FOR BIT DATA"
            }
            else {
              "VARCHAR FOR BIT DATA"
            }
}

class DerbyDatabaseAdapter
  extends DatabaseAdapter
{
  def new_column_definition(column_name : String,
                            column_type : SqlType,
                            options : List[ColumnOption]) : ColumnDefinition =
  {
    column_type match {
      case BooleanType => {
        val message = "Derby does not support a boolean type, you must " +
                      "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case CharType =>
        new DefaultCharColumnDefinition(column_name, options)
      case IntegerType =>
        new DerbyIntegerColumnDefinition(column_name, options)
      case VarbinaryType =>
        new DerbyVarbinaryColumnDefinition(column_name, options)
      case VarcharType =>
        new DefaultVarcharColumnDefinition(column_name, options)
    }
  }

  def remove_index_sql(schema_name_opt : Option[String],
                       table_name : String,
                       index_name : String) : String =
  {
    "DROP INDEX " +
    quote_column_name(index_name)
  }
}
