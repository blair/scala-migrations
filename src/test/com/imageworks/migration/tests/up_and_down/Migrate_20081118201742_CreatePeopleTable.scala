package com.imageworks.migration.tests.up_and_down

import com.imageworks.migration.Migration

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
      t.integer("age", Limit(3), NotNull)
    }

    add_index("people", "ssn", Unique)

    add_foreign_key(on("people" -> "pk_location"),
                    references("location" -> "pk_location"),
                    OnDelete(Cascade),
                    OnUpdate(Restrict))
  }

  def down : Unit =
  {
    remove_foreign_key(on("people" -> "pk_location"),
                       references("location" -> "pk_location"))
    remove_index("people", "ssn")
    drop_table("people")
  }
}
