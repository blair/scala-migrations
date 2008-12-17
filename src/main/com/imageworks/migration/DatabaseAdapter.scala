package com.imageworks.migration

import org.slf4j.LoggerFactory

object DatabaseAdapter
{
  /**
   * Look for the appropriate database adapter for the given database
   * driver class name.
   *
   * @param driver_class_name the class name of the JDBC database
   *        driver
   * @param schema_name_opt an optional schema name used to qualify
   *        all table names in the generated SQL; if Some(), then all
   *        table names are qualified with the name, otherwise, table
   *        names are unqualified
   * @return a DriverAdapter suitable to use for the database
   * @throws java.lang.IllegalArgumentException if the argument is
   *         null, scala.MatchError if an appropriate DatabaseAdapter
   *         cannot be found
   */
  def for_driver(driver_class_name : String,
                 schema_name_opt : Option[String]) : DatabaseAdapter =
  {
    driver_class_name match {
      case "oracle.jdbc.driver.OracleDriver" => {
        new OracleDatabaseAdapter(schema_name_opt)
      }
      case "org.apache.derby.jdbc.EmbeddedDriver" => {
        new DerbyDatabaseAdapter(schema_name_opt)
      }
      case "org.apache.derby.jdbc.ClientDriver" => {
        new DerbyDatabaseAdapter(schema_name_opt)
      }
      case null => {
        throw new IllegalArgumentException("Must pass a non-null JDBC " +
                                           "driver class name to this " +
                                           "function.")
      }
      case _ => {
        throw new scala.MatchError("No DatabaseAdapter can be found for " +
                                   "the JDBC driver class '" +
                                   driver_class_name +
                                   "'.'")
      }
    }
  }

  /**
   * Look for the appropriate database adapter for the given database
   * driver class.
   *
   * @param driver_class the class of the JDBC database driver
   * @param schema_name_opt an optional schema name used to qualify
   *        all table names in the generated SQL; if Some(), then all
   *        table names are qualified with the name, otherwise, table
   *        names are unqualified
   * @return a DriverAdapter suitable to use for the database
   * @throws java.lang.IllegalArgumentException if the argument is
   *         null, scala.MatchError if an appropriate DatabaseAdapter
   *         cannot be found
   */
  def for_driver(driver_class : Class[_],
                 schema_name_opt : Option[String]) : DatabaseAdapter =
  {
    if (driver_class eq null) {
      throw new IllegalArgumentException("Must pass a non-null JDBC " +
                                         "driver class to this function.")
    }
    else {
      for_driver(driver_class.getName, schema_name_opt)
    }
  }
}

/**
 * Base class for classes to customize SQL generation for specific
 * database drivers.
 *
 * @param schema_name_opt an optional schema name used to qualify
 *        all table names in the generated SQL; if Some(), then all
 *        table names are qualified with the name, otherwise, table
 *        names are unqualified
 */
abstract
class DatabaseAdapter(val schema_name_opt : Option[String])
{
  private final
  val logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Given a table name, column name and column data type, return a
   * newly constructed and fully initialized ColumnDefinition.  The
   * class of the returned ColumnDefinition only depends upon the
   * input column data type.
   *
   * @param table_name the name of the table the column is in
   * @param column_name the column's name
   * @param column_type the data type of the column
   * @param options a list of column options customizing the column
   * @return a new ColumnDefinition
   */
  def new_column_definition(table_name : String,
                            column_name : String,
                            column_type : SqlType,
                            options : List[ColumnOption]) : ColumnDefinition =
  {
    val d = column_definition_factory(column_type)

    d.adapter = this
    d.table_name = table_name
    d.column_name = column_name
    d.options = options

    d.initialize()

    d
  }

  /**
   * Concrete subclasses must define this method that returns a newly
   * constructed, but uninitialized, concrete ColumnDefinition
   * subclass for the given SQL data type.
   *
   * @param column_type the column's data type
   * @return a newly constructed but uninitialized ColumnDefinition
   *         for the column_type
   */
  protected
  def column_definition_factory(column_type : SqlType) : ColumnDefinition

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

  def quote_table_name(table_name : String) : String =
  {
    // use the default schema_name_opt defined in the adapter
    quote_table_name(schema_name_opt, table_name)
  }

  /**
   * Different databases require different SQL to drop an index.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param index_name the name of the index
   * @return the SQL to drop the index
   */
  def remove_index_sql(schema_name_opt : Option[String],
                       table_name : String,
                       index_name : String) : String

