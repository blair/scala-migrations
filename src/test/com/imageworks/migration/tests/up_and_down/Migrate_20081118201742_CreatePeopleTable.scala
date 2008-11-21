package com.imageworks.migration.tests.up_and_down

import com.imageworks.migration.Migration

class Migrate_20081118201742_CreatePeopleTable
  extends Migration
{
  def up : Unit =
  {
    create_table("people") { t =>
      t.varbinary("pk_people", Limit(16), NotNull)
      t.varchar("first_name", Limit(255), NotNull)
      t.char("middle_initial", Limit(1), Nullable)
      t.varchar("last_name", Limit(255), NotNull)
      t.integer("age", Limit(3), NotNull)
    }

    add_index("people", "pk_people", Unique)
  }

  def down : Unit =
  {
    remove_index("people", "pk_people")
    drop_table("people")
  }
}
