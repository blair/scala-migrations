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
  with ColumnSupportsDefault
  with ColumnSupportsLimit
{
  def sql = column_sql("VARCHAR") + " FOR BIT DATA"
}

class DerbyDatabaseAdapter(override val schema_name_opt : Option[String])
  extends DatabaseAdapter(schema_name_opt)
{
  def column_definition_factory(column_type : SqlType,
                                character_set_opt : Option[CharacterSet]) : ColumnDefinition =
  {
    character_set_opt match {
      case None =>
      case Some(CharacterSet(Unicode)) =>
      case Some(set @ CharacterSet(_)) => {
        logger.warn("Ignoring '{}' as Derby uses Unicode sequences to " +
                    "represent character data types.",
                    set)
      }
    }

    column_type match {
      case BlobType =>
        new DefaultBlobColumnDefinition
      case BigintType =>
        new DefaultBigintColumnDefinition
      case BooleanType => {
        val message = "Derby does not support a boolean type, you must " +
                      "choose a mapping your self."
        throw new UnsupportedColumnTypeException(message)
      }
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new DefaultIntegerColumnDefinition
      case TimestampType =>
        new DerbyTimestampColumnDefinition
      case SmallintType =>
        new DefaultSmallintColumnDefinition
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