  /**
   * Different databases require different SQL to drop an index.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param index_name the name of the index
   * @return the SQL to drop the index
   */
  def remove_index_sql(table_name : String,
                       index_name : String) : String = {
    remove_index_sql(schema_name_opt, table_name, index_name)
  }

  private
  def grant_revoke_common(action : String,
                          preposition : String,
                          schema_name_opt : Option[String],
                          table_name : String,
                          grantees : Array[String],
                          privileges : GrantPrivilegeType*) : String =
  {
    // The GRANT and REVOKE syntax is basically the same
    val sql = new java.lang.StringBuilder(256)
               .append(action)
               .append(" ")

    def formatColumns(columns : Seq[String]) : String = {
      if (columns.isEmpty) {
        ""
      }
      else {
        columns.mkString(" (", ", ", ")")
      }
    }

    sql.append((
      for (priv <- privileges) yield priv match {
        case AllPrivileges =>
          "ALL PRIVILEGES"
        case DeletePrivilege =>
          "DELETE"
        case InsertPrivilege =>
          "INSERT"
        case TriggerPrivilege =>
          "TRIGGER"

        case ReferencesPrivilege =>
          "REFERENCES"
        case SelectPrivilege =>
          "SELECT"
        case UpdatePrivilege =>
          "UPDATE"

        case ReferencesPrivilege(columns) =>
          "REFERENCES" + formatColumns(columns)
        case SelectPrivilege(columns) =>
          "SELECT" + formatColumns(columns)
        case UpdatePrivilege(columns) =>
          "UPDATE" + formatColumns(columns)
      }).mkString(", "))

    sql.append(" ON ")
       .append(quote_table_name(table_name))
       .append(" ")
       .append(preposition)
       .append(" ")
       .append(grantees.mkString(", "))
       .toString
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def grant_sql(schema_name_opt : Option[String],
                table_name : String,
                grantees : Array[String],
                privileges : GrantPrivilegeType*) : String =
  {
    val sql = new java.lang.StringBuilder(256)
               .append("GRANT")

    grant_revoke_common("GRANT",
                        "TO",
                        schema_name_opt,
                        table_name,
                        grantees,
                        privileges : _*)
  }

  /**
   * Different databases have different limitations on the GRANT statement.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def grant_sql(table_name : String,
                grantees : Array[String],
                privileges : GrantPrivilegeType*) : String =
  {
    grant_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   *
   * @param schema_name_opt the optional schema name to qualify the
   *        table name
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def revoke_sql(schema_name_opt : Option[String],
                 table_name : String,
                 grantees : Array[String],
                 privileges : GrantPrivilegeType*) : String =
  {
    grant_revoke_common("REVOKE",
                        "FROM",
                        schema_name_opt,
                        table_name,
                        grantees,
                        privileges : _*)
  }

  /**
   * Different databases have different limitations on the REVOKE statement.
   * Uses the schema_name_opt defined in the adapter.
   *
   * @param table_name the name of the table with the index
   * @param grantees one or more objects to grant the new permissions to.
   * @param privileges one or more GrantPrivilegeType objects describing the
   *        types of permissions to grant.
   * @return the SQL to grant permissions
   */
  def revoke_sql(table_name : String,
                 grantees : Array[String],
                 privileges : GrantPrivilegeType*) : String =
  {
    revoke_sql(schema_name_opt, table_name, grantees, privileges : _*)
  }

  /**
   * Given a check constraint, create a name for it, using a Name() if it is
   * provided in the options.
   *
   * @param on the table and columns the check contraint is on
   * @param options a varargs list of CheckOptions
   * @return a Tuple2 with the caclulated name or the overriden name
   *         from a Name and the remaining options
   */
  def generate_check_constraint_name(on : On,
                                     options : CheckOption*)
    : Tuple2[String,List[CheckOption]] = {
    var opts = options.toList

    var chk_name_opt : Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts -= opt
      if (chk_name_opt.isDefined && chk_name_opt.get != name) {
        logger.warn("Redefining the check constraint name from '{}'' to '{}'.",
                    chk_name_opt.get,
                    name)
      }
      chk_name_opt = Some(name)
    }

    val name = chk_name_opt.getOrElse {
                 "chk_" +
                 on.table_name +
                 "_" +
                 on.column_names.mkString("_")
               }

    (name, opts)
  }
}
