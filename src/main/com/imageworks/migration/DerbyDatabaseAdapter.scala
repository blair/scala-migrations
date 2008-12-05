package com.imageworks.migration

class DerbyTimestampColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsDefault
{
  // Derby does not take a size specifier for TIMESTAMP types.
  def sql = column_sql("TIMESTAMP")
}

class DerbyVarbinaryColumnDefinition
  extends ColumnDefinition
  with ColumnSupportsLimit
  with ColumnSupportsDefault
{
  def sql = column_sql("VARCHAR") + " FOR BIT DATA"
}

class DerbyDatabaseAdapter
  extends DatabaseAdapter
{
  def new_column_definition(table_name : String,
                            column_name : String,
                            column_type : SqlType,
                            options : List[ColumnOption]) : ColumnDefinition =
  {
    column_definition_factory(table_name, column_name, column_type, options) {
      case BooleanType => {
        val message = "Derby does not support a boolean type, you must " +
                      "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case BigintType =>
        new DefaultBigintColumnDefinition
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new DefaultIntegerColumnDefinition
      case TimestampType =>
        new DerbyTimestampColumnDefinition
      case VarbinaryType =>
        new DerbyVarbinaryColumnDefinition
      case VarcharType =>
        new DefaultVarcharColumnDefinition
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
