package com.imageworks.migration

abstract class Migration
{
  /**
   * Concrete migration classes must define this method to migrate the
   * database up to a new migration.
   */
  def up : Unit

  /**
   * Concrete migration classes must define this method to back out of
   * this migration.  If the migration cannot be reversed, then a
   * IrreversibleMigrationException should be thrown.
   */
  def down : Unit

  /**
   * The connection to the database that will be used for the
   * migration.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  var connection : java.sql.Connection = _

  /**
   * The database adapter that will be used for the migration.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  var adapter : DatabaseAdapter = _

  /**
   * The schema name that will be used for the migration.
   *
   * This is set using property style dependency injection instead of
   * constructor style injection, which makes for cleaner code for the
   * users of this migration framework.
   */
  var schema_name_opt : Option[String] = None

  final
  def execute(sql : String) : Unit =
  {
    System.out.println("Executing '" + sql + "'")
    connection.createStatement.execute(sql)
  }

  final
  def create_table(table_name : String,
                   options : TableOption*)
                  (body : TableDefinition => Unit) : Unit =
  {
    val table_definition = new TableDefinition(adapter)

    body(table_definition)

    val sql = new java.lang.StringBuilder(512)
                .append("CREATE TABLE ")
                .append(adapter.quote_table_name(schema_name_opt, table_name))
                .append(" (")
                .append(table_definition.to_sql)
                .append(')')
                .toString
    execute(sql)
  }

  final
  def drop_table(table_name : String) : Unit =
  {
    val sql = new java.lang.StringBuilder(512)
                .append("DROP TABLE ")
                .append(adapter.quote_table_name(schema_name_opt, table_name))
                .toString
    execute(sql)
  }

  private
  def index_name(table_name : String,
                 column_names : Array[String],
                 options : IndexOption*) : Tuple2[String,List[IndexOption]] =
  {
    var opts = options.toList

    var index_name_opt : Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts -= opt
      if (index_name_opt.isDefined && index_name_opt.get != name) {
        val message = "Redefining the index name from '" +
                      index_name_opt.get +
                      "' to '" +
                      name +
                      "'."
        System.out.println(message)
      }
      index_name_opt = Some(name)
    }

    val name = index_name_opt.getOrElse {
                 "index_" +
                 table_name +
                 "_on_" +
                 column_names.mkString("_and_")
               }

    (name, opts)
  }

  def add_index(table_name : String,
                column_names : Array[String],
                options : IndexOption*) : Unit =
  {
    if (column_names.length == 0) {
      throw new IllegalArgumentException("Adding an index requires at " +
                                         "least one column name.")
    }

    var (name, opts) = index_name(table_name, column_names, options : _*)

    var unique = false
    for (option @ Unique <- opts) {
      opts -= option
      unique = true
    }

    val quoted_column_names = column_names.map {
                                adapter.quote_column_name(_)
                              }.mkString(", ")

    val sql = new java.lang.StringBuilder(256)
               .append("CREATE ")
               .append(if (unique) "UNIQUE " else "")
               .append("INDEX ")
               .append(adapter.quote_column_name(name))
               .append(" ON ")
               .append(adapter.quote_table_name(schema_name_opt, table_name))
               .append(" (")
               .append(quoted_column_names)
               .append(")")
               .toString

    execute(sql)
  }

  def remove_index(table_name : String,
                   column_names : Array[String],
                   options : Name*) : Unit =
  {
    if (column_names.length == 0) {
      throw new IllegalArgumentException("Removing an index requires at " +
                                         "least one column name.")
    }

    val (name, opts) = index_name(table_name, column_names, options : _*)

    val sql = new java.lang.StringBuilder(256)
               .append("DROP INDEX ")
               .append(adapter.quote_column_name(name))
               .append(" ON ")
               .append(adapter.quote_table_name(schema_name_opt, table_name))
               .toString

    execute(sql)
  }

}
