package com.imageworks.migration

class TableDefinition(adapter : DatabaseAdapter)
{
  private
  val columns = new scala.collection.mutable.ListBuffer[ColumnDefinition]

  final
  def to_sql : String =
  {
    columns.map(_.to_sql).mkString("", ", ", "")
  }

  final
  def column(name : String,
             column_type : SqlType,
             options: ColumnOption*) : TableDefinition =
  {
    val column = adapter.new_column_definition(name,
                                               column_type,
                                               options.toList)
    columns += column
    this
  }

  final
  def bigint(name : String,
             options: ColumnOption*) : TableDefinition =
  {
    column(name, BigintType, options : _*)
  }

  final
  def boolean(name : String,
              options: ColumnOption*) : TableDefinition =
  {
   column(name, BooleanType, options : _*)
  }

  final
  def char(name : String,
           options: ColumnOption*) : TableDefinition =
  {
    column(name, CharType, options : _*)
  }

  final
  def decimal(name: String,
              options: ColumnOption*) : TableDefinition =
  {
    column(name, DecimalType, options : _*)
  }

  final
  def integer(name : String,
              options: ColumnOption*) : TableDefinition =
  {
    column(name, IntegerType, options : _*)
  }

  final
  def timestamp(name : String,
                options: ColumnOption*) : TableDefinition =
  {
    column(name, TimestampType, options : _*)
  }

  final
  def varbinary(name : String,
                options: ColumnOption*) : TableDefinition =
  {
    column(name, VarbinaryType, options : _*)
  }

  final
  def varchar(name : String,
              options: ColumnOption*) : TableDefinition =
  {
    column(name, VarcharType, options : _*)
  }
}
