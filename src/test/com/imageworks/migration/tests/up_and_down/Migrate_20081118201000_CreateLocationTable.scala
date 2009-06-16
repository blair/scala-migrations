package com.imageworks.migration.tests.up_and_down

import com.imageworks.migration.Migration

class Migrate_20081118201000_CreateLocationTable
  extends Migration
{
  def up : Unit =
  {
    create_table("location") { t =>
      t.varbinary("pk_location", PrimaryKey, Limit(16))
      t.varchar("name", Unique, Limit(255), NotNull)
    }
  }

  def down : Unit =
  {
    drop_table("location")
  }
}
