package com.imageworks.migration

class PostgresqlByteaColumnDefinition
  extends ColumnDefinition
{
  val sql = "BYTEA"
}

class PostgresqlDatabaseAdapter(override val schema_name_opt : Option[String])
  extends DatabaseAdapter(schema_name_opt)
{
  override protected
  val unquoted_name_converter = LowercaseUnquotedNameConverter

  override
  def column_definition_factory
    (column_type : SqlType,
     character_set_opt : Option[CharacterSet]) : ColumnDefinition =
  {
    character_set_opt match {
      case None =>
      case Some(set @ CharacterSet(_)) => {
        logger.warn("Ignoring '{}' as the character set encoding can only " +
                    "be specified in PostgreSQL when the database is created.",
                    set)
      }
    }

    column_type match {
      case BigintType =>
        new DefaultBigintColumnDefinition
      case BlobType =>
        new PostgresqlByteaColumnDefinition
      case BooleanType =>
        new DefaultBooleanColumnDefinition
      case CharType =>
        new DefaultCharColumnDefinition
      case DecimalType =>
        new DefaultDecimalColumnDefinition
      case IntegerType =>
        new DefaultIntegerColumnDefinition
      case TimestampType =>
        new DefaultTimestampColumnDefinition
      case SmallintType =>
        new DefaultSmallintColumnDefinition
      case VarbinaryType =>
        new PostgresqlByteaColumnDefinition
      case VarcharType =>
        new DefaultVarcharColumnDefinition
    }
  }

  override
  def remove_index_sql(schema_name_opt : Option[String],
                       table_name : String,
                       index_name : String) : String =
  {
    "DROP INDEX " +
    quote_column_name(index_name)
  }
}
