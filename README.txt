This project is a Scala port of the Ruby on Rails migrations, which
see

http://api.rubyonrails.org/classes/ActiveRecord/Migration.html

A sample Scala migration looks like

class Migrate_20081118201742_CreatePeopleTable
  extends Migration
{
  def up : Unit =
  {
    create_table("people") { t =>
      t.varbinary("pk_people", PrimaryKey, Limit(16))
      t.varbinary("pk_location", Limit(16), NotNull)
      t.integer("employee_id", Unique)
      t.integer("ssn", NotNull)
      t.varchar("first_name", Limit(255), NotNull)
      t.char("middle_initial", Limit(1), Nullable)
      t.varchar("last_name", Limit(255), NotNull)
      t.timestamp("birthdate", Limit(0), NotNull)
      t.integer("vacation_days", NotNull, Default("0"))
      t.bigint("hire_time_micros", NotNull)
      t.decimal("salary", Precision(7), Scale(2), Check("salary > 0"))
      t.blob("image")
    }

    add_index("people", "ssn", Unique)

    add_foreign_key(on("people" -> "pk_location"),
                    references("location" -> "pk_location"),
                    OnDelete(Cascade),
                    OnUpdate(Restrict))

    add_check(on("people" -> "vacation_days"),
              "vacation_days >= 0")
  }

  def down : Unit =
  {
    drop_table("people")
  }
}

The style of the Migration classes used for Scala Migrations is
similar to the Ruby on Rails migrations.  The implementation uses the
same schema_migrations table appearing in Ruby on Rails 2.1 and
migration naming convention to manage the list of migrations that have
been applied to the database.

MIGRATION NAMING
----------------

In Scala Migrations, the migrations needs to be compiled and their
*.class files need to be made available at runtime; the source files
will not be available at runtime.

Scala Migrations then needs to know an ordering on the migrations, so
the timestamp needs to be in the class name.  Scala does not support
naming a symbol such as 20080717013526_YourMigrationName because the
name begins with a digit (unless one were to quote the name which
would look odd), so the Scala Migrations looks for classes named

    Migrate_(\\d+)_([_a-zA-Z0-9]*)

This is different than Ruby on Rails migrations which are in filenames
of the form

    20080717013526_your_migration_name.rb

and have a corresponding class name such as YourMigrationName.  Ruby
on Rails can find all the migration *.rb files for a project and load
them at runtime and from the filename load the correct class name.
The ordering to apply the migrations is contained in the filename, not
the class name.

CAVATS
------

1) Index and foreign key names do not use the same naming convention
   as the Ruby on Rails migrations, so a port of Ruby on Rails
   migrations to Scala Migrations should specify the index name using
   the Name() case class as an option to add_index() or
   remove_index().

2) Only Oracle and Derby databases are currently supported.
