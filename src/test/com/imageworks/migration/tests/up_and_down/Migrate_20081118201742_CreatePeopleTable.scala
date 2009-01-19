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
      t.varchar("first_name", Limit(255), NotNull, CharacterSet(Unicode))
      t.char("middle_initial", Limit(1), Nullable)
      t.varchar("last_name", Limit(255), NotNull, CharacterSet(Unicode))
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
    remove_check(on("people" -> "vacation_days"))
    remove_foreign_key(on("people" -> "pk_location"),
                       references("location" -> "pk_location"))
    remove_index("people", "ssn")
    drop_table("people")
  }
}
