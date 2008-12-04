package com.imageworks.migration

class DerbyTimestampColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  // Derby does not take a size specifier for TIMESTAMP types.
  check_for_default()

  val sql = column_sql("TIMESTAMP")
}

class DerbyVarbinaryColumnDefinition(name : String,
                                     options : List[ColumnOption])
  extends ColumnDefinition(name, options)
{
  check_for_default()
  check_for_limit()

  val sql = column_sql("VARCHAR") + " FOR BIT DATA"
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
      case BigintType =>
        new DefaultBigintColumnDefinition(column_name, options)
      case CharType =>
        new DefaultCharColumnDefinition(column_name, options)
      case DecimalType =>
        new DefaultDecimalColumnDefinition(column_name, options)
      case IntegerType =>
        new DefaultIntegerColumnDefinition(column_name, options)
      case TimestampType =>
        new DerbyTimestampColumnDefinition(column_name, options)
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
