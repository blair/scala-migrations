package com.imageworks.migration

import org.slf4j.LoggerFactory

/**
 * A Tuple2 like class for containing a table name and a list of colum
 * names.
 */
class TableColumnDefinition(val table_name : String,
                            val column_names : Array[String])

/**
 * A container class for storing the table and column names a foreign
 * key reference is on.
 */
class On(definition : TableColumnDefinition)
{
  val table_name = definition.table_name
  val column_names = definition.column_names
}

/**
 * A container class for storing the table and column names a foreign
 * key reference references.
 */
class References(definition : TableColumnDefinition)
{
  val table_name = definition.table_name
  val column_names = definition.column_names
}

/**
 * Due to the JVM erasure, the scala.Predef.ArrowAssoc.->
 * method generates a Tuple2 and the following cannot be distinguished
 *
 *   "table_name" -> "column1"
 *
 *   "table_name" -> ("column1", "column2")
 *
 * After erasure a Tuple2[String,String] is identical to a
 * Tuple2[String,Tuple2[String,String]].  So to work around this, the
 * -> operator is redefined to operate only on String's, which
 * effectively removes the type from the first type of the Tuple2 and
 * allows it to be overloaded on the second type of the Tuple2.  The
 * MigrationArrowAssoc class has the new -> method.
 */
class MigrationArrowAssoc(s : String)
{
  def `->`(other : String) : TableColumnDefinition =
  {
    new TableColumnDefinition(s, Array(other))
  }

  def `->`(other : Tuple2[String,String]) : TableColumnDefinition =
  {
    new TableColumnDefinition(s, Array(other._1, other._2))
  }
}

abstract class Migration
{
  private final
  val logger = LoggerFactory.getLogger(this.getClass)

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
   * Override the -> implicit definition to create a
   * MigrationArrowAssoc instead of a scala.Predef.ArrowAssoc.  See
   * the above comment on the MigrationArrowAssoc class why this is
   * done.
   */
  implicit def stringToMigrationArrowAssoc(s : String) : MigrationArrowAssoc =
  {
    new MigrationArrowAssoc(s)
  }

  /**
   * Convert a table and column name definition into a On foreign key
   * instance.
   */
  def on(definition : TableColumnDefinition) : On =
  {
    new On(definition)
  }

  /**
   * Convert a table and column name definition into a References
   * foreign key instance.
   */
  def references(definition : TableColumnDefinition) : References =
  {
    new References(definition)
  }

  final
  def execute(sql : String) : Unit =
  {
    connection.createStatement.execute(sql)
  }

