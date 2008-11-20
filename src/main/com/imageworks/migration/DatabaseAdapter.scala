package com.imageworks.migration

abstract
class DatabaseAdapter
{
  def new_column_definition(column_name : String,
                            column_type : SqlType,
                            options: List[ColumnOption]) : ColumnDefinition

  def quote_column_name(column_name : String) : String =
  {
    column_name
  }

  def quote_table_name(schema_name_opt : Option[String],
                       table_name : String) : String =
  {
    if (schema_name_opt.isDefined) {
      '"' +
      schema_name_opt.get +
      "\".\"" +
      table_name.toUpperCase +
      '"'
    }
    else {
      '"' + table_name.toUpperCase + '"'
    }
  }
}