  final
  def create_table(table_name : String,
                   options : TableOption*)
                  (body : TableDefinition => Unit) : Unit =
  {
    val table_definition = new TableDefinition(adapter, table_name)

    body(table_definition)

    val sql = new java.lang.StringBuilder(512)
                .append("CREATE TABLE ")
                .append(adapter.quote_table_name(table_name))
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
                .append(adapter.quote_table_name(table_name))
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
        logger.warn("Redefining the index name from '{}' to '{}'.",
                    index_name_opt.get +
                    name)
      }
      index_name_opt = Some(name)
    }

    val name = index_name_opt.getOrElse {
                 "idx_" +
                 table_name +
                 "_" +
                 column_names.mkString("_")
               }

    (name, opts)
  }

  final
  def add_index(table_name : String,
                column_names : Array[String],
                options : IndexOption*) : Unit =
  {
    if (column_names.isEmpty) {
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

    val sql = new java.lang.StringBuilder(512)
               .append("CREATE ")
               .append(if (unique) "UNIQUE " else "")
               .append("INDEX ")
               .append(adapter.quote_column_name(name))
               .append(" ON ")
               .append(adapter.quote_table_name(table_name))
               .append(" (")
               .append(quoted_column_names)
               .append(")")
               .toString

    execute(sql)
  }

  final
  def add_index(table_name : String,
                column_name : String,
                options : IndexOption*) : Unit =
  {
    add_index(table_name, Array(column_name), options : _*)
  }

  final
  def remove_index(table_name : String,
                   column_names : Array[String],
                   options : Name*) : Unit =
  {
    if (column_names.isEmpty) {
      throw new IllegalArgumentException("Removing an index requires at " +
                                         "least one column name.")
    }

    val (name, opts) = index_name(table_name, column_names, options : _*)

    val sql = adapter.remove_index_sql(table_name, name)

    execute(sql)
  }

  final
  def remove_index(table_name : String,
                   column_name : String,
                   options : Name*) : Unit =
  {
    remove_index(table_name, Array(column_name), options : _*)
  }

  /**
   * Given a foreign key relationship, create a name for it, using a
   * Name() if it is provided in the options.
   *
   * @param on the table and columns the foreign key contraint is on
   * @param references the table and columns the foreign key contraint
   *        references
   * @options a varargs list of ForeignKeyOption's
   * @return a Tuple2 with the caclulated name or the overriden name
   *         from a Name and the remaining options
   */
  private
  def foreign_key_name(on : On,
                       references : References,
                       options : ForeignKeyOption*) : Tuple2[String,List[ForeignKeyOption]] =
  {
    var opts = options.toList

    var fk_name_opt : Option[String] = None

    for (opt @ Name(name) <- opts) {
      opts -= opt
      if (fk_name_opt.isDefined && fk_name_opt.get != name) {
        logger.warn("Redefining the foreign key name from '{}'' to '{}'.",
                    fk_name_opt.get,
                    name)
      }
      fk_name_opt = Some(name)
    }

    val name = fk_name_opt.getOrElse {
                 "fk_" +
                 on.table_name +
                 "_" +
                 on.column_names.mkString("_") +
                 "_" +
                 references.table_name +
                 "_" +
                 references.column_names.mkString("_")
               }

    (name, opts)
  }

  def add_foreign_key(on : On,
                      references : References,
                      options : ForeignKeyOption*) : Unit =
  {
    if (on.column_names.length == 0) {
      throw new IllegalArgumentException("Adding a foreign key constraint " +
                                         "requires at least one column name " +
                                         "in the table adding the constraint.")
    }

    if (references.column_names.length == 0) {
      throw new IllegalArgumentException("Adding a foreign key constraint " +
                                         "requires at least one column name " +
                                         "from the table being referenced.")
    }

    var (name, opts) = foreign_key_name(on, references, options : _*)

    val quoted_on_column_names = on.column_names.map {
                                   adapter.quote_column_name(_)
                                 }.mkString(", ")

    val quoted_references_column_names = references.column_names.map {
                                           adapter.quote_column_name(_)
                                         }.mkString(", ")

    var on_delete_opt : Option[OnDelete] = None

    for (opt @ OnDelete(action) <- opts) {
      if (on_delete_opt.isDefined && action != on_delete_opt.get.action) {
        logger.warn("Overriding the ON DELETE action from '{}' to '{}'.",
                    on_delete_opt.get.action,
                    action)
      }
      opts -= opt
      on_delete_opt = Some(opt)
    }

    var on_update_opt : Option[OnUpdate] = None

    for (opt @ OnUpdate(action) <- opts) {
      if (on_update_opt.isDefined && action != on_update_opt.get.action) {
        logger.warn("Overriding the ON UPDATE action from '{}' to '{}'.",
                    on_update_opt.get.action,
                    action)
      }
      opts -= opt
      on_update_opt = Some(opt)
    }

    val sql = new java.lang.StringBuilder(512)
               .append("ALTER TABLE ")
               .append(adapter.quote_table_name(on.table_name))
               .append(" ADD CONSTRAINT ")
               .append(name)
               .append(" FOREIGN KEY (")
               .append(quoted_on_column_names)
               .append(") REFERENCES ")
               .append(adapter.quote_table_name(references.table_name))
               .append(" (")
               .append(quoted_references_column_names)
               .append(")")

    on_delete_opt match {
      case Some(on_delete) => {
        sql.append(" ON DELETE ")
        sql.append(on_delete.action.sql)
      }
      case None =>
    }

    on_update_opt match {
      case Some(on_update) => {
        sql.append(" ON UPDATE ")
        sql.append(on_update.action.sql)
      }
      case None =>
    }

    execute(sql.toString)
  }

  def add_foreign_key(references : References,
                      on : On,
                      options : ForeignKeyOption*) : Unit =
  {
    add_foreign_key(on, references, options : _*)
  }

  def remove_foreign_key(on : On,
                         references : References,
                         options : Name*) : Unit =
  {
    if (on.column_names.length == 0) {
      throw new IllegalArgumentException("Removing a foreign key constraint " +
                                         "requires at least one column name " +
                                         "in the table adding the constraint.")
    }

    if (references.column_names.length == 0) {
      throw new IllegalArgumentException("Removing a foreign key constraint " +
                                         "requires at least one column name " +
                                         "from the table being referenced.")
    }

    var (name, opts) = foreign_key_name(on, references, options : _*)

    execute("ALTER TABLE " +
            adapter.quote_table_name(on.table_name) +
            " DROP CONSTRAINT " +
            name)
  }

  def remove_foreign_key(references : References,
                         on : On,
                         options : Name*) : Unit =
  {
    remove_foreign_key(on, references, options : _*)
  }

  final
  def grant(table_name : String,
            grantees : Array[String],
            privileges : GrantPrivilegeType*) : Unit =
  {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Granting permissions requires " +
                                         "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Granting permissions requires " +
                                         "at least one privilege.")
    }

    val sql = adapter.grant_sql(table_name,
                                grantees,
                                privileges : _*)

    execute(sql)
  }

  final
  def grant(table_name : String,
            grantee : String,
            privileges : GrantPrivilegeType*) : Unit =
  {
    grant(table_name, Array(grantee), privileges : _*)
  }

  final
  def revoke(table_name : String,
             grantees : Array[String],
             privileges : GrantPrivilegeType*) : Unit =
  {
    if (grantees.isEmpty) {
      throw new IllegalArgumentException("Revoking permissions requires " +
                                         "at least one grantee.")
    }

    if (privileges.isEmpty) {
      throw new IllegalArgumentException("Revoking permissions requires " +
                                         "at least one privilege.")
    }

    val sql = adapter.revoke_sql(table_name,
                                 grantees,
                                 privileges : _*)

    execute(sql)
  }

  final
  def revoke(table_name : String,
             grantee : String,
             privileges : GrantPrivilegeType*) : Unit =
  {
    revoke(table_name, Array(grantee), privileges : _*)
  }

  def add_check(on : On,
                expr : String,
                options : CheckOption*) : Unit =
  {
    if (on.column_names.isEmpty) {
      throw new IllegalArgumentException("Adding a check constraint " +
                                         "requires at least one column name " +
                                         "in the table adding the constraint.")
    }

    var (name, opts) = adapter.generate_check_constraint_name(on, options : _*)

    val quoted_on_column_names = on.column_names.map {
                                   adapter.quote_column_name(_)
                                 }.mkString(", ")

    val sql = new java.lang.StringBuilder(512)
               .append("ALTER TABLE ")
               .append(adapter.quote_table_name(on.table_name))
               .append(" ADD CONSTRAINT ")
               .append(name)
               .append(" CHECK (")
               .append(expr)
               .append(")")

    execute(sql.toString)
  }

  def remove_check(on : On,
                   options : Name*) : Unit =
  {
    if (on.column_names.isEmpty) {
      throw new IllegalArgumentException("Removing a check constraint " +
                                         "requires at least one column name " +
                                         "in the table adding the constraint.")
    }

    var (name, opts) = adapter.generate_check_constraint_name(on, options : _*)

    execute("ALTER TABLE " +
            adapter.quote_table_name(on.table_name) +
            " DROP CONSTRAINT " +
            name)
  }

}
